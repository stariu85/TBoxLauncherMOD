package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.CanDataRepository

enum class AutoWeatherType {
    None,
    Rain,
    Snow,
}

object LauncherAutoWeatherManager {
    const val RAIN_DETECT_WINDOW_MS = 30_000L // 30 seconds sliding window
    const val RAIN_DETECT_COUNT_THRESHOLD = 3 // 3 or more detections within 30s
    const val RAIN_CLEAR_THRESHOLD_MS = 120_000L // 120 seconds
    const val TEMP_RAIN_SNOW_THRESHOLD = 3.0f // < 3°C -> snow, >= 3°C -> rain
    const val CONTINUOUS_RAIN_EVENT_INTERVAL_MS = 5_000L // Interval to register repeated event during steady rain

    private const val ACTIVE_TICK_MS = 500L
    private const val IDLE_ENABLED_TICK_MS = 2_000L
    private const val DISABLED_TICK_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + LauncherCpuAffinity.lightPollersDispatcher)
    private val stateLock = Any()
    private val wakeChannel = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    private val _autoWeatherType = MutableStateFlow(AutoWeatherType.None)
    val autoWeatherType: StateFlow<AutoWeatherType> = _autoWeatherType.asStateFlow()

    private val detectionTimestamps = mutableListOf<Long>()
    private var lastRainDetected = false
    private var lastEventTime: Long? = null
    private var rainClearedStartTime: Long? = null
    private var isWeatherActive = false

    @Synchronized
    fun start() {
        if (job != null) return
        scope.launch {
            CanDataRepository.rainSensorDetected.drop(1).collect {
                wakeChannel.trySend(Unit)
            }
        }
        scope.launch {
            CanDataRepository.outsideTemperature.drop(1).collect {
                wakeChannel.trySend(Unit)
            }
        }
        job = scope.launch {
            while (isActive) {
                val nextDelayMs = stepAndComputeNextDelay()
                withTimeoutOrNull(nextDelayMs) {
                    wakeChannel.receive()
                }
            }
        }
    }

    private fun stepAndComputeNextDelay(): Long {
        val now = SystemClock.elapsedRealtime()
        val rainDetected = if (LauncherDevVehicleState.simulateEnabled) {
            LauncherDevVehicleState.rainDetected
        } else {
            CanDataRepository.rainSensorDetected.value
        }
        val outsideTemp = CanDataRepository.outsideTemperature.value
        val autoEnabled = LauncherThemeState.autoWeatherEnabled
        val manualActive = LauncherThemeState.winterThemeEnabled ||
            LauncherThemeState.rainThemeEnabled ||
            LauncherThemeState.winterThemeTempBelowZeroEnabled

        synchronized(stateLock) {
            updateAtTime(
                now = now,
                rainDetected = rainDetected,
                outsideTemp = outsideTemp,
                autoEnabled = autoEnabled,
                manualActive = manualActive,
            )
            val enabled = autoEnabled && !manualActive
            return when {
                !enabled -> DISABLED_TICK_MS
                rainDetected || isWeatherActive || detectionTimestamps.isNotEmpty() || LauncherDevVehicleState.simulateEnabled ->
                    ACTIVE_TICK_MS
                else -> IDLE_ENABLED_TICK_MS
            }
        }
    }

    internal fun updateAtTime(
        now: Long,
        rainDetected: Boolean,
        outsideTemp: Float?,
        autoEnabled: Boolean,
        manualActive: Boolean,
    ) {
        synchronized(stateLock) {
            val enabled = autoEnabled && !manualActive
            if (!enabled) {
                detectionTimestamps.clear()
                lastRainDetected = false
                lastEventTime = null
                rainClearedStartTime = null
                isWeatherActive = false
                if (_autoWeatherType.value != AutoWeatherType.None) {
                    _autoWeatherType.value = AutoWeatherType.None
                }
                return
            }

            // Check for rain detection event
            if (rainDetected) {
                val isNewPulse = !lastRainDetected
                val isContinuousInterval = lastEventTime != null && (now - lastEventTime!! >= CONTINUOUS_RAIN_EVENT_INTERVAL_MS)
                if (isNewPulse || isContinuousInterval || lastEventTime == null) {
                    detectionTimestamps.add(now)
                    lastEventTime = now
                }
                // Reset clear timer whenever rain is present
                rainClearedStartTime = null
            } else {
                // Rain is not currently detected
                lastEventTime = null
                if (isWeatherActive) {
                    if (rainClearedStartTime == null) {
                        rainClearedStartTime = now
                    } else if (now - rainClearedStartTime!! >= RAIN_CLEAR_THRESHOLD_MS) {
                        // No rain for >= 120 seconds: turn off
                        isWeatherActive = false
                        rainClearedStartTime = null
                        detectionTimestamps.clear()
                    }
                }
            }
            lastRainDetected = rainDetected

            // Prune events outside the 30-second window
            detectionTimestamps.removeAll { now - it > RAIN_DETECT_WINDOW_MS }

            // If not active, check if we reached 3 detections within 30 seconds
            if (!isWeatherActive) {
                if (detectionTimestamps.size >= RAIN_DETECT_COUNT_THRESHOLD) {
                    isWeatherActive = true
                }
            }

            if (isWeatherActive) {
                val targetType = if (outsideTemp != null && outsideTemp < TEMP_RAIN_SNOW_THRESHOLD) {
                    AutoWeatherType.Snow
                } else {
                    AutoWeatherType.Rain
                }
                if (_autoWeatherType.value != targetType) {
                    _autoWeatherType.value = targetType
                }
            } else {
                if (_autoWeatherType.value != AutoWeatherType.None) {
                    _autoWeatherType.value = AutoWeatherType.None
                }
            }
        }
    }

    internal fun resetForTesting() {
        synchronized(stateLock) {
            detectionTimestamps.clear()
            lastRainDetected = false
            lastEventTime = null
            rainClearedStartTime = null
            isWeatherActive = false
            _autoWeatherType.value = AutoWeatherType.None
        }
    }
}
