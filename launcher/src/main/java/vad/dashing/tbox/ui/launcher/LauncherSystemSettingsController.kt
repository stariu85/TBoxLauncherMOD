package vad.dashing.tbox.ui.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Live Android system settings for the launcher overlay (Wi‑Fi / BT / volume / brightness).
 * Does not launch OEM Settings APKs.
 */
class LauncherSystemSettingsController(private val appContext: Context) {
    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val audioManager =
        appContext.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    var wifiEnabled by mutableStateOf(false)
        private set
    var wifiSsid by mutableStateOf<String?>(null)
        private set
    var bluetoothEnabled by mutableStateOf(false)
        private set
    var bluetoothName by mutableStateOf<String?>(null)
        private set
    var mediaVolume by mutableIntStateOf(0)
        private set
    var mediaVolumeMax by mutableIntStateOf(15)
        private set
    var brightnessNorm by mutableFloatStateOf(0.5f)
        private set
    var brightnessWritable by mutableStateOf(false)
        private set

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        runCatching {
            appContext.registerReceiver(receiver, filter)
            registered = true
        }
        refresh()
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        wifiSsid = runCatching {
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo?.ssid
                ?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()

        bluetoothEnabled = runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)
        bluetoothName = runCatching {
            if (bluetoothEnabled) bluetoothAdapter?.name else null
        }.getOrNull()

        mediaVolumeMax = runCatching {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        }.getOrDefault(15)
        mediaVolume = runCatching {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, mediaVolumeMax)
        }.getOrDefault(0)

        brightnessWritable = runCatching {
            Settings.System.canWrite(appContext)
        }.getOrDefault(false)
        brightnessNorm = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                .coerceIn(0, 255) / 255f
        }.getOrDefault(0.5f)
    }

    fun applyWifiEnabled(enabled: Boolean) {
        runCatching {
            @Suppress("DEPRECATION")
            wifiManager?.isWifiEnabled = enabled
        }
        wifiEnabled = enabled
        refresh()
    }

    @SuppressLint("MissingPermission")
    fun applyBluetoothEnabled(enabled: Boolean) {
        runCatching {
            if (enabled) {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.enable()
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.disable()
            }
        }
        bluetoothEnabled = enabled
        refresh()
    }

    fun adjustMediaVolume(delta: Int) {
        runCatching {
            val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(kotlin.math.abs(delta)) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            }
        }
        refresh()
    }

    fun applyBrightnessNorm(value: Float) {
        if (!brightnessWritable) return
        val level = (value.coerceIn(0f, 1f) * 255f).toInt().coerceIn(1, 255)
        runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level,
            )
        }
        brightnessNorm = level / 255f
    }
}

@Suppress("unused")
val LocalLauncherSystemSettings = staticCompositionLocalOf<LauncherSystemSettingsController?> { null }
