package vad.dashing.tbox.mbcan

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.DclLogRecorder
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.SettingsManager
import java.util.concurrent.ConcurrentHashMap

/**
 * One entry point for car-control/CAN behavior across HU platforms.
 *
 * Android 9 mode delegates to [MbCanRepository].
 * Android 10 mode delegates to [Android10VhalRepository] (VHAL via CarPropertyManager reflection).
 */
@OptIn(ExperimentalCoroutinesApi::class)
object UniversalCanRepository {
    private const val AUTO_BIND_ATTEMPTS_PER_MODE = 3
    private const val AUTO_BIND_ATTEMPT_TIMEOUT_MS = 3_500L
    private const val AUTO_BIND_ATTEMPT_PAUSE_MS = 1_200L
    private const val CLEAR_SOURCE_DEBOUNCE_MS = 3 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Application-level canonical scope for CAN bindings (independent of Activity lifecycles). */
    val appScope: CoroutineScope
        get() = scope

    @Volatile
    private var boundScope: CoroutineScope? = null
    private val modeSwitchMutex = Mutex()
    private val sourcesLock = Any()
    private val canonicalSourceWidgetKeys = mutableMapOf<String, Set<String>>()
    private val canonicalSourceSignals = mutableMapOf<String, Set<MbCanSignal>>()
    private val pendingCanonicalClearJobs = ConcurrentHashMap<String, Job>()

    private fun nowElapsedMs(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    private fun currentBackendAvailability(): MbCanAvailability =
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.availability.value
        } else {
            Android10VhalRepository.availability.value
        }

    private val _mode = MutableStateFlow(HeadUnitCanMode.Android9MbCan)
    val mode: StateFlow<HeadUnitCanMode> = _mode.asStateFlow()

    val availability: StateFlow<MbCanAvailability> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.availability
            } else {
                Android10VhalRepository.availability
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanAvailability.Unknown)

    val steeringWheelHeatState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.steeringWheelHeatState
            } else {
                Android10VhalRepository.steeringWheelHeatState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val wiperMaintenanceState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.wiperMaintenanceState
            } else {
                Android10VhalRepository.wiperMaintenanceState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val parkingRadarState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.parkingRadarState
            } else {
                Android10VhalRepository.parkingRadarState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val frontWindscreenHeatState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.frontWindscreenHeatState
            } else {
                Android10VhalRepository.frontWindscreenHeatState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val tjaIcaState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.tjaIcaState
            } else {
                Android10VhalRepository.tjaIcaState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val lasModeRaw: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.lasModeRaw
            } else {
                Android10VhalRepository.lasModeRaw
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val bcmLowBeamState: StateFlow<Boolean?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.bcmLowBeamState
            } else {
                Android10VhalRepository.bcmLowBeamState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val bcmHighBeamState: StateFlow<Boolean?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.bcmHighBeamState
            } else {
                Android10VhalRepository.bcmHighBeamState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val bcmWiperStatusState: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.bcmWiperStatusState
            } else {
                Android10VhalRepository.bcmWiperStatusState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val hvacDefrosterState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacDefrosterState
            } else {
                Android10VhalRepository.hvacDefrosterState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val hvacAirRecirculationState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAirRecirculationState
            } else {
                Android10VhalRepository.hvacAirRecirculationState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val hvacAcPowerState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAcPowerState
            } else {
                Android10VhalRepository.hvacAcPowerState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val hvacAutoState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacAutoState
            } else {
                Android10VhalRepository.hvacAutoState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val hvacSyncState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacSyncState
            } else {
                Android10VhalRepository.hvacSyncState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val hvacPm25State: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacPm25State
            } else {
                Android10VhalRepository.hvacPm25State
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val hvacDefrosterFrontState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.hvacDefrosterFrontState
            } else {
                Android10VhalRepository.hvacDefrosterFrontState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    private val _hvacFanDirectionRaw = MutableStateFlow<Int?>(null)
    val hvacFanDirectionRawState: StateFlow<Int?> = _hvacFanDirectionRaw.asStateFlow()

    fun updateHvacFanDirectionRaw(raw: Int?) {
        _hvacFanDirectionRaw.value = raw
    }

    private val _hvacFanSpeedRaw = MutableStateFlow<Int>(1)
    val hvacFanSpeedRawState: StateFlow<Int> = _hvacFanSpeedRaw.asStateFlow()

    fun updateHvacFanSpeedRaw(raw: Int) {
        _hvacFanSpeedRaw.value = raw.coerceIn(0, 7)
    }

    val frontLeftSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.frontLeftSeatModeState
            } else {
                Android10VhalRepository.frontLeftSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanSeatModeState.Unknown)

    val frontRightSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.frontRightSeatModeState
            } else {
                Android10VhalRepository.frontRightSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanSeatModeState.Unknown)

    val rearLeftSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.rearLeftSeatModeState
            } else {
                Android10VhalRepository.rearLeftSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanSeatModeState.Unknown)

    val rearRightSeatModeState: StateFlow<MbCanSeatModeState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.rearRightSeatModeState
            } else {
                Android10VhalRepository.rearRightSeatModeState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanSeatModeState.Unknown)

    val audioVolumeState: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.audioVolumeState
            } else {
                Android10VhalRepository.audioVolumeState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val audioVolumeSpeedState: StateFlow<MbCanBinaryState> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.audioVolumeSpeedState
            } else {
                Android10VhalRepository.audioVolumeSpeedState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), MbCanBinaryState.Unknown)

    val audioVolumeSpeedModeState: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.audioVolumeSpeedModeState
            } else {
                Android10VhalRepository.audioVolumeSpeedModeState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val carSettingsEpsMode: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSettingsEpsMode
            } else {
                Android10VhalRepository.carSettingsEpsMode
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val carSettingsDriveMode: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSettingsDriveMode
            } else {
                Android10VhalRepository.carSettingsDriveMode
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val carSettingsDriveMode6dctWet: StateFlow<Int?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSettingsDriveMode6dctWet
            } else {
                Android10VhalRepository.carSettingsDriveMode6dctWet
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val engineRpmState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.engineRpmState
            } else {
                Android10VhalRepository.engineRpmState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val engineTemperatureState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.engineTemperatureState
            } else {
                Android10VhalRepository.engineTemperatureState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    val carSpeedState: StateFlow<Float?> = mode
        .flatMapLatest { activeMode ->
            if (activeMode == HeadUnitCanMode.Android9MbCan) {
                MbCanRepository.carSpeedState
            } else {
                Android10VhalRepository.carSpeedState
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    suspend fun setMode(mode: HeadUnitCanMode) {
        modeSwitchMutex.withLock {
            setModeLocked(mode, rebindIfBound = true)
        }
    }

    suspend fun bind(scope: CoroutineScope = this.scope) {
        modeSwitchMutex.withLock {
            bindLocked(this.scope)
        }
    }

    suspend fun unbind() {
        modeSwitchMutex.withLock {
            unbindLocked()
        }
    }

    suspend fun warmUpAvailabilityForUi() {
        modeSwitchMutex.withLock {
            warmUpAvailabilityForUiLocked()
        }
    }

    suspend fun setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>) {
        pendingCanonicalClearJobs.remove(sourceId)?.cancel()
        synchronized(sourcesLock) {
            canonicalSourceSignals.remove(sourceId)
            if (widgetKeys.isEmpty()) {
                canonicalSourceWidgetKeys.remove(sourceId)
            } else {
                canonicalSourceWidgetKeys[sourceId] = widgetKeys.toSet()
            }
        }
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.setSourceWidgetKeys(sourceId, widgetKeys)
        } else {
            Android10VhalRepository.setSourceWidgetKeys(sourceId, widgetKeys)
        }
    }

    suspend fun setSourceSignals(sourceId: String, signals: Set<MbCanSignal>) {
        pendingCanonicalClearJobs.remove(sourceId)?.cancel()
        val changed = synchronized(sourcesLock) {
            canonicalSourceWidgetKeys.remove(sourceId)
            val prev = canonicalSourceSignals[sourceId]
            if (signals.isEmpty()) {
                if (prev != null) {
                    canonicalSourceSignals.remove(sourceId)
                    true
                } else false
            } else {
                if (prev != signals) {
                    canonicalSourceSignals[sourceId] = signals.toSet()
                    true
                } else false
            }
        }
        if (!changed) return

        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.setSourceSignals(sourceId, signals)
        } else {
            Android10VhalRepository.setSourceSignals(sourceId, signals)
        }
    }

    fun enqueueClearSource(sourceId: String) {
        pendingCanonicalClearJobs.remove(sourceId)?.cancel()
        val clearJob = scope.launch {
            delay(CLEAR_SOURCE_DEBOUNCE_MS)
            if (pendingCanonicalClearJobs.remove(sourceId, coroutineContext.job)) {
                synchronized(sourcesLock) {
                    canonicalSourceWidgetKeys.remove(sourceId)
                    canonicalSourceSignals.remove(sourceId)
                }
            }
        }
        pendingCanonicalClearJobs[sourceId] = clearJob
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.enqueueClearSource(sourceId)
        } else {
            Android10VhalRepository.enqueueClearSource(sourceId)
        }
    }

    fun widgetConfigsNeedMbCan(dataKeys: Iterable<String>): Boolean {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.widgetConfigsNeedMbCan(dataKeys)
        } else {
            Android10VhalRepository.widgetConfigsNeedMbCan(dataKeys)
        }
    }

    fun launchExecute(command: MbCanCommand) {
        scope.launch {
            execute(command)
        }
    }

    suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        if (DclLogRecorder.isLoggingActive()) {
            DclLogRecorder.log("CanCommand", "execute command=$command mode=${_mode.value}")
        }
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.execute(command)
        } else {
            Android10VhalRepository.execute(command)
        }
    }

    suspend fun setAudioVolume(value: Int): MbCanCommandResult {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.setAudioVolume(value)
        } else {
            Android10VhalRepository.setAudioVolume(value)
        }
    }

    fun rememberAudioVolumeLastNonZeroInSession(value: Int) {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.rememberAudioVolumeLastNonZeroInSession(value)
        } else {
            Android10VhalRepository.rememberAudioVolumeLastNonZeroInSession(value)
        }
    }

    fun audioVolumeRestoreCandidate(defaultValue: Int = 10): Int {
        return if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.audioVolumeRestoreCandidate(defaultValue)
        } else {
            Android10VhalRepository.audioVolumeRestoreCandidate(defaultValue)
        }
    }

    @Volatile
    private var startupAutoBindStarted = false

    fun triggerStartupCanAutoBind(
        settingsManager: SettingsManager,
        scope: CoroutineScope = this.scope,
    ) {
        synchronized(this) {
            if (startupAutoBindStarted) return
            startupAutoBindStarted = true
        }
        val canonicalScope = this.scope
        canonicalScope.launch {
            runCatching {
                autoResolveModeOnStartup(settingsManager, canonicalScope)
            }.onFailure {
                Log.w("UniversalCanRepository", "CAN auto-bind failed", it)
            }
        }
    }

    suspend fun autoResolveModeOnStartup(
        settingsManager: SettingsManager,
        scope: CoroutineScope = this.scope,
    ) {
        val canonicalScope = this.scope
        modeSwitchMutex.withLock {
            if (!settingsManager.canAutoBindEnabledFlow.first()) {
                MbCanDiagnostics.log("INFO", "AUTO_CAN startup skipped: disabled")
                return
            }
            if (settingsManager.canAutoBindLockedFlow.first()) {
                MbCanDiagnostics.log("INFO", "AUTO_CAN startup skipped: locked")
                return
            }
            val primaryMode = settingsManager.headUnitCanModeFlow.first()
            val alternativeMode = primaryMode.otherMode()
            settingsManager.saveCanAutoBindLastPrimaryMode(primaryMode)

            // If CAN is already bound and Available on primaryMode, avoid disruptive unbind/rebind
            if (_mode.value == primaryMode && boundScope != null && currentBackendAvailability() is MbCanAvailability.Available) {
                MbCanDiagnostics.log(
                    "INFO",
                    "AUTO_CAN startup skipped: already bound and available on ${primaryMode.storageValue}"
                )
                settingsManager.saveCanAutoBindLastResult("already_available:${primaryMode.storageValue}")
                return
            }

            MbCanDiagnostics.log(
                "INFO",
                "AUTO_CAN startup begin primary=${primaryMode.storageValue} alternative=${alternativeMode.storageValue}"
            )

            val primaryResult = bindModeWithRetries(
                mode = primaryMode,
                scope = canonicalScope,
                attemptLabel = "primary"
            )
            if (primaryResult.success) {
                settingsManager.saveCanAutoBindLastResult(
                    "primary_ok:${primaryMode.storageValue}:attempt=${primaryResult.attempt}"
                )
                MbCanDiagnostics.log(
                    "INFO",
                    "AUTO_CAN primary success mode=${primaryMode.storageValue} attempt=${primaryResult.attempt}"
                )
                return
            }
            MbCanDiagnostics.log(
                "WARN",
                "AUTO_CAN primary failed mode=${primaryMode.storageValue} reason=${primaryResult.reason}"
            )

            setModeLocked(alternativeMode, rebindIfBound = false)
            settingsManager.saveHeadUnitCanMode(alternativeMode)
            val alternativeResult = bindModeWithRetries(
                mode = alternativeMode,
                scope = canonicalScope,
                attemptLabel = "alternative"
            )
            if (alternativeResult.success) {
                settingsManager.saveCanAutoBindLastResult(
                    "alternative_ok:${alternativeMode.storageValue}:attempt=${alternativeResult.attempt}"
                )
                MbCanDiagnostics.log(
                    "INFO",
                    "AUTO_CAN alternative success mode=${alternativeMode.storageValue} attempt=${alternativeResult.attempt}"
                )
                return
            }
            MbCanDiagnostics.log(
                "WARN",
                "AUTO_CAN alternative failed mode=${alternativeMode.storageValue} reason=${alternativeResult.reason}"
            )

            setModeLocked(primaryMode, rebindIfBound = false)
            settingsManager.saveHeadUnitCanMode(primaryMode)
            settingsManager.saveCanAutoBindLocked(true)
            settingsManager.saveCanAutoBindLastResult(
                "locked_after_fail:${primaryMode.storageValue}|${alternativeMode.storageValue}"
            )
            bindLocked(canonicalScope)
            MbCanDiagnostics.log(
                "WARN",
                "AUTO_CAN locked after failed retries; reverted to ${primaryMode.storageValue}"
            )
        }
    }

    private suspend fun bindModeWithRetries(
        mode: HeadUnitCanMode,
        scope: CoroutineScope,
        attemptLabel: String,
    ): AutoBindAttemptResult {
        repeat(AUTO_BIND_ATTEMPTS_PER_MODE) { index ->
            val attempt = index + 1
            setModeLocked(mode, rebindIfBound = false)
            warmUpAvailabilityForUiLocked()
            unbindLocked()
            bindLocked(scope)
            val attemptResult = waitForAvailability(
                timeoutMs = AUTO_BIND_ATTEMPT_TIMEOUT_MS,
                onTimeoutProbe = { warmUpAvailabilityForUiLocked() }
            )
            MbCanDiagnostics.log(
                "INFO",
                "AUTO_CAN $attemptLabel attempt=$attempt/${AUTO_BIND_ATTEMPTS_PER_MODE} " +
                    "mode=${mode.storageValue} result=${attemptResult.summary}"
            )
            if (attemptResult.success) {
                return AutoBindAttemptResult(success = true, attempt = attempt, reason = null)
            }
            if (attempt < AUTO_BIND_ATTEMPTS_PER_MODE) {
                delay(AUTO_BIND_ATTEMPT_PAUSE_MS)
            }
        }
        return AutoBindAttemptResult(
            success = false,
            attempt = AUTO_BIND_ATTEMPTS_PER_MODE,
            reason = (currentBackendAvailability() as? MbCanAvailability.Unavailable)?.reason
                ?: "timeout_unknown"
        )
    }

    private suspend fun waitForAvailability(
        timeoutMs: Long,
        onTimeoutProbe: suspend () -> Unit,
    ): AvailabilityAttemptResult {
        val startedAt = nowElapsedMs()
        while ((nowElapsedMs() - startedAt) < timeoutMs) {
            when (val current = currentBackendAvailability()) {
                MbCanAvailability.Available -> return AvailabilityAttemptResult(
                    success = true,
                    summary = "available"
                )
                is MbCanAvailability.Unavailable -> return AvailabilityAttemptResult(
                    success = false,
                    summary = "unavailable:${current.reason}"
                )
                MbCanAvailability.Unknown -> {
                    // Continue polling for a definitive backend state within timeout window.
                }
            }
            delay(120L)
        }
        onTimeoutProbe()
        return when (val current = currentBackendAvailability()) {
            MbCanAvailability.Available -> AvailabilityAttemptResult(
                success = true,
                summary = "available_after_timeout_probe"
            )
            is MbCanAvailability.Unavailable -> AvailabilityAttemptResult(
                success = false,
                summary = "unavailable_reason:${current.reason}"
            )
            MbCanAvailability.Unknown -> AvailabilityAttemptResult(
                success = false,
                summary = "timeout_unknown"
            )
        }
    }

    private fun HeadUnitCanMode.otherMode(): HeadUnitCanMode {
        return if (this == HeadUnitCanMode.Android9MbCan) {
            HeadUnitCanMode.Android10Vhal
        } else {
            HeadUnitCanMode.Android9MbCan
        }
    }

    private data class AvailabilityAttemptResult(
        val success: Boolean,
        val summary: String,
    )

    private data class AutoBindAttemptResult(
        val success: Boolean,
        val attempt: Int,
        val reason: String?,
    )

    internal fun normalizeWidgetDataKey(raw: String): String = raw.trim()

    internal fun isMeaningfulWidgetDataKey(raw: String): Boolean {
        val key = normalizeWidgetDataKey(raw)
        return key.isNotBlank() && key != "null"
    }

    private suspend fun replayCanonicalSourcesToBackend(targetMode: HeadUnitCanMode) {
        val widgetSnapshot: Map<String, Set<String>>
        val signalSnapshot: Map<String, Set<MbCanSignal>>
        synchronized(sourcesLock) {
            widgetSnapshot = canonicalSourceWidgetKeys.toMap()
            signalSnapshot = canonicalSourceSignals.toMap()
        }
        if (targetMode == HeadUnitCanMode.Android9MbCan) {
            widgetSnapshot.forEach { (sourceId, keys) ->
                MbCanRepository.setSourceWidgetKeys(sourceId, keys)
            }
            signalSnapshot.forEach { (sourceId, signals) ->
                MbCanRepository.setSourceSignals(sourceId, signals)
            }
        } else {
            widgetSnapshot.forEach { (sourceId, keys) ->
                Android10VhalRepository.setSourceWidgetKeys(sourceId, keys)
            }
            signalSnapshot.forEach { (sourceId, signals) ->
                Android10VhalRepository.setSourceSignals(sourceId, signals)
            }
        }
    }

    private suspend fun setModeLocked(mode: HeadUnitCanMode, rebindIfBound: Boolean) {
        if (_mode.value == mode) return
        _mode.value = mode
        replayCanonicalSourcesToBackend(mode)
        if (!rebindIfBound) return
        val scopeToRebind = boundScope ?: return
        when (mode) {
            HeadUnitCanMode.Android9MbCan -> {
                Android10VhalRepository.unbind()
                MbCanRepository.bind(scopeToRebind)
            }
            HeadUnitCanMode.Android10Vhal -> {
                MbCanRepository.unbind()
                Android10VhalRepository.bind(scopeToRebind)
            }
        }
    }

    private suspend fun bindLocked(scope: CoroutineScope) {
        boundScope = scope
        replayCanonicalSourcesToBackend(_mode.value)
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.bind(scope)
        } else {
            Android10VhalRepository.bind(scope)
        }
    }

    private suspend fun unbindLocked() {
        boundScope = null
        MbCanRepository.unbind()
        Android10VhalRepository.unbind()
    }

    private suspend fun warmUpAvailabilityForUiLocked() {
        if (_mode.value == HeadUnitCanMode.Android9MbCan) {
            MbCanRepository.warmUpAvailabilityForUi()
        } else {
            Android10VhalRepository.warmUpAvailabilityForUi()
        }
    }
}
