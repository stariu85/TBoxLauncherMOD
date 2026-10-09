package vad.dashing.tbox.ui.launcher

import vad.dashing.tbox.R

/**
 * Decoders for [com.mengbo.mbCan.entity.MBCanVehicleFrmDectInfo] and LKA status,
 * ported from stock `com.mengbo.adascard` / `SignalManager` thresholds.
 */
internal fun byteToUnsigned(raw: Byte): Int = raw.toInt() and 0xFF

/** Longitudinal distance in metres (byte 0..255, negative bytes shown as 256+n in stock ACC UI). */
internal fun decodeFrmDistanceMetres(raw: Byte): Int? {
    val u = byteToUnsigned(raw)
    if (u == 0) return null
    return if (raw < 0) u else u
}

enum class LauncherAdasAccMode {
    Off,
    Standby,
    ActiveDark,
    ActiveBlue,
    Override,
    Unknown,
}

enum class LauncherAdasFrontObjectType(val code: Int) {
    None(0),
    Car(1),
    Truck(2),
    Motorcycle(3),
    Pedestrian(4),
    Bicycle(5),
    Bus(6),
    Unknown(7),
    ;

    val labelRes: Int
        get() = when (this) {
            None, Unknown -> R.string.launcher_adas_obj_unknown
            Car -> R.string.launcher_adas_obj_car
            Truck -> R.string.launcher_adas_obj_truck
            Motorcycle -> R.string.launcher_adas_obj_moto
            Pedestrian -> R.string.launcher_adas_obj_pedestrian
            Bicycle -> R.string.launcher_adas_obj_bicycle
            Bus -> R.string.launcher_adas_obj_bus
        }

    companion object {
        fun fromCode(code: Int): LauncherAdasFrontObjectType =
            entries.firstOrNull { it.code == code } ?: Unknown
    }
}

enum class LauncherAdasLaneVisualization(val code: Int) {
    Hidden(0),
    Tracking(1),
    Intervention(2),
    Warning(3),
    ;

    companion object {
        fun fromCode(code: Int): LauncherAdasLaneVisualization =
            entries.firstOrNull { it.code == code } ?: Hidden
    }
}

data class LauncherAdasFrontObject(
    val valid: Boolean,
    val type: LauncherAdasFrontObjectType,
    /** Smoothed forward position used by stock ADAS card ([MBCanVehicleFrmDectInfo.getFRM_3_Obiect_Dx]). */
    val objectDxM: Int?,
    /** Radar target distance ([MBCanVehicleFrmDectInfo.getFRM_3_DxTarObj]). */
    val targetDxM: Int?,
) {
    val displayDistanceM: Int? = objectDxM ?: targetDxM
}

/**
 * Camera TSR (Traffic Sign Recognition) from [MBCanTrafficSignRecognition],
 * independent of FCM SLA instrument limit.
 */
data class LauncherAdasTsrSign(
    val valid: Boolean = false,
    val speedLimitKmh: Int? = null,
    val signClass: Int = 0,
    val confidence: Float = 0f,
    /** Longitudinal offset from vehicle when OEM provides coords (metres-ish). */
    val posX: Float? = null,
    val posY: Float? = null,
)

/** OEM adascard: 1 = pre/caution (yellow), 2 = hard warning (red). */
enum class LauncherRearThreatLevel {
    Off,
    Caution,
    Alert,
}

/** HMA / TJA icon modes from FCM (same mapping as AIService MainActivity). */
enum class LauncherAdasAssistIcon {
    Hidden,
    Dark,
    Active,
    Warning,
}

internal fun decodeAssistIcon(raw: Byte): LauncherAdasAssistIcon = when (byteToUnsigned(raw)) {
    1 -> LauncherAdasAssistIcon.Dark
    2 -> LauncherAdasAssistIcon.Active
    3 -> LauncherAdasAssistIcon.Warning
    else -> LauncherAdasAssistIcon.Hidden
}

/** SRR system telltale: 1 = active, 2/3 = fault (AIService handleSystemState). */
enum class LauncherSrrSystemState {
    Hidden,
    Active,
    Fault,
}

internal fun decodeSrrSystemState(raw: Byte): LauncherSrrSystemState = when (byteToUnsigned(raw)) {
    1 -> LauncherSrrSystemState.Active
    2, 3 -> LauncherSrrSystemState.Fault
    else -> LauncherSrrSystemState.Hidden
}

/**
 * Rear / side threats from SRR: BSD, RCTA (+ RCW), DOW — same bytes as OEM AIService.
 */
data class LauncherRearThreats(
    val bsdLeft: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
    val bsdRight: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
    val rctaLeft: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
    val rctaRight: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
    val dowLeft: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
    val dowRight: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
    val rcw: LauncherRearThreatLevel = LauncherRearThreatLevel.Off,
) {
    val hasAny: Boolean
        get() = bsdLeft != LauncherRearThreatLevel.Off ||
            bsdRight != LauncherRearThreatLevel.Off ||
            rctaLeft != LauncherRearThreatLevel.Off ||
            rctaRight != LauncherRearThreatLevel.Off ||
            dowLeft != LauncherRearThreatLevel.Off ||
            dowRight != LauncherRearThreatLevel.Off ||
            rcw != LauncherRearThreatLevel.Off

    val left: LauncherRearThreatLevel
        get() = maxOf(bsdLeft, rctaLeft, dowLeft)

    val right: LauncherRearThreatLevel
        get() = maxOf(bsdRight, rctaRight, dowRight)

    val hasBsd: Boolean
        get() = bsdLeft != LauncherRearThreatLevel.Off ||
            bsdRight != LauncherRearThreatLevel.Off
}

data class LauncherAdasState(
    val accMode: LauncherAdasAccMode = LauncherAdasAccMode.Off,
    val accSetSpeedKmh: Int? = null,
    val accActive: Boolean = false,
    val accStandby: Boolean = false,
    val accOverride: Boolean = false,
    val accTakeOver: Boolean = false,
    val timeGapLevel: Int? = null,
    /** [android.os.SystemClock.uptimeMillis] deadline for the transient time-gap flash. */
    val timeGapFlashUntilMs: Long = 0L,
    val frontObject: LauncherAdasFrontObject = LauncherAdasFrontObject(
        valid = false,
        type = LauncherAdasFrontObjectType.None,
        objectDxM = null,
        targetDxM = null,
    ),
    val fcwActive: Boolean = false,
    val distanceWarning: Boolean = false,
    val aebHint: Boolean = false,
    val leftLane: LauncherAdasLaneVisualization = LauncherAdasLaneVisualization.Hidden,
    val rightLane: LauncherAdasLaneVisualization = LauncherAdasLaneVisualization.Hidden,
    /** Raw FCM LKA/LDW status (non-zero ≈ assist engaged). */
    val lkaStatusCode: Int = 0,
    val adasTakeOver: Boolean = false,
    /** SLA recognized speed limit (km/h), null if none. */
    val speedLimitKmh: Int? = null,
    val speedLimitWarning: Boolean = false,
    /** Camera TSR sign (may duplicate SLA — useful for comparison). */
    val tsr: LauncherAdasTsrSign = LauncherAdasTsrSign(),
    /** Blind-spot / rear-cross / door-open / rear-collision from SRR. */
    val rearThreats: LauncherRearThreats = LauncherRearThreats(),
    /** Ultrasonic parking sensors (12 channels collapsed to 6 display zones). */
    val pdc: LauncherPdcZones = LauncherPdcZones(),
    /** Adaptive high beam (HMA) — AIService beamLight. */
    val hma: LauncherAdasAssistIcon = LauncherAdasAssistIcon.Hidden,
    /** Traffic jam / ICA assist — AIService tjaIcaIcon. */
    val tja: LauncherAdasAssistIcon = LauncherAdasAssistIcon.Hidden,
    /** SRR blind-spot system status from BSD alarm. */
    val srrSystem: LauncherSrrSystemState = LauncherSrrSystemState.Hidden,
    val hasAnyAlert: Boolean = false,
    val hasAnyAssist: Boolean = false,
) {
    val laneDepartureLeft: Boolean get() = leftLane == LauncherAdasLaneVisualization.Warning
    val laneDepartureRight: Boolean get() = rightLane == LauncherAdasLaneVisualization.Warning
    val laneAssistEngaged: Boolean
        get() = lkaStatusCode != 0 ||
            leftLane != LauncherAdasLaneVisualization.Hidden ||
            rightLane != LauncherAdasLaneVisualization.Hidden ||
            accActive
}

internal fun decodeAccMode(raw: Byte): LauncherAdasAccMode = when (byteToUnsigned(raw)) {
    0 -> LauncherAdasAccMode.Off
    9 -> LauncherAdasAccMode.Standby
    1, 2, 6, 7 -> LauncherAdasAccMode.ActiveDark
    3, 4, 5 -> LauncherAdasAccMode.ActiveBlue
    else -> LauncherAdasAccMode.Unknown
}

/**
 * FCM SLA speed-limit code (Chinese OEM scale): `(raw - 1) * 5` km/h.
 * Examples: 9→40, 13→60, 23→110. Raw 0/1 = no recognized limit.
 */
internal fun decodeSlaSpeedLimitKmh(raw: Byte): Int? {
    val code = byteToUnsigned(raw)
    if (code <= 1) return null
    val kmh = (code - 1) * 5
    return kmh.takeIf { it in 5..160 }
}

/**
 * Camera TSR speed-limit value from [MBCanTrafficSignRecognition].
 * Prefer literal km/h when in range; fall back to SLA coding; convert mph when unit=1.
 */
internal fun decodeTsrSign(
    valueRaw: Byte,
    unitRaw: Byte,
    confidence: Float,
    signClass: Byte,
    posX: Float? = null,
    posY: Float? = null,
): LauncherAdasTsrSign {
    val value = byteToUnsigned(valueRaw)
    if (value == 0) return LauncherAdasTsrSign()
    val unit = byteToUnsigned(unitRaw)
    val literal = value.takeIf { it in 5..160 }
    val coded = decodeSlaSpeedLimitKmh(valueRaw)
    var kmh = literal ?: coded ?: return LauncherAdasTsrSign()
    if (unit == 1) {
        // mph → km/h
        kmh = (kmh * 1.60934f).toInt().coerceIn(5, 160)
    }
    // Drop very low-confidence noise when OEM reports confidence.
    if (confidence in 0.01f..0.15f) {
        return LauncherAdasTsrSign()
    }
    return LauncherAdasTsrSign(
        valid = true,
        speedLimitKmh = kmh,
        signClass = byteToUnsigned(signClass),
        confidence = confidence,
        posX = posX,
        posY = posY,
    )
}

internal fun buildLauncherAdasState(
    accModeRaw: Byte,
    vSetDisRaw: Byte,
    objValidRaw: Byte,
    frontObjectTypeRaw: Byte,
    dxTarObjRaw: Byte,
    objectDxRaw: Byte,
    takeOverRaw: Byte,
    textInfoRaw: Byte,
    fcwPreWarningRaw: Byte,
    distanceWarningRaw: Byte,
    timeGapRaw: Byte,
    leftLaneRaw: Byte,
    rightLaneRaw: Byte,
    lkaStatusRaw: Byte,
    adasTakeOverRaw: Byte,
    slaSpdLimitRaw: Byte,
    slaSpdLimitWarningRaw: Byte,
    tsr: LauncherAdasTsrSign = LauncherAdasTsrSign(),
    rearThreats: LauncherRearThreats = LauncherRearThreats(),
    pdc: LauncherPdcZones = LauncherPdcZones(),
    hmaRaw: Byte = 0,
    tjaRaw: Byte = 0,
    srrSystemRaw: Byte = 0,
    timeGapFlashUntilMs: Long = 0L,
): LauncherAdasState {
    val accMode = decodeAccMode(accModeRaw)
    val accModeCode = byteToUnsigned(accModeRaw)
    val setSpeed = when {
        accMode == LauncherAdasAccMode.Off -> null
        vSetDisRaw < 0 -> byteToUnsigned(vSetDisRaw)
        byteToUnsigned(vSetDisRaw) <= 0 -> null
        else -> byteToUnsigned(vSetDisRaw)
    }
    val objValid = byteToUnsigned(objValidRaw) == 2
    val frontObject = LauncherAdasFrontObject(
        valid = objValid,
        type = LauncherAdasFrontObjectType.fromCode(byteToUnsigned(frontObjectTypeRaw)),
        objectDxM = decodeFrmDistanceMetres(objectDxRaw),
        targetDxM = decodeFrmDistanceMetres(dxTarObjRaw),
    )
    val fcw = byteToUnsigned(fcwPreWarningRaw) == 2 || byteToUnsigned(textInfoRaw) == 18
    val distWarn = byteToUnsigned(distanceWarningRaw) == 2 || byteToUnsigned(textInfoRaw) == 17
    val aeb = byteToUnsigned(textInfoRaw) == 11
    val takeOver = byteToUnsigned(takeOverRaw) == 2
    val override = accModeCode == 7
    val accActive = accMode == LauncherAdasAccMode.ActiveBlue || accMode == LauncherAdasAccMode.ActiveDark
    val accStandby = accMode == LauncherAdasAccMode.Standby
    val timeGap = byteToUnsigned(timeGapRaw).takeIf { it in 0..2 }
    var leftLane = LauncherAdasLaneVisualization.fromCode(byteToUnsigned(leftLaneRaw))
    var rightLane = LauncherAdasLaneVisualization.fromCode(byteToUnsigned(rightLaneRaw))
    val lkaStatus = byteToUnsigned(lkaStatusRaw)
    val adasTakeOver = byteToUnsigned(adasTakeOverRaw) == 2
    val slaLimit = decodeSlaSpeedLimitKmh(slaSpdLimitRaw)
    val slaWarn = byteToUnsigned(slaSpdLimitWarningRaw) == 2 ||
        (slaLimit != null && setSpeed != null && setSpeed > slaLimit)
    val hma = decodeAssistIcon(hmaRaw)
    val tja = decodeAssistIcon(tjaRaw)
    val tjaActive = tja == LauncherAdasAssistIcon.Active || tja == LauncherAdasAssistIcon.Dark
    val srrSystem = decodeSrrSystemState(srrSystemRaw)
    val effectiveLkaStatus = lkaStatus.takeIf { it != 0 } ?: if (tjaActive) 1 else 0

    // While NGP/TJA or LKA assist is engaged, keep lane guides active
    if ((tjaActive || effectiveLkaStatus != 0)) {
        if (leftLane == LauncherAdasLaneVisualization.Hidden) {
            leftLane = LauncherAdasLaneVisualization.Tracking
        }
        if (rightLane == LauncherAdasLaneVisualization.Hidden) {
            rightLane = LauncherAdasLaneVisualization.Tracking
        }
    }
    val hasAlert = fcw || distWarn || aeb || takeOver || override || adasTakeOver || slaWarn ||
        leftLane == LauncherAdasLaneVisualization.Warning ||
        rightLane == LauncherAdasLaneVisualization.Warning ||
        rearThreats.hasAny ||
        hma == LauncherAdasAssistIcon.Warning ||
        tja == LauncherAdasAssistIcon.Warning ||
        srrSystem == LauncherSrrSystemState.Fault
    val hasAssist = accActive || accStandby || frontObject.valid || effectiveLkaStatus != 0 ||
        leftLane != LauncherAdasLaneVisualization.Hidden ||
        rightLane != LauncherAdasLaneVisualization.Hidden ||
        slaLimit != null || tsr.valid || hasAlert ||
        hma != LauncherAdasAssistIcon.Hidden ||
        tja != LauncherAdasAssistIcon.Hidden ||
        srrSystem != LauncherSrrSystemState.Hidden
    return LauncherAdasState(
        accMode = accMode,
        accSetSpeedKmh = setSpeed,
        accActive = accActive,
        accStandby = accStandby,
        accOverride = override,
        accTakeOver = takeOver,
        timeGapLevel = timeGap,
        timeGapFlashUntilMs = timeGapFlashUntilMs,
        frontObject = frontObject,
        fcwActive = fcw,
        distanceWarning = distWarn,
        aebHint = aeb,
        leftLane = leftLane,
        rightLane = rightLane,
        lkaStatusCode = effectiveLkaStatus,
        adasTakeOver = adasTakeOver,
        speedLimitKmh = slaLimit,
        speedLimitWarning = slaWarn,
        tsr = tsr,
        rearThreats = rearThreats,
        pdc = pdc,
        hma = hma,
        tja = tja,
        srrSystem = srrSystem,
        hasAnyAlert = hasAlert,
        hasAnyAssist = hasAssist,
    )
}

/**
 * Decode SRR side status. BSD/DOW/RCW: 1=caution, 2=alert.
 * RCTA on OEM HU shows the approaching car only at hard warning (`== 2`).
 */
internal fun decodeRearThreatLevel(raw: Byte, hardOnly: Boolean = false): LauncherRearThreatLevel {
    val v = byteToUnsigned(raw)
    return when {
        hardOnly && v == 2 -> LauncherRearThreatLevel.Alert
        hardOnly -> LauncherRearThreatLevel.Off
        v == 2 -> LauncherRearThreatLevel.Alert
        v == 1 -> LauncherRearThreatLevel.Caution
        else -> LauncherRearThreatLevel.Off
    }
}

/**
 * Normalized depth on virtual road: 0 = horizon, 1 = bottom.
 *
 * Kept in the upper/mid band so the front target stays clear of the 3D car
 * (which sits over the near end of the road).
 */
internal fun distanceToRoadDepth(distanceM: Int): Float {
    val clamped = distanceM.coerceIn(1, 120).toFloat()
    val t = (clamped - 1f) / 119f // 0 = near (1 m), 1 = far (120 m)
    // Near (1 m) → 0.55 (~50dp in front of our 3D car's front bumper), far (120 m) → 0.12.
    return (0.55f - t * 0.43f).coerceIn(0.12f, 0.55f)
}

/** Parking-sensor urgency buckets derived from the raw centimetre distance. */
enum class LauncherPdcLevel {
    None,
    Far,
    Mid,
    Near,
}

/** Individual ultrasonic channels of MBCanRadarSensor (12 total). */
enum class LauncherPdcChannel {
    FrontSideLeft,
    FrontLeft,
    FrontMidLeft,
    FrontMidRight,
    FrontRight,
    FrontSideRight,
    RearSideLeft,
    RearLeft,
    RearMidLeft,
    RearMidRight,
    RearRight,
    RearSideRight,
}

/**
 * Ultrasonic park sensors ([com.mengbo.mbCan.entity.MBCanRadarSensor], 12 channels).
 * Each channel renders as its own sonar arc; channels reporting 0 stay hidden,
 * so different trims (4 rear + 2 diagonal front on Dashing) show only real sensors.
 * Distances are centimetres; 0 means "nothing detected".
 */
data class LauncherPdcZones(
    /** Front corner, facing sideways-diagonal (LHSF). */
    val frontSideLeftCm: Int = 0,
    val frontLeftCm: Int = 0,
    val frontMidLeftCm: Int = 0,
    val frontMidRightCm: Int = 0,
    val frontRightCm: Int = 0,
    /** Front corner, facing sideways-diagonal (RHSF). */
    val frontSideRightCm: Int = 0,
    /** Rear corner, facing sideways-diagonal (LHSR). */
    val rearSideLeftCm: Int = 0,
    val rearLeftCm: Int = 0,
    val rearMidLeftCm: Int = 0,
    val rearMidRightCm: Int = 0,
    val rearRightCm: Int = 0,
    /** Rear corner, facing sideways-diagonal (RHSR). */
    val rearSideRightCm: Int = 0,
) {
    private val all: List<Int>
        get() = listOf(
            frontSideLeftCm, frontLeftCm, frontMidLeftCm,
            frontMidRightCm, frontRightCm, frontSideRightCm,
            rearSideLeftCm, rearLeftCm, rearMidLeftCm,
            rearMidRightCm, rearRightCm, rearSideRightCm,
        )

    val hasAny: Boolean get() = all.any { it in 1..PDC_MAX_CM }

    val nearestCm: Int get() = all.filter { it in 1..PDC_MAX_CM }.minOrNull() ?: 0

    fun distanceCm(channel: LauncherPdcChannel): Int = when (channel) {
        LauncherPdcChannel.FrontSideLeft -> frontSideLeftCm
        LauncherPdcChannel.FrontLeft -> frontLeftCm
        LauncherPdcChannel.FrontMidLeft -> frontMidLeftCm
        LauncherPdcChannel.FrontMidRight -> frontMidRightCm
        LauncherPdcChannel.FrontRight -> frontRightCm
        LauncherPdcChannel.FrontSideRight -> frontSideRightCm
        LauncherPdcChannel.RearSideLeft -> rearSideLeftCm
        LauncherPdcChannel.RearLeft -> rearLeftCm
        LauncherPdcChannel.RearMidLeft -> rearMidLeftCm
        LauncherPdcChannel.RearMidRight -> rearMidRightCm
        LauncherPdcChannel.RearRight -> rearRightCm
        LauncherPdcChannel.RearSideRight -> rearSideRightCm
    }

    companion object {
        /** Above this the sensor reports free space. */
        const val PDC_MAX_CM = 150
        const val PDC_NEAR_CM = 35
        const val PDC_MID_CM = 75

            fun levelFor(distanceCm: Int): LauncherPdcLevel = when {
                // 150 cm and 0 both mean "free space" in practice (0 = no reading).
                distanceCm !in 1 until PDC_MAX_CM -> LauncherPdcLevel.None
                distanceCm <= PDC_NEAR_CM -> LauncherPdcLevel.Near
                distanceCm <= PDC_MID_CM -> LauncherPdcLevel.Mid
                else -> LauncherPdcLevel.Far
            }
    }
}
