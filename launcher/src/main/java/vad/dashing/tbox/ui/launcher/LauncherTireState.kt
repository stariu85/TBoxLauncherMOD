package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Immutable
import vad.dashing.tbox.Wheels

/**
 * TPMS wheel index matches OEM [MBTiresView] / CAN 0x51B:
 * 0=FL, 1=FR, 2=RL, 3=RR.
 */
enum class LauncherWheelCorner(val index: Int) {
    FL(0),
    FR(1),
    RL(2),
    RR(3),
}

/**
 * Per-wheel TPMS snapshot from mbCAN [MBCanTireInfo] or TBox CAN (0x51B).
 *
 * OEM “abnormal” = [warningSts] != 0; pressure `-1` means calibrating / invalid
 * (shown as `---/--` in carsettings).
 */
@Immutable
data class LauncherTireWheelState(
    val pressureBar: Float? = null,
    val temperatureC: Int? = null,
    val warningSts: Int = 0,
    val calibrating: Boolean = false,
    val available: Boolean = false,
) {
    /** True if tire has low pressure error. */
    fun isLowPressure(lowPressureThreshold: Float = LOW_PRESSURE_BAR): Boolean {
        if (!available || calibrating || pressureBar == null) return false
        if (pressureBar < lowPressureThreshold) return true
        if (warningSts != 0 && !isLowTemperature()) return true
        return false
    }

    /** True if tire has low temperature error. */
    fun isLowTemperature(): Boolean {
        if (!available) return false
        if (warningSts == 4 || (warningSts and 4 != 0)) return true
        if (warningSts != 0 && (temperatureC != null && temperatureC <= 0)) return true
        if (warningSts != 0 && pressureBar != null && pressureBar >= LOW_PRESSURE_BAR && (temperatureC != null && temperatureC <= 5)) return true
        return false
    }

    /** Show callout next to the 3D car — OEM abnormal or clearly low pressure/temperature. */
    fun needsAttention(
        lowPressureThreshold: Float = LOW_PRESSURE_BAR,
        allowLowTempNotification: Boolean = true,
    ): Boolean {
        if (!available || calibrating || pressureBar == null) return false
        val lowPress = isLowPressure(lowPressureThreshold)
        val lowTemp = allowLowTempNotification && isLowTemperature()
        return lowPress || lowTemp
    }

    val needsAttention: Boolean
        get() = needsAttention(LOW_PRESSURE_BAR, true)

    companion object {
        /** User-requested visual threshold for low-pressure callout. */
        const val LOW_PRESSURE_BAR = 2.0f
    }
}

@Immutable
data class LauncherTireState(
    val fl: LauncherTireWheelState = LauncherTireWheelState(),
    val fr: LauncherTireWheelState = LauncherTireWheelState(),
    val rl: LauncherTireWheelState = LauncherTireWheelState(),
    val rr: LauncherTireWheelState = LauncherTireWheelState(),
) {
    fun wheel(corner: LauncherWheelCorner): LauncherTireWheelState = when (corner) {
        LauncherWheelCorner.FL -> fl
        LauncherWheelCorner.FR -> fr
        LauncherWheelCorner.RL -> rl
        LauncherWheelCorner.RR -> rr
    }

    fun hasAttention(
        lowPressureThreshold: Float = LauncherTireWheelState.LOW_PRESSURE_BAR,
        allowLowTempNotification: Boolean = true,
    ): Boolean =
        fl.needsAttention(lowPressureThreshold, allowLowTempNotification) ||
            fr.needsAttention(lowPressureThreshold, allowLowTempNotification) ||
            rl.needsAttention(lowPressureThreshold, allowLowTempNotification) ||
            rr.needsAttention(lowPressureThreshold, allowLowTempNotification)

    val hasAttention: Boolean
        get() = hasAttention(LauncherTireWheelState.LOW_PRESSURE_BAR, true)

    fun hasLowPressure(lowPressureThreshold: Float = LauncherTireWheelState.LOW_PRESSURE_BAR): Boolean =
        fl.isLowPressure(lowPressureThreshold) ||
            fr.isLowPressure(lowPressureThreshold) ||
            rl.isLowPressure(lowPressureThreshold) ||
            rr.isLowPressure(lowPressureThreshold)

    fun hasLowTemperature(): Boolean =
        fl.isLowTemperature() ||
            fr.isLowTemperature() ||
            rl.isLowTemperature() ||
            rr.isLowTemperature()

    val hasAvailableData: Boolean
        get() = (fl.available && fl.pressureBar != null) ||
            (fr.available && fr.pressureBar != null) ||
            (rl.available && rl.pressureBar != null) ||
            (rr.available && rr.pressureBar != null)
}

internal fun mergeTireStateWithCanData(
    mbCanTires: LauncherTireState,
    canWheels: Wheels,
    canTemperatures: Wheels? = null,
): LauncherTireState {
    fun mergeWheel(
        wheel: LauncherTireWheelState,
        canPressure: Float?,
        canTemp: Float?,
    ): LauncherTireWheelState {
        val rawBar = wheel.pressureBar
        val convertedBar = if (rawBar != null && rawBar > 10f) rawBar / 36f else rawBar
        val effectiveBar = canPressure ?: convertedBar
        val effectiveTemp = canTemp?.toInt() ?: wheel.temperatureC
        val isAvailable = wheel.available || effectiveBar != null || effectiveTemp != null
        if (isAvailable) {
            return wheel.copy(
                pressureBar = effectiveBar,
                temperatureC = effectiveTemp,
                available = true,
            )
        }
        return wheel
    }
    return LauncherTireState(
        fl = mergeWheel(mbCanTires.fl, canWheels.wheel1, canTemperatures?.wheel1),
        fr = mergeWheel(mbCanTires.fr, canWheels.wheel2, canTemperatures?.wheel2),
        rl = mergeWheel(mbCanTires.rl, canWheels.wheel3, canTemperatures?.wheel3),
        rr = mergeWheel(mbCanTires.rr, canWheels.wheel4, canTemperatures?.wheel4),
    )
}

internal fun parseLauncherTireWheel(info: Any?): LauncherTireWheelState {
    if (info == null) return LauncherTireWheelState()
    val rawPressure = runCatching {
        (info.javaClass.getMethod("getPressure").invoke(info) as? Number)?.toFloat()
    }.getOrNull()
    val warning = runCatching {
        (info.javaClass.getMethod("getWarningSts").invoke(info) as? Number)?.toInt() ?: 0
    }.getOrDefault(0)
    val temperature = runCatching {
        (info.javaClass.getMethod("getTemperature").invoke(info) as? Number)?.toInt()
    }.getOrNull()
    val calibrating = rawPressure == -1f
    val pressureValid = rawPressure != null && rawPressure.isFinite() && rawPressure > 0f
    val bar = if (pressureValid) {
        if (rawPressure > 10f) rawPressure / 36f else rawPressure
    } else null
    val tempValid = temperature != null && temperature != -100 && temperature in -80..150
    return LauncherTireWheelState(
        pressureBar = bar,
        temperatureC = if (tempValid) temperature else null,
        warningSts = warning,
        calibrating = calibrating,
        available = true,
    )
}

internal fun parseLauncherTireState(tires: Any?): LauncherTireState {
    if (tires == null) return LauncherTireState()
    val arr = runCatching {
        tires.javaClass.getMethod("getVstTire").invoke(tires) as? Array<*>
    }.getOrNull()
    if (arr == null || arr.size < 4) return LauncherTireState()
    return LauncherTireState(
        fl = parseLauncherTireWheel(arr[0]),
        fr = parseLauncherTireWheel(arr[1]),
        rl = parseLauncherTireWheel(arr[2]),
        rr = parseLauncherTireWheel(arr[3]),
    )
}
