package vad.dashing.tbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import vad.dashing.tbox.ui.ProvideUiClickSounds
import vad.dashing.tbox.ui.launcher.LauncherSettingsScreen
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.update.InstallPermissionHelper
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.update.UpdateViewModelFactory
import java.lang.ref.WeakReference

/**
 * About / OTA console opened from the launcher «Настроить» button.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private var instanceRef: WeakReference<MainActivity>? = null

        fun isOpen(): Boolean {
            val act = instanceRef?.get() ?: return false
            return !act.isFinishing && !act.isDestroyed
        }

        fun finishIfOpen(): Boolean {
            val act = instanceRef?.get() ?: return false
            if (!act.isFinishing && !act.isDestroyed) {
                act.finish()
                return true
            }
            return false
        }
    }

    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val updateViewModel: UpdateViewModel by viewModels {
        UpdateViewModelFactory(application, settingsManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceRef = WeakReference(this)
        setContent {
            ProvideUiClickSounds {
                TboxAppTheme(theme = 2) {
                    BackHandler { finish() }
                    LauncherSettingsScreen(
                        updateViewModel = updateViewModel,
                        settingsManager = settingsManager,
                        onOpenInstallPermissionSettings = {
                            startActivity(
                                InstallPermissionHelper.createUnknownSourcesSettingsIntent(this),
                            )
                        },
                        onClose = { finish() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (instanceRef?.get() === this) instanceRef = null
        super.onDestroy()
    }
}
