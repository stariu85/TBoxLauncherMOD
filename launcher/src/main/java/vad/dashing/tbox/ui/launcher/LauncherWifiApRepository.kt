package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vad.dashing.tbox.mbcan.MbCanEngineFacade

/** Cached SoftAP credentials (mbCAN string properties 62/63/116, WifiManager fallback). */
internal data class LauncherWifiApInfo(
    val ssid: String,
    val password: String,
    val mac: String,
)

/**
 * AIService publishes SoftAP params into mbCAN only for a short window (≈10–30 s) after
 * tethering starts, so [pollStartupWindow] reads aggressively right after process start.
 * Last non-empty snapshot is persisted and restored on next launch. When mbCAN returns
 * nothing, values are read straight from [WifiManager.getWifiApConfiguration] via
 * reflection — the same source AIService uses.
 */
internal object LauncherWifiApRepository {
    private const val TAG = "LauncherWifiAp"
    private const val PROP_WIFI_SSID = 62 // eCFG_WIFI_NAME
    private const val PROP_WIFI_PASSWORD = 63 // eCFG_WIFI_PASSWORD
    private const val PROP_WIFI_MAC = 116 // eCFG_WIFI_ADRESS
    private const val PREFS = "tbox_launcher_wifi_ap"
    private const val KEY_SSID = "ssid"
    private const val KEY_PASSWORD = "password"
    private const val KEY_MAC = "mac"
    private const val KEY_BOOT_ID = "boot_id"
    private const val POLL_INTERVAL_MS = 1500L
    private const val POLL_WINDOW_MS = 45_000L
    private const val LOGCAT_EVERY_N_ATTEMPTS = 4

    private val SEND_TO_CAN_REGEX = Regex("SSID: ([^,]+), PASSWORD: (\\S+)")
    private val GET_SOFT_AP_REGEX = Regex("getSoftAp ssid: ([^,]+), strPasd: (\\S+)")

    private val _state = MutableStateFlow<LauncherWifiApInfo?>(null)
    val state: StateFlow<LauncherWifiApInfo?> = _state

    @Volatile
    private var pollStarted = false

    @Volatile
    private var wifiManagerFallbackUsable = true

    @Volatile
    private var logcatReadUsable = true

    /** Kernel boot id — SoftAP password is randomized on every boot, stale cache must not leak. */
    private fun currentBootId(): String = runCatching {
        java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
    }.getOrDefault("")

    /** Restores the persisted snapshot (same boot only) and starts the boot-time poll. */
    fun init(context: Context) {
        val appContext = context.applicationContext
        if (_state.value == null) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bootId = currentBootId()
            val cached = LauncherWifiApInfo(
                ssid = prefs.getString(KEY_SSID, "").orEmpty(),
                password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
                mac = prefs.getString(KEY_MAC, "").orEmpty(),
            )
            val sameBoot = bootId.isNotEmpty() && prefs.getString(KEY_BOOT_ID, "") == bootId
            if (sameBoot && (cached.ssid.isNotBlank() || cached.password.isNotBlank() || cached.mac.isNotBlank())) {
                _state.value = cached
            } else if (!sameBoot) {
                prefs.edit().clear().apply()
            }
        }
        pollStartupWindow(appContext)
    }

    /** Single read (used when the vehicle settings open). */
    fun refresh(context: Context) {
        Thread({ readOnce(context.applicationContext, allowLogcat = true) }, "launcher-wifi-ap")
            .apply { isDaemon = true }
            .start()
    }

    /** Aggressive boot-window polling until SSID+password are captured or the window expires. */
    fun pollStartupWindow(context: Context) {
        if (pollStarted) return
        pollStarted = true
        Thread({
            val appContext = context.applicationContext
            val deadline = System.currentTimeMillis() + POLL_WINDOW_MS
            var attempt = 0
            while (System.currentTimeMillis() < deadline) {
                attempt++
                val current = _state.value
                val complete = readOnce(appContext, allowLogcat = attempt % LOGCAT_EVERY_N_ATTEMPTS == 1)
                if (complete) {
                    Log.w(TAG, "wifi ap captured after $attempt attempts")
                    break
                }
                // MAC may lag behind SSID/PSK — keep polling a little if only MAC is missing.
                if (current != null && current.ssid.isNotBlank() && current.password.isNotBlank() &&
                    System.currentTimeMillis() + POLL_INTERVAL_MS * 2 >= deadline
                ) {
                    break
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "launcher-wifi-ap-poll").apply { isDaemon = true }.start()
    }

    /** Returns true when both SSID and password were captured. */
    private fun readOnce(context: Context, allowLogcat: Boolean): Boolean {
        var ssid = ""
        var password = ""
        var mac = ""
        if (wifiManagerFallbackUsable) {
            readViaWifiManager(context)?.let { (s, p) ->
                ssid = s
                password = p
            }
        }
        // Last resort: AIService (tag "chitin") logs SoftAP credentials in plaintext for the
        // first ~40 s after boot. Requires READ_LOGS (granted once via adb).
        if ((ssid.isEmpty() || password.isEmpty()) && allowLogcat && logcatReadUsable) {
            readViaLogcat(context)?.let { (s, p) ->
                if (ssid.isEmpty()) ssid = s
                if (password.isEmpty()) password = p
            }
        }
        Log.w(TAG, "wifi ap read ssid='$ssid' pass_len=${password.length} mac='$mac'")
        if (ssid.isEmpty() && password.isEmpty() && mac.isEmpty()) return false
        // Merge with the previous snapshot so a late-arriving field is not dropped.
        val prev = _state.value
        val merged = LauncherWifiApInfo(
            ssid = ssid.ifEmpty { prev?.ssid.orEmpty() },
            password = password.ifEmpty { prev?.password.orEmpty() },
            mac = mac.ifEmpty { prev?.mac.orEmpty() },
        )
        _state.value = merged
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SSID, merged.ssid)
            .putString(KEY_PASSWORD, merged.password)
            .putString(KEY_MAC, merged.mac)
            .putString(KEY_BOOT_ID, currentBootId())
            .apply()
        return merged.ssid.isNotBlank() && merged.password.isNotBlank()
    }

    /** Same source the OEM AIService uses: WifiManager.getWifiApConfiguration() (hidden API). */
    private fun readViaWifiManager(context: Context): Pair<String, String>? =
        runCatching {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val config = wm.javaClass.getMethod("getWifiApConfiguration").invoke(wm) ?: return null
            val ssid = config.javaClass.getField("SSID").get(config) as? String
            val psk = config.javaClass.getField("preSharedKey").get(config) as? String
            unquote(ssid) to unquote(psk)
        }.onFailure {
            // WifiServiceImpl on this ROM throws RemoteException while SoftAP is off —
            // no point retrying within the same poll session.
            wifiManagerFallbackUsable = false
            val root = generateSequence<Throwable>(it) { t -> t.cause }.last()
            Log.w(TAG, "getWifiApConfiguration failed: ${root.javaClass.name}: ${root.message}")
        }.getOrNull()

    private fun unquote(value: String?): String =
        value.orEmpty().removePrefix("\"").removeSuffix("\"")

    /**
     * Parses AIService boot-time log lines (`chitin` tag):
     * `AP_Configuration: sendToCan SSID: x, PASSWORD: y` / `getSoftAp ssid: x, strPasd: y`.
     * Last match wins (most recent publish). Runs `logcat -d` — needs READ_LOGS.
     */
    private fun readViaLogcat(context: Context): Pair<String, String>? {
        val granted = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            if (logcatReadUsable) {
                Log.w(TAG, "READ_LOGS not granted, logcat fallback off")
            }
            logcatReadUsable = false
            return null
        }
        return runCatching {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "brief", "-t", "4000", "chitin:D", "*:S"),
            )
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            var result: Pair<String, String>? = null
            text.lineSequence().forEach { line ->
                SEND_TO_CAN_REGEX.find(line)?.let { m ->
                    result = m.groupValues[1] to m.groupValues[2]
                }
                GET_SOFT_AP_REGEX.find(line)?.let { m ->
                    result = m.groupValues[1] to m.groupValues[2]
                }
            }
            if (result != null) {
                Log.w(TAG, "wifi ap from logcat ssid='${result?.first}' pass_len=${result?.second?.length}")
            }
            result
        }.onFailure {
            logcatReadUsable = false
            Log.w(TAG, "logcat read failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()
    }

    /** WIFI: QR payload for phone scanners; special chars escaped per ZXing spec. */
    fun wifiQrPayload(info: LauncherWifiApInfo): String? {
        if (info.ssid.isBlank()) return null
        fun escape(value: String): String =
            value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:")
                .replace("\"", "\\\"")
        return if (info.password.isEmpty()) {
            "WIFI:T:nopass;S:${escape(info.ssid)};;"
        } else {
            "WIFI:T:WPA;S:${escape(info.ssid)};P:${escape(info.password)};;"
        }
    }
}
