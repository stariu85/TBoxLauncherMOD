package vad.dashing.tbox

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight TTL cache for [android.content.pm.PackageManager.getPackageInfo] presence checks
 * to avoid repeated synchronous Binder IPC from hot CAN/UI paths.
 */
object PackagePresenceCache {
    private const val DEFAULT_TTL_MS = 15_000L

    private data class Entry(
        val installed: Boolean,
        val updatedAtElapsedMs: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun isInstalled(
        context: Context,
        packageName: String,
        ttlMs: Long = DEFAULT_TTL_MS,
        forceRefresh: Boolean = false,
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh) {
            val cached = entries[packageName]
            if (cached != null && (now - cached.updatedAtElapsedMs) < ttlMs) {
                return cached.installed
            }
        }
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.applicationContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
        entries[packageName] = Entry(installed = installed, updatedAtElapsedMs = now)
        return installed
    }

    fun invalidate(packageName: String) {
        entries.remove(packageName)
    }

    fun invalidateAll() {
        entries.clear()
    }
}
