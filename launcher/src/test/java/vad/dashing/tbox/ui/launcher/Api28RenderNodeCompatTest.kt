package vad.dashing.tbox.ui.launcher

import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Api28RenderNodeCompatTest {

    @Test
    fun shouldApplyRenderNodeExemption_onlyMatchesApi28() {
        assertFalse(Api28RenderNodeCompat.shouldApplyRenderNodeExemption(Build.VERSION_CODES.O_MR1))
        assertTrue(Api28RenderNodeCompat.shouldApplyRenderNodeExemption(Build.VERSION_CODES.P))
        assertFalse(Api28RenderNodeCompat.shouldApplyRenderNodeExemption(Build.VERSION_CODES.Q))
        assertFalse(Api28RenderNodeCompat.shouldApplyRenderNodeExemption(34))
    }

    @Test
    fun mergeExemptionPrefixes_preservesExistingAndDeduplicates() {
        assertArrayEquals(
            arrayOf(
                "Landroid/view/RenderNode;",
                "Landroid/view/DisplayListCanvas;",
                "Landroid/graphics/Canvas;",
            ),
            Api28RenderNodeCompat.mergeExemptionPrefixes(null),
        )

        assertArrayEquals(
            arrayOf(
                "Lcom/oem/Custom;",
                "Landroid/view/RenderNode;",
                "Landroid/view/DisplayListCanvas;",
                "Landroid/graphics/Canvas;",
            ),
            Api28RenderNodeCompat.mergeExemptionPrefixes(" Lcom/oem/Custom; , Landroid/view/RenderNode; "),
        )

        assertArrayEquals(
            arrayOf("L"),
            Api28RenderNodeCompat.mergeExemptionPrefixes("L"),
        )
    }
}
