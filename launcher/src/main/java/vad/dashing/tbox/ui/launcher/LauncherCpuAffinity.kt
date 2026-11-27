package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import vad.dashing.tbox.ShellExecutor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated low-priority background coroutine dispatchers and optional experimental little-cluster
 * CPU 0–5 affinity helpers for SM6150 (8-core little/big: CPU 0–5 @ 1.59 GHz, CPU 6–7 @ 1.90 GHz).
 *
 * Uses mask `0x3f` (all 6 little cores CPU 0–5, matching `/dev/cpuset/background/cpus = 0-5`)
 * instead of `0x30` (CPU 4–5 only) because Qualcomm `core_ctl` dynamically isolates idle CPU 4/5
 * (`/sys/devices/system/cpu/cpu{4,5}/isolate = 1`), causing `taskset -p 30` to fail with `EINVAL`.
 * Mask `0x3f` always succeeds (CPU 0–3 remain unisolated), keeps workloads off big cores CPU 6–7,
 * and allows `core_ctl` to unisolate and schedule across CPU 4–5 under load.
 *
 * - Minimap JPEG decoding & light-theme conversion always run at [Process.THREAD_PRIORITY_BACKGROUND]
 *   and are pinned to CPU 0–5 (`0x3f`) when the 8-core heterogeneous topology is confirmed.
 * - Optional experimental toggles can also pin:
 *   - Filament 3D rendering threads (`FEngine::loop`, `JobSystem::loop`, etc.);
 *   - CAN bus polling/request threads (`dcl-can-poll-*`, `mbcan-state-apply`, vendor `can-thread`);
 *   - Launcher `RenderThread` (always or smartly when an external app is foreground, together with
 *     ART Runtime GC/JIT/finalizer threads in smart mode);
 *   - Dedicated service background threads (`dcl-logger`, `tbox-diag-log`, `launcher-tbox-packets`,
 *     `vhal-state-apply`, `dcl-shell-drain`, `dcl-affinity-ctl`, `dcl-screencap`, `LiveWallpaperRelease`);
 *   - Lightweight background pollers pool (`dcl-poll-*`).
 */
internal object LauncherCpuAffinity {
    private const val TAG = "LauncherCpuAffinity"
    internal const val EXPECTED_OCTA_CORE_COUNT = 8
    internal const val LITTLE_CLUSTER_CPU_05_HEX_MASK = "3f"
    private const val TASKSET_TIMEOUT_MS = 1_500L

    @Volatile
    private var pin3dRenderToCpu45Enabled: Boolean = false

    @Volatile
    private var pinCanBusToCpu45Enabled: Boolean = true

    @Volatile
    private var pinServiceBgThreadsToCpu05Enabled: Boolean = true

    @Volatile
    private var pinLightPollersToCpu05Enabled: Boolean = true

    @Volatile
    private var lastAppliedRenderThreadPinned: Boolean? = null

    @Volatile
    private var lastAppliedArtRuntimePinned: Boolean? = null

    internal enum class RenderThreadAffinityMode {
        OFF,
        ALWAYS_LITTLE,
        SMART_WHEN_EXTERNAL_APP,
    }

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

    /**
     * Shared bounded pool (`dcl-poll-*`) for lightweight background pollers and non-critical
     * background work (solar theme, auto-weather, platform audio polling, Wi-Fi AP polling,
     * window/app trackers, history persistence, CAMD/TBM polling, media palette).
     */
    val lightPollersDispatcher: ExecutorCoroutineDispatcher by lazy {
        createLightPollersPoolDispatcher(maxThreads = 8, baseName = "dcl-poll")
    }

    fun syncExperimentalFlagsFromStore(context: Context) {
        val appCtx = context.applicationContext
        val pin3d = LauncherAppConfigStore.experimentalPin3dRenderCpu45(appCtx)
        val pinCan = LauncherAppConfigStore.experimentalPinCanBusCpu45(appCtx)
        val pinServiceBg = LauncherAppConfigStore.experimentalPinServiceBgThreadsCpu05(appCtx)
        val pinLightPollers = LauncherAppConfigStore.experimentalPinLightPollersCpu05(appCtx)
        pin3dRenderToCpu45Enabled = pin3d
        pinCanBusToCpu45Enabled = pinCan
        pinServiceBgThreadsToCpu05Enabled = pinServiceBg
        pinLightPollersToCpu05Enabled = pinLightPollers
        if (pin3d) {
            reapply3dRenderAffinityIfEnabledAsync()
        }
        if (pinCan) {
            reapplyCanBusAffinityIfEnabledAsync()
        }
        if (pinServiceBg) {
            reapplyServiceBgThreadsAffinityIfEnabledAsync()
        }
        if (pinLightPollers) {
            reapplyLightPollersAffinityIfEnabledAsync()
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

    fun setPinServiceBgThreadsToCpu05(enabled: Boolean) {
        pinServiceBgThreadsToCpu05Enabled = enabled
        applyAffinityToProcessThreadsAsync(
            category = "service-bg",
            pinToCpu45 = enabled,
            matcher = ::isServiceBackgroundThreadName,
        )
    }

    fun reapplyServiceBgThreadsAffinityIfEnabledAsync() {
        if (!pinServiceBgThreadsToCpu05Enabled) return
        applyAffinityToProcessThreadsAsync(
            category = "service-bg",
            pinToCpu45 = true,
            matcher = ::isServiceBackgroundThreadName,
        )
    }

    fun setPinLightPollersToCpu05(enabled: Boolean) {
        pinLightPollersToCpu05Enabled = enabled
        applyAffinityToProcessThreadsAsync(
            category = "light-pollers",
            pinToCpu45 = enabled,
            matcher = ::isLightPollerThreadName,
        )
    }

    fun reapplyLightPollersAffinityIfEnabledAsync() {
        if (!pinLightPollersToCpu05Enabled) return
        applyAffinityToProcessThreadsAsync(
            category = "light-pollers",
            pinToCpu45 = true,
            matcher = ::isLightPollerThreadName,
        )
    }

    /**
     * Asynchronously pins the calling service background thread to CPU 0–5 if the experimental
     * service-background toggle is enabled. Safe to call at worker thread startup (offloaded to
     * [affinityControlExecutor] so it never blocks thread start or recurses into [ShellExecutor]).
     */
    fun applyOptionalServiceBackgroundAffinityOnCurrentThread(threadName: String) {
        if (!pinServiceBgThreadsToCpu05Enabled || !pinCpu45Supported) return
        val tid = runCatching { Process.myTid() }.getOrDefault(-1)
        if (tid <= 0) return
        affinityControlExecutor.execute {
            if (!pinServiceBgThreadsToCpu05Enabled || !pinCpu45Supported) return@execute
            if (applyTaskset(tid, LITTLE_CLUSTER_CPU_05_HEX_MASK, "service-bg:$threadName")) {
                Log.d(
                    TAG,
                    "Pinned service-bg thread $threadName (tid=$tid) to CPU 0-5 (mask=0x$LITTLE_CLUSTER_CPU_05_HEX_MASK)",
                )
            }
        }
    }

    internal fun parseCpuMaxFreqKhz(rawText: String?): Long? {
        val value = rawText?.trim()?.toLongOrNull() ?: return null
        return value.takeIf { it > 0L }
    }

    /**
     * Pure topology guard: pins background/offloaded threads to the little cluster CPU 0–5 (`0x3f`)
     * ONLY on confirmed 8-core heterogeneous SoCs where CPU 4 & 5 are little cores relative to
     * big cores CPU 6 & 7.
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
        if (pinToCpu45) return LITTLE_CLUSTER_CPU_05_HEX_MASK
        val clampedCores = availableProcessors.coerceIn(1, 63)
        val fullMask = (1L shl clampedCores) - 1L
        return fullMask.toString(16)
    }

    internal fun resolveRenderThreadAffinityMode(
        alwaysEnabled: Boolean,
        smartEnabled: Boolean,
    ): RenderThreadAffinityMode = when {
        alwaysEnabled -> RenderThreadAffinityMode.ALWAYS_LITTLE
        smartEnabled -> RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP
        else -> RenderThreadAffinityMode.OFF
    }

    internal fun isExternalAppForegroundForRenderThread(
        hiddenForExternalApp: Boolean,
        activeAppPackage: String?,
        isActiveAppFullscreen: Boolean,
        isWindowFocused: Boolean,
        isLauncherOverlayOpen: Boolean,
    ): Boolean {
        if (isLauncherOverlayOpen) return false
        if (hiddenForExternalApp) return true
        if (!activeAppPackage.isNullOrBlank()) {
            val recoveredFromFullscreen = isActiveAppFullscreen && isWindowFocused
            if (!recoveredFromFullscreen) return true
        }
        return false
    }

    internal fun shouldPinRenderThreadToLittleCores(
        mode: RenderThreadAffinityMode,
        isExternalAppOpen: Boolean,
    ): Boolean = when (mode) {
        RenderThreadAffinityMode.OFF -> false
        RenderThreadAffinityMode.ALWAYS_LITTLE -> true
        RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP -> isExternalAppOpen
    }

    internal fun shouldPinArtRuntimeToLittleCores(
        mode: RenderThreadAffinityMode,
        isExternalAppOpen: Boolean,
    ): Boolean = mode == RenderThreadAffinityMode.SMART_WHEN_EXTERNAL_APP && isExternalAppOpen

    internal fun isRenderThreadName(comm: String): Boolean =
        comm.trim().equals("RenderThread", ignoreCase = true)

    internal fun isArtRuntimeThreadName(comm: String): Boolean {
        val name = comm.trim()
        if (name.isEmpty()) return false
        return name.startsWith("HeapTaskDaemon", ignoreCase = true) ||
            name.startsWith("Jit thread pool", ignoreCase = true) ||
            name.startsWith("FinalizerDaemon", ignoreCase = true) ||
            name.startsWith("FinalizerWatchd", ignoreCase = true) ||
            name.startsWith("ReferenceQueueD", ignoreCase = true)
    }

    fun applyRenderThreadAffinityAsync(
        pinToLittleCores: Boolean,
        forceReapply: Boolean = false,
    ) {
        affinityControlExecutor.execute {
            if (!pinCpu45Supported) return@execute
            if (!forceReapply && lastAppliedRenderThreadPinned == pinToLittleCores) return@execute
            val targetMask = resolveTargetCpuHexMask(
                pinToCpu45 = pinToLittleCores,
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
                if (!isRenderThreadName(comm)) continue
                matched++
                matchedNames.add("$comm($tid)")
                if (applyTaskset(tid, targetMask, "render-thread:$comm")) {
                    succeeded++
                }
            }
            if (matched > 0 && succeeded == matched) {
                lastAppliedRenderThreadPinned = pinToLittleCores
            }
            Log.d(
                TAG,
                "Applied render-thread CPU affinity mask=0x$targetMask (pinLittle=$pinToLittleCores): " +
                    "$succeeded/$matched threads updated $matchedNames",
            )
        }
    }

    fun applyArtRuntimeAffinityAsync(
        pinToLittleCores: Boolean,
        forceReapply: Boolean = false,
    ) {
        affinityControlExecutor.execute {
            if (!pinCpu45Supported) return@execute
            if (!forceReapply && lastAppliedArtRuntimePinned == pinToLittleCores) return@execute
            val targetMask = resolveTargetCpuHexMask(
                pinToCpu45 = pinToLittleCores,
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
                if (!isArtRuntimeThreadName(comm)) continue
                matched++
                matchedNames.add("$comm($tid)")
                if (applyTaskset(tid, targetMask, "art-runtime:$comm")) {
                    succeeded++
                }
            }
            if (matched > 0 && succeeded == matched) {
                lastAppliedArtRuntimePinned = pinToLittleCores
            }
            Log.d(
                TAG,
                "Applied art-runtime CPU affinity mask=0x$targetMask (pinLittle=$pinToLittleCores): " +
                    "$succeeded/$matched threads updated $matchedNames",
            )
        }
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

    internal fun isServiceBackgroundThreadName(comm: String): Boolean {
        val name = comm.trim()
        if (name.isEmpty()) return false
        return name.startsWith("dcl-logger", ignoreCase = true) ||
            name.startsWith("tbox-diag-log", ignoreCase = true) ||
            name.startsWith("launcher-tbox-p", ignoreCase = true) ||
            name.startsWith("vhal-state-appl", ignoreCase = true) ||
            name.startsWith("dcl-shell-drain", ignoreCase = true) ||
            name.startsWith("dcl-affinity-ct", ignoreCase = true) ||
            name.startsWith("dcl-screencap", ignoreCase = true) ||
            name.startsWith("LiveWallpaperRe", ignoreCase = true)
    }

    internal fun isLightPollerThreadName(comm: String): Boolean {
        val name = comm.trim()
        if (name.isEmpty()) return false
        return name.startsWith("dcl-poll-", ignoreCase = true)
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
        if (applyTaskset(tid, LITTLE_CLUSTER_CPU_05_HEX_MASK, threadName)) {
            Log.d(TAG, "Pinned background thread $threadName (tid=$tid) to CPU 0-5 (mask=0x$LITTLE_CLUSTER_CPU_05_HEX_MASK)")
        }
    }

    private fun applyOptionalCanAffinityOnWorkerThread(threadName: String) {
        if (!pinCanBusToCpu45Enabled || !pinCpu45Supported) return
        val tid = runCatching { Process.myTid() }.getOrDefault(-1)
        if (applyTaskset(tid, LITTLE_CLUSTER_CPU_05_HEX_MASK, "can-bus:$threadName")) {
            Log.d(TAG, "Pinned CAN thread $threadName (tid=$tid) to CPU 0-5 (mask=0x$LITTLE_CLUSTER_CPU_05_HEX_MASK)")
        }
    }

    private fun applyOptionalLightPollerAffinityOnWorkerThread(threadName: String) {
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        }.onFailure { e ->
            Log.w(TAG, "Failed to set THREAD_PRIORITY_BACKGROUND for $threadName: ${e.message}")
        }
        if (!pinLightPollersToCpu05Enabled || !pinCpu45Supported) return
        val tid = runCatching { Process.myTid() }.getOrDefault(-1)
        if (applyTaskset(tid, LITTLE_CLUSTER_CPU_05_HEX_MASK, "light-pollers:$threadName")) {
            Log.d(TAG, "Pinned light-poller thread $threadName (tid=$tid) to CPU 0-5 (mask=0x$LITTLE_CLUSTER_CPU_05_HEX_MASK)")
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

    private fun createLightPollersPoolDispatcher(
        maxThreads: Int,
        baseName: String,
    ): ExecutorCoroutineDispatcher {
        val counter = AtomicInteger(0)
        val factory = ThreadFactory { runnable ->
            val idx = counter.incrementAndGet()
            val fullName = "$baseName-$idx"
            Thread(
                {
                    applyOptionalLightPollerAffinityOnWorkerThread(fullName)
                    runnable.run()
                },
                fullName,
            ).apply {
                isDaemon = true
            }
        }
        val executor = ThreadPoolExecutor(
            maxThreads,
            maxThreads,
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            factory,
        ).apply {
            allowCoreThreadTimeOut(true)
        }
        return executor.asCoroutineDispatcher()
    }
}
