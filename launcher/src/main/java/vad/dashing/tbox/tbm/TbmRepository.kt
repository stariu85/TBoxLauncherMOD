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

    private val _geoSatellitesVisible = MutableStateFlow<Int?>(null)
    val geoSatellitesVisible: StateFlow<Int?> = _geoSatellitesVisible.asStateFlow()

    private val _geoSatellitesUsed = MutableStateFlow<Int?>(null)
    val geoSatellitesUsed: StateFlow<Int?> = _geoSatellitesUsed.asStateFlow()

    private val _geoSpeedSource = MutableStateFlow<String?>(null)
    val geoSpeedSource: StateFlow<String?> = _geoSpeedSource.asStateFlow()

    private val _geoFixValid = MutableStateFlow<Boolean?>(null)
    val geoFixValid: StateFlow<Boolean?> = _geoFixValid.asStateFlow()

    private val _locateStatusSignal = MutableStateFlow<Boolean?>(null)
    val locateStatusSignal: StateFlow<Boolean?> = _locateStatusSignal.asStateFlow()

    private var pollingJob: Job? = null
    private var healthCheckJob: Job? = null

    // Default signal sets grouped by API source for Launcher widgets/panels
    val defaultAppSignals = listOf(
        "fuel_level_liters",
        "fuel_level_percent_filtered",
        "active_trip_distance_km",
        "active_trip_avg_fuel_l100km",
        "active_trip_duration_s",
        "active_trip_motor_hours",
        "motor_hours",
        "modem_signal_level",
        "tbox_connected",
        "locate_status",
        "geo_satellites_visible",
        "geo_satellites_used",
        "geo_speed_source",
        "geo_fix_valid",
    )

    val defaultTboxSignals = listOf(
        "gear_box_oil_temperature",
    )

    val defaultInterestedSignals = defaultAppSignals + defaultTboxSignals

    companion object {
        private const val TBM_CLIENT_ID = "ras.dashing.tbox.launcher"
    }

    fun startHealthMonitoring(recheckIntervalMs: Long = 60_000L) {
        if (healthCheckJob?.isActive == true) return
        healthCheckJob = scope.launch {
            while (isActive) {
                if (_connectionState.value !is TbmConnectionState.Connected) {
                    checkHealth()
                }
                delay(recheckIntervalMs)
            }
        }
    }

    suspend fun checkHealth(): Result<TbmHealthResponse> {
        startHealthMonitoring()
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
                startPolling()
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
        appSignalIds: List<String> = defaultAppSignals,
        tboxSignalIds: List<String> = defaultTboxSignals,
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

                val mergedMap = mutableMapOf<String, Any?>()
                var hasError = false
                var lastErrorMessage = ""

                if (appSignalIds.isNotEmpty()) {
                    val appResult = apiClient.getSignals(ids = appSignalIds, source = "app", token = token)
                    appResult.onSuccess { signalValues ->
                        val receivedIds = HashSet<String>()
                        signalValues.forEach { item ->
                            mergedMap[item.id] = item.value
                            receivedIds.add(item.id)
                        }
                        appSignalIds.forEach { id ->
                            if (!receivedIds.contains(id)) {
                                mergedMap[id] = null
                            }
                        }
                    }.onFailure { err ->
                        hasError = true
                        lastErrorMessage = err.message ?: "App signals polling failed"
                    }
                }

                if (tboxSignalIds.isNotEmpty()) {
                    val tboxResult = apiClient.getSignals(ids = tboxSignalIds, source = "tbox", token = token)
                    tboxResult.onSuccess { signalValues ->
                        val receivedIds = HashSet<String>()
                        signalValues.forEach { item ->
                            mergedMap[item.id] = item.value
                            receivedIds.add(item.id)
                        }
                        tboxSignalIds.forEach { id ->
                            if (!receivedIds.contains(id)) {
                                mergedMap[id] = null
                            }
                        }
                    }.onFailure { err ->
                        if (!hasError) {
                            hasError = true
                            lastErrorMessage = err.message ?: "TBox signals polling failed"
                        }
                    }
                }

                if (mergedMap.isNotEmpty()) {
                    val currentMap = _signals.value.toMutableMap()
                    currentMap.putAll(mergedMap)
                    _signals.value = currentMap
                    updateTypedSignals(currentMap)
                }

                if (hasError && mergedMap.isEmpty()) {
                    _connectionState.value = TbmConnectionState.Error(lastErrorMessage)
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
        _geoSatellitesVisible.value = map["geo_satellites_visible"]?.asInt()
        _geoSatellitesUsed.value = map["geo_satellites_used"]?.asInt()
        _geoSpeedSource.value = map["geo_speed_source"]?.toString()?.takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.equals("none", ignoreCase = true)
        }
        _geoFixValid.value = when (val v = map["geo_fix_valid"]) {
            is Boolean -> v
            "on" -> true
            "off" -> false
            else -> null
        }
        _locateStatusSignal.value = when (val v = map["locate_status"]) {
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
