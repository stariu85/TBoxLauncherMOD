package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import vad.dashing.tbox.R

object SpeedWarningAudioPlayer {
    private const val TAG = "SpeedWarningPlayer"

    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var isLoaded: Boolean = false
    private var pendingPlay: Boolean = false
    private var activeStreamId: Int = 0

    @Synchronized
    fun init(context: Context) {
        if (soundPool != null) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                synchronized(this@SpeedWarningAudioPlayer) {
                    if (status == 0 && sampleId == soundId) {
                        isLoaded = true
                        Log.d(TAG, "Speed warning sound loaded successfully")
                        if (pendingPlay) {
                            pendingPlay = false
                            activeStreamId = pool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
                        }
                    } else {
                        Log.w(TAG, "Failed to load speed warning sound, status: $status")
                        pendingPlay = false
                    }
                }
            }

            soundId = pool.load(context.applicationContext, R.raw.speed_warning, 1)
            soundPool = pool
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeedWarningAudioPlayer", e)
        }
    }

    @Synchronized
    fun play(context: Context) {
        if (soundPool == null) {
            init(context)
        }
        val pool = soundPool ?: return
        if (!isLoaded) {
            pendingPlay = true
            return
        }
        if (activeStreamId != 0) {
            pool.stop(activeStreamId)
        }
        activeStreamId = pool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    @Synchronized
    fun stop() {
        pendingPlay = false
        val pool = soundPool ?: return
        if (activeStreamId != 0) {
            pool.stop(activeStreamId)
            activeStreamId = 0
        }
    }

    @Synchronized
    fun release() {
        stop()
        soundPool?.release()
        soundPool = null
        soundId = 0
        isLoaded = false
        pendingPlay = false
    }
}
