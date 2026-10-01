package vad.dashing.tbox.ui.launcher

import android.util.Log

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

        fun flush() {
            val id = stackId ?: return
            rows += StackRow(id, mode, taskId, pkg, visible, topClass)
            stackId = null
            mode = ""
            taskId = null
            pkg = null
            visible = false
            topClass = null
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
        }
        flush()
        return rows
    }

    fun removeStack(stackId: Int): Boolean {
        val out = runAm("stack", "remove", stackId.toString())
        Log.w(TAG, "am stack remove $stackId out=${out?.take(120)}")
        return out != null
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
            val isFreeform = s.windowingMode.equals("freeform", ignoreCase = true)
            val tracked = pkg in packages
            if (!isFreeform && !tracked) continue
            if (removeStack(s.stackId)) {
                removed++
                FreeformLaunchRegistry.remove(pkg)
                Log.w(TAG, "removed stack=${s.stackId} pkg=$pkg mode=${s.windowingMode}")
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
            thread.join(250L)
            if (thread.isAlive) {
                proc.destroy()
                Log.w(TAG, "am ${args.joinToString(" ")} timed out")
                return null
            }
            val code = proc.waitFor()
            if (code != 0 && text.contains("Security exception", ignoreCase = true)) {
                Log.w(TAG, "am ${args.joinToString(" ")} denied: $text")
                return null
            }
            text
        }.onFailure {
            Log.w(TAG, "am ${args.joinToString(" ")} failed", it)
        }.getOrNull()
}
