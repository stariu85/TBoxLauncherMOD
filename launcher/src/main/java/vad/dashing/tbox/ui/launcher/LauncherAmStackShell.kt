package vad.dashing.tbox.ui.launcher

import android.util.Log
import java.util.concurrent.TimeUnit

private const val TAG = "LauncherAmStack"

/**
 * OEM ActivityManager shell helpers that work under the app UID on X50
 * (`am stack list` / `am stack remove`). Binder [ActivityManager.removeTask] is unreliable here.
 */
internal object LauncherAmStackShell {
    data class StackRow(
        val stackId: Int,
        val windowingMode: String,
        val taskId: Int?,
        val packageName: String?,
        val visible: Boolean,
        /** Class of the task's top activity (from topActivity=ComponentInfo), if printed. */
        val topActivityClass: String? = null,
        val bounds: android.graphics.Rect? = null,
    )

    fun listStacks(): List<StackRow> {
        val out = runAm("stack", "list") ?: return emptyList()
        val rows = mutableListOf<StackRow>()
        var stackId: Int? = null
        var mode = ""
        var taskId: Int? = null
        var pkg: String? = null
        var visible = false
        var topClass: String? = null
        var stackBounds: android.graphics.Rect? = null

        fun flush() {
            val id = stackId ?: return
            rows += StackRow(id, mode, taskId, pkg, visible, topClass, stackBounds)
            stackId = null
            mode = ""
            taskId = null
            pkg = null
            visible = false
            topClass = null
            stackBounds = null
        }

        for (raw in out.lineSequence()) {
            val line = raw.trim()
            val stackMatch = Regex("""Stack id=(\d+)""").find(line)
            if (stackMatch != null) {
                flush()
                stackId = stackMatch.groupValues[1].toInt()
                continue
            }
            Regex("""mWindowingMode=(\w+)""").find(line)?.let {
                mode = it.groupValues[1]
            }
            Regex("""taskId=(\d+):\s*([^/\s]+)""").find(line)?.let {
                taskId = it.groupValues[1].toInt()
                pkg = it.groupValues[2]
            }
            Regex("""topActivity=ComponentInfo\{[^/]+/([^}\s]+)""").find(line)?.let {
                topClass = it.groupValues[1]
            }
            Regex("""visible=(true|false)""").find(line)?.let {
                visible = it.groupValues[1] == "true"
            }
            val boundsMatch = Regex("""bounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)""").find(line)
                ?: Regex("""mBounds=Rect\((\d+),\s*(\d+)\s*-\s*(\d+),\s*(\d+)\)""").find(line)
            if (boundsMatch != null) {
                val l = boundsMatch.groupValues[1].toInt()
                val t = boundsMatch.groupValues[2].toInt()
                val r = boundsMatch.groupValues[3].toInt()
                val b = boundsMatch.groupValues[4].toInt()
                stackBounds = android.graphics.Rect(l, t, r, b)
            }
        }
        flush()
        return rows
    }

    fun removeStack(stackId: Int): Boolean {
        val out = runAm("stack", "remove", stackId.toString())
        Log.w(TAG, "am stack remove $stackId out=${out?.take(120)}")
        return out != null
    }

    fun moveStackToBack(stackId: Int): Boolean {
        val out = runAm("stack", "move-to-back", stackId.toString())
        Log.w(TAG, "am stack move-to-back $stackId out=${out?.take(120)}")
        return out != null
    }

    /**
     * Move foreign stacks (freeform, split, embedded, fullscreen) to the back of the stack hierarchy
     * WITHOUT killing or removing them.
     */
    fun moveForeignToBack(
        launcherPackage: String,
        packages: Set<String> = emptySet(),
    ): Int {
        val stacks = listStacks()
        Log.w(TAG, "moveForeignToBack stacks=${stacks.size} tracked=$packages")
        var moved = 0
        for (s in stacks) {
            val pkg = s.packageName ?: continue
            if (pkg == launcherPackage) continue
            val isFreeform = s.windowingMode.equals("freeform", ignoreCase = true) ||
                s.windowingMode.equals("extend", ignoreCase = true)
            val tracked = pkg in packages
            if (!isFreeform && !tracked && !s.visible) continue
            if (moveStackToBack(s.stackId)) {
                moved++
                Log.w(TAG, "moved stack to back id=${s.stackId} pkg=$pkg mode=${s.windowingMode}")
            }
        }
        return moved
    }

    /**
     * Remove freeform stacks and any stack whose top package is in [packages].
     * Never touches the HOME launcher package.
     */
    fun dismissForeign(
        launcherPackage: String,
        packages: Set<String> = emptySet(),
    ): Int {
        val stacks = listStacks()
        Log.w(TAG, "dismissForeign stacks=${stacks.size} tracked=$packages")
        var removed = 0
        for (s in stacks) {
            val pkg = s.packageName ?: continue
            if (pkg == launcherPackage) continue
            val isFreeformMode = s.windowingMode.equals("freeform", ignoreCase = true) ||
                s.windowingMode.equals("extend", ignoreCase = true)
            val isFullscreenBounds = s.bounds != null && s.bounds.left == 0 && s.bounds.height() >= 1000
            val isTrulyFreeform = isFreeformMode && !isFullscreenBounds

            if (isTrulyFreeform) {
                if (removeStack(s.stackId)) {
                    removed++
                    FreeformLaunchRegistry.remove(pkg)
                    Log.w(TAG, "removed freeform stack=${s.stackId} pkg=$pkg mode=${s.windowingMode}")
                }
            } else {
                if (moveStackToBack(s.stackId)) {
                    Log.w(TAG, "moved fullscreen stack to back id=${s.stackId} pkg=$pkg mode=${s.windowingMode}")
                }
            }
        }
        return removed
    }

    private fun runAm(vararg args: String): String? =
        runCatching {
            val cmd = ArrayList<String>(args.size + 1).apply {
                add("am")
                addAll(args)
            }
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            var text = ""
            val thread = Thread {
                runCatching {
                    text = proc.inputStream.bufferedReader().readText()
                }
            }
            thread.start()
            thread.join(200L)
            if (thread.isAlive) {
                runCatching { proc.destroy() }
                Log.w(TAG, "am ${args.joinToString(" ")} timed out")
                return null
            }
            runCatching { proc.waitFor(200L, TimeUnit.MILLISECONDS) }
            runCatching { proc.destroy() }
            if (text.contains("Security exception", ignoreCase = true) || text.contains("Error", ignoreCase = true)) {
                Log.w(TAG, "am ${args.joinToString(" ")} denied: $text")
                return null
            }
            text
        }.onFailure {
            Log.w(TAG, "am ${args.joinToString(" ")} failed", it)
        }.getOrNull()
}
