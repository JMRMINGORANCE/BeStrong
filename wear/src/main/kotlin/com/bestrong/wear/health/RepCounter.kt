package com.bestrong.wear.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bestrong.core.sensors.BarVelocity
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contador de repeticiones con el acelerómetro del reloj (beta).
 *
 * Separa la gravedad con un filtro paso bajo y proyecta la aceleración lineal sobre la vertical.
 * En cada repetición hay un pico positivo en el punto bajo (frenar la bajada + empezar a subir):
 * se cuenta un pico cuando supera el umbral, con histéresis y un intervalo mínimo.
 * Además estima la velocidad media de cada repetición (para el RIR por pérdida de velocidad)
 * y se puede calibrar con unas repeticiones de prueba.
 */
class RepCounter(context: Context, initialThreshold: Float = DEFAULT_THRESHOLD) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _reps = MutableStateFlow(0)
    val reps: StateFlow<Int> = _reps.asStateFlow()

    /** Velocidad media (m/s) de cada repetición de la serie actual. */
    private val _velocities = MutableStateFlow<List<Double>>(emptyList())
    val velocities: StateFlow<List<Double>> = _velocities.asStateFlow()

    /** Calibrando: se registran los picos y al terminar se calcula el umbral. */
    private val _calibrating = MutableStateFlow(false)
    val calibrating: StateFlow<Boolean> = _calibrating.asStateFlow()

    var threshold: Float = initialThreshold
        private set

    val isAvailable: Boolean get() = accelerometer != null
    private var running = false

    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var smoothed = 0f
    private var armed = true
    private var lastRepAt = 0L
    private var lastSampleNs = 0L
    private val repSamples = ArrayList<Float>(256)
    private var dtSum = 0.0
    private var dtCount = 0
    private val calibrationPeaks = mutableListOf<Float>()
    private var currentPeak = 0f

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
        _velocities.value = emptyList()
        armed = true
        smoothed = 0f
        lastRepAt = 0L
        repSamples.clear()
    }

    /** Empieza la calibración: haz 3–5 repeticiones normales y llama a [finishCalibration]. */
    fun startCalibration() {
        calibrationPeaks.clear()
        _calibrating.value = true
        start()
    }

    /** Termina la calibración y devuelve el nuevo umbral. */
    fun finishCalibration(): Float {
        _calibrating.value = false
        threshold = BarVelocity.calibratedThreshold(calibrationPeaks, threshold)
        reset()
        return threshold
    }

    override fun onSensorChanged(event: SensorEvent) {
        val a = event.values
        if (!gravityReady) {
            a.copyInto(gravity, endIndex = 3)
            gravityReady = true
            lastSampleNs = event.timestamp
            return
        }
        if (lastSampleNs > 0) {
            dtSum += (event.timestamp - lastSampleNs) / 1e9
            dtCount++
        }
        lastSampleNs = event.timestamp
        for (i in 0..2) gravity[i] = GRAVITY_ALPHA * gravity[i] + (1 - GRAVITY_ALPHA) * a[i]
        val gNorm = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).takeIf { it > 0.1f } ?: return
        // Aceleración lineal proyectada sobre la vertical (positivo = hacia arriba)
        var vertical = 0f
        for (i in 0..2) vertical += (a[i] - gravity[i]) * (gravity[i] / gNorm)
        smoothed = SMOOTH_ALPHA * vertical + (1 - SMOOTH_ALPHA) * smoothed
        if (repSamples.size < MAX_SAMPLES) repSamples += vertical
        if (smoothed > currentPeak) currentPeak = smoothed

        // Durante la calibración el umbral es bajo para ver todos los picos
        val limit = if (_calibrating.value) CALIBRATION_THRESHOLD else threshold
        val now = event.timestamp / 1_000_000
        if (armed && smoothed > limit && now - lastRepAt > MIN_INTERVAL_MS) {
            onRep(now)
        } else if (!armed && smoothed < RESET_THRESHOLD) {
            armed = true
            if (_calibrating.value && currentPeak > 0f) calibrationPeaks += currentPeak
            currentPeak = 0f
        }
    }

    private fun onRep(now: Long) {
        _reps.value += 1
        lastRepAt = now
        armed = false
        // Velocidad de la repetición que acaba de terminar (muestras desde la anterior)
        val dt = if (dtCount > 0) (dtSum / dtCount).toFloat() else 0.02f
        val v = BarVelocity.meanConcentricVelocity(repSamples, dt)
        if (v > 0 && _reps.value > 1) _velocities.value = _velocities.value + v
        repSamples.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val DEFAULT_THRESHOLD = 1.2f
        private const val GRAVITY_ALPHA = 0.9f
        private const val SMOOTH_ALPHA = 0.3f
        private const val CALIBRATION_THRESHOLD = 0.5f
        private const val RESET_THRESHOLD = 0.3f
        private const val MIN_INTERVAL_MS = 800L
        private const val MAX_SAMPLES = 1_000
    }
}
