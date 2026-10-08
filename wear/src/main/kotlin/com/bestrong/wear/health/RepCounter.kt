package com.bestrong.wear.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contador de repeticiones experimental con el acelerómetro del reloj.
 *
 * Separa la gravedad con un filtro paso bajo y proyecta la aceleración lineal sobre la vertical.
 * En cada repetición hay un pico positivo en el punto bajo (frenar la bajada + empezar a subir):
 * se cuenta un pico cuando supera [UP_THRESHOLD], con histéresis y un intervalo mínimo.
 * Funciona mejor en ejercicios donde la muñeca sube y baja (press, curl, remo); es una beta.
 */
class RepCounter(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _reps = MutableStateFlow(0)
    val reps: StateFlow<Int> = _reps.asStateFlow()

    val isAvailable: Boolean get() = accelerometer != null
    private var running = false

    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var smoothed = 0f
    private var armed = true
    private var lastRepAt = 0L

    fun start() {
        if (running || accelerometer == null) return
        running = true
        reset()
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
    }

    fun reset() {
        _reps.value = 0
        armed = true
        smoothed = 0f
        lastRepAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        val a = event.values
        if (!gravityReady) {
            a.copyInto(gravity, endIndex = 3)
            gravityReady = true
            return
        }
        for (i in 0..2) gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * a[i]
        val gNorm = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).takeIf { it > 0.1f } ?: return
        // Aceleración lineal proyectada sobre la vertical (positivo = hacia arriba)
        var vertical = 0f
        for (i in 0..2) vertical += (a[i] - gravity[i]) * (gravity[i] / gNorm)
        smoothed = SMOOTH_ALPHA * vertical + (1 - SMOOTH_ALPHA) * smoothed

        val now = event.timestamp / 1_000_000
        if (armed && smoothed > UP_THRESHOLD && now - lastRepAt > MIN_INTERVAL_MS) {
            _reps.value += 1
            lastRepAt = now
            armed = false
        } else if (!armed && smoothed < RESET_THRESHOLD) {
            armed = true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val GRAVITY_ALPHA = 0.9f
        const val SMOOTH_ALPHA = 0.3f
        const val UP_THRESHOLD = 1.2f
        const val RESET_THRESHOLD = 0.3f
        const val MIN_INTERVAL_MS = 800L
    }
}
