package vad.dashing.tbox.ui.launcher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live TPMS for the launcher overlay.
 * Fed from [vad.dashing.tbox.mbcan.MbCanEngineFacade] settings telemetry
 * (`onCanVehicleTires`) — same push path as OEM carsettings widgets.
 */
object LauncherTireRepository {
    private val _state = MutableStateFlow(LauncherTireState())
    val state: StateFlow<LauncherTireState> = _state.asStateFlow()
    private var applicationContext: Context? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        applicationContext = appCtx
        val saved = LauncherAppConfigStore.loadSavedTirePressures(appCtx)
        if (saved != null && !_state.value.hasAvailableData) {
            _state.value = saved
            LauncherVehicleAlertsRepository.refresh()
        }
    }

    fun applyFromMbCan(tires: Any?, context: Context? = null) {
        val ctx = context ?: applicationContext
        val parsed = parseLauncherTireState(tires)
        _state.value = parsed
        if (ctx != null && parsed.hasAvailableData) {
            LauncherAppConfigStore.saveTirePressures(ctx, parsed)
        }
        LauncherVehicleAlertsRepository.refresh()
    }

    fun updateFromCan(newState: LauncherTireState, context: Context? = null) {
        val ctx = context ?: applicationContext
        _state.value = newState
        if (ctx != null && newState.hasAvailableData) {
            LauncherAppConfigStore.saveTirePressures(ctx, newState)
        }
        LauncherVehicleAlertsRepository.refresh()
    }

    fun clear() {
        val ctx = applicationContext
        if (ctx != null && LauncherAppConfigStore.tirePressurePersistEnabled(ctx)) {
            val saved = LauncherAppConfigStore.loadSavedTirePressures(ctx)
            if (saved != null) {
                _state.value = saved
                LauncherVehicleAlertsRepository.refresh()
                return
            }
        }
        _state.value = LauncherTireState()
        LauncherVehicleAlertsRepository.refresh()
    }
}
