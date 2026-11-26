package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * RAM cache for the 13.7 MB GLB car model asset.
 * Pre-reads the model bytes directly into a direct [ByteBuffer] on a background thread during app startup,
 * eliminating disk/APK I/O delay and avoiding intermediate heap byte-array spikes.
 */
internal object LauncherCarModelCache {
    private const val TAG = "LauncherCarModelCache"
    private const val STREAM_CHUNK_BYTES = 64 * 1024

    private val lock = Any()
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedBuffer: ByteBuffer? = null

    @Volatile
    private var isPreloading = false

    fun getCachedBufferOrNull(): ByteBuffer? = cachedBuffer?.duplicate()

    fun preload(context: Context) {
        if (cachedBuffer != null || isPreloading) return
        val appCtx = context.applicationContext
        synchronized(lock) {
            if (cachedBuffer != null || isPreloading) return
            isPreloading = true
        }
        preloadScope.launch {
            try {
                loadIntoCache(appCtx)
            } finally {
                synchronized(lock) {
                    isPreloading = false
                }
            }
        }
    }

    private fun loadIntoCache(context: Context): ByteBuffer {
        synchronized(lock) {
            cachedBuffer?.let { return it }
            val directBuffer = runCatching {
                val exactLength = runCatching {
                    context.assets.openFd(LAUNCHER_CAR_MODEL_ASSET).use { fd ->
                        val len = fd.length
                        if (len in 1L..Int.MAX_VALUE.toLong()) len.toInt() else null
                    }
                }.getOrNull()

                context.assets.open(LAUNCHER_CAR_MODEL_ASSET).use { input ->
                    val initialCapacity = exactLength
                        ?: input.available().takeIf { it > 0 }
                        ?: (14 * 1024 * 1024)
                    var buffer = ByteBuffer.allocateDirect(initialCapacity)
                    val chunk = ByteArray(STREAM_CHUNK_BYTES)
                    var totalRead = 0
                    while (true) {
                        val n = input.read(chunk)
                        if (n <= 0) break
                        if (totalRead + n > buffer.capacity()) {
                            val newCap = maxOf(buffer.capacity() * 2, totalRead + n)
                            val grown = ByteBuffer.allocateDirect(newCap)
                            buffer.flip()
                            grown.put(buffer)
                            buffer = grown
                        }
                        buffer.put(chunk, 0, n)
                        totalRead += n
                    }
                    buffer.flip()
                    Log.w(TAG, "Loaded $LAUNCHER_CAR_MODEL_ASSET into RAM ($totalRead bytes)")
                    buffer
                }
            }.getOrElse { ex ->
                Log.w(TAG, "Failed to load $LAUNCHER_CAR_MODEL_ASSET: ${ex.message}")
                throw ex
            }
            cachedBuffer = directBuffer
            return directBuffer
        }
    }

    suspend fun awaitBuffer(context: Context): ByteBuffer {
        cachedBuffer?.let { return it.duplicate() }
        val appCtx = context.applicationContext
        return withContext(Dispatchers.IO) {
            loadIntoCache(appCtx).duplicate()
        }
    }

    fun getBuffer(context: Context): ByteBuffer {
        cachedBuffer?.let { return it.duplicate() }
        val buffer = loadIntoCache(context.applicationContext)
        return buffer.duplicate()
    }
}
