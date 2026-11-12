package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.AdayoSettingsService
import vad.dashing.tbox.AppContextHolder
import vad.dashing.tbox.LauncherHomeActivityHolder
import vad.dashing.tbox.PlatformAudioDomain
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

enum class PlatformVolumeChannel(
    val title: String,
    val minVal: Int,
    val maxVal: Int,
    val a9Usage: Int,
    val a10StreamType: Int,
    val a9FallbackStream: Int,
) {
    Media(
        title = "Громкость Медиа",
        minVal = 0,
        maxVal = 31,
        a9Usage = 1, // USAGE_MEDIA
        a10StreamType = 3,
        a9FallbackStream = AudioManager.STREAM_MUSIC,
    ),
    Phone(
        title = "Громкость Звонков",
        minVal = 1,
        maxVal = 31,
        a9Usage = 2, // USAGE_VOICE_COMMUNICATION
        a10StreamType = 6,
        a9FallbackStream = AudioManager.STREAM_VOICE_CALL,
    ),
    Navi(
        title = "Громкость Навигации",
        minVal = 0,
        maxVal = 10,
        a9Usage = 12, // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        a10StreamType = 7,
        a9FallbackStream = 7,
    ),
    Voice(
        title = "Громкость Голоса",
        minVal = 2,
        maxVal = 10,
        a9Usage = 16, // USAGE_ASSISTANT
        a10StreamType = 9,
        a9FallbackStream = 9,
    ),
}

internal object PlatformAudioRepository {
    private const val TAG = "PlatformAudioRepo"
    private const val VOLUME_POLL_MS = 500L

    private val _mediaVolume = MutableStateFlow(15)
    val mediaVolume: StateFlow<Int> = _mediaVolume.asStateFlow()

    private val _phoneVolume = MutableStateFlow(15)
    val phoneVolume: StateFlow<Int> = _phoneVolume.asStateFlow()

    private val _naviVolume = MutableStateFlow(5)
    val naviVolume: StateFlow<Int> = _naviVolume.asStateFlow()

    private val _voiceVolume = MutableStateFlow(5)
    val voiceVolume: StateFlow<Int> = _voiceVolume.asStateFlow()

    private val _headrestMode = MutableStateFlow(PlatformAudioDomain.HEADREST_OFF) // 1: ONLY, 2: ASSIST, 3: OFF
    val headrestMode: StateFlow<Int> = _headrestMode.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isObserving = false
    private var appContext: Context? = null

    fun startObserving(context: Context) {
        appContext = context.applicationContext
        if (isObserving) return
        isObserving = true
        pollRunnable.run()
    }

    fun stopObserving() {
        isObserving = false
        mainHandler.removeCallbacks(pollRunnable)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isObserving) return
            updateAllVolumes()
            mainHandler.postDelayed(this, VOLUME_POLL_MS)
        }
    }

    fun updateAllVolumes() {
        val media = readChannelVolume(PlatformVolumeChannel.Media)
        val phone = readChannelVolume(PlatformVolumeChannel.Phone)
        val navi = readChannelVolume(PlatformVolumeChannel.Navi)
        val voice = readChannelVolume(PlatformVolumeChannel.Voice)

        if (media != null) _mediaVolume.value = media
        if (phone != null) _phoneVolume.value = phone
        if (navi != null) _naviVolume.value = navi
        if (voice != null) _voiceVolume.value = voice

        // Sync Headrest speaker state
        val hr = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            readHeadrestAdayo()
        } else {
            readHeadrestMbCan()
        }
        if (hr != null && hr in PlatformAudioDomain.headrestUiValues) {
            _headrestMode.value = hr
        }
    }

    fun readChannelVolume(channel: PlatformVolumeChannel): Int? {
        val raw = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            readA10Volume(channel)
        } else {
            readA9Volume(channel)
        }
        return raw?.coerceIn(channel.minVal, channel.maxVal)
    }

    fun setChannelVolume(context: Context, channel: PlatformVolumeChannel, value: Int) {
        appContext = context.applicationContext
        val target = value.coerceIn(channel.minVal, channel.maxVal)
        when (channel) {
            PlatformVolumeChannel.Media -> _mediaVolume.value = target
            PlatformVolumeChannel.Phone -> _phoneVolume.value = target
            PlatformVolumeChannel.Navi -> _naviVolume.value = target
            PlatformVolumeChannel.Voice -> _voiceVolume.value = target
        }

        callOnMain(Unit) {
            val ok = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
                writeA10Volume(channel, target)
            } else {
                writeA9Volume(channel, target)
            }
            if (!ok) {
                Log.w(TAG, "Failed to set volume for channel ${channel.name} to $target")
            }
        }
    }

    fun cycleHeadrestMode(context: Context): Int {
        appContext = context.applicationContext
        val current = _headrestMode.value
        val next = when (current) {
            PlatformAudioDomain.HEADREST_ONLY -> PlatformAudioDomain.HEADREST_ASSIST
            PlatformAudioDomain.HEADREST_ASSIST -> PlatformAudioDomain.HEADREST_OFF
            else -> PlatformAudioDomain.HEADREST_ONLY
        }
        setHeadrestMode(next)
        return next
    }

    fun setHeadrestMode(uiValue: Int): Boolean {
        if (uiValue !in PlatformAudioDomain.headrestUiValues) return false
        _headrestMode.value = uiValue
        val ok = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            writeHeadrestAdayo(uiValue)
        } else {
            writeHeadrestMbCan(uiValue)
        }
        Log.d(TAG, "setHeadrestMode to $uiValue (ok=$ok)")
        return ok
    }

    private fun readHeadrestAdayo(): Int? =
        AdayoSettingsService.getInt("getHeadrestSpeakerMode")
            ?.let(PlatformAudioDomain::decodeHeadrestVhal)

    private fun writeHeadrestAdayo(uiValue: Int): Boolean =
        AdayoSettingsService.setInt("setHeadrestSpeakerMode", uiValue)

    private fun readHeadrestMbCan(): Int? =
        MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.HEADREST_SPEAKER)
            ?.let(PlatformAudioDomain::decodeHeadrestMbCan)

    private fun writeHeadrestMbCan(uiValue: Int): Boolean {
        val raw = PlatformAudioDomain.encodeHeadrestMbCan(uiValue) ?: return false
        val result = MbCanEngineFacade.canSetAudioParam(MbCanKnownAudioPropertyId.HEADREST_SPEAKER, raw)
        return result != null && result >= 0
    }

    private fun writeA10Volume(channel: PlatformVolumeChannel, value: Int): Boolean {
        val stream = channel.a10StreamType
        if (AdayoSettingsService.setInt("setAudioStreamVolume", stream, value)) return true
        return AdayoSettingsService.setInt("setAudioStreamVolume", stream, value, 0)
    }

    private fun readA10Volume(channel: PlatformVolumeChannel): Int? {
        return AdayoSettingsService.getInt("getAudioStreamVolume", channel.a10StreamType)
    }

    private fun writeA9Volume(channel: PlatformVolumeChannel, value: Int): Boolean {
        if (OpenOsAudio.setVolume(channel, value)) return true
        val context = audioContext() ?: return false
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val stream = channel.a9FallbackStream
        return runCatching {
            val maxStreamVol = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            val streamTarget = (value.toFloat() / channel.maxVal.toFloat() * maxStreamVol).toInt().coerceIn(0, maxStreamVol)
            am.setStreamVolume(stream, streamTarget, 0)
            true
        }.getOrDefault(false)
    }

    private fun readA9Volume(channel: PlatformVolumeChannel): Int? {
        OpenOsAudio.getVolume(channel)?.let { return it }
        val context = audioContext() ?: return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val stream = channel.a9FallbackStream
        return runCatching {
            val streamVol = am.getStreamVolume(stream)
            val maxStreamVol = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            (streamVol.toFloat() / maxStreamVol.toFloat() * channel.maxVal.toFloat()).toInt().coerceIn(channel.minVal, channel.maxVal)
        }.getOrNull()
    }

    private fun audioContext(): Context? =
        appContext ?: AppContextHolder.appContextOrNull ?: LauncherHomeActivityHolder.instance

    private fun <T> callOnMain(default: T, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val pending = CompletableFuture<T>()
        val posted = mainHandler.post {
            pending.complete(runCatching(block).getOrDefault(default))
        }
        if (!posted) return default
        return runCatching { pending.get(2, TimeUnit.SECONDS) }.getOrDefault(default)
    }

    private object OpenOsAudio {
        @Volatile
        private var manager: Any? = null
        @Volatile
        private var probeCompleted = false

        fun getVolume(channel: PlatformVolumeChannel): Int? = runCatching {
            val audio = manager() ?: return null
            val groupId = groupId(audio, channel) ?: return null
            invokeInt(audio, "getGroupVolume", groupId)
        }.onFailure { Log.w(TAG, "OpenOS getVolume failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()

        fun setVolume(channel: PlatformVolumeChannel, value: Int): Boolean = runCatching {
            val audio = manager() ?: return false
            val groupId = groupId(audio, channel) ?: return false
            val method = audio.javaClass.methods.firstOrNull { candidate ->
                candidate.name == "setGroupVolume" &&
                    candidate.parameterTypes.size == 3 &&
                    candidate.parameterTypes.all { it == Int::class.javaPrimitiveType }
            } ?: audio.javaClass.methods.firstOrNull {
                it.name == "setGroupVolume" && it.parameterTypes.size == 2 &&
                    it.parameterTypes.all { type -> type == Int::class.javaPrimitiveType }
            } ?: return false
            if (method.parameterTypes.size == 3) {
                method.invoke(audio, groupId, value, 0)
            } else {
                method.invoke(audio, groupId, value)
            }
            true
        }.onFailure { Log.w(TAG, "OpenOS setVolume failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(false)

        private fun groupId(audio: Any, channel: PlatformVolumeChannel): Int? =
            invokeInt(audio, "getVolumeGroupIdForUsage", channel.a9Usage)

        private fun invokeInt(target: Any, name: String, arg: Int): Int? {
            val method = target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            } ?: return null
            return (method.invoke(target, arg) as? Number)?.toInt()
        }

        private fun manager(): Any? {
            if (probeCompleted) return manager
            if (Looper.myLooper() != Looper.getMainLooper()) {
                return callOnMain(null) { manager() }
            }
            val context = audioContext() ?: return null
            synchronized(this) {
                if (probeCompleted) return manager
                val created = runCatching {
                    val openOsClass = Class.forName("com.openos.OpenOSContext")
                    val instance = openOsClass.getField("INSTANCE").get(null)
                    val audioCtx = Class.forName("com.openos.ManagerContext\$AudioManagerContext")
                        .getField("INSTANCE")
                        .get(null)
                    openOsClass.methods.firstOrNull { it.name == "initContext" && it.parameterTypes.size == 2 }
                        ?.invoke(instance, context, audioCtx)
                    openOsClass.methods.firstOrNull { it.name == "getManager" && it.parameterTypes.size == 1 }
                        ?.invoke(instance, audioCtx)
                }.onFailure { e ->
                    Log.w(
                        TAG,
                        "OpenOS AudioManager unavailable (${e.javaClass.simpleName}); using AudioManager streams",
                    )
                }.getOrNull()
                manager = created
                probeCompleted = true
                return created
            }
        }
    }
}
