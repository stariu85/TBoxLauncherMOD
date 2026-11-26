package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Front and rear seat slots for shared poll + confirmation + burst logic.
 */
enum class MbCanSeatSlot {
    FrontLeft,
    FrontRight,
    RearLeft,
    RearRight;

    val propertyId: Int
        get() = when (this) {
            FrontLeft -> MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH
            FrontRight -> MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH
            RearLeft -> MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH
            RearRight -> MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH
        }

    val signal: MbCanSignal
        get() = when (this) {
            FrontLeft -> MbCanSignal.FrontLeftSeatMode
            FrontRight -> MbCanSignal.FrontRightSeatMode
            RearLeft -> MbCanSignal.RearLeftSeatMode
            RearRight -> MbCanSignal.RearRightSeatMode
        }

    fun stateFlow(
        frontLeft: MutableStateFlow<MbCanSeatModeState>,
        frontRight: MutableStateFlow<MbCanSeatModeState>,
        rearLeft: MutableStateFlow<MbCanSeatModeState>,
        rearRight: MutableStateFlow<MbCanSeatModeState>,
    ): MutableStateFlow<MbCanSeatModeState> = when (this) {
        FrontLeft -> frontLeft
        FrontRight -> frontRight
        RearLeft -> rearLeft
        RearRight -> rearRight
    }
}

/**
 * Deterministic mbCAN widget state: confirmation for [Unknown]/[Unavailable], burst on
 * transition from non-problem to problem, single place for push + poll application.
 */
internal class MbCanSignalStateEngine(
    private val steeringFlow: MutableStateFlow<MbCanBinaryState>,
    private val wiperMaintenanceFlow: MutableStateFlow<MbCanBinaryState>,
    private val parkingRadarFlow: MutableStateFlow<MbCanBinaryState>,
    private val windshieldHeatFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacDefrosterFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAirRecirculationFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAcPowerFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAutoStateFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacSyncFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacDefrosterFrontFlow: MutableStateFlow<MbCanBinaryState>,
    private val wirelessChargingFlow: MutableStateFlow<MbCanBinaryState>,
    private val tjaIcaFlow: MutableStateFlow<MbCanBinaryState>,
    private val volumeSpeedFlow: MutableStateFlow<MbCanBinaryState>,
    private val frontLeftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val frontRightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rearLeftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rearRightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val requiredConsecutiveProblems: Int = 3,
    private val onBurstRequest: suspend (MbCanSignal) -> Unit = { MbCanJobManager.requestBurst(it) },
) {
    private class ProblemStreak {
        var unknown: Int = 0
        var unavailable: Int = 0
    }

    private val stateLock = Any()
    private val binaryStreaks = java.util.EnumMap<MbCanSignal, ProblemStreak>(MbCanSignal::class.java)
    private val seatStreaks = java.util.EnumMap<MbCanSeatSlot, ProblemStreak>(MbCanSeatSlot::class.java)

    private fun MbCanBinaryState.isProblemState(): Boolean =
        this is MbCanBinaryState.Unknown || this is MbCanBinaryState.Unavailable

    private fun MbCanSeatModeState.isProblemState(): Boolean =
        this is MbCanSeatModeState.Unknown || this is MbCanSeatModeState.Unavailable

    private suspend fun applyBinaryCandidate(
        flow: MutableStateFlow<MbCanBinaryState>,
        signal: MbCanSignal,
        decoded: MbCanBinaryState,
    ) {
        val shouldBurst = synchronized(stateLock) {
            val streak = binaryStreaks.getOrPut(signal) { ProblemStreak() }
            val published = flow.value
            val burst = decoded.isProblemState() && !published.isProblemState()
            when (decoded) {
                is MbCanBinaryState.Unknown -> {
                    streak.unknown += 1
                    streak.unavailable = 0
                    if (streak.unknown >= requiredConsecutiveProblems) {
                        flow.value = MbCanBinaryState.Unknown
                    }
                }
                is MbCanBinaryState.Unavailable -> {
                    streak.unavailable += 1
                    streak.unknown = 0
                    if (streak.unavailable >= requiredConsecutiveProblems) {
                        flow.value = decoded
                    }
                }
                else -> {
                    streak.unknown = 0
                    streak.unavailable = 0
                    flow.value = decoded
                }
            }
            burst
        }
        if (shouldBurst) {
            onBurstRequest(signal)
        }
    }

    suspend fun applySteeringCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(steeringFlow, MbCanSignal.SteeringWheelHeat, decoded)

    suspend fun applyWiperMaintenanceCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(wiperMaintenanceFlow, MbCanSignal.WiperMaintenance, decoded)

    suspend fun applyParkingRadarCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(parkingRadarFlow, MbCanSignal.ParkingRadar, decoded)

    suspend fun applyWindshieldHeatCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(windshieldHeatFlow, MbCanSignal.FrontWindscreenHeat, decoded)

    suspend fun applyHvacDefrosterCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(hvacDefrosterFlow, MbCanSignal.HvacDefroster, decoded)

    suspend fun applyHvacAirRecirculationCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(hvacAirRecirculationFlow, MbCanSignal.HvacAirRecirculation, decoded)

    suspend fun applyHvacAcPowerCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(hvacAcPowerFlow, MbCanSignal.HvacAcPower, decoded)

    suspend fun applyHvacAutoStateCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(hvacAutoStateFlow, MbCanSignal.HvacAutoState, decoded)

    suspend fun applyHvacSyncCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(hvacSyncFlow, MbCanSignal.HvacSync, decoded)

    suspend fun applyHvacDefrosterFrontCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(hvacDefrosterFrontFlow, MbCanSignal.HvacDefrosterFront, decoded)

    suspend fun applyWirelessChargingCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(wirelessChargingFlow, MbCanSignal.WirelessChargingSwitch, decoded)

    suspend fun applyTjaIcaCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(tjaIcaFlow, MbCanSignal.TjaIca, decoded)

    suspend fun applyVolumeSpeedCandidate(decoded: MbCanBinaryState) =
        applyBinaryCandidate(volumeSpeedFlow, MbCanSignal.AudioVolumeSpeed, decoded)

    suspend fun applySeatCandidate(slot: MbCanSeatSlot, decoded: MbCanSeatModeState) {
        val flow = slot.stateFlow(
            frontLeftSeatFlow,
            frontRightSeatFlow,
            rearLeftSeatFlow,
            rearRightSeatFlow
        )
        val shouldBurst = synchronized(stateLock) {
            val streak = seatStreaks.getOrPut(slot) { ProblemStreak() }
            val published = flow.value
            val burst = decoded.isProblemState() && !published.isProblemState()
            when (decoded) {
                is MbCanSeatModeState.Unknown -> {
                    streak.unknown += 1
                    streak.unavailable = 0
                    if (streak.unknown >= requiredConsecutiveProblems) {
                        flow.value = MbCanSeatModeState.Unknown
                    }
                }
                is MbCanSeatModeState.Unavailable -> {
                    streak.unavailable += 1
                    streak.unknown = 0
                    if (streak.unavailable >= requiredConsecutiveProblems) {
                        flow.value = decoded
                    }
                }
                else -> {
                    streak.unknown = 0
                    streak.unavailable = 0
                    flow.value = decoded
                }
            }
            burst
        }
        if (shouldBurst) {
            onBurstRequest(slot.signal)
        }
    }

    companion object {
        fun decodeSteeringWheelHeatRaw(raw: Int): MbCanBinaryState = when (raw) {
            2 -> MbCanBinaryState.On
            1 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT] — same on/off encoding as steering heat. */
        fun decodeFrontWindscreenHeatRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER] — rear window + mirrors; same 1/2 as steering if used as binary. */
        fun decodeHvacDefrosterRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** Same raw values as [MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON]/[_OFF] and [MbCanCommandRegistry] toggle. */
        fun decodeHvacAirRecirculationRaw(raw: Int): MbCanBinaryState = when (raw) {
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON -> MbCanBinaryState.On
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** Same raw values as steering heat: 1 off, 2 on (mbCAN / write side). */
        fun decodeHvacAcPowerRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** Same raw values as steering heat: 1 off, 2 on (mbCAN / write side). */
        fun decodeHvacAutoStateRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYNCSWTICH_REQ] — dual/sync mode; 1 off, 2 on. */
        fun decodeHvacSyncRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** mbCAN [HVAC_FAN_DIRECTION]: 4/5 = front defrost blow on. */
        fun decodeHvacFrontDefrostMbCanRaw(raw: Int): MbCanBinaryState = when (raw) {
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> MbCanBinaryState.On
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** VHAL [FrontBlowModeSts]: mode 4 = front defrost blow on. */
        fun decodeHvacFrontDefrostVhalRaw(raw: Int): MbCanBinaryState = when (raw) {
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_DEFROST -> MbCanBinaryState.On
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_FACE,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_DEFROST_FOOT,
            1, 2 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** [MBFrontDefrostingView.getValue] toggle target for mbCAN. Always target defrost-only (4) when turning on. */
        fun resolveHvacFrontDefrostMbCanToggleTarget(currentRaw: Int): Int {
            val active = decodeHvacFrontDefrostMbCanRaw(currentRaw) is MbCanBinaryState.On
            return if (active) {
                if (currentRaw == MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT) {
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT
                } else {
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE
                }
            } else {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST
            }
        }

        /** Toggle target for VHAL [ModeAdjust_Req]: 4 on, 0 off (face). */
        fun resolveHvacFrontDefrostVhalToggleTarget(currentRaw: Int): Int =
            if (decodeHvacFrontDefrostVhalRaw(currentRaw) is MbCanBinaryState.On) {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_FACE
            } else {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_DEFROST
            }

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_CHG_WIRELESS_SWITCH] — 1 off, 2 on. */
        fun decodeWirelessChargingRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** [MbCanKnownVehiclePropertyId.TJA_ICA] — 1 off, 2 on. */
        fun decodeTjaIcaRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** Front seat heat + ventilation raw values (1 off, 2–4 heat, 5–7 vent). */
        fun decodeSeatModeRaw(raw: Int): MbCanSeatModeState = when (raw) {
            1 -> MbCanSeatModeState.Off
            2 -> MbCanSeatModeState.Heat(1)
            3 -> MbCanSeatModeState.Heat(2)
            4 -> MbCanSeatModeState.Heat(3)
            5 -> MbCanSeatModeState.Vent(1)
            6 -> MbCanSeatModeState.Vent(2)
            7 -> MbCanSeatModeState.Vent(3)
            else -> MbCanSeatModeState.Unknown
        }

        /** Rear seat heating only ([MBVehicleProperty] 318 / 319): 1 off, 2–4 heat levels. */
        fun decodeRearSeatHeatRaw(raw: Int): MbCanSeatModeState = when (raw) {
            1 -> MbCanSeatModeState.Off
            2 -> MbCanSeatModeState.Heat(1)
            3 -> MbCanSeatModeState.Heat(2)
            4 -> MbCanSeatModeState.Heat(3)
            else -> MbCanSeatModeState.Unknown
        }

        /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED] — 1 off, 2..4 on. */
        fun decodeVolumeSpeedRaw(raw: Int): MbCanBinaryState = when (raw) {
            1 -> MbCanBinaryState.Off
            2, 3, 4 -> MbCanBinaryState.On
            else -> MbCanBinaryState.Unknown
        }
    }
}
