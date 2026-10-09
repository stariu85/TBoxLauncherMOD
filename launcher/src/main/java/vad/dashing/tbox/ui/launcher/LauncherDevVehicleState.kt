package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vad.dashing.tbox.mbcan.VehicleBodyState
import kotlin.math.roundToInt

/** Debug overrides for launcher vehicle animation when not in the car. */
object LauncherDevVehicleState {
    var simulateEnabled by mutableStateOf(false)
    var showAllIndicators by mutableStateOf(false)
    /**
     * Preview steer animation from the physical wheel while stationary (real CAN steer, speed visual = 0).
     */
    var motionPreviewEnabled by mutableStateOf(false)
    var speedKmh by mutableFloatStateOf(0f)
    var steerAngleDeg by mutableFloatStateOf(0f)
    var doorFlOpen by mutableStateOf(false)
    var doorFrOpen by mutableStateOf(false)
    var doorRlOpen by mutableStateOf(false)
    var doorRrOpen by mutableStateOf(false)
    var tailgateOpen by mutableStateOf(false)
    var seatBeltDriver by mutableStateOf(false)
    var seatBeltPassenger by mutableStateOf(false)
    var tirePressureOverride by mutableStateOf<Map<LauncherWheelCorner, Float>>(emptyMap())
    var batteryVoltageOverride by mutableStateOf<Float?>(null)
    /** 'P'/'R'/'N'/'D' override; null = real gearbox state. */
    var gearSlotOverride by mutableStateOf<Char?>(null)

    // --- ADAS simulation (cruise / lanes / BSD / front object / parking sensors) ---
    var adasCruiseActive by mutableStateOf(false)
    var adasLanesActive by mutableStateOf(false)
    var adasLkaActive by mutableStateOf(false)
    var adasLdwWarning by mutableStateOf(false)
    var adasTimeGapLevel by mutableIntStateOf(2)
    var adasTimeGapFlashUntilMs by mutableLongStateOf(0L)
    var adasBsdLeft by mutableStateOf(LauncherRearThreatLevel.Off)
    var adasBsdRight by mutableStateOf(LauncherRearThreatLevel.Off)
    /** Lead vehicle distance in metres; 0 = no object. */
    var adasFrontObjectM by mutableFloatStateOf(0f)
    var adasFrontObjectType by mutableStateOf(LauncherAdasFrontObjectType.Car)
    /** Per-channel parking distances (cm); absent = sensor silent. */
    val pdcChannels = androidx.compose.runtime.mutableStateMapOf<LauncherPdcChannel, Float>()
    var lowBeam by mutableStateOf(false)
    var highBeam by mutableStateOf(false)
    var turnLeft by mutableStateOf(false)
    var turnRight by mutableStateOf(false)

    fun cycleBsdLeft() {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasBsdLeft = nextThreatLevel(adasBsdLeft)
    }

    fun cycleBsdRight() {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasBsdRight = nextThreatLevel(adasBsdRight)
    }

    fun toggleAdasCruise() {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasCruiseActive = !adasCruiseActive
    }

    fun triggerTimeGapFlash(ms: Long = 3000L) {
        simulateEnabled = true
        adasTimeGapFlashUntilMs = android.os.SystemClock.uptimeMillis() + ms
    }

    fun clearTimeGapFlash() {
        adasTimeGapFlashUntilMs = 0L
    }

    fun toggleAdasLanes() {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasLanesActive = !adasLanesActive
        adasLkaActive = adasLanesActive
        if (!adasLanesActive) {
            adasLdwWarning = false
        }
    }

    fun toggleAdasLka() {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasLkaActive = !adasLkaActive
        if (adasLkaActive) {
            adasLanesActive = true
        }
    }

    fun toggleAdasLdwWarning() {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasLdwWarning = !adasLdwWarning
        if (adasLdwWarning) {
            adasLanesActive = true
        }
    }

    fun setAdasFrontObject(metres: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasFrontObjectM = metres.coerceIn(0f, 120f)
    }

    fun selectAdasFrontObjectType(type: LauncherAdasFrontObjectType) {
        simulateEnabled = true
        motionPreviewEnabled = false
        adasCruiseActive = true
        if (gearSlotOverride != 'D') gearSlotOverride = 'D'
        if (type == LauncherAdasFrontObjectType.None) {
            adasFrontObjectM = 0f
            return
        }
        adasFrontObjectType = type
        if (adasFrontObjectM < 1f) adasFrontObjectM = 35f
    }

    /**
     * Slider semantics: 0 cm = obstacle touching (alarm), 150 cm (default) = free space,
     * channel silent. Anything in between is a live obstacle distance.
     * Note: motion preview is intentionally NOT disabled — parking sensors are exactly
     * what the user wants to see while the drive camera is active.
     */
    fun setPdcChannel(channel: LauncherPdcChannel, cm: Float) {
        simulateEnabled = true
        val value = cm.coerceIn(0f, 150f)
        if (value >= 149.5f) pdcChannels.remove(channel) else pdcChannels[channel] = value.coerceAtLeast(1f)
    }

    fun pdcChannelValue(channel: LauncherPdcChannel): Float = pdcChannels[channel] ?: 150f

    /** Front pair shortcut: the two diagonal corner sensors. */
    fun setPdcFrontGroup(cm: Float) {
        fun scaled(f: Float) = if (cm >= 149.5f) 150f else (cm * f).coerceIn(1f, 149f)
        setPdcChannel(LauncherPdcChannel.FrontSideLeft, cm)
        setPdcChannel(LauncherPdcChannel.FrontSideRight, scaled(0.8f))
    }

    /** Rear shortcut: 4 bumper sensors with a natural spread. */
    fun setPdcRearGroup(cm: Float) {
        fun scaled(f: Float) = if (cm >= 149.5f) 150f else (cm * f).coerceIn(1f, 149f)
        setPdcChannel(LauncherPdcChannel.RearLeft, cm)
        setPdcChannel(LauncherPdcChannel.RearMidLeft, scaled(0.85f))
        setPdcChannel(LauncherPdcChannel.RearMidRight, scaled(0.7f))
        setPdcChannel(LauncherPdcChannel.RearRight, scaled(0.55f))
    }

    fun toggleLowBeam() {
        simulateEnabled = true
        motionPreviewEnabled = false
        lowBeam = !lowBeam
        if (!lowBeam) highBeam = false
    }

    fun toggleHighBeam() {
        simulateEnabled = true
        motionPreviewEnabled = false
        highBeam = !highBeam
        if (highBeam) lowBeam = true
    }

    fun toggleTurnLeft() {
        simulateEnabled = true
        motionPreviewEnabled = false
        turnLeft = !turnLeft
    }

    fun toggleTurnRight() {
        simulateEnabled = true
        motionPreviewEnabled = false
        turnRight = !turnRight
    }

    fun pdcFrontGroupValue(): Float = pdcChannels[LauncherPdcChannel.FrontSideLeft] ?: 150f

    fun pdcRearGroupValue(): Float = pdcChannels[LauncherPdcChannel.RearLeft] ?: 150f

    private fun nextThreatLevel(level: LauncherRearThreatLevel): LauncherRearThreatLevel = when (level) {
        LauncherRearThreatLevel.Off -> LauncherRearThreatLevel.Caution
        LauncherRearThreatLevel.Caution -> LauncherRearThreatLevel.Alert
        LauncherRearThreatLevel.Alert -> LauncherRearThreatLevel.Off
    }

    fun toggleShowAllIndicators() {
        applyShowAllIndicators(!showAllIndicators)
    }

    fun applyShowAllIndicators(enabled: Boolean) {
        showAllIndicators = enabled
        if (enabled) {
            simulateEnabled = true
            motionPreviewEnabled = false
            doorFlOpen = true
            doorFrOpen = true
            doorRlOpen = true
            doorRrOpen = true
            tailgateOpen = true
            seatBeltDriver = true
            seatBeltPassenger = true
            lowBeam = true
            highBeam = true
            turnLeft = true
            turnRight = true
            adasCruiseActive = true
            adasLanesActive = true
            adasBsdLeft = LauncherRearThreatLevel.Alert
            adasBsdRight = LauncherRearThreatLevel.Alert
            adasFrontObjectM = 35f
            adasFrontObjectType = LauncherAdasFrontObjectType.Car
            setPdcFrontGroup(20f)
            setPdcRearGroup(20f)
            tirePressureOverride = mapOf(
                LauncherWheelCorner.FL to 1.5f,
                LauncherWheelCorner.FR to 1.5f,
                LauncherWheelCorner.RL to 1.5f,
                LauncherWheelCorner.RR to 1.5f,
            )
        } else {
            lowBeam = false
            highBeam = false
            turnLeft = false
            turnRight = false
            doorFlOpen = false
            doorFrOpen = false
            doorRlOpen = false
            doorRrOpen = false
            tailgateOpen = false
            seatBeltDriver = false
            seatBeltPassenger = false
            adasCruiseActive = false
            adasLanesActive = false
            adasLdwWarning = false
            adasBsdLeft = LauncherRearThreatLevel.Off
            adasBsdRight = LauncherRearThreatLevel.Off
            adasFrontObjectM = 0f
            pdcChannels.clear()
            tirePressureOverride = emptyMap()
        }
        LauncherVehicleAlertsRepository.refresh()
    }

    /** Simulated ADAS state, or null when no ADAS sim values are set (live data shows). */
    fun adasStateOrNull(): LauncherAdasState? {
        if (!simulateEnabled) return null
        if (showAllIndicators) {
            return LauncherAdasState(
                accMode = LauncherAdasAccMode.ActiveBlue,
                accSetSpeedKmh = 90,
                accActive = true,
                accStandby = false,
                accOverride = true,
                accTakeOver = true,
                timeGapLevel = 2,
                frontObject = LauncherAdasFrontObject(
                    valid = true,
                    type = LauncherAdasFrontObjectType.Car,
                    objectDxM = 35,
                    targetDxM = 35,
                ),
                fcwActive = true,
                distanceWarning = true,
                aebHint = true,
                leftLane = LauncherAdasLaneVisualization.Warning,
                rightLane = LauncherAdasLaneVisualization.Warning,
                lkaStatusCode = 1,
                adasTakeOver = true,
                speedLimitKmh = 90,
                speedLimitWarning = true,
                tsr = LauncherAdasTsrSign(valid = true, speedLimitKmh = 90),
                rearThreats = LauncherRearThreats(
                    bsdLeft = LauncherRearThreatLevel.Alert,
                    bsdRight = LauncherRearThreatLevel.Alert,
                    rctaLeft = LauncherRearThreatLevel.Alert,
                    rctaRight = LauncherRearThreatLevel.Alert,
                    dowLeft = LauncherRearThreatLevel.Alert,
                    dowRight = LauncherRearThreatLevel.Alert,
                    rcw = LauncherRearThreatLevel.Alert,
                ),
                pdc = LauncherPdcZones(
                    frontSideLeftCm = 30,
                    frontLeftCm = 30,
                    frontMidLeftCm = 30,
                    frontMidRightCm = 30,
                    frontRightCm = 30,
                    frontSideRightCm = 30,
                    rearSideLeftCm = 30,
                    rearLeftCm = 30,
                    rearMidLeftCm = 30,
                    rearMidRightCm = 30,
                    rearRightCm = 30,
                    rearSideRightCm = 30,
                ),
                hma = LauncherAdasAssistIcon.Active,
                tja = LauncherAdasAssistIcon.Active,
                srrSystem = LauncherSrrSystemState.Fault,
                hasAnyAlert = true,
                hasAnyAssist = true,
            )
        }
        val frontM = adasFrontObjectM.roundToInt()
        val anySet = adasCruiseActive || adasLanesActive || adasLdwWarning ||
            adasBsdLeft != LauncherRearThreatLevel.Off ||
            adasBsdRight != LauncherRearThreatLevel.Off ||
            frontM > 0 || pdcChannels.isNotEmpty()
        if (!anySet) return null

        val threats = LauncherRearThreats(
            bsdLeft = adasBsdLeft,
            bsdRight = adasBsdRight,
        )
        fun pdcCm(channel: LauncherPdcChannel): Int =
            (pdcChannels[channel] ?: 0f).roundToInt()
        val pdc = LauncherPdcZones(
            frontSideLeftCm = pdcCm(LauncherPdcChannel.FrontSideLeft),
            frontLeftCm = pdcCm(LauncherPdcChannel.FrontLeft),
            frontMidLeftCm = pdcCm(LauncherPdcChannel.FrontMidLeft),
            frontMidRightCm = pdcCm(LauncherPdcChannel.FrontMidRight),
            frontRightCm = pdcCm(LauncherPdcChannel.FrontRight),
            frontSideRightCm = pdcCm(LauncherPdcChannel.FrontSideRight),
            rearSideLeftCm = pdcCm(LauncherPdcChannel.RearSideLeft),
            rearLeftCm = pdcCm(LauncherPdcChannel.RearLeft),
            rearMidLeftCm = pdcCm(LauncherPdcChannel.RearMidLeft),
            rearMidRightCm = pdcCm(LauncherPdcChannel.RearMidRight),
            rearRightCm = pdcCm(LauncherPdcChannel.RearRight),
            rearSideRightCm = pdcCm(LauncherPdcChannel.RearSideRight),
        )
        val laneVis = when {
            adasLdwWarning -> LauncherAdasLaneVisualization.Warning
            adasLanesActive -> LauncherAdasLaneVisualization.Tracking
            else -> LauncherAdasLaneVisualization.Hidden
        }
        val setSpeed = if (adasCruiseActive) {
            speedKmh.roundToInt().takeIf { it > 0 } ?: 90
        } else {
            null
        }
        return LauncherAdasState(
            accMode = if (adasCruiseActive) LauncherAdasAccMode.ActiveBlue else LauncherAdasAccMode.Off,
            accSetSpeedKmh = setSpeed,
            accActive = adasCruiseActive,
            timeGapLevel = adasTimeGapLevel,
            timeGapFlashUntilMs = adasTimeGapFlashUntilMs,
            frontObject = LauncherAdasFrontObject(
                valid = frontM > 0,
                type = if (frontM > 0) adasFrontObjectType else LauncherAdasFrontObjectType.None,
                objectDxM = frontM.takeIf { it > 0 },
                targetDxM = frontM.takeIf { it > 0 },
            ),
            leftLane = laneVis,
            rightLane = laneVis,
            lkaStatusCode = when {
                adasLkaActive || (adasLanesActive && adasCruiseActive) -> 2
                adasLanesActive || adasLdwWarning -> 1
                else -> 0
            },
            rearThreats = threats,
            pdc = pdc,
            srrSystem = if (threats.hasAny) LauncherSrrSystemState.Active else LauncherSrrSystemState.Hidden,
            hasAnyAlert = threats.hasAny || adasLdwWarning,
            hasAnyAssist = adasCruiseActive || adasLanesActive || adasLkaActive || adasLdwWarning || frontM > 0 || pdc.hasAny,
        )
    }

    fun bodyState(): VehicleBodyState = VehicleBodyState(
        doorFlOpen = doorFlOpen,
        doorFrOpen = doorFrOpen,
        doorRlOpen = doorRlOpen,
        doorRrOpen = doorRrOpen,
        tailgateOpen = tailgateOpen,
    )

    fun toggleSimulate() {
        simulateEnabled = !simulateEnabled
        if (simulateEnabled) motionPreviewEnabled = false
    }

    fun toggleMotionPreview() {
        motionPreviewEnabled = !motionPreviewEnabled
        if (motionPreviewEnabled) simulateEnabled = false
    }

    fun bumpSpeed(delta: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        speedKmh = (speedKmh + delta).coerceIn(0f, 180f)
    }

    fun bumpSteer(delta: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        steerAngleDeg = (steerAngleDeg + delta).coerceIn(-540f, 540f)
    }

    fun toggleDoorFl() {
        simulateEnabled = true
        doorFlOpen = !doorFlOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleDoorFr() {
        simulateEnabled = true
        doorFrOpen = !doorFrOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleTailgate() {
        simulateEnabled = true
        tailgateOpen = !tailgateOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleSeatBeltDriver() {
        simulateEnabled = true
        seatBeltDriver = !seatBeltDriver
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleSeatBeltPassenger() {
        simulateEnabled = true
        seatBeltPassenger = !seatBeltPassenger
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleDoorRl() {
        simulateEnabled = true
        doorRlOpen = !doorRlOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun toggleDoorRr() {
        simulateEnabled = true
        doorRrOpen = !doorRrOpen
        LauncherVehicleAlertsRepository.refresh()
    }

    fun setSpeed(value: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        speedKmh = value.coerceIn(0f, 220f)
    }

    fun setSteer(value: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        steerAngleDeg = value.coerceIn(-540f, 540f)
    }

    fun setTirePressure(corner: LauncherWheelCorner, bar: Float?) {
        simulateEnabled = true
        tirePressureOverride = tirePressureOverride.toMutableMap().apply {
            if (bar == null) remove(corner) else put(corner, bar.coerceIn(0f, 4.5f))
        }
        LauncherVehicleAlertsRepository.refresh()
    }

    fun setBatteryVoltage(value: Float?) {
        simulateEnabled = true
        batteryVoltageOverride = value?.coerceIn(9f, 16f)
    }

    fun setGearSlot(slot: Char?) {
        simulateEnabled = true
        motionPreviewEnabled = false
        gearSlotOverride = slot?.takeIf { it in SIM_GEAR_SLOTS }
    }

    fun tireStateOrNull(): LauncherTireState? {
        if (tirePressureOverride.isEmpty()) return null
        fun wheel(c: LauncherWheelCorner): LauncherTireWheelState {
            val bar = tirePressureOverride[c]
            return LauncherTireWheelState(
                pressureBar = bar,
                temperatureC = null,
                warningSts = if (bar != null && bar < LauncherTireWheelState.LOW_PRESSURE_BAR) 1 else 0,
                calibrating = false,
                available = bar != null,
            )
        }
        return LauncherTireState(
            fl = wheel(LauncherWheelCorner.FL),
            fr = wheel(LauncherWheelCorner.FR),
            rl = wheel(LauncherWheelCorner.RL),
            rr = wheel(LauncherWheelCorner.RR),
        )
    }

    fun resetSimulation() {
        showAllIndicators = false
        simulateEnabled = false
        motionPreviewEnabled = false
        speedKmh = 0f
        steerAngleDeg = 0f
        doorFlOpen = false
        doorFrOpen = false
        doorRlOpen = false
        doorRrOpen = false
        tailgateOpen = false
        seatBeltDriver = false
        seatBeltPassenger = false
        tirePressureOverride = emptyMap()
        batteryVoltageOverride = null
        gearSlotOverride = null
        adasCruiseActive = false
        adasLanesActive = false
        adasLkaActive = false
        adasLdwWarning = false
        adasBsdLeft = LauncherRearThreatLevel.Off
        adasBsdRight = LauncherRearThreatLevel.Off
        adasFrontObjectM = 0f
        adasFrontObjectType = LauncherAdasFrontObjectType.Car
        pdcChannels.clear()
        lowBeam = false
        highBeam = false
        turnLeft = false
        turnRight = false
        LauncherVehicleAlertsRepository.refresh()
    }
}

/** Selectable gearbox slots for the simulation tab. */
val SIM_GEAR_SLOTS: List<Char> = listOf('P', 'R', 'N', 'D')

/** Front-object types that have dedicated ADAS lead icons. */
val SIM_FRONT_OBJECT_TYPES: List<LauncherAdasFrontObjectType> = listOf(
    LauncherAdasFrontObjectType.Car,
    LauncherAdasFrontObjectType.Truck,
    LauncherAdasFrontObjectType.Motorcycle,
    LauncherAdasFrontObjectType.Pedestrian,
)
