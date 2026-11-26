package vad.dashing.tbox.tbm

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class LocalAccessResult(
    val status: String,
    val accessToken: String? = null,
    val port: Int = 8765,
    val serverEnabled: Boolean = true,
    val message: String? = null,
) {
    val isGranted: Boolean get() = status == LocalAccessContract.STATUS_GRANTED
    val isPending: Boolean get() = status == LocalAccessContract.STATUS_PENDING
    val isNotSupported: Boolean get() = status == LocalAccessContract.STATUS_NOT_SUPPORTED
}

object LocalAccessClient {
    private const val TAG = "LocalAccessClient"

    private fun nowElapsedMs(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    private fun isWaitingStatus(status: String): Boolean =
        status == LocalAccessContract.STATUS_PENDING ||
            status == LocalAccessContract.STATUS_CONSENT_REQUIRED

    suspend fun getAccess(context: Context, interactive: Boolean = false): LocalAccessResult = withContext(Dispatchers.IO) {
        try {
            val uri = LocalAccessContract.AUTHORITY_URI
            val extras = Bundle().apply {
                putBoolean("interactive", interactive)
            }

            val bundle = try {
                val client = context.contentResolver.acquireUnstableContentProviderClient(uri)
                try {
                    client?.call("getAccess", null, extras)
                } finally {
                    client?.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unstable ContentResolver call failed, TBox Monitor might be missing or unsupported", e)
                null
            }

            if (bundle == null) {
                return@withContext LocalAccessResult(status = LocalAccessContract.STATUS_NOT_SUPPORTED)
            }

            val status = bundle.getString("status") ?: LocalAccessContract.STATUS_UNKNOWN_CLIENT
            val accessToken = bundle.getString("accessToken")
            val port = if (bundle.containsKey("port")) bundle.getInt("port", 8765) else 8765
            val serverEnabled = bundle.getBoolean("serverEnabled", true)

            LocalAccessResult(
                status = status,
                accessToken = accessToken,
                port = port,
                serverEnabled = serverEnabled,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local access via ContentResolver call", e)
            LocalAccessResult(status = LocalAccessContract.STATUS_NOT_SUPPORTED, message = e.message)
        }
    }

    /**
     * Awaits access consent by periodically calling the provider (up to 120 seconds) while status is pending or consent required.
     */
    suspend fun awaitAccess(context: Context, timeoutMs: Long = 120_000L, pollIntervalMs: Long = 2_000L): LocalAccessResult = withContext(Dispatchers.IO) {
        var result = getAccess(context, interactive = true)
        if (result.isGranted || !isWaitingStatus(result.status)) {
            return@withContext result
        }

        val startTime = nowElapsedMs()
        while (nowElapsedMs() - startTime < timeoutMs) {
            if (result.isGranted || !isWaitingStatus(result.status)) {
                break
            }
            delay(pollIntervalMs)
            result = getAccess(context, interactive = false)
        }
        result
    }
}
