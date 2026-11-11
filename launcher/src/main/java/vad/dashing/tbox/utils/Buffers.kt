package vad.dashing.tbox.utils

import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.roundToInt

class FuelLevelBuffer(private val bufferSize: Int = 15, private val alpha: Float = 0.05f) {
    private val buffer = ArrayDeque<UInt>()
    private var smoothedValue: Float? = null

    /**
     * Добавляет новое сырое значение процента топлива и возвращает отфильтрованное значение (EMA).
     */
    fun addValue(newValue: UInt): UInt {
        buffer.addLast(newValue)
        if (buffer.size > bufferSize) {
            buffer.removeFirst()
        }

        val rawFloat = newValue.toFloat()
        val currentSmoothed = smoothedValue
        if (currentSmoothed == null) {
            smoothedValue = rawFloat
        } else {
            // Если произошел резкий скачок (например, заправка >= 5%), сразу сбрасываем фильтр к новому значению
            if (abs(rawFloat - currentSmoothed) >= 5.0f) {
                smoothedValue = rawFloat
            } else {
                // Экспоненциальное скользящее среднее (EMA)
                smoothedValue = currentSmoothed * (1f - alpha) + rawFloat * alpha
            }
        }

        return smoothedValue!!.roundToInt().coerceIn(0, 100).toUInt()
    }

    fun reset(newValue: UInt? = null) {
        buffer.clear()
        smoothedValue = newValue?.toFloat()
    }
}

class MotorHoursBuffer(private val maxDif: Float = 0.02f) {
    private var lastTime = SystemClock.elapsedRealtime()
    private var lastRPM = 0f

    fun updateValue(rpm: Float): Float {
        if (rpm < 100f && lastRPM < 100f) {
            lastRPM = rpm
            lastTime = SystemClock.elapsedRealtime()
            return 0f
        }
        lastRPM = rpm
        val newHours = (SystemClock.elapsedRealtime() - lastTime) / 3600000.0f
        if (newHours > 10 * maxDif) {
            lastTime = SystemClock.elapsedRealtime()
            return 0f
        } else if (newHours >= maxDif) {
            lastTime = SystemClock.elapsedRealtime()
            return newHours
        } else {
            return 0f
        }
    }
}