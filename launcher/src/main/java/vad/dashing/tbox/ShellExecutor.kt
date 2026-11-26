package vad.dashing.tbox

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Result of a bounded shell/subprocess execution.
 */
data class ShellResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0
}

/**
 * Safe subprocess execution helper that:
 * - redirects stderr into stdout (avoiding stderr buffer deadlocks),
 * - drains output on a bounded daemon reader pool up to [maxOutputBytes],
 * - waits with an explicit timeout,
 * - forcibly destroys timed-out processes and closes all process streams.
 */
object ShellExecutor {
    private const val TAG = "ShellExecutor"
    private const val DEFAULT_MAX_OUTPUT_BYTES = 512 * 1024

    private val drainExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "dcl-shell-drain").apply { isDaemon = true }
    }

    fun exec(
        command: List<String>,
        timeoutMs: Long = 5_000L,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): ShellResult {
        var process: Process? = null
        var drainFuture: Future<String>? = null
        try {
            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val proc = process
            runCatching { proc.outputStream.close() }
            drainFuture = drainExecutor.submit<String> {
                readBounded(proc.inputStream, maxOutputBytes)
            }

            val finished = proc.waitFor(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                runCatching { proc.waitFor(200L, TimeUnit.MILLISECONDS) }
                val partial = runCatching { drainFuture.get(150L, TimeUnit.MILLISECONDS) }.getOrDefault("")
                drainFuture.cancel(true)
                return ShellResult(exitCode = -1, output = partial, timedOut = true)
            }

            val exitCode = proc.exitValue()
            val output = runCatching { drainFuture.get(500L, TimeUnit.MILLISECONDS) }.getOrDefault("")
            return ShellResult(exitCode = exitCode, output = output, timedOut = false)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process?.destroyForcibly()
            drainFuture?.cancel(true)
            return ShellResult(exitCode = -1, output = "", timedOut = true)
        } catch (e: Exception) {
            Log.w(TAG, "exec failed for ${command.firstOrNull()}: ${e.message}")
            process?.destroyForcibly()
            drainFuture?.cancel(true)
            return ShellResult(exitCode = -1, output = "", timedOut = false)
        } finally {
            process?.let { p ->
                runCatching { p.inputStream.close() }
                runCatching { p.errorStream.close() }
                runCatching { p.outputStream.close() }
                if (p.isAlive) {
                    runCatching { p.destroyForcibly() }
                }
            }
        }
    }

    fun exec(
        vararg command: String,
        timeoutMs: Long = 5_000L,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    ): ShellResult = exec(command.toList(), timeoutMs, maxOutputBytes)

    private fun readBounded(stream: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        try {
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                val remaining = maxBytes - total
                if (remaining > 0) {
                    val toWrite = minOf(read, remaining)
                    out.write(buf, 0, toWrite)
                    total += toWrite
                }
                // Continue draining to prevent child process blocking on stdout pipe.
            }
        } catch (_: Exception) {
            // Stream closed on destroyForcibly() or EOF.
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
