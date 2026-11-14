package vad.dashing.tbox.ui.launcher

import androidx.annotation.StringRes
import com.google.android.filament.Colors
import com.google.android.filament.MaterialInstance
import io.github.sceneview.material.setParameter
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.model.renderableManager
import vad.dashing.tbox.R

data class LauncherCarPaintOption(
    val id: String,
    @StringRes val labelRes: Int,
    val colorArgb: Int,
)

object LauncherCarPaint {
    val options: List<LauncherCarPaintOption> = listOf(
        LauncherCarPaintOption("cheqi01", R.string.launcher_paint_blue, 0xFF498BB1.toInt()),
        LauncherCarPaintOption("cheqi02", R.string.launcher_paint_red, 0xFF8F0313.toInt()),
        // Near-black: 0x222222 reads as charcoal under Filament IBL/specular.
        LauncherCarPaintOption("cheqi03", R.string.launcher_paint_black, 0xFF050505.toInt()),
        LauncherCarPaintOption("cheqi04", R.string.launcher_paint_white, 0xFFCACACA.toInt()),
        LauncherCarPaintOption("cheqi05", R.string.launcher_paint_gray, 0xFF141618.toInt()),
        // Deep British-racing green. IBL lifts mid-greens, so the stored
        // swatch is already darker than #013220 and apply() shades it again.
        LauncherCarPaintOption("cheqi06", R.string.launcher_paint_green, 0xFF000C08.toInt()),
    )

    val defaultId: String = "cheqi04"

    fun optionFor(id: String): LauncherCarPaintOption =
        options.firstOrNull { it.id == id } ?: options.first()

    fun nextId(currentId: String): String {
        val index = options.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        return options[(index + 1) % options.size].id
    }

    /** Collects body-paint material instances from GLB materials named cheqi*. */
    fun bindMaterials(modelInstance: ModelInstance): List<MaterialInstance> {
        val asset = modelInstance.model
        val renderableManager = modelInstance.renderableManager
        val allMaterials = buildList {
            for (entity in modelInstance.entities) {
                if (!renderableManager.hasComponent(entity)) continue
                val renderable = renderableManager.getInstance(entity)
                val primitiveCount = renderableManager.getPrimitiveCount(renderable)
                for (primitiveIndex in 0 until primitiveCount) {
                    add(renderableManager.getMaterialInstanceAt(renderable, primitiveIndex))
                }
            }
        }.distinct()
        val byMaterialName = allMaterials.filter { material ->
            material.instanceName().startsWith("cheqi")
        }
        if (byMaterialName.isNotEmpty()) return byMaterialName

        return buildList {
            for (entity in modelInstance.entities) {
                if (!renderableManager.hasComponent(entity)) continue
                val name = asset.getName(entity).orEmpty()
                if (!name.contains("cheqi", ignoreCase = true)) continue
                val renderable = renderableManager.getInstance(entity)
                val primitiveCount = renderableManager.getPrimitiveCount(renderable)
                for (primitiveIndex in 0 until primitiveCount) {
                    add(renderableManager.getMaterialInstanceAt(renderable, primitiveIndex))
                }
            }
        }
    }

    private fun MaterialInstance.instanceName(): String =
        runCatching {
            val method = javaClass.getMethod("getName")
            method.invoke(this) as? String
        }.getOrNull().orEmpty()

    fun apply(materials: List<MaterialInstance>, paintId: String) {
        if (materials.isEmpty()) return
        val color = optionFor(paintId).colorArgb
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val isGreen = paintId == "cheqi06"
        val shade = if (isGreen) 0.38f else 1f
        val rr = r * shade
        val gg = g * shade
        val bb = b * shade
        // Same clearcoat as black: highlights read the body panels on every colour.
        val metallic = 0.85f
        val roughness = 0.28f
        materials.forEach { material ->
            runCatching { material.setParameter("baseColorFactor", rr, gg, bb, 1f) }
            runCatching { material.setParameter("baseColor", Colors.RgbaType.SRGB, rr, gg, bb, 1f) }
            runCatching { material.setParameter("roughnessFactor", roughness) }
            runCatching { material.setParameter("metallicFactor", metallic) }
            runCatching { material.setParameter("roughness", roughness) }
            runCatching { material.setParameter("metallic", metallic) }
            runCatching { material.setParameter("specularFactor", 0.55f) }
            runCatching { material.setParameter("reflectance", 0.50f) }
            if (isGreen) {
                runCatching { material.setParameter("emissiveFactor", 0f, 0f, 0f) }
                runCatching { material.setParameter("emissive", Colors.RgbaType.LINEAR, 0f, 0f, 0f, 1f) }
            }
        }
    }
}
