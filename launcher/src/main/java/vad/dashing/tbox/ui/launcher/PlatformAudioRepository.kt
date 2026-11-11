package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import java.lang.reflect.Method

enum class PlatformVolumeChannel(
    val title: String,
    val minVal: Int,
    val maxVal: Int,
    val a9Usage: Int,
    val a9FallbackStream: Int,
) {
    Media(
        title = "Громкость Медиа",
        minVal = 0,
        maxVal = 31,
        a9Usage = 1, // USAGE_MEDIA
        a9FallbackStream = AudioManager.STREAM_MUSIC,
    ),
    Phone(
        title = "Громкость Звонков",
        minVal = 1,
        maxVal = 31,
        a9Usage = 2, // USAGE_VOICE_COMMUNICATION
        a9FallbackStream = AudioManager.STREAM_VOICE_CALL,
    ),
    Navi(
        title = "Громкость Навигации",
        minVal = 0,
        maxVal = 10,
        a9Usage = 12, // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        a9FallbackStream = 7,
    ),
    Voice(
        title = "Громкость Голоса",
        minVal = 2,
        maxVal = 10,
        a9Usage = 16, // USAGE_ASSISTANT
        a9FallbackStream = 9,
    ),
}

internal object PlatformAudioRepository {
    private const val TAG = "PlatformAudioRepo"
    private const val VOLUME_POLL_MS = 500L
    private const val CAN_HEADREST_PROP_ID = 37 // MBAudioProperty.eAUDIO_AUDIO_HEADREST_SPEAKER

    private val _mediaVolume = MutableStateFlow(15)
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    private val _phoneVolume = MutableStateFlow(15)
    val phoneVolume: StateFlow<Int> = _phoneVolume.asStateFlow()

    private val _naviVolume = MutableStateFlow(5)
    val naviVolume: StateFlow<Int> = _naviVolume.asStateFlow()

    private val _voiceVolume = MutableStateFlow(5)
    val voiceVolume: StateFlow<Int> = _voiceVolume.asStateFlow()

    private val _headrestMode = MutableStateFlow(3) // 1: HEADREST_ONLY, 2: HEADREST_ASSIST, 3: HEADREST_OFF
    val headrestMode: StateFlow<Int> = _headrestMode.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var isObserving = false

    // Reflection cache for OpenOS Audio Groups (Android 9 / A9)
    private var getGroupIdMethod: Method? = null
    private var getGroupVolMethod: Method? = null
    private var setGroupVolMethod: Method? = null
    private var reflectionInitialized = false

    private fun initOpenOsReflection(audioManager: AudioManager) {
        if (reflectionInitialized) return
        reflectionInitialized = true
        runCatching {
            val amClass = audioManager.javaClass
            getGroupIdMethod = amClass.methods.firstOrNull { it.name == "getVolumeGroupIdForUsage" }
            getGroupVolMethod = amClass.methods.firstOrNull { it.name == "getGroupVolume" }
            setGroupVolMethod = amClass.methods.firstOrNull { it.name == "setGroupVolume" }
            Log.w(TAG, "OpenOS A9 Audio reflection initialized: getGroupId=$getGroupIdMethod getGroupVol=$getGroupVolMethod setGroupVol=$setGroupVolMethod")
        }.onFailure {
            Log.w(TAG, "Failed to reflect OpenOS Audio methods", it)
        }
    }

    fun startObserving(context: Context) {
        if (isObserving) return
        isObserving = true
        pollRunnable.run()
    }

    fun stopObserving() {
        isObserving = false
        handler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isObserving) return
            updateAllVolumes()
            handler.postDelayed(this, VOLUME_POLL_MS)
        }
    }

    fun updateAllVolumes() {
        val activity = LauncherHomeActivityHolder.instance ?: return
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        initOpenOsReflection(am)

        _mediaVolume.value = readChannelVolume(am, PlatformVolumeChannel.Media)
        _phoneVolume.value = readChannelVolume(am, PlatformVolumeChannel.Phone)
        _naviVolume.value = readChannelVolume(am, PlatformVolumeChannel.Navi)
        _voiceVolume.value = readChannelVolume(am, PlatformVolumeChannel.Voice)

        // Sync Headrest speaker state from CAN
        runCatching {
            val currentCanHeadrest = MbCanEngineFacade.canGetAudioParam(CAN_HEADREST_PROP_ID)
            if (currentCanHeadrest != null && currentCanHeadrest in 1..3) {
                _headrestMode.value = currentCanHeadrest
            }
        }
    }

    fun readChannelVolume(am: AudioManager, channel: PlatformVolumeChannel): Int {
        return runCatching {
            if (getGroupIdMethod != null && getGroupVolMethod != null) {
                val groupId = getGroupIdMethod?.invoke(am, channel.a9Usage) as? Int
                if (groupId != null && groupId >= 0) {
                    val vol = getGroupVolMethod?.invoke(am, groupId) as? Int
                    if (vol != null) return vol.coerceIn(channel.minVal, channel.maxVal)
                }
            }
            // Fallback to AudioManager stream
            am.getStreamVolume(channel.a9FallbackStream).coerceIn(channel.minVal, channel.maxVal)
        }.getOrElse {
            channel.minVal
        }
    }

    fun setChannelVolume(context: Context, channel: PlatformVolumeChannel, value: Int) {
        val target = value.coerceIn(channel.minVal, channel.maxVal)
        when (channel) {
            PlatformVolumeChannel.Media -> _mediaVolume.value = target
            PlatformVolumeChannel.Phone -> _phoneVolume.value = target
            PlatformVolumeChannel.Navi -> _naviVolume.value = target
            PlatformVolumeChannel.Voice -> _voiceVolume.value = target
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        initOpenOsReflection(am)

        runCatching {
            var setViaOpenOs = false
            if (getGroupIdMethod != null && setGroupVolMethod != null) {
                val groupId = getGroupIdMethod?.invoke(am, channel.a9Usage) as? Int
                if (groupId != null && groupId >= 0) {
                    if (setGroupVolMethod?.parameterTypes?.size == 3) {
                        setGroupVolMethod?.invoke(am, groupId, target, 0)
                    } else {
                        setGroupVolMethod?.invoke(am, groupId, target)
                    }
                    setViaOpenOs = true
                }
            }
            if (!setViaOpenOs) {
                val maxStreamVol = am.getStreamMaxVolume(channel.a9FallbackStream).coerceAtLeast(1)
                val streamTarget = (target.toFloat() / channel.maxVal.toFloat() * maxStreamVol).toInt().coerceIn(0, maxStreamVol)
                am.setStreamVolume(channel.a9FallbackStream, streamTarget, 0)
            }
        }.onFailure {
            Log.e(TAG, "Failed to set volume for channel ${channel.name}", it)
        }
    }

    fun cycleHeadrestMode(context: Context): Int {
        val current = _headrestMode.value
        val next = when (current) {
            1 -> 2 // HEADREST_ONLY (1) -> HEADREST_ASSIST (2)
            2 -> 3 // HEADREST_ASSIST (2) -> HEADREST_OFF (3)
            else -> 1 // HEADREST_OFF (3) -> HEADREST_ONLY (1)
        }
        _headrestMode.value = next
        runCatching {
            MbCanEngineFacade.canSetAudioParam(CAN_HEADREST_PROP_ID, next)
            Log.w(TAG, "Cycle headrest mode set to $next via CAN Property $CAN_HEADREST_PROP_ID")
        }
        return next
    }
}
