package vad.dashing.tbox.mbcan

/**
 * Representation of turn indicators and hazard state.
 */
data class TurnSignalsState(
    val leftActive: Boolean? = null,
    val rightActive: Boolean? = null,
    val hazardActive: Boolean? = null,
)

enum class TurnSignalSide {
    Left,
    Right,
    Hazard,
}

object TurnSignalsDomain {
    /**
     * mbCAN turn-light byte: active when raw == 2.
     * Observed idle / off encodings in OEM payloads: 0, 1.
     */
    fun decodeMbCanTurnLightActive(raw: Int): Boolean? = when (raw) {
        2 -> true
        0, 1 -> false
        else -> null
    }

    /**
     * Hazard from paired turn-light actives.
     * Both true => hazard on; either known false with the other not both-true => hazard off
     * when both sides are known.
     */
    fun hazardFromTurnLightPair(leftActive: Boolean?, rightActive: Boolean?): Boolean? {
        if ((leftActive == true) && (rightActive == true)) return true
        if (leftActive != null && rightActive != null) return false
        return null
    }

    /** Build state from TURNLIGHT left/right raw bytes. */
    fun fromMbCanTurnLightRaw(leftRaw: Int, rightRaw: Int): TurnSignalsState {
        val left = decodeMbCanTurnLightActive(leftRaw)
        val right = decodeMbCanTurnLightActive(rightRaw)
        return TurnSignalsState(
            leftActive = left,
            rightActive = right,
            hazardActive = hazardFromTurnLightPair(left, right),
        )
    }
}
