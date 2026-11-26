package vad.dashing.tbox

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlin.Boolean
import kotlin.collections.List

class CanDataViewModel : ViewModel() {
    val odometer: StateFlow<UInt?> = CanDataRepository.odometer
    val localTripDistanceKm: StateFlow<Float?> = CanDataRepository.localTripDistanceKm
    val manualTripDistanceKm: StateFlow<Float?> = CanDataRepository.manualTripDistanceKm
    val localTripDurationSeconds: StateFlow<Long?> = CanDataRepository.localTripDurationSeconds
    val distanceToNextMaintenance: StateFlow<UInt?> = CanDataRepository.distanceToNextMaintenance
    val distanceToFuelEmpty: StateFlow<UInt?> = CanDataRepository.distanceToFuelEmpty
    val breakingForce: StateFlow<UInt?> = CanDataRepository.breakingForce
    val engineRPM: StateFlow<Float?> = CanDataRepository.engineRPM
    val param1: StateFlow<Float?> = CanDataRepository.param1
    val param2: StateFlow<Float?> = CanDataRepository.param2
    val param3: StateFlow<Float?> = CanDataRepository.param3
    val param4: StateFlow<Float?> = CanDataRepository.param4
    val carSpeed: StateFlow<Float?> = CanDataRepository.carSpeed
    val carSpeedAccurate: StateFlow<Float?> = CanDataRepository.carSpeedAccurate
    val voltage: StateFlow<Float?> = CanDataRepository.voltage
    val fuelLevelPercentage: StateFlow<UInt?> = CanDataRepository.fuelLevelPercentage
    val fuelLevelPercentageFiltered: StateFlow<UInt?> = CanDataRepository.fuelLevelPercentageFiltered
    val fuelLevelCalibratedLiters: StateFlow<Float?> = CanDataRepository.fuelLevelCalibratedLiters
    val fuelLevelCalibratedLitersActual: StateFlow<Float?> = CanDataRepository.fuelLevelCalibratedLitersActual
    val fuelCalibrationConfidence: StateFlow<Float?> = CanDataRepository.fuelCalibrationConfidence
    val currentFuelConsumption: StateFlow<Float?> = CanDataRepository.currentFuelConsumption
    val averageFuelConsumption: StateFlow<Float?> = CanDataRepository.averageFuelConsumption
    val cruiseSetSpeed: StateFlow<UInt?> = CanDataRepository.cruiseSetSpeed
    val wheelsSpeed: StateFlow<Wheels> = CanDataRepository.wheelsSpeed
    val wheelsPressure: StateFlow<Wheels> = CanDataRepository.wheelsPressure
    val wheelsTemperature: StateFlow<Wheels> = CanDataRepository.wheelsTemperature
    val steerAngle: StateFlow<Float?> = CanDataRepository.steerAngle
    val steerSpeed: StateFlow<Int?> = CanDataRepository.steerSpeed
    val climateSetTemperature1: StateFlow<Float?> = CanDataRepository.climateSetTemperature1
    val climateSetTemperature2: StateFlow<Float?> = CanDataRepository.climateSetTemperature2
    val engineTemperature: StateFlow<Float?> = CanDataRepository.engineTemperature
    val param5: StateFlow<Float?> = CanDataRepository.param5
    val gearBoxMode: StateFlow<String> = CanDataRepository.gearBoxMode
    val gearBoxCurrentGear: StateFlow<Int?> = CanDataRepository.gearBoxCurrentGear
    val gearBoxChangeGear: StateFlow<Boolean?> = CanDataRepository.gearBoxChangeGear
    val gearBoxPreparedGear: StateFlow<Int?> = CanDataRepository.gearBoxPreparedGear
    val gearBoxOilTemperature: StateFlow<Int?> = CanDataRepository.gearBoxOilTemperature
    val gearBoxDriveMode: StateFlow<String> = CanDataRepository.gearBoxDriveMode
    val gearBoxWork: StateFlow<String> = CanDataRepository.gearBoxWork
    val gearboxType: StateFlow<GearboxType> = CanDataRepository.gearboxType
    val is6DctGearbox: StateFlow<Boolean> = CanDataRepository.is6DctGearbox
    val isManualGearbox: StateFlow<Boolean> = CanDataRepository.isManualGearbox
    val frontLeftSeatMode: StateFlow<UInt?> = CanDataRepository.frontLeftSeatMode
    val frontRightSeatMode: StateFlow<UInt?> = CanDataRepository.frontRightSeatMode
    val outsideTemperature: StateFlow<Float?> = CanDataRepository.outsideTemperature
    val insideTemperature: StateFlow<Float?> = CanDataRepository.insideTemperature
    val outsideAirQuality: StateFlow<UInt?> = CanDataRepository.outsideAirQuality
    val insideAirQuality: StateFlow<UInt?> = CanDataRepository.insideAirQuality
    val isWindowsBlocked: StateFlow<Boolean?> = CanDataRepository.isWindowsBlocked
    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = CanDataRepository.canFramesStructured
    val motorHoursTrip: StateFlow<Float?> = CanDataRepository.motorHoursTrip
}

@StringRes
private fun seatModeToStringRes(seatMode: UInt?): Int? {
    return when (seatMode) {
        1u -> R.string.seat_mode_off
        2u -> R.string.seat_mode_heat_1
        3u -> R.string.seat_mode_heat_2
        4u -> R.string.seat_mode_heat_3
        5u -> R.string.seat_mode_vent_1
        6u -> R.string.seat_mode_vent_2
        7u -> R.string.seat_mode_vent_3
        else -> null
    }
}

fun seatModeToString(context: Context, seatMode: UInt?): String {
    if (seatMode == null) return ""
    val resId = seatModeToStringRes(seatMode) ?: return seatMode.toString()
    return context.getString(resId)
}