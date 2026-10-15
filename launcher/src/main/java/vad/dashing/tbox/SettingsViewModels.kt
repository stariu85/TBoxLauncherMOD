package vad.dashing.tbox

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Slim settings ViewModel — only APIs used by the launcher UI. */
class SettingsViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    val launcherAppIconRevision: StateFlow<Int> = settingsManager.launcherAppIconRevisionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val uiClickSoundsEnabled: StateFlow<Boolean> = settingsManager.uiClickSoundsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun saveUiClickSoundsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveUiClickSoundsEnabled(enabled)
        }
    }

    /** Standalone build has no theme packs — always empty. */
    val activeThemeUri: StateFlow<String> =
        kotlinx.coroutines.flow.MutableStateFlow("").stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            "",
        )

    val activeThemeSections: StateFlow<Set<ThemeSection>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptySet<ThemeSection>()).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptySet(),
        )

    fun setCustomLauncherAppIconFromUri(
        packageName: String,
        sourceUri: Uri?,
        onResult: (SetLauncherAppCustomIconResult) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(settingsManager.setCustomLauncherAppIconFromUri(packageName, sourceUri))
        }
    }

    fun clearCustomLauncherAppIcon(packageName: String) {
        viewModelScope.launch {
            settingsManager.clearCustomLauncherAppIcon(packageName)
        }
    }

    suspend fun hasCustomLauncherAppIcon(packageName: String): Boolean =
        settingsManager.hasCustomLauncherAppIcon(packageName)
}

class SettingsViewModelFactory(private val settingsManager: SettingsManager) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
