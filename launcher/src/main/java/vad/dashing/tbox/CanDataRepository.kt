package vad.dashing.tbox

import android.content.Context
import android.os.SystemClock
import com.mengbo.mbconfig.MBConfigConstant
import com.mengbo.mbconfig.PropManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vad.dashing.tbox.trip.TripRepository
import vad.dashing.tbox.utils.InOutTemperatureNullDebounce


data class Wheels(
    val wheel1: Float? = null,
    val wheel2: Float? = null,
    val wheel3: Float? = null,
    val wheel4: Float? = null,
    val wheel1LastTimeNotNull: Long? = null,
    val wheel2LastTimeNotNull: Long? = null,
    val wheel3LastTimeNotNull: Long? = null,
    val wheel4LastTimeNotNull: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Wheels) return false
        return wheel1 == other.wheel1 &&
            wheel2 == other.wheel2 &&
            wheel3 == other.wheel3 &&
            wheel4 == other.wheel4
    }

    override fun hashCode(): Int {
        var result = wheel1?.hashCode() ?: 0
        result = 31 * result + (wheel2?.hashCode() ?: 0)
        result = 31 * result + (wheel3?.hashCode() ?: 0)
        result = 31 * result + (wheel4?.hashCode() ?: 0)
        return result
    }
}

data class CanFrame(
    val date: Date,
    val rawValue: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CanFrame

        if (date != other.date) return false
        if (!rawValue.contentEquals(other.rawValue)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + rawValue.contentHashCode()
        return result
    }
}

object CanDataRepository {
    private val _cruiseSetSpeed = MutableStateFlow<UInt?>(null)
    val cruiseSetSpeed: StateFlow<UInt?> = _cruiseSetSpeed.asStateFlow()

    private val _cruiseStatusByte = MutableStateFlow<Int?>(null)
    val cruiseStatusByte: StateFlow<Int?> = _cruiseStatusByte.asStateFlow()

    private val _ccsCruiseStatus = MutableStateFlow<Int?>(null)
    val ccsCruiseStatus: StateFlow<Int?> = _ccsCruiseStatus.asStateFlow()

    private val _wheelsSpeed = MutableStateFlow(Wheels())
    val wheelsSpeed: StateFlow<Wheels> = _wheelsSpeed.asStateFlow()

    private val _wheelsPressure = MutableStateFlow(Wheels())
    val wheelsPressure: StateFlow<Wheels> = _wheelsPressure.asStateFlow()

    private val _wheelsTemperature = MutableStateFlow(Wheels())
    val wheelsTemperature: StateFlow<Wheels> = _wheelsTemperature.asStateFlow()

    private val _climateSetTemperature1 = MutableStateFlow<Float?>(null)
    val climateSetTemperature1: StateFlow<Float?> = _climateSetTemperature1.asStateFlow()
    private val _climateSetTemperature2 = MutableStateFlow<Float?>(null)
    /** Passenger (front-right) climate setpoint, °C. */
    val climateSetTemperature2: StateFlow<Float?> = _climateSetTemperature2.asStateFlow()

    private val _engineRPM = MutableStateFlow<Float?>(null)
    val engineRPM: StateFlow<Float?> = _engineRPM.asStateFlow()

    private val _param5 = MutableStateFlow<Float?>(null)
    val param5: StateFlow<Float?> = _param5.asStateFlow()

    private val _param1 = MutableStateFlow<Float?>(null)
    val param1: StateFlow<Float?> = _param1.asStateFlow()

    private val _param2 = MutableStateFlow<Float?>(null)
    val param2: StateFlow<Float?> = _param2.asStateFlow()

    private val _param3 = MutableStateFlow<Float?>(null)
    val param3: StateFlow<Float?> = _param3.asStateFlow()

    private val _param4 = MutableStateFlow<Float?>(null)
    val param4: StateFlow<Float?> = _param4.asStateFlow()

    private val _steerAngle = MutableStateFlow<Float?>(null)
    val steerAngle: StateFlow<Float?> = _steerAngle.asStateFlow()

    private val _steerSpeed = MutableStateFlow<Int?>(null)
    val steerSpeed: StateFlow<Int?> = _steerSpeed.asStateFlow()

    private val _engineTemperature = MutableStateFlow<Float?>(null)
    val engineTemperature: StateFlow<Float?> = _engineTemperature.asStateFlow()

    private val _odometer = MutableStateFlow<UInt?>(null)
    val odometer: StateFlow<UInt?> = _odometer.asStateFlow()

    private val _localTripDistanceKm = MutableStateFlow<Float?>(null)
    val localTripDistanceKm: StateFlow<Float?> = _localTripDistanceKm.asStateFlow()

    private val _manualTripDistanceKm = MutableStateFlow<Float?>(null)
    val manualTripDistanceKm: StateFlow<Float?> = _manualTripDistanceKm.asStateFlow()

    private val _localTripDurationSeconds = MutableStateFlow<Long?>(null)
    val localTripDurationSeconds: StateFlow<Long?> = _localTripDurationSeconds.asStateFlow()

    private val _distanceToNextMaintenance = MutableStateFlow<UInt?>(null)
    val distanceToNextMaintenance: StateFlow<UInt?> = _distanceToNextMaintenance.asStateFlow()

    private val _distanceToFuelEmpty = MutableStateFlow<UInt?>(null)
    val distanceToFuelEmpty: StateFlow<UInt?> = _distanceToFuelEmpty.asStateFlow()

    private val _breakingForce = MutableStateFlow<UInt?>(null)
    val breakingForce: StateFlow<UInt?> = _breakingForce.asStateFlow()

    private val _carSpeed = MutableStateFlow<Float?>(null)
    val carSpeed: StateFlow<Float?> = _carSpeed.asStateFlow()

    private val _carSpeedAccurate = MutableStateFlow<Float?>(null)
    val carSpeedAccurate: StateFlow<Float?> = _carSpeedAccurate.asStateFlow()

    private val _voltage = MutableStateFlow<Float?>(null)
    val voltage: StateFlow<Float?> = _voltage.asStateFlow()

    private val _fuelLevelPercentage = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentage: StateFlow<UInt?> = _fuelLevelPercentage.asStateFlow()

    private val _fuelLevelPercentageFiltered = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentageFiltered: StateFlow<UInt?> = _fuelLevelPercentageFiltered.asStateFlow()

    /** Литры в баке после калибровки (см. BackgroundService и пакет fuellevelcalibration). */
    private val _fuelLevelCalibratedLiters = MutableStateFlow<Float?>(null)
    val fuelLevelCalibratedLiters: StateFlow<Float?> = _fuelLevelCalibratedLiters.asStateFlow()

    /** Литры в баке после калибровки с корректировкой по температуре (см. BackgroundService и пакет fuellevelcalibration). */
    private val _fuelLevelCalibratedLitersActual = MutableStateFlow<Float?>(null)
    val fuelLevelCalibratedLitersActual: StateFlow<Float?> = _fuelLevelCalibratedLitersActual.asStateFlow()

    /** Уверенность калибровки, 0…1 (в UI пока не показывается). */
    private val _fuelCalibrationConfidence = MutableStateFlow<Float?>(null)
    val fuelCalibrationConfidence: StateFlow<Float?> = _fuelCalibrationConfidence.asStateFlow()

    private val _currentFuelConsumption = MutableStateFlow<Float?>(null)
    val currentFuelConsumption: StateFlow<Float?> = _currentFuelConsumption.asStateFlow()

    private val _averageFuelConsumption = MutableStateFlow<Float?>(null)
    val averageFuelConsumption: StateFlow<Float?> = _averageFuelConsumption.asStateFlow()

    private val _gearBoxMode = MutableStateFlow("")
    val gearBoxMode: StateFlow<String> = _gearBoxMode.asStateFlow()

    private val _gearBoxCurrentGear = MutableStateFlow<Int?>(null)
    val gearBoxCurrentGear: StateFlow<Int?> = _gearBoxCurrentGear.asStateFlow()

    private val _gearBoxPreparedGear = MutableStateFlow<Int?>(null)
    val gearBoxPreparedGear: StateFlow<Int?> = _gearBoxPreparedGear.asStateFlow()

    private val _gearBoxChangeGear = MutableStateFlow<Boolean?>(null)
    val gearBoxChangeGear: StateFlow<Boolean?> = _gearBoxChangeGear.asStateFlow()

    private val _gearBoxOilTemperature = MutableStateFlow<Int?>(null)
    val gearBoxOilTemperature: StateFlow<Int?> = _gearBoxOilTemperature.asStateFlow()

    private val _gearBoxDriveMode = MutableStateFlow<String>("")
    val gearBoxDriveMode: StateFlow<String> = _gearBoxDriveMode.asStateFlow()

    private val _gearBoxWork = MutableStateFlow<String>("")
    val gearBoxWork: StateFlow<String> = _gearBoxWork.asStateFlow()

    private val _maxGearSeen = MutableStateFlow(0)
    val maxGearSeen: StateFlow<Int> = _maxGearSeen.asStateFlow()

    private val initialGearboxType: GearboxType = gearboxTypeBySystemConfig()

    private val _gearboxType = MutableStateFlow(initialGearboxType)
    val gearboxType: StateFlow<GearboxType> = _gearboxType.asStateFlow()

    private val _is6DctGearbox = MutableStateFlow(initialGearboxType == GearboxType.DCT_6)
    val is6DctGearbox: StateFlow<Boolean> = _is6DctGearbox.asStateFlow()

    private val _isManualGearbox = MutableStateFlow(initialGearboxType == GearboxType.MANUAL)
    val isManualGearbox: StateFlow<Boolean> = _isManualGearbox.asStateFlow()

    private val _frontLeftSeatMode = MutableStateFlow<UInt?>(null)
    val frontLeftSeatMode: StateFlow<UInt?> = _frontLeftSeatMode.asStateFlow()

    private val _frontRightSeatMode = MutableStateFlow<UInt?>(null)
    val frontRightSeatMode: StateFlow<UInt?> = _frontRightSeatMode.asStateFlow()

    private val _outsideTemperature = MutableStateFlow<Float?>(null)
    val outsideTemperature: StateFlow<Float?> = _outsideTemperature.asStateFlow()
    private var outsideTemperatureLastTimeNotNull: Long? = null

    private val _insideTemperature = MutableStateFlow<Float?>(null)
    val insideTemperature: StateFlow<Float?> = _insideTemperature.asStateFlow()
    private var insideTemperatureLastTimeNotNull: Long? = null

    private val _outsideAirQuality = MutableStateFlow<UInt?>(null)
    val outsideAirQuality: StateFlow<UInt?> = _outsideAirQuality.asStateFlow()

    private val _insideAirQuality = MutableStateFlow<UInt?>(null)
    val insideAirQuality: StateFlow<UInt?> = _insideAirQuality.asStateFlow()

    private val _isWindowsBlocked = MutableStateFlow<Boolean?>(null)
    val isWindowsBlocked: StateFlow<Boolean?> = _isWindowsBlocked.asStateFlow()

    private val _rainSensorDetected = MutableStateFlow(false)
    val rainSensorDetected: StateFlow<Boolean> = _rainSensorDetected.asStateFlow()

    private val _canFramesStructured = MutableStateFlow<Map<String, List<CanFrame>>>(emptyMap())
    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = _canFramesStructured.asStateFlow()

    private val _motorHoursTrip = MutableStateFlow<Float?>(null)
    val motorHoursTrip: StateFlow<Float?> = _motorHoursTrip.asStateFlow()

    private val tripFuelLock = Any()
    private var lastOdometer: UInt? = null
    private var accumulatedTripDistanceKm: Float = 0f
    private var accumulatedManualTripDistanceKm: Float = 0f
    private var initialFuelLiters: Float? = null
    private var previousAvgConsumption: Float? = null
    private var tripStartTimeMs: Long = System.currentTimeMillis()

    private var overallAccumulatedDistanceKm: Float = 0f
    private var overallAccumulatedFuelLiters: Float = 0f
    private var overallAccumulatedIdleFuelLiters: Double = 0.0
    private var currentSegmentDistKm: Float = 0f
    private var currentSegmentIdleFuelLiters: Double = 0.0
    private var lastKnownFuelLiters: Float? = null
    private var lastIdleCheckMs: Long = 0L

    private const val PREFS_PERSISTENCE = "tbox_can_data_persistence"
    private const val KEY_AVG_FUEL = "saved_avg_fuel_consumption"
    private const val KEY_TRIP_DIST = "saved_local_trip_distance"
    private const val KEY_MANUAL_TRIP_DIST = "saved_manual_trip_distance"
    private const val KEY_INIT_FUEL = "saved_initial_fuel_liters"
    private const val KEY_LAST_ODO = "saved_last_odometer"
    private const val KEY_TRIP_START_TIME = "saved_trip_start_time"
    private const val KEY_OVERALL_DIST = "saved_overall_distance_km"
    private const val KEY_OVERALL_FUEL = "saved_overall_fuel_liters"
    private const val KEY_OVERALL_IDLE_FUEL = "saved_overall_idle_fuel_liters"
    private const val KEY_SEGMENT_DIST = "saved_segment_distance_km"
    private const val KEY_SEGMENT_IDLE_FUEL = "saved_segment_idle_fuel_liters"

    private const val MAX_CAN_FRAMES = 5
    private const val MAX_CAN_IDS = 500
    private const val MAX_FRAMES_PER_ID = 10
    private const val CAN_FRAMES_PUBLISH_MIN_INTERVAL_MS = 250L

    private val canFramesLock = Any()
    private val canFramesBuffer = LinkedHashMap<String, ArrayDeque<CanFrame>>()
    private var lastCanFramesPublishMs: Long = 0L

    @Volatile
    private var isPersistedStateLoaded = false

    private fun nowElapsedMs(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    init {
        loadPersistedStateIfNeeded()
    }

    private fun loadPersistedStateIfNeeded() {
        if (isPersistedStateLoaded) return
        synchronized(tripFuelLock) {
            if (isPersistedStateLoaded) return
            val context = AppContextHolder.appContextOrNull ?: return
            runCatching {
                val prefs = context.getSharedPreferences(PREFS_PERSISTENCE, Context.MODE_PRIVATE)

                if (prefs.contains(KEY_AVG_FUEL)) {
                    val savedAvg = prefs.getFloat(KEY_AVG_FUEL, -1f)
                    if (savedAvg > 0f) {
                        previousAvgConsumption = savedAvg
                        _averageFuelConsumption.value = savedAvg
                    }
                }
                if (prefs.contains(KEY_TRIP_DIST)) {
                    val savedDist = prefs.getFloat(KEY_TRIP_DIST, -1f)
                    if (savedDist >= 0f) {
                        accumulatedTripDistanceKm = savedDist
                        _localTripDistanceKm.value = savedDist
                    }
                }
                if (prefs.contains(KEY_MANUAL_TRIP_DIST)) {
                    val savedManualDist = prefs.getFloat(KEY_MANUAL_TRIP_DIST, -1f)
                    if (savedManualDist >= 0f) {
                        accumulatedManualTripDistanceKm = savedManualDist
                        _manualTripDistanceKm.value = savedManualDist
                    }
                } else {
                    _manualTripDistanceKm.value = accumulatedManualTripDistanceKm
                }
                if (prefs.contains(KEY_INIT_FUEL)) {
                    val savedFuel = prefs.getFloat(KEY_INIT_FUEL, -1f)
                    if (savedFuel >= 0f) {
                        initialFuelLiters = savedFuel
                    }
                }
                if (prefs.contains(KEY_LAST_ODO)) {
                    val savedOdo = prefs.getLong(KEY_LAST_ODO, -1L)
                    if (savedOdo >= 0) {
                        lastOdometer = savedOdo.toUInt()
                    }
                }
                if (prefs.contains(KEY_TRIP_START_TIME)) {
                    val savedStartTime = prefs.getLong(KEY_TRIP_START_TIME, -1L)
                    if (savedStartTime > 0L) {
                        tripStartTimeMs = savedStartTime
                    }
                } else {
                    tripStartTimeMs = System.currentTimeMillis()
                }
                if (prefs.contains(KEY_OVERALL_DIST)) {
                    overallAccumulatedDistanceKm = prefs.getFloat(KEY_OVERALL_DIST, 0f)
                }
                if (prefs.contains(KEY_OVERALL_FUEL)) {
                    overallAccumulatedFuelLiters = prefs.getFloat(KEY_OVERALL_FUEL, 0f)
                }
                if (prefs.contains(KEY_OVERALL_IDLE_FUEL)) {
                    overallAccumulatedIdleFuelLiters = prefs.getFloat(KEY_OVERALL_IDLE_FUEL, 0f).toDouble()
                }
                if (prefs.contains(KEY_SEGMENT_DIST)) {
                    currentSegmentDistKm = prefs.getFloat(KEY_SEGMENT_DIST, 0f)
                }
                if (prefs.contains(KEY_SEGMENT_IDLE_FUEL)) {
                    currentSegmentIdleFuelLiters = prefs.getFloat(KEY_SEGMENT_IDLE_FUEL, 0f).toDouble()
                }
                val totalDist = overallAccumulatedDistanceKm + currentSegmentDistKm
                val isIgnoreIdle = AppContextHolder.appContextOrNull?.let {
                    vad.dashing.tbox.ui.launcher.LauncherAppConfigStore.ignoreIdleFuel(it)
                } ?: false
                val totalFuel = overallAccumulatedFuelLiters
                val totalIdle = (overallAccumulatedIdleFuelLiters + currentSegmentIdleFuelLiters).toFloat()
                val effectiveFuel = if (isIgnoreIdle) {
                    (totalFuel - totalIdle).coerceAtLeast(0f)
                } else {
                    totalFuel
                }
                if (totalDist > 0f && effectiveFuel > 0f) {
                    val calculated = (effectiveFuel / totalDist) * 100f
                    if (calculated > 0f && calculated < 50f) {
                        _averageFuelConsumption.value = calculated
                        previousAvgConsumption = calculated
                    }
                } else if (previousAvgConsumption != null && previousAvgConsumption!! > 0f && overallAccumulatedDistanceKm == 0f) {
                    val baselineDist = 50f
                    overallAccumulatedDistanceKm = baselineDist
                    overallAccumulatedFuelLiters = (previousAvgConsumption!! / 100f) * baselineDist
                }
                updateTripDurationLocked()
                isPersistedStateLoaded = true
            }
        }
    }

    private var lastSavePersistedStateMs: Long = 0L
    private const val SAVE_PERSISTED_STATE_MIN_INTERVAL_MS = 15_000L

    private fun savePersistedStateLocked(immediate: Boolean = false) {
        val now = nowElapsedMs()
        if (!immediate && (now - lastSavePersistedStateMs) < SAVE_PERSISTED_STATE_MIN_INTERVAL_MS) {
            return
        }
        lastSavePersistedStateMs = now
        val context = AppContextHolder.appContextOrNull ?: return
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_PERSISTENCE, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            previousAvgConsumption?.let { editor.putFloat(KEY_AVG_FUEL, it) } ?: editor.remove(KEY_AVG_FUEL)
            editor.putFloat(KEY_TRIP_DIST, accumulatedTripDistanceKm)
            editor.putFloat(KEY_MANUAL_TRIP_DIST, accumulatedManualTripDistanceKm)
            initialFuelLiters?.let { editor.putFloat(KEY_INIT_FUEL, it) } ?: editor.remove(KEY_INIT_FUEL)
            lastOdometer?.let { editor.putLong(KEY_LAST_ODO, it.toLong()) } ?: editor.remove(KEY_LAST_ODO)
            editor.putLong(KEY_TRIP_START_TIME, tripStartTimeMs)
            editor.putFloat(KEY_OVERALL_DIST, overallAccumulatedDistanceKm)
            editor.putFloat(KEY_OVERALL_FUEL, overallAccumulatedFuelLiters)
            editor.putFloat(KEY_OVERALL_IDLE_FUEL, overallAccumulatedIdleFuelLiters.toFloat())
            editor.putFloat(KEY_SEGMENT_DIST, currentSegmentDistKm)
            editor.putFloat(KEY_SEGMENT_IDLE_FUEL, currentSegmentIdleFuelLiters.toFloat())
            editor.apply()
        }
    }

    private val timeFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    private fun <T> MutableStateFlow<T>.setIfChanged(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }

    /*fun addCanFrame(rawValue: String) {
        val timestamp = timeFormat.format(Date())
        val entry = "$timestamp;$rawValue"

        _canFramesList.update { currentData ->
            (currentData + entry).takeLast(MAX_CAN_FRAMES)
        }
        _canFrameTime.value = Date()
    }*/

    fun updateVoltage(newValue: Float) {
        _voltage.setIfChanged(newValue)
    }

    fun updateCarSpeed(newValue: Float) {
        val current = _carSpeed.value
        if (current == null || kotlin.math.abs(current - newValue) >= 1.0f || (newValue == 0f && current != 0f)) {
            _carSpeed.value = newValue
        }
        updateIdleFuelTracking()
    }

    fun updateCarSpeedAccurate(newValue: Float) {
        _carSpeedAccurate.setIfChanged(newValue)
        updateIdleFuelTracking()
    }

    fun updateOdometer(newValue: UInt) {
        loadPersistedStateIfNeeded()
        _odometer.setIfChanged(newValue)
        synchronized(tripFuelLock) {
            updateTripDurationLocked()
            val prevOdo = lastOdometer
            if (prevOdo != null) {
                if (newValue >= prevOdo) {
                    val deltaKm = (newValue - prevOdo).toFloat()
                    if (deltaKm > 0f && deltaKm < 1000f) {
                        accumulatedTripDistanceKm += deltaKm
                        accumulatedManualTripDistanceKm += deltaKm
                        currentSegmentDistKm += deltaKm
                        _localTripDistanceKm.value = accumulatedTripDistanceKm
                        _manualTripDistanceKm.value = accumulatedManualTripDistanceKm
                    }
                } else {
                    lastOdometer = newValue
                }
            } else {
                _localTripDistanceKm.value = accumulatedTripDistanceKm
                _manualTripDistanceKm.value = accumulatedManualTripDistanceKm
            }
            lastOdometer = newValue
            recalculateLocalAverageFuelConsumptionLocked()
            savePersistedStateLocked()
        }
        if (TripRepository.activeTrip.value == null) {
            TripRepository.setActiveTrip(true)
        }
    }

    private fun getTankCapacityLiters(): Float {
        val context = AppContextHolder.appContextOrNull ?: return 57.0f
        return vad.dashing.tbox.ui.launcher.LauncherAppConfigStore.fuelTankCapacityLiters(context)
    }

    private fun getEffectiveFuelPercentage(): Float? {
        return _fuelLevelPercentageFiltered.value?.toFloat()
    }

    fun resetLocalTripDistance() {
        loadPersistedStateIfNeeded()
        synchronized(tripFuelLock) {
            accumulatedTripDistanceKm = 0f
            _localTripDistanceKm.value = 0f
            lastOdometer = _odometer.value
            val currentPercent = getEffectiveFuelPercentage()
            initialFuelLiters = currentPercent?.let { it * (getTankCapacityLiters() / 100f) }
            tripStartTimeMs = System.currentTimeMillis()
            updateTripDurationLocked()
            savePersistedStateLocked(immediate = true)
        }
    }

    fun resetOverallAverageFuelConsumption() {
        loadPersistedStateIfNeeded()
        synchronized(tripFuelLock) {
            overallAccumulatedDistanceKm = 0f
            overallAccumulatedFuelLiters = 0f
            overallAccumulatedIdleFuelLiters = 0.0
            currentSegmentDistKm = 0f
            currentSegmentIdleFuelLiters = 0.0
            lastIdleCheckMs = 0L
            val currentPercent = getEffectiveFuelPercentage()
            initialFuelLiters = currentPercent?.let { it * (getTankCapacityLiters() / 100f) }
            lastKnownFuelLiters = initialFuelLiters
            previousAvgConsumption = null
            _averageFuelConsumption.value = null
            savePersistedStateLocked(immediate = true)
        }
    }

    fun resetManualTripDistance() {
        loadPersistedStateIfNeeded()
        synchronized(tripFuelLock) {
            accumulatedManualTripDistanceKm = 0f
            _manualTripDistanceKm.value = 0f
            savePersistedStateLocked(immediate = true)
        }
    }

    fun updateTripDuration() {
        synchronized(tripFuelLock) {
            updateTripDurationLocked()
        }
    }

    private fun updateTripDurationLocked() {
        val durationSec = ((System.currentTimeMillis() - tripStartTimeMs) / 1000L).coerceAtLeast(0L)
        _localTripDurationSeconds.value = durationSec
    }

    fun isEngineRunning(): Boolean {
        val rpm = _engineRPM.value
            ?: vad.dashing.tbox.mbcan.UniversalCanRepository.engineRpmState.value
        if (rpm != null) {
            return rpm > 300f
        }
        val currentCons = _currentFuelConsumption.value
        if (currentCons != null && currentCons > 0f) {
            return true
        }
        return false
    }

    fun isVehicleStationary(): Boolean {
        val speed = _carSpeedAccurate.value
            ?: _carSpeed.value
            ?: vad.dashing.tbox.mbcan.UniversalCanRepository.carSpeedState.value
            ?: 0f
        return speed < 1.0f
    }

    private fun updateIdleFuelTracking() {
        synchronized(tripFuelLock) {
            updateIdleFuelTrackingLocked()
        }
    }

    private fun updateIdleFuelTrackingLocked() {
        val now = nowElapsedMs()
        val isRunning = isEngineRunning()
        val isStationary = isVehicleStationary()
        if (isRunning && isStationary) {
            if (lastIdleCheckMs > 0L) {
                val deltaMs = now - lastIdleCheckMs
                if (deltaMs in 20L..5000L) {
                    val currentCons = _currentFuelConsumption.value
                    val rateLitersPerHour = if (currentCons != null && currentCons in 0.3f..5.0f) {
                        currentCons
                    } else {
                        0.8f
                    }
                    val idleFuelDelta = (rateLitersPerHour / 3_600_000.0) * deltaMs
                    currentSegmentIdleFuelLiters += idleFuelDelta
                }
            }
            lastIdleCheckMs = now
        } else {
            lastIdleCheckMs = 0L
        }
    }

    private fun recalculateLocalAverageFuelConsumption() {
        loadPersistedStateIfNeeded()
        synchronized(tripFuelLock) {
            recalculateLocalAverageFuelConsumptionLocked()
        }
    }

    private fun recalculateLocalAverageFuelConsumptionLocked() {
        updateIdleFuelTrackingLocked()
        val currentPercent = getEffectiveFuelPercentage()
            ?: (_fuelLevelCalibratedLitersActual.value ?: _fuelLevelCalibratedLiters.value)?.let { (it / getTankCapacityLiters()) * 100f }
            ?: return
        val currentFuelLiters = _fuelLevelCalibratedLitersActual.value
            ?: _fuelLevelCalibratedLiters.value
            ?: (currentPercent * (getTankCapacityLiters() / 100f))

        var initFuel = initialFuelLiters
        if (initFuel == null) {
            initFuel = currentFuelLiters
            initialFuelLiters = initFuel
        } else {
            val refuelBaseline = lastKnownFuelLiters ?: initFuel
            if (currentFuelLiters > refuelBaseline + 5.0f) {
                // Refuel detected (including partial refuels): commit segment fuel and distance to overall totals
                val segUsed = (initFuel - refuelBaseline).coerceAtLeast(0f)
                overallAccumulatedFuelLiters += segUsed
                overallAccumulatedIdleFuelLiters += currentSegmentIdleFuelLiters
                currentSegmentIdleFuelLiters = 0.0
                overallAccumulatedDistanceKm += currentSegmentDistKm
                currentSegmentDistKm = 0f
                initFuel = currentFuelLiters
                initialFuelLiters = initFuel
            }
        }

        lastKnownFuelLiters = currentFuelLiters
        val segUsedLiters = (initFuel - currentFuelLiters).coerceAtLeast(0f)
        val combinedTotalDist = overallAccumulatedDistanceKm + currentSegmentDistKm
        val combinedTotalFuel = overallAccumulatedFuelLiters + segUsedLiters
        val combinedTotalIdleFuel = (overallAccumulatedIdleFuelLiters + currentSegmentIdleFuelLiters).toFloat()

        val isIgnoreIdle = AppContextHolder.appContextOrNull?.let {
            vad.dashing.tbox.ui.launcher.LauncherAppConfigStore.ignoreIdleFuel(it)
        } ?: false

        val effectiveFuel = if (isIgnoreIdle) {
            (combinedTotalFuel - combinedTotalIdleFuel).coerceAtLeast(0f)
        } else {
            combinedTotalFuel
        }

        val overallAvg = if (combinedTotalDist >= 0.1f && effectiveFuel > 0f) {
            (effectiveFuel / combinedTotalDist) * 100f
        } else {
            previousAvgConsumption
        }

        if (overallAvg != null && overallAvg > 0f && overallAvg < 50f) {
            previousAvgConsumption = overallAvg
            _averageFuelConsumption.value = overallAvg
            savePersistedStateLocked()
        }
    }

    fun onIgnoreIdleFuelSettingChanged() {
        recalculateLocalAverageFuelConsumption()
    }

    fun updateDistanceToNextMaintenance(newValue: UInt) {
        _distanceToNextMaintenance.setIfChanged(newValue)
    }

    fun updateDistanceToFuelEmpty(newValue: UInt) {
        _distanceToFuelEmpty.setIfChanged(newValue)
        vad.dashing.tbox.ui.launcher.LauncherVehicleAlertsRepository.refresh()
    }

    fun updateBreakingForce(newValue: UInt) {
        _breakingForce.setIfChanged(newValue)
    }

    fun updateFuelLevelPercentage(newValue: UInt) {
        _fuelLevelPercentage.setIfChanged(newValue)
        vad.dashing.tbox.ui.launcher.LauncherVehicleAlertsRepository.refresh()
    }

    fun updateFuelLevelPercentageFiltered(newValue: UInt) {
        _fuelLevelPercentageFiltered.setIfChanged(newValue)
        recalculateLocalAverageFuelConsumption()
        vad.dashing.tbox.ui.launcher.LauncherVehicleAlertsRepository.refresh()
    }

    fun updateFuelLevelCalibratedLiters(newValue: Float?) {
        _fuelLevelCalibratedLiters.setIfChanged(newValue)
        recalculateLocalAverageFuelConsumption()
    }

    fun updateFuelLevelCalibratedLitersActual(newValue: Float?) {
        _fuelLevelCalibratedLitersActual.setIfChanged(newValue)
        recalculateLocalAverageFuelConsumption()
    }

    fun updateFuelCalibrationConfidence(newValue: Float?) {
        _fuelCalibrationConfidence.setIfChanged(newValue)
    }

    fun updateCurrentFuelConsumption(newValue: Float?) {
        _currentFuelConsumption.setIfChanged(newValue)
        updateIdleFuelTracking()
    }

    fun updateAverageFuelConsumption(newValue: Float?) {
        _averageFuelConsumption.setIfChanged(newValue)
    }

    fun updateCruiseSetSpeed(newValue: UInt) {
        _cruiseSetSpeed.setIfChanged(newValue)
    }

    fun updateCruiseStatusByte(newValue: Int) {
        _cruiseStatusByte.setIfChanged(newValue)
    }

    fun updateCcsCruiseStatus(newValue: Int?) {
        _ccsCruiseStatus.setIfChanged(newValue)
    }

    fun updateWheelsSpeed(newValue: Wheels) {
        _wheelsSpeed.setIfChanged(newValue)
    }

    fun updateWheelsPressure(newValue: Wheels) {
        _wheelsPressure.setIfChanged(newValue)
    }

    fun updateWheelsTemperature(newValue: Wheels) {
        _wheelsTemperature.setIfChanged(newValue)
    }

    fun updateEngineRPM(newValue: Float) {
        _engineRPM.setIfChanged(newValue)
        updateIdleFuelTracking()
    }

    fun updateParam5(newValue: Float?) {
        _param5.setIfChanged(newValue)
    }

    fun updateParam1(newValue: Float) {
        _param1.setIfChanged(newValue)
    }

    fun updateParam2(newValue: Float) {
        _param2.setIfChanged(newValue)
    }

    fun updateParam3(newValue: Float) {
        _param3.setIfChanged(newValue)
    }

    fun updateParam4(newValue: Float) {
        _param4.setIfChanged(newValue)
    }

    fun updateSteerAngle(newValue: Float?) {
        val current = _steerAngle.value
        if (current == null || newValue == null || kotlin.math.abs(current - newValue) >= 0.5f) {
            _steerAngle.value = newValue
        }
    }

    fun updateSteerSpeed(newValue: Int) {
        _steerSpeed.setIfChanged(newValue)
    }

    fun updateClimateSetTemperature1(newValue: Float) {
        _climateSetTemperature1.setIfChanged(newValue)
    }

    fun updateClimateSetTemperature2(newValue: Float) {
        _climateSetTemperature2.setIfChanged(newValue)
    }

    fun updateEngineTemperature(newValue: Float) {
        _engineTemperature.setIfChanged(newValue)
    }

    fun updateGearBoxMode(newValue: String) {
        _gearBoxMode.setIfChanged(newValue)
    }

    fun updateGearBoxCurrentGear(newValue: Int) {
        _gearBoxCurrentGear.setIfChanged(newValue)
    }

    fun updateGearBoxChangeGear(newValue: Boolean) {
        _gearBoxChangeGear.setIfChanged(newValue)
    }

    fun updateGearBoxPreparedGear(newValue: Int) {
        _gearBoxPreparedGear.setIfChanged(newValue)
    }

    fun updateGearBoxOilTemperature(newValue: Int) {
        _gearBoxOilTemperature.setIfChanged(newValue)
    }

    fun updateGearBoxDriveMode(newValue: String) {
        _gearBoxDriveMode.setIfChanged(newValue)
    }

    fun updateGearBoxWork(newValue: String) {
        _gearBoxWork.setIfChanged(newValue)
    }

    fun gearboxTypeBySystemConfig(): GearboxType {
        return try {
            val pm = PropManager.getInstance()
            val c1 = pm.config ?: ""
            val c2 = pm.persistConfig ?: ""
            val c3 = pm.carType ?: ""
            val c4 = pm.getProp(MBConfigConstant.RO_MB_CAN_CONFIG)
            val c5 = pm.getProp(MBConfigConstant.PERSIST_MB_CAN_CONFIG)
            val combined = "$c1 $c2 $c3 $c4 $c5".uppercase(Locale.ROOT)

            if (combined.contains(MBConfigConstant.MB_X50_GEAR_BOX_TYPE_6MT) ||
                combined.contains(MBConfigConstant.MB_X50_GEAR_BOX_TYPE_5MT) ||
                combined.contains("6MT") ||
                combined.contains("5MT") ||
                combined.contains("1.5_6MT") ||
                combined.contains("MANUAL")
            ) {
                return GearboxType.MANUAL
            }
            if (combined.contains(MBConfigConstant.MB_X50_GEAR_BOX_TYPE_6DCT) ||
                combined.contains("6DCT") ||
                combined.contains("1.5_6DCT")
            ) {
                return GearboxType.DCT_6
            }
            GearboxType.DCT_7
        } catch (_: Throwable) {
            GearboxType.DCT_7
        }
    }

    fun is6DctBySystemConfig(): Boolean = gearboxTypeBySystemConfig() == GearboxType.DCT_6

    fun setGearboxType(type: GearboxType) {
        _gearboxType.setIfChanged(type)
        _is6DctGearbox.setIfChanged(type == GearboxType.DCT_6)
        _isManualGearbox.setIfChanged(type == GearboxType.MANUAL)
    }

    fun set6DctGearbox(is6Dct: Boolean) {
        setGearboxType(if (is6Dct) GearboxType.DCT_6 else GearboxType.DCT_7)
    }

    fun updateMaxGearSeen(newValue: Int) {
        _maxGearSeen.update { current -> if (newValue > current) newValue else current }
    }

    fun updateFrontLeftSeatMode(newValue: UInt) {
        _frontLeftSeatMode.setIfChanged(newValue)
    }

    fun updateFrontRightSeatMode(newValue: UInt) {
        _frontRightSeatMode.setIfChanged(newValue)
    }

    fun updateOutsideTemperature(newValue: Float?) {
        _outsideTemperature.setIfChanged(newValue)
    }

    fun applyOutsideTemperatureFromCan(
        decodedCelsius: Float,
        now: Long,
        nullDebounceMs: Long = InOutTemperatureNullDebounce.DEFAULT_NULL_DEBOUNCE_MS,
    ) {
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = _outsideTemperature.value,
            lastTimeNotNull = outsideTemperatureLastTimeNotNull,
            decodedCelsius = decodedCelsius,
            now = now,
            debounceMs = nullDebounceMs,
        )
        outsideTemperatureLastTimeNotNull = resolved.lastTimeNotNull
        _outsideTemperature.setIfChanged(resolved.value)
    }

    fun updateInsideTemperature(newValue: Float?) {
        _insideTemperature.setIfChanged(newValue)
    }

    fun updateRainSensorDetected(detected: Boolean) {
        _rainSensorDetected.setIfChanged(detected)
    }

    fun applyInsideTemperatureFromCan(
        decodedCelsius: Float,
        now: Long,
        nullDebounceMs: Long = InOutTemperatureNullDebounce.DEFAULT_NULL_DEBOUNCE_MS,
    ) {
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = _insideTemperature.value,
            lastTimeNotNull = insideTemperatureLastTimeNotNull,
            decodedCelsius = decodedCelsius,
            now = now,
            debounceMs = nullDebounceMs,
        )
        insideTemperatureLastTimeNotNull = resolved.lastTimeNotNull
        _insideTemperature.setIfChanged(resolved.value)
    }

    internal fun resetInOutTemperatureStateForTest() {
        outsideTemperatureLastTimeNotNull = null
        insideTemperatureLastTimeNotNull = null
        _outsideTemperature.value = null
        _insideTemperature.value = null
    }

    fun updateOutsideAirQuality(newValue: UInt?) {
        _outsideAirQuality.setIfChanged(newValue)
    }

    fun updateInsideAirQuality(newValue: UInt?) {
        _insideAirQuality.setIfChanged(newValue)
    }

    fun updateIsWindowsBlocked(newValue: Boolean) {
        _isWindowsBlocked.setIfChanged(newValue)
    }

    fun addMotorHoursTrip(newValue: Float) {
        _motorHoursTrip.update { current -> (current ?: 0f) + newValue }
    }

    fun hasCanFramesStructuredSubscribers(): Boolean {
        return _canFramesStructured.subscriptionCount.value > 0
    }

    fun addCanFrameStructured(canId: String, rawValue: ByteArray, maxFrames: Int = MAX_FRAMES_PER_ID) {
        if (_canFramesStructured.subscriptionCount.value <= 0) return
        val nowMs = nowElapsedMs()
        val frame = CanFrame(Date(), rawValue)
        var snapshotToPublish: Map<String, List<CanFrame>>? = null
        synchronized(canFramesLock) {
            val deque = canFramesBuffer.remove(canId) ?: ArrayDeque(maxFrames.coerceAtLeast(1))
            deque.addLast(frame)
            while (deque.size > maxFrames) {
                deque.removeFirst()
            }
            canFramesBuffer[canId] = deque
            while (canFramesBuffer.size > MAX_CAN_IDS) {
                val oldestKey = canFramesBuffer.keys.firstOrNull() ?: break
                canFramesBuffer.remove(oldestKey)
            }
            if (nowMs - lastCanFramesPublishMs >= CAN_FRAMES_PUBLISH_MIN_INTERVAL_MS) {
                lastCanFramesPublishMs = nowMs
                snapshotToPublish = LinkedHashMap<String, List<CanFrame>>(canFramesBuffer.size).apply {
                    for ((k, v) in canFramesBuffer) {
                        put(k, v.toList())
                    }
                }
            }
        }
        snapshotToPublish?.let { _canFramesStructured.value = it }
    }

    fun getFramesForId(canId: String): List<CanFrame> = synchronized(canFramesLock) {
        canFramesBuffer[canId]?.toList() ?: _canFramesStructured.value[canId] ?: emptyList()
    }

    // Получить последний фрейм для конкретного CAN ID
    fun getLastFrameForId(canId: String): CanFrame? = synchronized(canFramesLock) {
        canFramesBuffer[canId]?.lastOrNull() ?: _canFramesStructured.value[canId]?.lastOrNull()
    }

    // Получить все CAN ID
    fun getAllCanIds(): Set<String> = synchronized(canFramesLock) {
        if (canFramesBuffer.isNotEmpty()) canFramesBuffer.keys.toSet() else _canFramesStructured.value.keys
    }

    // Очистить все фреймы для конкретного CAN ID
    fun clearFramesForId(canId: String) {
        val snapshot = synchronized(canFramesLock) {
            canFramesBuffer.remove(canId)
            LinkedHashMap<String, List<CanFrame>>(canFramesBuffer.size).apply {
                for ((k, v) in canFramesBuffer) {
                    put(k, v.toList())
                }
            }
        }
        _canFramesStructured.value = snapshot
    }

    // Очистить все фреймы
    fun clearAllFrames() {
        synchronized(canFramesLock) {
            canFramesBuffer.clear()
            lastCanFramesPublishMs = 0L
        }
        _canFramesStructured.value = emptyMap()
    }

    // Метод для сброса всех данных (при переподключении)
    fun resetConnectionData() {
    }
}
