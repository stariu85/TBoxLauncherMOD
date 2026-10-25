package vad.dashing.tbox.ui.launcher

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
data class LauncherTireWheelState(
    val pressureBar: Float? = null,
    val temperatureC: Int? = null,
    val warningSts: Int = 0,
    val calibrating: Boolean = false,
    val available: Boolean = false,
) {
    /** Show callout next to the 3D car — OEM abnormal or clearly low pressure (< [lowPressureThreshold]). */
    fun needsAttention(lowPressureThreshold: Float = LOW_PRESSURE_BAR): Boolean =
        warningSts != 0 || calibrating || (available && pressureBar == null) ||
            (pressureBar != null && pressureBar < lowPressureThreshold)

    val needsAttention: Boolean
        get() = needsAttention(LOW_PRESSURE_BAR)

    companion object {
        /** User-requested visual threshold for low-pressure callout. */
        const val LOW_PRESSURE_BAR = 2.0f
    }
}

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

    fun hasAttention(lowPressureThreshold: Float = LauncherTireWheelState.LOW_PRESSURE_BAR): Boolean =
        fl.needsAttention(lowPressureThreshold) ||
            fr.needsAttention(lowPressureThreshold) ||
            rl.needsAttention(lowPressureThreshold) ||
            rr.needsAttention(lowPressureThreshold)

    val hasAttention: Boolean
        get() = hasAttention(LauncherTireWheelState.LOW_PRESSURE_BAR)

    val hasAvailableData: Boolean
        get() = (fl.available && fl.pressureBar != null) ||
            (fr.available && fr.pressureBar != null) ||
            (rl.available && rl.pressureBar != null) ||
            (rr.available && rr.pressureBar != null)
}

internal fun mergeTireStateWithCanData(mbCanTires: LauncherTireState, canWheels: Wheels): LauncherTireState {
    fun mergeWheel(wheel: LauncherTireWheelState, canPressure: Float?): LauncherTireWheelState {
        if (wheel.available && wheel.pressureBar != null) return wheel
        if (canPressure != null) {
            return wheel.copy(
                pressureBar = canPressure,
                available = true,
            )
        }
        return wheel
    }
    return LauncherTireState(
        fl = mergeWheel(mbCanTires.fl, canWheels.wheel1),
        fr = mergeWheel(mbCanTires.fr, canWheels.wheel2),
        rl = mergeWheel(mbCanTires.rl, canWheels.wheel3),
        rr = mergeWheel(mbCanTires.rr, canWheels.wheel4),
    )
}

internal fun parseLauncherTireWheel(info: Any?): LauncherTireWheelState {
    if (info == null) return LauncherTireWheelState()
    val pressure = runCatching {
        (info.javaClass.getMethod("getPressure").invoke(info) as? Number)?.toFloat()
    }.getOrNull()
    val warning = runCatching {
        (info.javaClass.getMethod("getWarningSts").invoke(info) as? Number)?.toInt() ?: 0
    }.getOrDefault(0)
    val temperature = runCatching {
        (info.javaClass.getMethod("getTemperature").invoke(info) as? Number)?.toInt()
    }.getOrNull()
    val calibrating = pressure == -1f
    val pressureValid = pressure != null && pressure.isFinite() && pressure > 0f
    val tempValid = temperature != null && temperature != -100 && temperature in -80..150
    return LauncherTireWheelState(
        pressureBar = pressure.takeIf { pressureValid },
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
