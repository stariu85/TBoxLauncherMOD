package vad.dashing.tbox.ui.launcher

import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.FilamentInstance

/**
 * Drives authored glTF clips through gltfio instead of writing TransformManager matrices.
 * This is the supported animation path on the Android 9 head unit and keeps sibling meshes intact.
 */
class LauncherCarAnimationController private constructor(
    private val animator: Animator,
    private val clipIndices: Map<String, Int>,
) {
    private var doorFl = 0f
    private var doorFr = 0f
    private var doorRl = 0f
    private var doorRr = 0f
    private var tailgate = 0f
    private var settleSec = 0f

    fun update(state: LauncherCarRigState, dtSec: Float, modelReady: Boolean = true) {
        // A freshly bound rig always shows every body part closed first, then animates
        // toward the live state. Snapping to an already-open pose on bind (e.g. settings
        // opened with the trunk open) made the model appear broken; the settle window
        // covers model warmup + fade-in so the closed pose is actually seen first.
        if (modelReady) {
            settleSec += dtSec.coerceIn(0f, 0.1f)
        }
        val settled = modelReady && (settleSec >= SETTLE_BEFORE_OPEN_SEC)

        doorFl = stepDoor(doorFl, state.doorFlOpen, settled, dtSec)
        doorFr = stepDoor(doorFr, state.doorFrOpen, settled, dtSec)
        doorRl = stepDoor(doorRl, state.doorRlOpen, settled, dtSec)
        doorRr = stepDoor(doorRr, state.doorRrOpen, settled, dtSec)
        // Snap closed: exponential approach alone left the lid visually ajar when CAN
        // briefly flickered or settled slowly after a physical close.
        tailgate = if (!state.tailgateOpen || !settled) {
            0f
        } else {
            LauncherCarRig.approachBody(tailgate, true, dtSec)
        }

        apply(CLIP_DOOR_FL, doorFl)
        apply(CLIP_DOOR_FR, doorFr)
        apply(CLIP_DOOR_RL, doorRl)
        apply(CLIP_DOOR_RR, doorRr)
        apply(CLIP_TAILGATE, tailgate)
        animator.updateBoneMatrices()
    }

    private fun stepDoor(current: Float, open: Boolean, settled: Boolean, dtSec: Float): Float =
        if (!settled) 0f else LauncherCarRig.approachBody(current, open, dtSec)

    private fun apply(name: String, progress: Float) {
        val index = clipIndices[name] ?: return
        animator.applyAnimation(index, progress.coerceIn(0f, 1f))
    }

    companion object {
        /** Closed pose hold after bind — model warmup (12 frames) + alpha fade (~0.22s). */
        private const val SETTLE_BEFORE_OPEN_SEC = 0.6f
        private const val CLIP_DOOR_FL = "door_fl"
        private const val CLIP_DOOR_FR = "door_fr"
        private const val CLIP_DOOR_RL = "door_rl"
        private const val CLIP_DOOR_RR = "door_rr"
        private const val CLIP_TAILGATE = "tailgate"
        private val REQUIRED_CLIPS = setOf(
            CLIP_DOOR_FL,
            CLIP_DOOR_FR,
            CLIP_DOOR_RL,
            CLIP_DOOR_RR,
            CLIP_TAILGATE,
        )

        fun bind(modelInstance: FilamentInstance): LauncherCarAnimationController? {
            val animator = modelInstance.animator
            val indices = buildMap {
                for (index in 0 until animator.animationCount) {
                    val name = animator.getAnimationName(index)
                    if (name in REQUIRED_CLIPS) put(name, index)
                }
            }
            if (!indices.keys.containsAll(REQUIRED_CLIPS)) return null
            val controller = LauncherCarAnimationController(animator, indices)
            indices.values.forEach { clipIndex ->
                animator.applyAnimation(clipIndex, 0f)
            }
            animator.updateBoneMatrices()
            return controller
        }
    }
}
