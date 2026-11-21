package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * RAM cache for the 13.7 MB GLB car model asset.
 * Pre-reads the model bytes into a direct [ByteBuffer] on a background thread during app startup,
 * eliminating disk/APK I/O delay when Filament instantiates the 3D car model.
 */
internal object LauncherCarModelCache {
    private const val TAG = "LauncherCarModelCache"

    private val lock = Any()

    @Volatile
    private var cachedBuffer: ByteBuffer? = null

    @Volatile
    private var isPreloading = false

    fun preload(context: Context) {
        if (cachedBuffer != null || isPreloading) return
        val appCtx = context.applicationContext
        synchronized(lock) {
            if (cachedBuffer != null || isPreloading) return
            isPreloading = true
        }
        CoroutineScope(Dispatchers.IO).launch {
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
                context.assets.open(LAUNCHER_CAR_MODEL_ASSET).use { input ->
                    val bytes = input.readBytes()
                    val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                        put(bytes)
                        flip()
                    }
                    Log.w(TAG, "Loaded $LAUNCHER_CAR_MODEL_ASSET into RAM (${bytes.size} bytes)")
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

    fun getBuffer(context: Context): ByteBuffer {
        cachedBuffer?.let { return it.duplicate() }
        val buffer = loadIntoCache(context.applicationContext)
        return buffer.duplicate()
    }
}
