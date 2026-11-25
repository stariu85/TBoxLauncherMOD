package vad.dashing.tbox.camd

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Representation of a preset exposed by the Camd service (camd >= 0.5.0).
 *
 * @param cameraId camera id associated with this preset (e.g. 0, 1, 3, 4, 5, 8, 9)
 * @param name preset name (e.g. "full_left", "left_top", "left_bottom", "full_front", etc.)
 * @param isBuiltin true if builtin preset in Camd, false if created by user in Camd UI
 */
data class CamdPreset(
    val cameraId: Int,
    val name: String,
    val isBuiltin: Boolean,
)

sealed class CamdConnectionStatus {
    data object NotInstalled : CamdConnectionStatus()
    data class Connected(
        val openWindows: List<Int>,
        val avmState: String,
        val keepAvm: Boolean,
        val rawStatus: String,
        val presets: List<CamdPreset> = emptyList(),
        val recEnabled: Boolean = false,
        val recActive: Boolean = false,
        val recSos: Boolean = false,
        val recSegSeconds: Int? = null,
    ) : CamdConnectionStatus()
    data class NoConnection(val errorMessage: String) : CamdConnectionStatus()

    fun resolveRecIndicatorState(): CamdRecIndicatorState {
        val connected = this as? Connected ?: return CamdRecIndicatorState.OFF
        return when {
            connected.recActive -> CamdRecIndicatorState.ACTIVE
            connected.recEnabled -> CamdRecIndicatorState.STANDBY
            else -> CamdRecIndicatorState.OFF
        }
    }
}

/**
 * Three-state visual representation for the DVR REC indicator:
 * - [ACTIVE]: recording is actively writing (`rec_active=1`) -> red
 * - [STANDBY]: recorder master switch is enabled, but not actively recording (`rec_enabled=1`, `rec_active=0`) -> blue
 * - [OFF]: recorder is disabled or disconnected -> idle/muted
 */
enum class CamdRecIndicatorState {
    OFF,
    STANDBY,
    ACTIVE,
}

/**
 * Communicates with the local `vad.dashing.camd` service via TCP socket on 127.0.0.1:8770.
 * Specification: docs/DCL_CAMD_CAMERA_API_FOR_VENDOR_RU.md
 */
object CamdSocketClient {
    private const val TAG = "CamdSocketClient"
    const val CAMD_PACKAGE_NAME = "vad.dashing.camd"
    const val HOST = "127.0.0.1"
    const val PORT = 8770

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(CAMD_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            Log.w(TAG, "isInstalled check failed", t)
            false
        }
    }

    /**
     * Wakes up the camd service (spec §2):
     * 1. Attempts startForegroundService to vad.dashing.camd.CamdService (if signature permission matches)
     * 2. Broadcasts vad.dashing.camd.START to StartReceiver
     */
    fun wakeUpService(context: Context) {
        try {
            val serviceIntent = Intent().setClassName(CAMD_PACKAGE_NAME, "$CAMD_PACKAGE_NAME.CamdService")
            try {
                context.startForegroundService(serviceIntent)
                Log.i(TAG, "Sent startForegroundService to $CAMD_PACKAGE_NAME.CamdService")
                vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "Sent startForegroundService to CamdService")
            } catch (t: Throwable) {
                // Expected if signature differs
                Log.d(TAG, "startForegroundService to CamdService failed: ${t.message}")
            }

            val intent = Intent("vad.dashing.camd.START").setPackage(CAMD_PACKAGE_NAME)
            context.sendBroadcast(intent)
            Log.i(TAG, "Sent wakeUp broadcast vad.dashing.camd.START")
            vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "Sent wakeUp broadcast vad.dashing.camd.START")
        } catch (t: Throwable) {
            Log.w(TAG, "wakeUpService failed", t)
            vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "wakeUpService failed: ${t.message}")
        }
    }

    /**
     * Specification §4: Success response starts with OK and does NOT contain state=error.
     */
    fun isSuccessResponse(response: String): Boolean =
        response.startsWith("OK") && !response.contains("state=error")

    /**
     * Specification §4: Error response starts with ERR or contains state=error.
     */
    fun isErrorResponse(response: String): Boolean =
        response.startsWith("ERR") || response.contains("state=error")

    /**
     * Extracts human-readable error description from response line.
     */
    fun extractErrorDetail(response: String): String {
        if (response.startsWith("ERR")) return response
        val errorPrefix = "error="
        val idx = response.indexOf(errorPrefix)
        if (idx != -1) {
            return response.substring(idx + errorPrefix.length).trim()
        }
        return response
    }

    /**
     * Executes a single command session: connect -> send line -> read line -> close.
     */
    suspend fun sendCommand(
        line: String,
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 20_000,
        logToRecorder: Boolean = true,
    ): Result<String> = withContext(Dispatchers.IO) {
        val result = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST, PORT), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                socket.getOutputStream().apply {
                    write("$line\n".toByteArray(Charsets.UTF_8))
                    flush()
                }
                val response = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine().orEmpty()
                if (logToRecorder) {
                    Log.d(TAG, "Camd cmd='$line' -> resp='$response'")
                }
                response
            }
        }
        if (logToRecorder) {
            val response = result.getOrNull()
            if (result.isSuccess && response != null) {
                vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "cmd='$line' -> resp='$response'")
            } else {
                val err = result.exceptionOrNull()?.message ?: "unknown"
                vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "cmd='$line' -> FAILED: $err")
            }
        }
        result
    }

    /**
     * Sends a command, waking up the service and retrying if initial connect fails.
     * Per spec §2, §5 & §6.1:
     * - connectMs = 1500..2000
     * - readMs = 20_000
     * - if connection fails: send broadcast vad.dashing.camd.START
     * - retry loop with ~250..300ms intervals up to maxWaitMs (~4s).
     */
    suspend fun sendCommandWithWakeup(
        context: Context,
        line: String,
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 20_000,
        maxWaitMs: Long = 4000L,
    ): Result<String> = withContext(Dispatchers.IO) {
        val firstAttempt = sendCommand(line, connectTimeoutMs, readTimeoutMs)
        if (firstAttempt.isSuccess) {
            return@withContext firstAttempt
        }

        if (!isInstalled(context)) {
            vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "sendCommandWithWakeup: Package $CAMD_PACKAGE_NAME not installed")
            return@withContext Result.failure(IllegalStateException("Package $CAMD_PACKAGE_NAME not installed"))
        }

        // Wake up service via broadcast per spec §2
        vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "sendCommandWithWakeup: First attempt failed, waking up service for cmd='$line'...")
        wakeUpService(context)
        val startTime = System.currentTimeMillis()
        var lastError: Throwable? = firstAttempt.exceptionOrNull()

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            delay(300L)
            val retry = sendCommand(line, connectTimeoutMs = 1500, readTimeoutMs = readTimeoutMs)
            if (retry.isSuccess) {
                return@withContext retry
            }
            lastError = retry.exceptionOrNull()
        }

        vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "sendCommandWithWakeup failed after ${maxWaitMs}ms for cmd='$line': ${lastError?.message}")
        Result.failure(lastError ?: RuntimeException("Camd service did not respond on port $PORT"))
    }

    /**
     * Requests available presets from Camd service (spec §3.1.1, Camd >= 0.5.0):
     * Format: PRESETS
     * Response: OK presets=<camId>:<name>:<builtin|user>,... or OK presets=none
     */
    suspend fun getPresets(): Result<List<CamdPreset>> = withContext(Dispatchers.IO) {
        val res = sendCommand("PRESETS", connectTimeoutMs = 1200, readTimeoutMs = 3000)
        res.mapCatching { response ->
            if (response.startsWith("OK presets=")) {
                val content = response.removePrefix("OK presets=").trim()
                if (content == "none" || content.isEmpty()) {
                    emptyList()
                } else {
                    content.split(",").mapNotNull { entry ->
                        val parts = entry.trim().split(":")
                        if (parts.size >= 2) {
                            val camId = parts[0].toIntOrNull() ?: return@mapNotNull null
                            val name = parts[1]
                            val isBuiltin = parts.getOrNull(2) != "user"
                            CamdPreset(camId, name, isBuiltin)
                        } else null
                    }
                }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Sends AVM command (spec §3.3):
     * AVM ON (or AVM 1) - keep OEM AVM (com.mengbo.avm) stopped
     * AVM OFF (or AVM 0) - restore/start OEM AVM (com.mengbo.avm)
     * AVM (no arg) - query current status
     */
    suspend fun setAvmHold(context: Context, holdOff: Boolean): Result<String> {
        val cmd = if (holdOff) "AVM ON" else "AVM OFF"
        return sendCommandWithWakeup(context, cmd)
    }

    suspend fun setAvm(enabled: Boolean? = null): Result<String> {
        val cmd = if (enabled != null) "AVM ${if (enabled) "1" else "0"}" else "AVM"
        return sendCommand(cmd)
    }

    /**
     * Sends REC command (spec §3.3.1, Camd >= 0.8.2):
     * REC ON (or REC 1) - enable master recorder switch
     * REC OFF (or REC 0) - disable and stop recording
     */
    suspend fun setRecorder(context: Context, enabled: Boolean): Result<String> {
        val cmd = if (enabled) "REC ON" else "REC OFF"
        return sendCommandWithWakeup(context, cmd)
    }

    /**
     * Sends REC SOS command (spec §3.3.1, Camd >= 0.8.6):
     * Emergency save of current and previous segments to sos folder.
     */
    suspend fun triggerRecorderSos(context: Context): Result<String> {
        return sendCommandWithWakeup(context, "REC SOS")
    }

    /**
     * Checks connection status without waking up the service on STATUS (per spec: STATUS doesn't wake).
     * If connected, also fetches available presets.
     */
    suspend fun checkStatus(
        context: Context,
        fetchPresets: Boolean = true,
        existingPresets: List<CamdPreset> = emptyList(),
        logToRecorder: Boolean = true,
    ): CamdConnectionStatus = withContext(Dispatchers.IO) {
        if (!isInstalled(context)) {
            return@withContext CamdConnectionStatus.NotInstalled
        }

        val result = sendCommand(
            line = "STATUS",
            connectTimeoutMs = 1200,
            readTimeoutMs = 2000,
            logToRecorder = logToRecorder,
        )
        val status = result.fold(
            onSuccess = { response ->
                if (response.startsWith("OK")) {
                    val st = parseStatusResponse(response)
                    val presets = if (fetchPresets) {
                        getPresets().getOrDefault(existingPresets)
                    } else {
                        existingPresets
                    }
                    st.copy(presets = presets)
                } else {
                    CamdConnectionStatus.NoConnection("Ответ службы: $response")
                }
            },
            onFailure = { error ->
                CamdConnectionStatus.NoConnection(error.message ?: "Порт $PORT недоступен")
            }
        )
        if (logToRecorder) {
            when (status) {
                is CamdConnectionStatus.Connected -> {
                    val recInfo = if (status.recActive || status.recEnabled) {
                        " rec(enabled=${status.recEnabled}, active=${status.recActive}, sos=${status.recSos}, seg=${status.recSegSeconds}s)"
                    } else ""
                    vad.dashing.tbox.DclLogRecorder.log(
                        "CamdSocketClient",
                        "checkStatus: Connected openWindows=${status.openWindows} avm=${status.avmState} keepAvm=${status.keepAvm}$recInfo presets=${status.presets.map { "${it.name}(#${it.cameraId})" }}"
                    )
                }
                is CamdConnectionStatus.NoConnection -> {
                    vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "checkStatus: NoConnection err='${status.errorMessage}'")
                }
                is CamdConnectionStatus.NotInstalled -> {
                    vad.dashing.tbox.DclLogRecorder.log("CamdSocketClient", "checkStatus: NotInstalled")
                }
            }
        }
        status
    }

    private fun parseStatusResponse(response: String): CamdConnectionStatus.Connected {
        // Example: OK windows=8,4 avm=stopped keep_avm=0 rec_enabled=0 rec_active=0 rec_sos=0 rec_seg=180
        val tokens = response.split(" ")
        var openWindows: List<Int> = emptyList()
        var avmState = "unknown"
        var keepAvm = false
        var recEnabled = false
        var recActive = false
        var recSos = false
        var recSegSeconds: Int? = null

        for (token in tokens) {
            when {
                token.startsWith("windows=") -> {
                    val rawWins = token.removePrefix("windows=")
                    if (rawWins != "none" && rawWins.isNotBlank()) {
                        openWindows = rawWins.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                    }
                }
                token.startsWith("avm=") -> {
                    avmState = token.removePrefix("avm=")
                }
                token.startsWith("keep_avm=") -> {
                    keepAvm = token.removePrefix("keep_avm=") == "1"
                }
                token.startsWith("rec_enabled=") -> {
                    recEnabled = token.removePrefix("rec_enabled=") == "1"
                }
                token.startsWith("rec_active=") -> {
                    recActive = token.removePrefix("rec_active=") == "1"
                }
                token.startsWith("rec_sos=") -> {
                    recSos = token.removePrefix("rec_sos=") == "1"
                }
                token.startsWith("rec_seg=") -> {
                    recSegSeconds = token.removePrefix("rec_seg=").toIntOrNull()
                }
            }
        }

        return CamdConnectionStatus.Connected(
            openWindows = openWindows,
            avmState = avmState,
            keepAvm = keepAvm,
            rawStatus = response,
            presets = emptyList(),
            recEnabled = recEnabled,
            recActive = recActive,
            recSos = recSos,
            recSegSeconds = recSegSeconds,
        )
    }
}
