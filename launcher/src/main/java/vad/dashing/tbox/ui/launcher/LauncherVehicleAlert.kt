package vad.dashing.tbox.ui.launcher

/**
 * Vehicle telltales / reminders for the launcher strip.
 * Active when the corresponding OEM status byte is non-zero (same rule as ICM/chime widgets).
 */
enum class LauncherAlertSeverity {
    Warning,
    Critical,
}

enum class LauncherAlertId {
    SeatBeltDriver,
    SeatBeltPassenger,
    SeatBeltRearLeft,
    SeatBeltRearMid,
    SeatBeltRearRight,
    DoorDriver,
    DoorPassenger,
    DoorRearLeft,
    DoorRearRight,
    HoodOpen,
    TrunkOpen,
    TirePressure,
    LowFuel,
    Speeding,
    HighTemperature,
    PressBrake,
    SysFault,
    BattFault,
    ChargeFault,
    HvFaultStop,
    PowerModeFail,
    LowBatterySoc,
    KeyBatteryLow,
}

data class LauncherVehicleAlert(
    val id: LauncherAlertId,
    val severity: LauncherAlertSeverity,
)

data class LauncherVehicleAlertsState(
    val alerts: List<LauncherVehicleAlert> = emptyList(),
) {
    val isEmpty: Boolean get() = alerts.isEmpty()
}
