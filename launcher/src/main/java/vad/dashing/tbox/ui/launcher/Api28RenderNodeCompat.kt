package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unblocks Compose's `GraphicsLayerV23` (`android.view.RenderNode` / `DisplayListCanvas`) on
 * Android 9 (API 28) when the app targets SDK 28+.
 *
 * Background:
 * - On Android 10+ (API 29+), Jetpack Compose uses public `android.graphics.RenderNode`
 *   (`GraphicsLayerV29`).
 * - On Android 9 (API 28), Compose falls back to `GraphicsLayerV23`, which probes hidden
 *   `android.view.RenderNode` & `android.view.DisplayListCanvas` methods (including
 *   `getAmbientShadowColor` / `setAmbientShadowColor`).
 * - Android 9 hidden-API restrictions block those methods for apps targeting API 28+, forcing
 *   `AndroidGraphicsContext` onto `GraphicsViewLayer`.
 * - Every `GraphicsViewLayer.record()` toggles `viewLayer.visibility = INVISIBLE -> VISIBLE`
 *   inside `AndroidComposeView.dispatchDraw()`, which causes `ViewRootImpl` on API 28 to schedule
 *   a phantom `CALLBACK_INPUT` + `CALLBACK_TRAVERSAL` + `RenderThread::DrawFrame` on the very
 *   next VSYNC (~16.7 ms later) — doubling the real frame rate (15 FPS -> 30 FPS, 27 FPS -> 53 FPS).
 *
 * Exempting only the three RenderNode/Canvas prefixes via `VMRuntime.setHiddenApiExemptions`
 * before the first `ComposeView` is created lets Compose use native `RenderNode` display lists
 * on API 28 without `ViewLayer` visibility toggling. Completely no-op on API != 28.
 */
internal object Api28RenderNodeCompat {
    private const val TAG = "Api28RenderNodeCompat"
    private const val SETTING_HIDDEN_API_EXEMPTIONS = "hidden_api_blacklist_exemptions"

    internal val RENDER_NODE_EXEMPTION_PREFIXES: List<String> = listOf(
        "Landroid/view/RenderNode;",
        "Landroid/view/DisplayListCanvas;",
        "Landroid/graphics/Canvas;",
    )

    private val installed = AtomicBoolean(false)

    internal fun shouldApplyRenderNodeExemption(sdkInt: Int): Boolean =
        sdkInt == Build.VERSION_CODES.P

    internal fun mergeExemptionPrefixes(
        existingRaw: String?,
        required: List<String> = RENDER_NODE_EXEMPTION_PREFIXES,
    ): Array<String> {
        val merged = LinkedHashSet<String>()
        if (!existingRaw.isNullOrBlank()) {
            for (token in existingRaw.split(',')) {
                val trimmed = token.trim()
                if (trimmed.isNotEmpty()) {
                    merged.add(trimmed)
                }
            }
        }
        if ("L" !in merged && "*" !in merged) {
            merged.addAll(required)
        }
        return merged.toTypedArray()
    }

    fun installIfNeeded(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        if (!shouldApplyRenderNodeExemption(sdkInt)) return false
        if (!installed.compareAndSet(false, true)) return true

        val existingRaw = runCatching {
            Settings.Global.getString(context.contentResolver, SETTING_HIDDEN_API_EXEMPTIONS)
        }.getOrNull()
        val prefixes = mergeExemptionPrefixes(existingRaw)

        val exempted = runCatching {
            val classArrayType = emptyArray<Class<*>>().javaClass
            val stringArrayType = emptyArray<String>().javaClass
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                classArrayType,
            )
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "getRuntime",
                emptyArray<Class<*>>(),
            ) as Method
            val setHiddenApiExemptions = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf<Class<*>>(stringArrayType),
            ) as Method
            val vmRuntime = getRuntime.invoke(null)
            setHiddenApiExemptions.invoke(vmRuntime, prefixes)
            true
        }.onFailure { error ->
            Log.w(TAG, "Failed to install API 28 RenderNode hidden-API exemption: ${error.message}")
        }.getOrDefault(false)

        if (!exempted) return false

        val verified = verifyRenderNodeAccess()
        Log.i(
            TAG,
            "Installed API 28 RenderNode hidden-API exemption (verified=$verified, prefixes=${prefixes.joinToString(",")})",
        )
        return verified
    }

    @Suppress("PrivateApi", "DiscouragedPrivateApi")
    private fun verifyRenderNodeAccess(): Boolean = runCatching {
        val renderNodeClass = Class.forName("android.view.RenderNode")
        renderNodeClass.getDeclaredMethod("getAmbientShadowColor")
        renderNodeClass.getDeclaredMethod("setAmbientShadowColor", Int::class.javaPrimitiveType)
        val displayListCanvasClass = Class.forName("android.view.DisplayListCanvas")
        displayListCanvasClass.getDeclaredMethod("drawRenderNode", renderNodeClass)
        true
    }.onFailure { error ->
        Log.w(TAG, "API 28 RenderNode access verification failed: ${error.message}")
    }.getOrDefault(false)
}
