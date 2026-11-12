package vad.dashing.tbox

/**
 * Stock SystemSettings mixer mapping (not mbCAN/VHAL).
 *
 * A9: OpenOS `AudioManager` volume groups by `AudioAttributes.USAGE`.
 * A10: Adayo `SettingsSvcIfManager.setAudioStreamVolume`.
 */
object PlatformAudioDomain {
    const val HEADREST_ONLY = 1
    const val HEADREST_ASSIST = 2
    const val HEADREST_OFF = 3

    val headrestUiValues: Set<Int> = setOf(HEADREST_ONLY, HEADREST_ASSIST, HEADREST_OFF)

    /**
     * A9 `eAUDIO_AUDIO_HEADREST_SPEAKER` (37): 0 close / 1 headrest / 2 auxiliary.
     * Shared UI matches A10: 1 only / 2 assist / 3 off.
     */
    fun decodeHeadrestMbCan(raw: Int): Int? = when (raw) {
        0 -> HEADREST_OFF
        1 -> HEADREST_ONLY
        2 -> HEADREST_ASSIST
        else -> null
    }

    fun encodeHeadrestMbCan(ui: Int): Int? = when (ui) {
        HEADREST_ONLY -> 1
        HEADREST_ASSIST -> 2
        HEADREST_OFF -> 0
        else -> null
    }

    fun decodeHeadrestVhal(raw: Int): Int? = raw.takeIf { it in headrestUiValues }
}
