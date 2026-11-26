package vad.dashing.tbox.ui.launcher

import android.util.Log
import vad.dashing.tbox.ShellExecutor

private const val TAG = "LauncherAmStack"

/**
 * OEM ActivityManager shell helpers that work under the app UID on X50
 * (`am stack list` / `am stack remove`). Binder [ActivityManager.removeTask] is unreliable here.
 */
internal object LauncherAmStackShell {
    private const val AM_SHELL_TIMEOUT_MS = 1_000L

    private val STACK_ID_REGEX = Regex("""Stack id=(\d+)""")
    private val WINDOWING_MODE_REGEX = Regex("""mWindowingMode=(\w+)""")
    private val TASK_ID_PKG_REGEX = Regex("""taskId=(\d+):\s*([^/\s]+)""")
    private val TOP_ACTIVITY_REGEX = Regex("""topActivity=ComponentInfo\{[^/]+/([^}\s]+)""")
    private val VISIBLE_REGEX = Regex("""visible=(true|false)""")
    private val BOUNDS_RECT_REGEX = Regex("""bounds=Rect\((-?\d+),\s*(-?\d+)\s*-\s*(-?\d+),\s*(-?\d+)\)""")
    private val MBOUNDS_RECT_REGEX = Regex("""mBounds=Rect\((-?\d+),\s*(-?\d+)\s*-\s*(-?\d+),\s*(-?\d+)\)""")
    private val BOUNDS_BRACKETS_REGEX = Regex("""bounds=\[(-?\d+),\s*(-?\d+)]\[(-?\d+),\s*(-?\d+)]""")

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
            val stackMatch = STACK_ID_REGEX.find(line)
            if (stackMatch != null) {
                flush()
                stackId = stackMatch.groupValues[1].toInt()
                continue
            }
            WINDOWING_MODE_REGEX.find(line)?.let {
                mode = it.groupValues[1]
            }
            TASK_ID_PKG_REGEX.find(line)?.let {
                taskId = it.groupValues[1].toInt()
                pkg = it.groupValues[2]
            }
            TOP_ACTIVITY_REGEX.find(line)?.let {
                topClass = it.groupValues[1]
            }
            VISIBLE_REGEX.find(line)?.let {
                visible = it.groupValues[1] == "true"
            }
            val boundsMatch = BOUNDS_RECT_REGEX.find(line)
                ?: MBOUNDS_RECT_REGEX.find(line)
                ?: BOUNDS_BRACKETS_REGEX.find(line)
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

    fun focusTask(taskId: Int): Boolean {
        val out = runAm("stack", "focus-task", taskId.toString())
        Log.w(TAG, "am stack focus-task $taskId out=${out?.take(120)}")
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
            val isFullscreenBounds = s.bounds == null || s.bounds.left == 0
            val isTrulyFreeform = isFreeformMode && !isFullscreenBounds

            if (isTrulyFreeform) {
                if (removeStack(s.stackId)) {
                    removed++
                    FreeformLaunchRegistry.remove(pkg)
                    Log.w(TAG, "removed freeform stack=${s.stackId} pkg=$pkg mode=${s.windowingMode}")
                }
            } else if (packages.isNotEmpty() && pkg in packages) {
                if (removeStack(s.stackId)) {
                    removed++
                    FreeformLaunchRegistry.remove(pkg)
                    Log.w(TAG, "removed fullscreen stack=${s.stackId} pkg=$pkg mode=${s.windowingMode}")
                }
            }
        }
        return removed
    }

    private fun runAm(vararg args: String): String? {
        val cmd = ArrayList<String>(args.size + 1).apply {
            add("am")
            addAll(args)
        }
        val res = ShellExecutor.exec(cmd, timeoutMs = AM_SHELL_TIMEOUT_MS)
        if (res.timedOut) {
            Log.w(TAG, "am ${args.joinToString(" ")} timed out")
            return null
        }
        val text = res.output
        val hasError = text.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed.startsWith("SecurityException", ignoreCase = true) ||
                trimmed.startsWith("java.lang.SecurityException", ignoreCase = true) ||
                trimmed.startsWith("Error:", ignoreCase = true) ||
                trimmed.contains("Security exception", ignoreCase = true)
        }
        if (hasError) {
            Log.w(TAG, "am ${args.joinToString(" ")} denied: ${text.take(200)}")
            return null
        }
        return text
    }
}
