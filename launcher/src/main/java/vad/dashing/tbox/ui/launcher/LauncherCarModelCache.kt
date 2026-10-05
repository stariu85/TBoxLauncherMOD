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

    @Volatile
    private var cachedBuffer: ByteBuffer? = null

    fun preload(context: Context) {
        if (cachedBuffer != null) return
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                appCtx.assets.open(LAUNCHER_CAR_MODEL_ASSET).use { input ->
                    val bytes = input.readBytes()
                    val directBuffer = ByteBuffer.allocateDirect(bytes.size).apply {
                        put(bytes)
                        flip()
                    }
                    cachedBuffer = directBuffer
                    Log.w(TAG, "Preloaded $LAUNCHER_CAR_MODEL_ASSET into RAM (${bytes.size} bytes)")
                }
            }.onFailure {
                Log.w(TAG, "Failed to preload $LAUNCHER_CAR_MODEL_ASSET: ${it.message}")
            }
        }
    }

    fun getBuffer(context: Context): ByteBuffer {
        val existing = cachedBuffer
        if (existing != null) return existing.duplicate()
        val bytes = context.assets.open(LAUNCHER_CAR_MODEL_ASSET).use { it.readBytes() }
        val directBuffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            flip()
        }
        cachedBuffer = directBuffer
        return directBuffer.duplicate()
    }
}
