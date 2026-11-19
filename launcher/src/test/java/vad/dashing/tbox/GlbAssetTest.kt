package vad.dashing.tbox

import org.junit.Test
import vad.dashing.tbox.ui.launcher.LAUNCHER_CAR_MODEL_ASSET
import vad.dashing.tbox.ui.launcher.LauncherCarAssetStructure
import java.io.File

class GlbAssetTest {
    @Test
    fun testGlbAssetStructure() {
        val glbFile = File("src/main/assets/$LAUNCHER_CAR_MODEL_ASSET").let {
            if (it.exists()) it else File("launcher/src/main/assets/$LAUNCHER_CAR_MODEL_ASSET")
        }
        val bytes = glbFile.readBytes()
        val json = LauncherCarAssetStructure.extractGlbJson(bytes)
        val animSectionIdx = json.indexOf("\"animations\"")
        check(animSectionIdx != -1) { "Animations expected in GLB" }
    }
}
