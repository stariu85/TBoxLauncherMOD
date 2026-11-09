package vad.dashing.tbox.mbcan

import androidx.compose.runtime.Immutable

/** Door / tailgate state for launcher and vehicle UI. */
@Immutable
data class VehicleBodyState(
    val doorFlOpen: Boolean = false,
    val doorFrOpen: Boolean = false,
    val doorRlOpen: Boolean = false,
    val doorRrOpen: Boolean = false,
    val tailgateOpen: Boolean = false,
    val hoodOpen: Boolean = false,
) {
    fun anyDoorOpen(): Boolean =
        doorFlOpen || doorFrOpen || doorRlOpen || doorRrOpen || tailgateOpen || hoodOpen
}

/**
 * OEM CEM body status observed on the Android 9 head unit:
 * 1 = closed, 2 = open. 0/other values are invalid/unknown and must not raise
 * a warning, otherwise every closed door is shown as open.
 */
internal fun decodeMbCanDoorByte(status: Byte, previousOpen: Boolean = false): Boolean {
    val v = status.toInt() and 0xFF
    return when (v) {
        2 -> true
        1 -> false
        else -> previousOpen
    }
}

internal fun decodeMbCanTrunkByte(status: Byte, previousOpen: Boolean = false): Boolean =
    decodeMbCanDoorByte(status, previousOpen)

internal fun decodeVhalDoorOpen(raw: Int?, previousOpen: Boolean = false): Boolean = when (raw) {
    2 -> true
    1 -> false
    else -> previousOpen
}
