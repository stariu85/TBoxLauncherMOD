package vad.dashing.tbox

import android.content.Context

/** Stock [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_MIRROR_FOLD_SWITCH]: fold. */
const val MIRROR_FOLD_SWITCH_VALUE_FOLD = 1

/** Stock: unfold. */
const val MIRROR_FOLD_SWITCH_VALUE_UNFOLD = 2

/**
 * Remembers the last mirror fold/unfold command we sent.
 * Actual mirror position is not readable over mbCAN/VHAL, so single-tap toggle
 * alternates relative to this value.
 */
object MirrorFoldLastCommandStore {
    const val DEFAULT_LAST_VALUE = MIRROR_FOLD_SWITCH_VALUE_UNFOLD
    private const val PREFS_NAME = "tbox_mirror_fold_store"
    private const val KEY_LAST_FOLD_VALUE = "last_fold_value"

    @Volatile
    private var lastValue: Int = DEFAULT_LAST_VALUE

    @Volatile
    private var isLoaded = false

    fun readLastValue(): Int {
        if (!isLoaded) {
            val context = AppContextHolder.appContextOrNull
            if (context != null) {
                runCatching {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    lastValue = prefs.getInt(KEY_LAST_FOLD_VALUE, DEFAULT_LAST_VALUE)
                    isLoaded = true
                }
            }
        }
        return MirrorFoldToggleLogic.normalizeStoredValue(lastValue)
    }

    fun rememberSent(value: Int) {
        val normalized = MirrorFoldToggleLogic.normalizeStoredValue(value)
        lastValue = normalized
        val context = AppContextHolder.appContextOrNull ?: return
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_FOLD_VALUE, normalized)
                .apply()
        }
    }

    /** Opposite of the last sent command — value for the next single tap. */
    fun nextSingleTapValue(): Int = MirrorFoldToggleLogic.nextSingleTapValue(readLastValue())

    /** Test helper: restore default (unfolded) session state. */
    internal fun resetForTests() {
        lastValue = DEFAULT_LAST_VALUE
        isLoaded = true
    }
}

/** Pure toggle policy for mirror fold commands (unit-testable without Android). */
object MirrorFoldToggleLogic {
    fun normalizeStoredValue(value: Int): Int = if (value == MIRROR_FOLD_SWITCH_VALUE_FOLD) {
        MIRROR_FOLD_SWITCH_VALUE_FOLD
    } else {
        MIRROR_FOLD_SWITCH_VALUE_UNFOLD
    }

    fun opposite(value: Int): Int = if (normalizeStoredValue(value) == MIRROR_FOLD_SWITCH_VALUE_FOLD) {
        MIRROR_FOLD_SWITCH_VALUE_UNFOLD
    } else {
        MIRROR_FOLD_SWITCH_VALUE_FOLD
    }

    fun nextSingleTapValue(lastSentValue: Int): Int = opposite(lastSentValue)
}
