package vad.dashing.tbox.mbcan

import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId.SYSTEM_REBOOT


/**
 * Catalog of mbCAN capabilities collected from vendor apps in the mbCAN workspace.
 * These lists are used as a reference/spec and do not imply automatic subscription.
 */
enum class MbCanConfidence {
    CONFIRMED_IN_APP_CALLS,
    DECLARED_IN_API
}

data class MbCanTelemetryParam(
    val domain: String,
    val name: String,
    val dataType: String,
    val confidence: MbCanConfidence
)

data class MbCanControlParam(
    val domain: String,
    val name: String,
    val property: String,
    val confidence: MbCanConfidence
)

sealed class MbCanCommandPolicy {
    data class ToggleBinary(
        val offValue: Int,
        val onValue: Int,
        val unknownFallbackValue: Int = onValue
    ) : MbCanCommandPolicy()

    /** Front windscreen blow (not heated glass) — [MBFrontDefrostingView] / [AcFragment] ib_front_defrosting. */
    data object ToggleHvacFrontDefrost : MbCanCommandPolicy()

    data class SetExact(
        val allowedValues: Set<Int>
    ) : MbCanCommandPolicy()
}

data class MbCanCommandSpec(
    val propertyId: Int,
    val policy: MbCanCommandPolicy,
    val refreshSignal: MbCanSignal? = null
)

object MbCanCatalog {
    val telemetry: List<MbCanTelemetryParam> = listOf(
        MbCanTelemetryParam("Powertrain", "Vehicle speed", "eMBCAN_VEHICLE_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Vehicle gear", "eMBCAN_VEHICLE_GEAR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Vehicle engine", "eMBCAN_VEHICLE_ENGINE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Vehicle engine+gear", "eMBCAN_VEHICLE_ENGINE_GEAR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "EBS SOC", "eMBCAN_VEHICLE_EBS_SOC", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Consumption", "eMBCAN_VEHICLE_CONSUMPTION", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Powertrain", "Inverter status", "eMBCAN_VEHICLE_INVERTER_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "Door", "eMBCAN_VEHICLE_DOOR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "BCM status", "eMBCAN_VEHICLE_BCM_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "Seat belt status", "eMBCAN_SEAT_BELT_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "Seat status", "eMBCAN_SEAT_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Body/BCM", "WPC status", "eMBCAN_WPC_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Climate", "PM2.5", "eMBCAN_PM25INFO", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Climate", "AQS status", "eMBCAN_VEHICLE_AQS_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "Radar sensor", "eMBCAN_RADARSENSOR", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "RCTA alarm", "eMBCAN_RCTA_ALARM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "BSD alarm", "eMBCAN_BSD_ALARM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "DOW alarm", "eMBCAN_DOW_ALARM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "LKA status", "eMBCAN_VEHICLE_LKA_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("ADAS", "FRM info", "eMBCAN_VEHICLE_FRM_INFO", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Multimedia", "Audio cfg", "eMBCAN_CFG_AUDIO", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Multimedia", "Vehicle cfg", "eMBCAN_CFG_VEHICLE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("Multimedia", "DMS cfg", "eMBCAN_CFG_DMS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("EV/Charge", "Charging reserve", "eMBCAN_CHARGING_RESERVE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "System mode", "eMBCAN_SYSTEMMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "Hard key", "eMBCAN_HARDKEY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "Upgrade progress", "eMBCAN_UPGRADE_PROGRESS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "DVR status", "eMBCAN_DVR_STATUS", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "DVR params", "eMBCAN_VEHICLE_DVR_PARAM", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanTelemetryParam("System", "DTC", "eMBCAN_DTC", MbCanConfidence.DECLARED_IN_API),
        MbCanTelemetryParam("System", "External temp raw", "eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW", MbCanConfidence.DECLARED_IN_API),
        MbCanTelemetryParam("System", "ICM drive info", "eMBCAN_VEHICLE_ICM_DRIVE_INFO", MbCanConfidence.DECLARED_IN_API)
    )

    val controls: List<MbCanControlParam> = listOf(
        MbCanControlParam("Powertrain", "Drive mode", "eVEHICLE_DRIVEMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "Power mode", "eVEHICLE_POWERMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "AVH switch", "eVEHICLE_AVH_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "HDC switch", "eVEHICLE_HDC_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "ESC off switch", "eVEHICLE_ESCOFF_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Powertrain", "ISS switch", "eVEHICLE_ISS_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("EV/Charge", "Wireless phone charging switch", "eVEHICLE_CHG_WIRELESS_SWITCH", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Body/BCM", "Door auto lock", "eVEHICLE_PROPERTY_DOOR_AUTO_LOCK", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Body/BCM", "Ignition-off unlock", "eVEHICLE_PROPERTY_DOOR_IGNOFF_UNLOCK", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Body/BCM", "Mirror reverse turn location", "eVEHICLE_SET_MIRROR_REVERSE_TURN_LOC", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC power", "eVEHICLE_PROPERTY_HVAC_POWER", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC auto", "eHVAC_AUTO_STATE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC fan speed", "eVEHICLE_PROPERTY_HVAC_FAN_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC air recirculation", "eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "PM25 display source", "eVEHICLE_PM25_DISPLAY_TOGGLE", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "UV lamp request", "eVEHICLE_UV_LAMP_REQ", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "Sterilize strength request", "eVEHICLE_STERILIZE_STRENGTH_REQ", MbCanConfidence.DECLARED_IN_API),
        MbCanControlParam("Climate", "HVAC front defrost blow", "eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC temperature (driver)", "eVEHICLE_PROPERTY_HVAC_TEMPERATURE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "HVAC temperature (passenger)", "eHVAC_FR_TEMPERATURE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Climate", "Fragrance switch", "eVEHICLE_PROPERTY_FRAGRANCE_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "FCW switch", "eFCW_SWTICH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "Auto brake switch", "eVEHICLE_PROPERTY_ACC_AUTOBRAKE_SW", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("ADAS", "LKA sensitivity", "eVEHICLE_PROPERTY_LAS_SENSITIVITY_LEVEL", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "EQ mode", "eAUDIO_PROPERTY_EQMODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Media volume key mode", "eAUDIO_PROPERTY_VOLUME_KEY", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "Volume vs speed", "eAUDIO_PROPERTY_VOLUME_SPEED", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("Multimedia", "AVM language", "eAVM_SET_LANG", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "System reboot", "eSYSTEM_REBOOT", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "ICM brightness mode", "eVEHICLE_SET_ICM_BRIGHTNESS_MODE", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Steering wheel heating switch", "eVEHICLE_SET_MFS_HEAT_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Wiper maintenance switch", "eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS),
        MbCanControlParam("System", "Parking radar switch", "eVEHICLE_SET_PAS_SWITCH", MbCanConfidence.CONFIRMED_IN_APP_CALLS)
    )
}

object MbCanKnownVehiclePropertyId {
    // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH.
    const val STEERING_WHEEL_HEAT_SWITCH = 188
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH]. */
    const val WIPER_MAINTENANCE_SWITCH = 185
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_PAS_SWITCH]. */
    const val PARKING_RADAR_SWITCH = 218
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT] */
    const val FRONT_WINDSCREEN_HEAT_SWITCH = 316
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER] — rear window + mirrors. */
    const val HVAC_DEFROSTER_SWITCH = 41
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION] — property id. */
    const val HVAC_AIR_RECIRCULATION = 39
    /** [canSetVehicleParam]/[canGetVehicleParam] value: recirculation on. */
    const val HVAC_AIR_RECIRCULATION_VALUE_ON = 1
    /** Same property: recirculation off. */
    const val HVAC_AIR_RECIRCULATION_VALUE_OFF = 2
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_TEMPERATURE] — driver zone; raw = °C × 10. */
    const val HVAC_TEMPERATURE = 37
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eHVAC_FR_TEMPERATURE] — passenger (front-right) zone;
     * raw = °C × 10 (same scale as [HVAC_TEMPERATURE]). Confirmed in OEM MB_ACSettings.
     */
    const val HVAC_FR_TEMPERATURE = 111
    /** OEM AC range: Lo=16.0°C … Hi=30.0°C, step 0.5°C → raw step 5. */
    const val HVAC_TEMPERATURE_RAW_MIN = 160
    const val HVAC_TEMPERATURE_RAW_MAX = 300
    const val HVAC_TEMPERATURE_RAW_STEP = 5
    const val HVAC_TEMPERATURE_RAW_PER_CELSIUS = 10
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_POWER] — AC compressor; 1 off, 2 on. */
    const val HVAC_POWER = 36
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eHVAC_AUTO_STATE] — AUTO mode; 1 off, 2 on. */
    const val HVAC_AUTO_STATE = 110
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYNCSWTICH_REQ] — dual/sync mode; 1 off, 2 on. */
    const val HVAC_SYNC_SWITCH = 94
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_PM25_MONITORING] — PM2.5 air purifier switch; 1 off, 2 on. */
    const val HVAC_PM25_MONITORING = 166
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION] — blow mode. */
    const val HVAC_FAN_DIRECTION = 40
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_SPEED]. */
    const val HVAC_FAN_SPEED = 38
    /** mbCAN blow modes (Android 9 [MBFrontDefrostingView]). */
    const val HVAC_FAN_DIRECTION_FACE = 1
    const val HVAC_FAN_DIRECTION_FOOT = 2
    const val HVAC_FAN_DIRECTION_FACE_FOOT = 3
    const val HVAC_FAN_DIRECTION_DEFROST = 4
    const val HVAC_FAN_DIRECTION_DEFROST_FOOT = 5
    /** VHAL blow modes (Android 10 [AcFragment.mWindModeIds]). */
    const val HVAC_FAN_DIRECTION_VHAL_FACE = 0
    const val HVAC_FAN_DIRECTION_VHAL_DEFROST_FOOT = 3
    const val HVAC_FAN_DIRECTION_VHAL_DEFROST = 4
    /**
     * [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PLG_CONTROL] — power liftgate.
     * Stock [MBVehicleManager.openClosePlgControl]: write 1, then reset to 0 after ~100ms.
     */
    const val VEHICLE_PLG_CONTROL = 134
    const val VEHICLE_PLG_CONTROL_RESET = 0
    const val VEHICLE_PLG_CONTROL_OPEN_CLOSE = 1
    const val VEHICLE_PLG_CONTROL_PAUSE = 2
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_CHG_WIRELESS_SWITCH] — 1 off, 2 on. */
    const val CHG_WIRELESS_SWITCH = 264
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_STEERING_MODE] — 0–6. */
    const val VEHICLE_PROPERTY_STEERING_MODE = 24
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_EPS_MODE] — 0–6. */
    const val VEHICLE_PROPERTY_EPS_MODE = 25
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYSTEM_MODE] — 0–6. */
    const val SYSTEM_MODE = 73
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSYSTEM_REBOOT] — head unit reboot via [canSetVehicleParam]. */
    const val SYSTEM_REBOOT = 74
    /** Value written to [SYSTEM_REBOOT] to request HU reboot. */
    const val SYSTEM_REBOOT_VALUE = 1
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_DRIVEMODE] — 0–6. */
    const val VEHICLE_DRIVEMODE = 145
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_POWERMODE] — 0–6. */
    const val VEHICLE_POWERMODE = 147
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_DRIVEMODE_6DCT_WET] — 0–6. */
    const val VEHICLE_DRIVEMODE_6DCT_WET = 149
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PM25_DISPLAY_TOGGLE] — 1 inside, 2 outside. */
    const val VEHICLE_PM25_DISPLAY_TOGGLE = 163
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_UV_LAMP_REQ] — 1 off, 2 on, 3 auto. */
    const val VEHICLE_UV_LAMP_REQ = 164
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_STERILIZE_STRENGTH_REQ] — 1 low, 2 medium, 3 high. */
    const val VEHICLE_STERILIZE_STRENGTH_REQ = 165
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eSOURCE_STATION_MODE] — 1 off, 2 on. */
    const val SOURCE_STATION_MODE = 127
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_VEHWASH_MODESET] — 1 off, 2 on. */
    const val VEHICLE_VEHWASH_MODESET = 252
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICEL_BRAKE_PEDA_FEEL_MODE] — 0–6. */
    const val VEHICEL_BRAKE_PEDA_FEEL_MODE = 300
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_REARFOGLIGHT] — 1 off, 2 on. */
    const val REAR_FOG_LIGHT = 136
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_LIGHTCONTROL] — 4 off, 2 position, 3 low, 1 auto. */
    const val LIGHT_CONTROL = 135
    const val FRONT_LEFT_SEAT_HEAT_VENT_SWITCH = 138
    const val FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH = 139
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_SEAT_LR_HEATVENTSW] — rear heat only (values 1–4). */
    const val REAR_LEFT_SEAT_HEAT_SWITCH = 318
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_SEAT_RR_HEATVENTSW] — rear heat only (values 1–4). */
    const val REAR_RIGHT_SEAT_HEAT_SWITCH = 319

    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_DOOR_AUTO_LOCK] — 1 off, 2 on. */
    const val DOOR_AUTO_LOCK = 1
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_DOOR_IGNOFF_UNLOCK] — 1 on, 2 off. */
    const val DOOR_IGNOFF_UNLOCK = 2
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_MIRROR_AUTOFOLD_SW] — 1 off, 2 on. */
    const val MIRROR_AUTOFOLD = 4
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_MIRROR_REVERSE_TURN] — 1 off, 2 on. */
    const val MIRROR_REVERSE_TURN = 5
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HEADLIGHTS_HOMELIGHT_DELAY] — 0/1/2/3. */
    const val HEADLIGHTS_HOMELIGHT_DELAY = 7
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_LAS_SENSITIVITY_LEVEL] — 1–3. */
    const val LAS_SENSITIVITY_LEVEL = 16
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_ID_HEADLIGHTS_SWITCH] — 0–3. */
    const val HEADLIGHTS_SWITCH = 19
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_ACC_AUTOBRAKE_SW] — 1 off, 2 on. */
    const val ACC_AUTOBRAKE_SW = 20
    /**
     * Steering-wheel cruise / ACC keys. OEM [HardKeyService] pulses value 1
     * (`eVEHICLE_MFS_CRUISE_CONTROL` = SET/on).
     */
    const val MFS_CRUISE_CONTROL = 210
    const val MFS_SPEED_LIMIT = 211
    const val MFS_CANCEL = 212
    const val MFS_RES_PLUS = 213
    const val MFS_SET_MINUS = 214
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eFCW_SWTICH] — 1 off, 2 on. */
    const val FCW_SWITCH = 96
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_HDC_SWITCH] — 1 off, 2 on. */
    const val HDC_SWITCH = 143
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_ESCOFF_SWITCH] — 1 off, 2 on (ESC off active). */
    const val ESC_OFF_SWITCH = 144
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_AVH_SWITCH] — 1 off, 2 on. */
    const val AVH_SWITCH = 142

    // --- Experimental / OEM body & light effects (launcher «Экспериментальные») ---
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_MUSICAL_RHYTHM] — 1 off, 2 on. */
    const val MUSICAL_RHYTHM = 30
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_WELCOME_LAMP] — 1 off, 2 on. */
    const val WELCOME_LAMP = 32
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_FRAGRANCE_SWITCH] — 1 off, 2 on. */
    const val FRAGRANCE_SWITCH = 33
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_BREATHING_UNLOCK] — 1 off, 2 on. */
    const val BREATHING_UNLOCK = 48
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_BREATHING_LOCK] — 1 off, 2 on. */
    const val BREATHING_LOCK = 49
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_DRIVER_UNLOCKMODE] — 1 driver door, 2 all doors. */
    const val DRIVER_UNLOCK_MODE = 131
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_DOORKONB_SWITCH] — 1 off, 2 on. */
    const val DOORKNOB_SWITCH = 133
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_ISS_SWITCH] — 1 off, 2 on. */
    const val ISS_SWITCH = 148
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_STATIC_EFFECT] — 0 off, 1 mono, 3 full-color. */
    const val STATIC_EFFECT = 169
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_SET_WELCOME_SEAT] — 1 off, 2 on. */
    const val WELCOME_SEAT = 190
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_UNLOCK_ANIMATION] — 1 off, 2 on. */
    const val UNLOCK_ANIMATION = 206
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_LOCK_ANIMATION] — 1 off, 2 on. */
    const val LOCK_ANIMATION = 207
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_WINDOW_AUTOCLOSE_SWITCH] — 1 off, 2 on. */
    const val WINDOW_AUTOCLOSE_SWITCH = 243
    /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_LIGHT_DOME_DOORCTRL_SWITCH] — 1 off, 2 on. */
    const val LIGHT_DOME_DOORCTRL_SWITCH = 245
}

/** [com.mengbo.mbCan.defines.MBAudioProperty] integer ids for [com.mengbo.mbCan.MBCanEngine.canGetAudioParam]. */
object MbCanKnownAudioPropertyId {
    /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME] */
    const val VOLUME = 2
    /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED] */
    const val VOLUME_SPEED = 13
}

data class MbCanAudioCommandSpec(
    val propertyId: Int,
    val policy: MbCanCommandPolicy,
    val refreshSignal: MbCanSignal? = null,
)

object MbCanAudioCommandRegistry {
    private val specsByPropertyId: Map<Int, MbCanAudioCommandSpec> = listOf(
        MbCanAudioCommandSpec(
            propertyId = MbCanKnownAudioPropertyId.VOLUME_SPEED,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3, 4)),
            refreshSignal = MbCanSignal.AudioVolumeSpeed,
        ),
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanAudioCommandSpec? = specsByPropertyId[propertyId]
}

object MbCanCommandRegistry {
    private val specsByPropertyId: Map<Int, MbCanCommandSpec> = listOf(
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.SteeringWheelHeat
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 2,
                onValue = 1,
                unknownFallbackValue = 1
            ),
            refreshSignal = MbCanSignal.WiperMaintenance
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.ParkingRadar
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.FrontWindscreenHeat
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacDefroster
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF,
                onValue = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON,
                unknownFallbackValue = MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF,
            ),
            refreshSignal = MbCanSignal.HvacAirRecirculation
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_POWER,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacAcPower
        ),
        MbCanCommandSpec(
            propertyId = HVAC_TEMPERATURE,
            // OEM MBACTempView: raw = °C×10, Lo=160 Hi=300, hard-key ±5.
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = (MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MIN..
                    MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MAX)
                    .filter { it % MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_STEP == 0 }
                    .toSet(),
            ),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FR_TEMPERATURE,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = (MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MIN..
                    MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_MAX)
                    .filter { it % MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RAW_STEP == 0 }
                    .toSet(),
            ),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.HvacAutoState
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            )
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_PM25_MONITORING,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            )
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION,
            policy = MbCanCommandPolicy.ToggleHvacFrontDefrost,
            refreshSignal = MbCanSignal.HvacDefrosterFront
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..7).toSet()),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(
                    MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL_RESET,
                    MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL_OPEN_CLOSE,
                    MbCanKnownVehiclePropertyId.VEHICLE_PLG_CONTROL_PAUSE,
                ),
            ),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.CHG_WIRELESS_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.WirelessChargingSwitch
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.SYSTEM_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = SYSTEM_REBOOT,
            policy = MbCanCommandPolicy.SetExact(
                allowedValues = setOf(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT_VALUE),
            ),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_POWERMODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_PM25_DISPLAY_TOGGLE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2)),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_UV_LAMP_REQ,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_STERILIZE_STRENGTH_REQ,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICEL_BRAKE_PEDA_FEEL_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..6).toSet()),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.SOURCE_STATION_MODE,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET,
            policy = MbCanCommandPolicy.ToggleBinary(
                offValue = 1,
                onValue = 2,
                unknownFallbackValue = 2
            ),
            refreshSignal = MbCanSignal.CarSettingsVehicleParams
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..7).toSet()),
            refreshSignal = MbCanSignal.FrontLeftSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..7).toSet()),
            refreshSignal = MbCanSignal.FrontRightSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..4).toSet()),
            refreshSignal = MbCanSignal.RearLeftSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..4).toSet()),
            refreshSignal = MbCanSignal.RearRightSeatMode
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 2, onValue = 1, unknownFallbackValue = 1),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MIRROR_REVERSE_TURN,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..3).toSet()),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (1..3).toSet()),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HEADLIGHTS_SWITCH,
            policy = MbCanCommandPolicy.SetExact(allowedValues = (0..3).toSet()),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LIGHT_CONTROL,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3, 4)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ACC_AUTOBRAKE_SW,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FCW_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.HDC_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ESC_OFF_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.AVH_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.MUSICAL_RHYTHM,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.WELCOME_LAMP,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.FRAGRANCE_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.BREATHING_UNLOCK,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.BREATHING_LOCK,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.DOORKNOB_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.ISS_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.STATIC_EFFECT,
            policy = MbCanCommandPolicy.SetExact(allowedValues = setOf(0, 1, 3)),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.WELCOME_SEAT,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.UNLOCK_ANIMATION,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LOCK_ANIMATION,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.WINDOW_AUTOCLOSE_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
        MbCanCommandSpec(
            propertyId = MbCanKnownVehiclePropertyId.LIGHT_DOME_DOORCTRL_SWITCH,
            policy = MbCanCommandPolicy.ToggleBinary(offValue = 1, onValue = 2, unknownFallbackValue = 2),
        ),
    ).associateBy { it.propertyId }

    fun get(propertyId: Int): MbCanCommandSpec? = specsByPropertyId[propertyId]
}

