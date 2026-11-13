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
)

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

    private val _localTripDurationSeconds = MutableStateFlow<Long?>(null)
    val localTripDurationSeconds: StateFlow<Long?> = _localTripDurationSeconds.asStateFlow()

    @Volatile
    private var lastOdometer: UInt? = null
    @Volatile
    private var accumulatedTripDistanceKm: Float = 0f
    @Volatile
    private var initialFuelLiters: Float? = null
    @Volatile
    private var previousAvgConsumption: Float? = null
    @Volatile
    private var tripStartTimeMs: Long = System.currentTimeMillis()

    @Volatile
    private var overallAccumulatedDistanceKm: Float = 0f
    @Volatile
    private var overallAccumulatedFuelLiters: Float = 0f
    @Volatile
    private var currentSegmentDistKm: Float = 0f
    @Volatile
    private var lastKnownFuelLiters: Float? = null

    private const val PREFS_PERSISTENCE = "tbox_can_data_persistence"
    private const val KEY_AVG_FUEL = "saved_avg_fuel_consumption"
    private const val KEY_TRIP_DIST = "saved_local_trip_distance"
    private const val KEY_INIT_FUEL = "saved_initial_fuel_liters"
    private const val KEY_LAST_ODO = "saved_last_odometer"
    private const val KEY_TRIP_START_TIME = "saved_trip_start_time"
    private const val KEY_OVERALL_DIST = "saved_overall_distance_km"
    private const val KEY_OVERALL_FUEL = "saved_overall_fuel_liters"
    private const val KEY_SEGMENT_DIST = "saved_segment_distance_km"

    @Volatile
    private var isPersistedStateLoaded = false

    init {
        loadPersistedStateIfNeeded()
    }

    private fun loadPersistedStateIfNeeded() {
        if (isPersistedStateLoaded) return
        synchronized(this) {
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
                if (prefs.contains(KEY_SEGMENT_DIST)) {
                    currentSegmentDistKm = prefs.getFloat(KEY_SEGMENT_DIST, 0f)
                }
                val totalDist = overallAccumulatedDistanceKm + currentSegmentDistKm
                if (totalDist > 0f && overallAccumulatedFuelLiters > 0f) {
                    val calculated = (overallAccumulatedFuelLiters / totalDist) * 100f
                    if (calculated > 0f && calculated < 50f) {
                        _averageFuelConsumption.value = calculated
                        previousAvgConsumption = calculated
                    }
                } else if (previousAvgConsumption != null && previousAvgConsumption!! > 0f && overallAccumulatedDistanceKm == 0f) {
                    val baselineDist = 50f
                    overallAccumulatedDistanceKm = baselineDist
                    overallAccumulatedFuelLiters = (previousAvgConsumption!! / 100f) * baselineDist
                }
                updateTripDuration()
                isPersistedStateLoaded = true
            }
        }
    }

    private var lastSavePersistedStateMs: Long = 0L
    private const val SAVE_PERSISTED_STATE_MIN_INTERVAL_MS = 15_000L

    private fun savePersistedState(immediate: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
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
            initialFuelLiters?.let { editor.putFloat(KEY_INIT_FUEL, it) } ?: editor.remove(KEY_INIT_FUEL)
            lastOdometer?.let { editor.putLong(KEY_LAST_ODO, it.toLong()) } ?: editor.remove(KEY_LAST_ODO)
            editor.putLong(KEY_TRIP_START_TIME, tripStartTimeMs)
            editor.putFloat(KEY_OVERALL_DIST, overallAccumulatedDistanceKm)
            editor.putFloat(KEY_OVERALL_FUEL, overallAccumulatedFuelLiters)
            editor.putFloat(KEY_SEGMENT_DIST, currentSegmentDistKm)
            editor.apply()
        }
    }

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

    private val _is6DctGearbox = MutableStateFlow(is6DctBySystemConfig())
    val is6DctGearbox: StateFlow<Boolean> = _is6DctGearbox.asStateFlow()

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

    private val _canFramesStructured = MutableStateFlow<Map<String, List<CanFrame>>>(emptyMap())
    val canFramesStructured: StateFlow<Map<String, List<CanFrame>>> = _canFramesStructured.asStateFlow()

    private val _motorHoursTrip = MutableStateFlow<Float?>(null)
    val motorHoursTrip: StateFlow<Float?> = _motorHoursTrip.asStateFlow()

    private const val MAX_CAN_FRAMES = 5
    private const val MAX_CAN_IDS = 500
    private const val MAX_FRAMES_PER_ID = 10

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
    }

    fun updateCarSpeedAccurate(newValue: Float) {
        _carSpeedAccurate.setIfChanged(newValue)
    }

    fun updateOdometer(newValue: UInt) {
        loadPersistedStateIfNeeded()
        updateTripDuration()
        _odometer.setIfChanged(newValue)
        val prevOdo = lastOdometer
        if (prevOdo != null) {
            if (newValue >= prevOdo) {
                val deltaKm = (newValue - prevOdo).toFloat()
                if (deltaKm > 0f && deltaKm < 1000f) {
                    accumulatedTripDistanceKm += deltaKm
                    currentSegmentDistKm += deltaKm
                    _localTripDistanceKm.value = accumulatedTripDistanceKm
                }
            } else {
                lastOdometer = newValue
            }
        } else {
            _localTripDistanceKm.value = accumulatedTripDistanceKm
        }
        lastOdometer = newValue

        if (TripRepository.activeTrip.value == null) {
            TripRepository.setActiveTrip(true)
        }
        recalculateLocalAverageFuelConsumption()
        savePersistedState()
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
        accumulatedTripDistanceKm = 0f
        _localTripDistanceKm.value = 0f
        lastOdometer = _odometer.value
        val currentPercent = getEffectiveFuelPercentage()
        initialFuelLiters = currentPercent?.let { it * (getTankCapacityLiters() / 100f) }
        tripStartTimeMs = System.currentTimeMillis()
        updateTripDuration()
        savePersistedState(immediate = true)
    }

    fun resetOverallAverageFuelConsumption() {
        loadPersistedStateIfNeeded()
        overallAccumulatedDistanceKm = 0f
        overallAccumulatedFuelLiters = 0f
        currentSegmentDistKm = 0f
        val currentPercent = getEffectiveFuelPercentage()
        initialFuelLiters = currentPercent?.let { it * (getTankCapacityLiters() / 100f) }
        lastKnownFuelLiters = initialFuelLiters
        previousAvgConsumption = null
        _averageFuelConsumption.value = null
        savePersistedState(immediate = true)
    }

    fun updateTripDuration() {
        val durationSec = ((System.currentTimeMillis() - tripStartTimeMs) / 1000L).coerceAtLeast(0L)
        _localTripDurationSeconds.value = durationSec
    }

    private fun recalculateLocalAverageFuelConsumption() {
        loadPersistedStateIfNeeded()
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
        } else if (currentFuelLiters > initFuel + 5.0f) {
            // Refuel detected: commit segment fuel and distance to overall totals
            val segUsed = (initFuel - (lastKnownFuelLiters ?: initFuel)).coerceAtLeast(0f)
            overallAccumulatedFuelLiters += segUsed
            overallAccumulatedDistanceKm += currentSegmentDistKm
            currentSegmentDistKm = 0f
            initFuel = currentFuelLiters
            initialFuelLiters = initFuel
        }

        lastKnownFuelLiters = currentFuelLiters
        val segUsedLiters = (initFuel - currentFuelLiters).coerceAtLeast(0f)
        val combinedTotalDist = overallAccumulatedDistanceKm + currentSegmentDistKm
        val combinedTotalFuel = overallAccumulatedFuelLiters + segUsedLiters

        val overallAvg = if (combinedTotalDist >= 0.1f && combinedTotalFuel > 0f) {
            (combinedTotalFuel / combinedTotalDist) * 100f
        } else {
            previousAvgConsumption
        }

        if (overallAvg != null && overallAvg > 0f && overallAvg < 50f) {
            previousAvgConsumption = overallAvg
            _averageFuelConsumption.value = overallAvg
            savePersistedState()
        }
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
    }

    fun updateAverageFuelConsumption(newValue: Float?) {
        _averageFuelConsumption.setIfChanged(newValue)
    }

    fun updateCruiseSetSpeed(newValue: UInt) {
        _cruiseSetSpeed.setIfChanged(newValue)
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

    fun is6DctBySystemConfig(): Boolean {
        return try {
            val pm = PropManager.getInstance()
            val c1 = pm.config ?: ""
            val c2 = pm.persistConfig ?: ""
            val c3 = pm.carType ?: ""
            val c4 = pm.getProp(MBConfigConstant.RO_MB_CAN_CONFIG)
            val c5 = pm.getProp(MBConfigConstant.PERSIST_MB_CAN_CONFIG)
            val combined = "$c1 $c2 $c3 $c4 $c5".uppercase(Locale.ROOT)

            if (combined.contains(MBConfigConstant.MB_X50_GEAR_BOX_TYPE_7DCT)) {
                return false
            }
            combined.contains(MBConfigConstant.MB_X50_GEAR_BOX_TYPE_6DCT) ||
                combined.contains("6DCT") ||
                combined.contains("1.5_6DCT")
        } catch (_: Throwable) {
            false
        }
    }

    fun set6DctGearbox(is6Dct: Boolean) {
        _is6DctGearbox.setIfChanged(is6Dct)
    }

    fun updateMaxGearSeen(newValue: Int) {
        if (newValue > _maxGearSeen.value) {
            _maxGearSeen.value = newValue
        }
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
        _motorHoursTrip.value = (_motorHoursTrip.value ?: 0f) + newValue
    }

    fun hasCanFramesStructuredSubscribers(): Boolean {
        return _canFramesStructured.subscriptionCount.value > 0 || DclLogRecorder.isLoggingActive()
    }

    fun addCanFrameStructured(canId: String, rawValue: ByteArray, maxFrames: Int = MAX_FRAMES_PER_ID) {
        if (!hasCanFramesStructuredSubscribers()) return
        val date = Date()
        val frame = CanFrame(date, rawValue)

        _canFramesStructured.update { currentMap ->
            // Use insertion-ordered map so we can evict the oldest CAN IDs.
            val limitedMap = LinkedHashMap(currentMap)
            val currentFrames = limitedMap.remove(canId) ?: emptyList()
            val updatedFrames = (currentFrames + frame).takeLast(maxFrames)
            limitedMap[canId] = updatedFrames

            while (limitedMap.size > MAX_CAN_IDS) {
                val oldestCanId = limitedMap.keys.firstOrNull() ?: break
                limitedMap.remove(oldestCanId)
            }
            limitedMap
        }
    }

    fun getFramesForId(canId: String): List<CanFrame> {
        return _canFramesStructured.value[canId] ?: emptyList()
    }

    // Получить последний фрейм для конкретного CAN ID
    fun getLastFrameForId(canId: String): CanFrame? {
        return _canFramesStructured.value[canId]?.lastOrNull()
    }

    // Получить все CAN ID
    fun getAllCanIds(): Set<String> {
        return _canFramesStructured.value.keys
    }

    // Очистить все фреймы для конкретного CAN ID
    fun clearFramesForId(canId: String) {
        _canFramesStructured.update { currentMap ->
            currentMap - canId
        }
    }

    // Очистить все фреймы
    fun clearAllFrames() {
        _canFramesStructured.value = emptyMap()
    }

    // Метод для сброса всех данных (при переподключении)
    fun resetConnectionData() {
    }
}
