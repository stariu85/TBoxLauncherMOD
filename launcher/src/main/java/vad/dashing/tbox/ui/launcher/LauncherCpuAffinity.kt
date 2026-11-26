package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import vad.dashing.tbox.ShellExecutor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated low-priority background coroutine dispatchers and optional experimental CPU 4–5
 * affinity helpers for SM6150 (8-core little/big: CPU 0–5 @ 1.59 GHz, CPU 6–7 @ 1.90 GHz).
 *
 * - Minimap JPEG decoding & light-theme conversion always run at [Process.THREAD_PRIORITY_BACKGROUND]
 *   and are pinned to CPU 4–5 (`0x30`) when the 8-core heterogeneous topology is confirmed.
 * - Optional experimental toggles can also pin Filament 3D rendering threads (`FEngine::loop`,
 *   `JobSystem::loop`, etc.) and CAN bus polling/request threads (`dcl-can-poll-*`,
 *   `mbcan-state-apply`, vendor `can-thread`) to CPU 4–5 (`0x30`) or restore full CPU mask (`0xff`).
 */
internal object LauncherCpuAffinity {
    private const val TAG = "LauncherCpuAffinity"
    internal const val EXPECTED_OCTA_CORE_COUNT = 8
    internal const val BACKGROUND_CPU_45_HEX_MASK = "30"
    private const val TASKSET_TIMEOUT_MS = 1_500L

    @Volatile
    private var pin3dRenderToCpu45Enabled: Boolean = false

    @Volatile
    private var pinCanBusToCpu45Enabled: Boolean = false

    private val pinCpu45Supported: Boolean by lazy {
        val cores = Runtime.getRuntime().availableProcessors()
        val cpu4 = readCpuMaxFreqKhz(4)
        val cpu5 = readCpuMaxFreqKhz(5)
        val cpu6 = readCpuMaxFreqKhz(6)
        val cpu7 = readCpuMaxFreqKhz(7)
        val supported = shouldPinBackgroundThreadsToCpu45(
            availableProcessors = cores,
            cpu4MaxFreqKhz = cpu4,
            cpu5MaxFreqKhz = cpu5,
            cpu6MaxFreqKhz = cpu6,
            cpu7MaxFreqKhz = cpu7,
        )
        Log.d(
            TAG,
            "CPU topology check: cores=$cores freq4=$cpu4 freq5=$cpu5 freq6=$cpu6 freq7=$cpu7 -> pinCpu45=$supported",
        )
        supported
    }

    private val affinityControlExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(
                {
                    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                    runnable.run()
                },
                "dcl-affinity-ctl",
            ).apply {
                isDaemon = true
            }
        }
    }

    /** Dedicated single-threaded background dispatcher for Yandex minimap JPEG decoding. */
    val minimapDecodeDispatcher: ExecutorCoroutineDispatcher by lazy {
        createBackgroundSingleThreadDispatcher("dcl-minimap-decode")
    }

    /** Dedicated single-threaded background dispatcher for minimap light-theme / bitmap processing. */
    val minimapLightConvertDispatcher: ExecutorCoroutineDispatcher by lazy {
        createBackgroundSingleThreadDispatcher("dcl-minimap-light")
    }

    /** Dedicated 2-thread dispatcher for CAN bus polling and CAN read/write requests. */
    val canBusDispatcher: ExecutorCoroutineDispatcher by lazy {
        createCanBusPoolDispatcher(threadCount = 2, baseName = "dcl-can-poll")
    }

    fun syncExperimentalFlagsFromStore(context: Context) {
        val appCtx = context.applicationContext
        val pin3d = LauncherAppConfigStore.experimentalPin3dRenderCpu45(appCtx)
        val pinCan = LauncherAppConfigStore.experimentalPinCanBusCpu45(appCtx)
        pin3dRenderToCpu45Enabled = pin3d
        pinCanBusToCpu45Enabled = pinCan
        if (pin3d) {
            reapply3dRenderAffinityIfEnabledAsync()
        }
        if (pinCan) {
            reapplyCanBusAffinityIfEnabledAsync()
        }
    }

    fun setPin3dRenderToCpu45(enabled: Boolean) {
        pin3dRenderToCpu45Enabled = enabled
        applyAffinityToProcessThreadsAsync(
            category = "3d-render",
            pinToCpu45 = enabled,
            matcher = ::is3dRenderThreadName,
        )
    }

    fun reapply3dRenderAffinityIfEnabledAsync() {
        if (!pin3dRenderToCpu45Enabled) return
        applyAffinityToProcessThreadsAsync(
            category = "3d-render",
            pinToCpu45 = true,
            matcher = ::is3dRenderThreadName,
        )
    }

    fun setPinCanBusToCpu45(enabled: Boolean) {
        pinCanBusToCpu45Enabled = enabled
        applyAffinityToProcessThreadsAsync(
            category = "can-bus",
            pinToCpu45 = enabled,
            matcher = ::isCanBusThreadName,
        )
    }

    fun reapplyCanBusAffinityIfEnabledAsync() {
        if (!pinCanBusToCpu45Enabled) return
        applyAffinityToProcessThreadsAsync(
            category = "can-bus",
            pinToCpu45 = true,
            matcher = ::isCanBusThreadName,
        )
    }

    internal fun parseCpuMaxFreqKhz(rawText: String?): Long? {
        val value = rawText?.trim()?.toLongOrNull() ?: return null
        return value.takeIf { it > 0L }
    }

    /**
     * Pure topology guard: pins background threads to CPU 4–5 (`0x30`) ONLY on confirmed
     * 8-core heterogeneous SoCs where CPU 4 & 5 are little cores relative to big cores CPU 6 & 7.
     */
    internal fun shouldPinBackgroundThreadsToCpu45(
        availableProcessors: Int,
        cpu4MaxFreqKhz: Long?,
        cpu5MaxFreqKhz: Long?,
        cpu6MaxFreqKhz: Long?,
        cpu7MaxFreqKhz: Long?,
    ): Boolean {
        if (availableProcessors != EXPECTED_OCTA_CORE_COUNT) return false
        if ((cpu4MaxFreqKhz == null) || (cpu5MaxFreqKhz == null) ||
            (cpu6MaxFreqKhz == null) || (cpu7MaxFreqKhz == null)
        ) {
            return false
        }
        return cpu4MaxFreqKhz > 0L &&
            cpu5MaxFreqKhz > 0L &&
            cpu6MaxFreqKhz > 0L &&
            cpu7MaxFreqKhz > 0L &&
            cpu4MaxFreqKhz < cpu6MaxFreqKhz &&
            cpu5MaxFreqKhz < cpu7MaxFreqKhz
    }

    internal fun resolveTargetCpuHexMask(pinToCpu45: Boolean, availableProcessors: Int): String {
        if (pinToCpu45) return BACKGROUND_CPU_45_HEX_MASK
        val clampedCores = availableProcessors.coerceIn(1, 63)
        val fullMask = (1L shl clampedCores) - 1L
        return fullMask.toString(16)
    }

    internal fun is3dRenderThreadName(comm: String): Boolean {
        val name = comm.trim()
        if (name.isEmpty()) return false
        return name.startsWith("FEngine", ignoreCase = true) ||
            name.startsWith("JobSystem", ignoreCase = true) ||
            name.startsWith("Filament", ignoreCase = true) ||
            name.contains("GLThread", ignoreCase = true)
    }

    internal fun isCanBusThreadName(comm: String): Boolean {
        val name = comm.trim()
        if (name.isEmpty()) return false
        return name.startsWith("dcl-can-", ignoreCase = true) ||
            name.startsWith("mbcan-", ignoreCase = true) ||
            name.equals("can-thread", ignoreCase = true)
    }

    private fun readCpuMaxFreqKhz(cpuIndex: Int): Long? = runCatching {
        val file = File("/sys/devices/system/cpu/cpu$cpuIndex/cpufreq/cpuinfo_max_freq")
        if (!file.isFile) return@runCatching null
        parseCpuMaxFreqKhz(file.readText())
    }.getOrNull()

    private fun applyTaskset(tid: Int, hexMask: String, threadLabel: String): Boolean {
        if (tid <= 0) return false
        return runCatching {
            val result = ShellExecutor.exec(
                listOf("taskset", "-p", hexMask, tid.toString()),
                timeoutMs = TASKSET_TIMEOUT_MS,
            )
            if (result.isSuccess) {
                true
            } else {
                Log.w(
                    TAG,
                    "taskset failed for $threadLabel (tid=$tid, mask=0x$hexMask, exit=${result.exitCode}, timeout=${result.timedOut}): ${result.output.trim()}",
                )
                false
            }
        }.onFailure { e ->
            Log.w(TAG, "taskset exception for $threadLabel (tid=$tid, mask=0x$hexMask): ${e.message}")
        }.getOrDefault(false)
    }

    private fun applyAffinityToProcessThreadsAsync(
        category: String,
        pinToCpu45: Boolean,
        matcher: (String) -> Boolean,
    ) {
        affinityControlExecutor.execute {
            if (!pinCpu45Supported) return@execute
            val targetMask = resolveTargetCpuHexMask(
                pinToCpu45 = pinToCpu45,
                availableProcessors = Runtime.getRuntime().availableProcessors(),
            )
            val taskDir = File("/proc/self/task")
            val entries = runCatching { taskDir.listFiles() }.getOrNull() ?: return@execute
            var matched = 0
            var succeeded = 0
            val matchedNames = mutableListOf<String>()
            for (entry in entries) {
                val tid = entry.name.toIntOrNull() ?: continue
                val comm = runCatching { File(entry, "comm").readText().trim() }.getOrNull() ?: continue
                if (!matcher(comm)) continue
                matched++
                matchedNames.add("$comm($tid)")
                if (applyTaskset(tid, targetMask, "$category:$comm")) {
                    succeeded++
                }
            }
            Log.d(
                TAG,
                "Applied $category CPU affinity mask=0x$targetMask (pinCpu45=$pinToCpu45): " +
                    "$succeeded/$matched threads updated $matchedNames",
            )
        }
    }

    private fun applyBackgroundThreadPolicyOnWorkerThread(threadName: String) {
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        }.onFailure { e ->
            Log.w(TAG, "Failed to set THREAD_PRIORITY_BACKGROUND for $threadName: ${e.message}")
        }

        if (!pinCpu45Supported) return
        val tid = runCatching { Process.myTid() }.getOrDefault(-1)
        if (applyTaskset(tid, BACKGROUND_CPU_45_HEX_MASK, threadName)) {
            Log.d(TAG, "Pinned background thread $threadName (tid=$tid) to CPU 4-5 (mask=0x$BACKGROUND_CPU_45_HEX_MASK)")
        }
    }

    private fun applyOptionalCanAffinityOnWorkerThread(threadName: String) {
        if (!pinCanBusToCpu45Enabled || !pinCpu45Supported) return
        val tid = runCatching { Process.myTid() }.getOrDefault(-1)
        if (applyTaskset(tid, BACKGROUND_CPU_45_HEX_MASK, "can-bus:$threadName")) {
            Log.d(TAG, "Pinned CAN thread $threadName (tid=$tid) to CPU 4-5 (mask=0x$BACKGROUND_CPU_45_HEX_MASK)")
        }
    }

    internal fun createBackgroundSingleThreadDispatcher(threadName: String): ExecutorCoroutineDispatcher {
        val counter = AtomicInteger(0)
        val factory = ThreadFactory { runnable ->
            val idx = counter.incrementAndGet()
            val fullName = if (idx == 1) threadName else "$threadName-$idx"
            Thread(
                {
                    applyBackgroundThreadPolicyOnWorkerThread(fullName)
                    runnable.run()
                },
                fullName,
            ).apply {
                isDaemon = true
            }
        }
        return Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()
    }

    internal fun createCanBusSingleThreadDispatcher(threadName: String): ExecutorCoroutineDispatcher {
        val counter = AtomicInteger(0)
        val factory = ThreadFactory { runnable ->
            val idx = counter.incrementAndGet()
            val fullName = if (idx == 1) threadName else "$threadName-$idx"
            Thread(
                {
                    applyOptionalCanAffinityOnWorkerThread(fullName)
                    runnable.run()
                },
                fullName,
            ).apply {
                isDaemon = true
            }
        }
        return Executors.newSingleThreadExecutor(factory).asCoroutineDispatcher()
    }

    private fun createCanBusPoolDispatcher(
        threadCount: Int,
        baseName: String,
    ): ExecutorCoroutineDispatcher {
        val counter = AtomicInteger(0)
        val factory = ThreadFactory { runnable ->
            val idx = counter.incrementAndGet()
            val fullName = "$baseName-$idx"
            Thread(
                {
                    applyOptionalCanAffinityOnWorkerThread(fullName)
                    runnable.run()
                },
                fullName,
            ).apply {
                isDaemon = true
            }
        }
        return Executors.newFixedThreadPool(threadCount, factory).asCoroutineDispatcher()
    }
}
