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

sealed class CamdConnectionStatus {
    data object NotInstalled : CamdConnectionStatus()
    data class Connected(
        val openWindows: List<Int>,
        val avmState: String,
        val keepAvm: Boolean,
        val rawStatus: String,
    ) : CamdConnectionStatus()
    data class NoConnection(val errorMessage: String) : CamdConnectionStatus()
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
     * Wakes up the camd service via exported broadcast `vad.dashing.camd.START`.
     * Does not require signature permission.
     */
    fun wakeUpService(context: Context) {
        try {
            val intent = Intent("vad.dashing.camd.START").setPackage(CAMD_PACKAGE_NAME)
            context.sendBroadcast(intent)
            Log.i(TAG, "Sent wakeUp broadcast vad.dashing.camd.START")
        } catch (t: Throwable) {
            Log.w(TAG, "wakeUpService broadcast failed", t)
        }
    }

    /**
     * Executes a single command session: connect -> send line -> read line -> close.
     */
    suspend fun sendCommand(
        line: String,
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 20_000,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(HOST, PORT), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                socket.getOutputStream().apply {
                    write("$line\n".toByteArray(Charsets.UTF_8))
                    flush()
                }
                val response = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine().orEmpty()
                Log.d(TAG, "Camd cmd='$line' -> resp='$response'")
                response
            }
        }
    }

    /**
     * Sends a command, waking up the service and retrying with delays if initial connect fails.
     * Per spec §6.1:
     * - connectMs = 1500..2000
     * - readMs = 20_000
     * - after vad.dashing.camd.START: wait 1..2 s before reconnecting.
     */
    suspend fun sendCommandWithWakeup(
        context: Context,
        line: String,
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 20_000,
        maxWaitMs: Long = 8000L,
    ): Result<String> = withContext(Dispatchers.IO) {
        val firstAttempt = sendCommand(line, connectTimeoutMs, readTimeoutMs)
        if (firstAttempt.isSuccess) {
            return@withContext firstAttempt
        }

        if (!isInstalled(context)) {
            return@withContext Result.failure(IllegalStateException("Package $CAMD_PACKAGE_NAME not installed"))
        }

        // Wake up service and retry per spec §6.1
        wakeUpService(context)
        delay(1200L) // pause 1..2 s after broadcast before reconnecting
        val startTime = System.currentTimeMillis()
        var lastError: Throwable? = firstAttempt.exceptionOrNull()

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val retry = sendCommand(line, connectTimeoutMs = 1500, readTimeoutMs = readTimeoutMs)
            if (retry.isSuccess) {
                return@withContext retry
            }
            lastError = retry.exceptionOrNull()
            delay(500L)
        }

        Result.failure(lastError ?: RuntimeException("Camd service did not respond on port $PORT"))
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
     * Checks connection status without waking up the service on STATUS (per spec: STATUS doesn't wake).
     */
    suspend fun checkStatus(context: Context): CamdConnectionStatus = withContext(Dispatchers.IO) {
        if (!isInstalled(context)) {
            return@withContext CamdConnectionStatus.NotInstalled
        }

        val result = sendCommand("STATUS", connectTimeoutMs = 1200, readTimeoutMs = 2000)
        result.fold(
            onSuccess = { response ->
                if (response.startsWith("OK")) {
                    parseStatusResponse(response)
                } else {
                    CamdConnectionStatus.NoConnection("Ответ службы: $response")
                }
            },
            onFailure = { error ->
                CamdConnectionStatus.NoConnection(error.message ?: "Порт $PORT недоступен")
            }
        )
    }

    private fun parseStatusResponse(response: String): CamdConnectionStatus.Connected {
        // Example: OK windows=8,4 avm=stopped keep_avm=0
        val tokens = response.split(" ")
        var openWindows: List<Int> = emptyList()
        var avmState = "unknown"
        var keepAvm = false

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
            }
        }

        return CamdConnectionStatus.Connected(
            openWindows = openWindows,
            avmState = avmState,
            keepAvm = keepAvm,
            rawStatus = response,
        )
    }
}
