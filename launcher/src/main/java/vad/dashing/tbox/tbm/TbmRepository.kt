package vad.dashing.tbox.tbm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.SettingsManager

sealed interface TbmConnectionState {
    data object Disconnected : TbmConnectionState
    data object Connecting : TbmConnectionState
    data class Connected(val health: TbmHealthResponse) : TbmConnectionState
    data object PairingRequired : TbmConnectionState
    data class Error(val message: String) : TbmConnectionState
}

object TbmRepositoryProvider {
    @Volatile
    private var instance: TbmRepository? = null

    fun get(context: android.content.Context): TbmRepository {
        return instance ?: synchronized(this) {
            instance ?: TbmRepository(SettingsManager(context.applicationContext)).also { instance = it }
        }
    }
}

class TbmRepository(
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var currentPort: Int = 8765

    init {
        scope.launch {
            settingsManager.tbmApiPortFlow.collect { port ->
                currentPort = port
            }
        }
    }

    private val apiClient: TbmApiClient = TbmApiClient(
        portProvider = { currentPort },
    )
    private val _connectionState = MutableStateFlow<TbmConnectionState>(TbmConnectionState.Disconnected)
    val connectionState: StateFlow<TbmConnectionState> = _connectionState.asStateFlow()

    private val _automations = MutableStateFlow<List<TbmAutomationItem>>(emptyList())
    val automations: StateFlow<List<TbmAutomationItem>> = _automations.asStateFlow()

    private val _signals = MutableStateFlow<Map<String, Any?>>(emptyMap())
    val signals: StateFlow<Map<String, Any?>> = _signals.asStateFlow()

    // Strongly-typed flows for key telemetry signals from TBM API
    private val _fuelLiters = MutableStateFlow<Float?>(null)
    val fuelLiters: StateFlow<Float?> = _fuelLiters.asStateFlow()

    private val _fuelPercentFiltered = MutableStateFlow<Float?>(null)
    val fuelPercentFiltered: StateFlow<Float?> = _fuelPercentFiltered.asStateFlow()

    private val _activeTripDistanceKm = MutableStateFlow<Float?>(null)
    val activeTripDistanceKm: StateFlow<Float?> = _activeTripDistanceKm.asStateFlow()

    private val _activeTripAvgFuelL100km = MutableStateFlow<Float?>(null)
    val activeTripAvgFuelL100km: StateFlow<Float?> = _activeTripAvgFuelL100km.asStateFlow()

    private val _activeTripDurationSeconds = MutableStateFlow<Long?>(null)
    val activeTripDurationSeconds: StateFlow<Long?> = _activeTripDurationSeconds.asStateFlow()

    private val _motorHours = MutableStateFlow<Float?>(null)
    val motorHours: StateFlow<Float?> = _motorHours.asStateFlow()

    private val _gearboxOilTemperature = MutableStateFlow<Float?>(null)
    val gearboxOilTemperature: StateFlow<Float?> = _gearboxOilTemperature.asStateFlow()

    private val _modemSignalLevel = MutableStateFlow<Int?>(null)
    val modemSignalLevel: StateFlow<Int?> = _modemSignalLevel.asStateFlow()

    private val _tboxConnectedSignal = MutableStateFlow<Boolean?>(null)
    val tboxConnectedSignal: StateFlow<Boolean?> = _tboxConnectedSignal.asStateFlow()

    private var pollingJob: Job? = null

    // Default signal set for Launcher widgets/panels
    val defaultInterestedSignals = listOf(
        "fuel_level_liters",
        "fuel_level_percent_filtered",
        "active_trip_distance_km",
        "active_trip_avg_fuel_l100km",
        "active_trip_duration_s",
        "active_trip_motor_hours",
        "motor_hours",
        "gear_box_oil_temperature",
        "modem_signal_level",
        "tbox_connected",
        "locate_status",
    )

    companion object {
        private const val TBM_CLIENT_ID = "ras.dashing.tbox.launcher"
    }

    suspend fun checkHealth(): Result<TbmHealthResponse> {
        _connectionState.value = TbmConnectionState.Connecting
        val healthResult = apiClient.checkHealth()
        healthResult.onSuccess { health ->
            var token = settingsManager.getTbmApiToken()
            if (token.isNullOrEmpty()) {
                val pairResult = apiClient.requestPairing(TBM_CLIENT_ID, "TBox Launcher")
                val pairResp = pairResult.getOrNull()
                if (pairResp?.status == "approved" && !pairResp.accessToken.isNullOrEmpty()) {
                    token = pairResp.accessToken
                    settingsManager.saveTbmApiToken(token)
                }
            }

            if (!token.isNullOrEmpty()) {
                _connectionState.value = TbmConnectionState.Connected(health)
            } else {
                _connectionState.value = TbmConnectionState.PairingRequired
            }
        }.onFailure { err ->
            _connectionState.value = TbmConnectionState.Error(err.message ?: "Health check failed")
        }
        return healthResult
    }

    suspend fun requestPairing(
        clientId: String = TBM_CLIENT_ID,
        clientName: String = "TBox Launcher",
        pollUntilApproved: Boolean = true,
    ): Result<TbmPairResponse> {
        var result = apiClient.requestPairing(clientId, clientName)
        var pairResp = result.getOrNull()

        if (pairResp?.status == "approved" && !pairResp.accessToken.isNullOrEmpty()) {
            settingsManager.saveTbmApiToken(pairResp.accessToken)
            checkHealth()
            return result
        }

        val currentRequestId = pairResp?.requestId

        if (pollUntilApproved && pairResp?.status == "pending") {
            repeat(40) {
                delay(1500L)
                result = apiClient.requestPairing(clientId, clientName, requestId = currentRequestId)
                pairResp = result.getOrNull()
                if (pairResp?.status == "approved" && !pairResp.accessToken.isNullOrEmpty()) {
                    settingsManager.saveTbmApiToken(pairResp.accessToken)
                    checkHealth()
                    return result
                }
                if (pairResp?.status == "rejected") {
                    return result
                }
            }
        }

        return result
    }

    suspend fun refreshAutomations(): Result<List<TbmAutomationItem>> {
        var token = settingsManager.getTbmApiToken()
        if (token.isNullOrEmpty()) {
            val pairResult = apiClient.requestPairing(TBM_CLIENT_ID, "TBox Launcher")
            val pairResp = pairResult.getOrNull()
            if (pairResp?.status == "approved" && !pairResp.accessToken.isNullOrEmpty()) {
                token = pairResp.accessToken
                settingsManager.saveTbmApiToken(token)
                checkHealth()
            }
        }

        if (token.isNullOrEmpty()) {
            return Result.failure(IllegalStateException("No TBM API token"))
        }

        val result = apiClient.getAutomations(token)
        result.onSuccess { list ->
            _automations.value = list
        }
        return result
    }

    suspend fun runAutomation(automationId: String): Result<Boolean> {
        var token = settingsManager.getTbmApiToken()
        if (token.isNullOrEmpty()) {
            val pairResult = apiClient.requestPairing(TBM_CLIENT_ID, "TBox Launcher")
            val pairResp = pairResult.getOrNull()
            if (pairResp?.status == "approved" && !pairResp.accessToken.isNullOrEmpty()) {
                token = pairResp.accessToken
                settingsManager.saveTbmApiToken(token)
            }
        }

        if (token.isNullOrEmpty()) {
            return Result.failure(IllegalStateException("No TBM API token"))
        }

        val result = apiClient.runAutomation(automationId, token)
        return result.map { it.success }
    }

    suspend fun invokeAction(action: TbmActionItem): Result<Boolean> {
        val token = settingsManager.getTbmApiToken()
            ?: return Result.failure(IllegalStateException("No TBM API token"))
        val result = apiClient.invokeActions(listOf(action), token)
        return result.map { it.firstOrNull()?.success == true }
    }

    fun startPolling(
        signalIds: List<String> = defaultInterestedSignals,
        intervalMs: Long = 3000L,
    ) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val token = settingsManager.getTbmApiToken()
                if (token.isNullOrEmpty()) {
                    _connectionState.value = TbmConnectionState.PairingRequired
                    delay(intervalMs)
                    continue
                }

                val result = apiClient.getSignals(ids = signalIds, token = token)
                result.onSuccess { signalValues ->
                    val map = signalValues.associateBy({ it.id }, { it.value })
                    _signals.value = map
                    updateTypedSignals(map)
                }.onFailure { err ->
                    _connectionState.value = TbmConnectionState.Error(err.message ?: "Polling failed")
                }

                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun updateTypedSignals(map: Map<String, Any?>) {
        _fuelLiters.value = map["fuel_level_liters"]?.asFloat()
        _fuelPercentFiltered.value = map["fuel_level_percent_filtered"]?.asFloat()
        _activeTripDistanceKm.value = map["active_trip_distance_km"]?.asFloat()
        _activeTripAvgFuelL100km.value = map["active_trip_avg_fuel_l100km"]?.asFloat()
        _activeTripDurationSeconds.value = map["active_trip_duration_s"]?.asLong()
        _motorHours.value = map["motor_hours"]?.asFloat()
        _gearboxOilTemperature.value = map["gear_box_oil_temperature"]?.asFloat()
        _modemSignalLevel.value = map["modem_signal_level"]?.asInt()
        _tboxConnectedSignal.value = when (val v = map["tbox_connected"]) {
            is Boolean -> v
            "on" -> true
            "off" -> false
            else -> null
        }
    }

    private fun Any.asFloat(): Float? = when (this) {
        is Number -> this.toFloat()
        is String -> this.toFloatOrNull()
        else -> null
    }

    private fun Any.asLong(): Long? = when (this) {
        is Number -> this.toLong()
        is String -> this.toLongOrNull()
        else -> null
    }

    private fun Any.asInt(): Int? = when (this) {
        is Number -> this.toInt()
        is String -> this.toIntOrNull()
        else -> null
    }
}
