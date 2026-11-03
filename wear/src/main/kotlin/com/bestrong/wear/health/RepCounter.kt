package com.bestrong.wear.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bestrong.core.sensors.BarVelocity
import com.bestrong.core.sensors.RepCalibration
import com.bestrong.core.sensors.RepConfidence
import com.bestrong.core.sensors.RepDetector
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contador de repeticiones con el acelerómetro del reloj (beta).
 *
 * Separa la gravedad con un filtro paso bajo, proyecta la aceleración lineal sobre la vertical y la suaviza; la
 * decisión de qué es una repetición (arranque, golpes, rebotes, picos desproporcionados, confianza) está en
 * [RepDetector], en el núcleo, probada con señales sintéticas. Además estima la velocidad media de cada repetición
 * (para el RIR por pérdida de velocidad) y se puede calibrar con unas repeticiones de prueba.
 */
class RepCounter(context: Context, initialThreshold: Float = RepDetector.DEFAULT_THRESHOLD) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = RepDetector(initialThreshold)

    private val _reps = MutableStateFlow(0)
    val reps: StateFlow<Int> = _reps.asStateFlow()

    /** Qué fiable es el recuento de la serie actual. */
    private val _confidence = MutableStateFlow(RepConfidence.LOW)
    val confidence: StateFlow<RepConfidence> = _confidence.asStateFlow()

    /** Velocidad media (m/s) de cada repetición de la serie actual. */
    private val _velocities = MutableStateFlow<List<Double>>(emptyList())
    val velocities: StateFlow<List<Double>> = _velocities.asStateFlow()

    /** Calibrando: se registran los picos y al terminar se calcula el umbral. */
    private val _calibrating = MutableStateFlow(false)
    val calibrating: StateFlow<Boolean> = _calibrating.asStateFlow()

    val threshold: Float get() = detector.threshold

    val isAvailable: Boolean get() = accelerometer != null
    private var running = false

    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var smoothed = 0f
    private var lastSampleNs = 0L
    private val repSamples = ArrayList<Float>(256)
    private var dtSum = 0.0
    private var dtCount = 0

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
        detector.reset(nowMs())
        smoothed = 0f
        repSamples.clear()
        _velocities.value = emptyList()
        publish()
    }

    /** Corrige el recuento de un toque (+1 / −1) antes de guardar la serie. */
    fun adjust(delta: Int) {
        detector.adjust(delta)
        publish()
    }

    /** Empieza la calibración: unas repeticiones normales y luego [finishCalibration]. */
    fun startCalibration() {
        _calibrating.value = true
        start()
        detector.startCalibration(nowMs())
        repSamples.clear()
        publish()
    }

    /** Termina la calibración: el umbral nuevo (o el de antes si no se vieron bien las reps) y cuántas se vieron. */
    fun finishCalibration(): RepCalibration {
        _calibrating.value = false
        val result = detector.finishCalibration()
        reset()
        return result
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
        val before = detector.count
        val accepted = detector.onSample(nowMs(), smoothed)
        if (accepted) onRep()
        if (detector.count != before || accepted) publish()
    }

    /** Velocidad de la repetición que acaba de terminar (muestras desde la anterior). */
    private fun onRep() {
        val dt = if (dtCount > 0) (dtSum / dtCount).toFloat() else DEFAULT_DT
        val v = BarVelocity.meanConcentricVelocity(repSamples, dt)
        // La primera rep incluye la colocación: su velocidad no es comparable
        if (v > 0 && detector.reps.size > 1) _velocities.value = _velocities.value + v
        repSamples.clear()
    }

    private fun publish() {
        _reps.value = detector.count
        _confidence.value = detector.confidence()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun nowMs() = android.os.SystemClock.elapsedRealtime()

    companion object {
        const val DEFAULT_THRESHOLD = RepDetector.DEFAULT_THRESHOLD
        private const val GRAVITY_ALPHA = 0.9f
        private const val SMOOTH_ALPHA = 0.3f
        private const val MAX_SAMPLES = 1_000
        private const val DEFAULT_DT = 0.02f
    }
}
