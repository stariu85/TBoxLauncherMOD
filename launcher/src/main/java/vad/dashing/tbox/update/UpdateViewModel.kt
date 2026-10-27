package vad.dashing.tbox.update

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.SettingsManager

class UpdateViewModel(
    application: Application,
    private val settingsManager: SettingsManager,
) : AndroidViewModel(application) {
    private val repository = UpdateRepository(application, settingsManager)
    private var downloadJob: Job? = null

    val uiState = repository.uiState
    val updateChannel = settingsManager.updateChannelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateChannel.RELEASE)
    val updateCheckEnabled = settingsManager.updateCheckEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            repository.checkForUpdate(force = force)
        }
    }

    fun checkForUpdateOnStartupIfEnabled() {
        viewModelScope.launch {
            if (!settingsManager.updateCheckEnabledFlow.first()) return@launch
            if (!UpdateSessionGate.tryBeginSessionCheck()) return@launch

            val startTimeMs = SystemClock.uptimeMillis()
            val totalDurationMs = 5 * 60 * 1000L // 5 minutes = 300,000 ms
            val checkIntervalMs = 30 * 1000L      // 30 seconds = 30,000 ms

            while (isActive) {
                if (!settingsManager.updateCheckEnabledFlow.first()) break

                val currentState = repository.uiState.value
                if (currentState is UpdateUiState.Available ||
                    currentState is UpdateUiState.WaitingForServer ||
                    currentState is UpdateUiState.Downloading ||
                    currentState is UpdateUiState.Verifying ||
                    currentState is UpdateUiState.ReadyToInstall
                ) {
                    break
                }

                repository.checkForUpdate(force = true)

                val newState = repository.uiState.value
                if (newState is UpdateUiState.Available ||
                    newState is UpdateUiState.WaitingForServer ||
                    newState is UpdateUiState.Downloading ||
                    newState is UpdateUiState.Verifying ||
                    newState is UpdateUiState.ReadyToInstall
                ) {
                    break
                }

                val elapsedMs = SystemClock.uptimeMillis() - startTimeMs
                if (elapsedMs >= totalDurationMs) break

                val remainingMs = totalDurationMs - elapsedMs
                val waitMs = minOf(checkIntervalMs, remainingMs)
                if (waitMs <= 0) break

                delay(waitMs)
            }
        }
    }

    fun downloadAndVerify() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                repository.downloadAndVerify()
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        repository.cancelDownload()
    }

    fun installPreparedApk() {
        viewModelScope.launch {
            val info = when (val state = repository.uiState.value) {
                is UpdateUiState.ReadyToInstall -> state.info
                else -> repository.peekUpdateInfo()
            }
            if (info != null) {
                runCatching { settingsManager.markAwaitingHuRebootAfterInstall(info.versionCode) }
            }
            repository.installPreparedApk()
        }
    }

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun shouldShowMenuEntry(): Boolean = repository.shouldShowMenuEntry()

    fun peekUpdateInfo(): UpdateReleaseInfo? = repository.peekUpdateInfo()

    fun saveUpdateChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            val current = settingsManager.updateChannelFlow.first()
            if (current == channel) return@launch
            settingsManager.saveUpdateChannel(channel)
            repository.resetAfterChannelChange()
            repository.checkForUpdate(force = true)
        }
    }

    fun saveUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveUpdateCheckEnabled(enabled)
        }
    }
}

class UpdateViewModelFactory(
    private val application: Application,
    private val settingsManager: SettingsManager,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UpdateViewModel::class.java)) {
            return UpdateViewModel(application, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
