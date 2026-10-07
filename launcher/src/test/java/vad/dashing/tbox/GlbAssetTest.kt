package vad.dashing.tbox

import org.junit.Test
import vad.dashing.tbox.ui.launcher.LauncherCarAssetStructure
import java.io.File

class GlbAssetTest {
    @Test
    fun testGlbAssetStructure() {
        val glbFile = File("src/main/assets/models/jetour_dashing_site.glb").let {
            if (it.exists()) it else File("launcher/src/main/assets/models/jetour_dashing_site.glb")
        }
        val bytes = glbFile.readBytes()
        val json = LauncherCarAssetStructure.extractGlbJson(bytes)
        val animSectionIdx = json.indexOf("\"animations\"")
        check(animSectionIdx != -1) { "Animations expected in GLB" }
    }
}
