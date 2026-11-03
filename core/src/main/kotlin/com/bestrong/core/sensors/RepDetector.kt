package com.bestrong.core.sensors

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Qué fiable es el recuento del acelerómetro en la serie actual. */
enum class RepConfidence { HIGH, MEDIUM, LOW }

/** Resultado de calibrar con unas repeticiones de prueba. */
data class RepCalibration(val threshold: Float, val repsSeen: Int, val ok: Boolean)

/**
 * Detección de repeticiones a partir de la aceleración vertical (m/s², sin gravedad y suavizada) de la muñeca.
 * Es lógica pura (sin Android) para poder probarla con señales sintéticas.
 *
 * En cada repetición hay un pico positivo en el punto bajo (frenar la bajada y empezar a subir). Para no contar de más:
 * - **Arranque**: lo primero de la serie (coger la barra o las mancuernas) se ignora durante [WARMUP_MS].
 * - **Golpes**: un pico por encima del umbral más corto que [MIN_PEAK_MS] (dejar la barra en los soportes, un golpe)
 *   no es una repetición.
 * - **Rebotes**: dos picos más juntos que el intervalo mínimo cuentan como uno; con unas cuantas reps hechas ese mínimo
 *   se adapta al ritmo de la serie.
 * - **Desproporcionados**: con dos o más reps contadas, un pico mucho mayor que los demás (recolocar la barra) se descarta.
 */
class RepDetector(var threshold: Float = DEFAULT_THRESHOLD) {
    /** Una repetición aceptada: cuándo empezó su pico y su valor máximo. */
    data class Rep(val atMs: Long, val peak: Float)

    private val accepted = ArrayList<Rep>()
    private val calibrationPeaks = ArrayList<Float>()

    /** Picos descartados (golpes, rebotes, desproporcionados) en la serie actual. */
    var rejected: Int = 0
        private set

    /** Corrección a mano (+1 / −1) sobre lo detectado. */
    private var manual = 0

    /** Calibrando: umbral bajo para ver todos los picos y se guardan para calcular el nuevo umbral. */
    var calibrating: Boolean = false
        private set

    private var startMs = Long.MIN_VALUE
    private var inPeak = false
    private var peakStartMs = 0L
    private var peakMax = 0f

    /** Repeticiones de la serie (detectadas + corrección a mano), nunca negativas. */
    val count: Int get() = (accepted.size + manual).coerceAtLeast(0)

    val reps: List<Rep> get() = accepted

    /** Empieza una serie nueva: [nowMs] marca el inicio de la ventana de arranque. */
    fun reset(nowMs: Long) {
        accepted.clear()
        rejected = 0
        manual = 0
        inPeak = false
        peakMax = 0f
        startMs = nowMs
    }

    /** Corrige el recuento de un toque (antes de guardar la serie). */
    fun adjust(delta: Int) {
        manual = (manual + delta).coerceAtLeast(-accepted.size)
    }

    fun startCalibration(nowMs: Long) {
        calibrationPeaks.clear()
        calibrating = true
        reset(nowMs)
    }

    /** Termina la calibración: el umbral nuevo y si las reps de prueba se vieron con claridad. */
    fun finishCalibration(): RepCalibration {
        calibrating = false
        val valid = calibrationPeaks.filter { abs(it) > MIN_CALIBRATION_PEAK }
        val spread = coefficientOfVariation(valid)
        val ok = valid.size >= MIN_CALIBRATION_REPS && spread < MAX_CALIBRATION_SPREAD
        if (ok) threshold = BarVelocity.calibratedThreshold(valid, threshold)
        return RepCalibration(threshold, valid.size, ok)
    }

    /**
     * Una muestra nueva de aceleración vertical suavizada. Devuelve true si con ella se acepta una repetición (al
     * terminar su pico), para que quien escucha calcule la velocidad de esa rep.
     */
    fun onSample(timeMs: Long, vertical: Float): Boolean {
        if (startMs == Long.MIN_VALUE) startMs = timeMs
        val limit = if (calibrating) CALIBRATION_THRESHOLD else threshold
        var repEnded = false
        when {
            timeMs - startMs < WARMUP_MS -> Unit
            !inPeak -> if (vertical > limit) {
                inPeak = true
                peakStartMs = timeMs
                peakMax = vertical
            }
            else -> {
                peakMax = max(peakMax, vertical)
                // El pico termina al bajar claramente (histéresis): así una meseta con ruido no son dos reps
                if (vertical <= max(RESET_THRESHOLD, limit * END_FRACTION)) {
                    inPeak = false
                    repEnded = true
                }
            }
        }
        return repEnded && evaluate(peakStartMs, timeMs - peakStartMs, peakMax)
    }

    private fun evaluate(atMs: Long, durationMs: Long, peak: Float): Boolean {
        if (calibrating) {
            if (durationMs >= MIN_PEAK_MS) {
                calibrationPeaks += peak
                accepted += Rep(atMs, peak)
            }
            return false
        }
        val last = accepted.lastOrNull()
        val accept = when {
            durationMs < MIN_PEAK_MS -> false
            last != null && atMs - last.atMs < minIntervalMs() -> false
            accepted.size >= 2 && peak > OUTLIER_FACTOR * median(accepted.map { it.peak }) -> false
            else -> true
        }
        if (accept) accepted += Rep(atMs, peak) else rejected++
        return accept
    }

    /** Intervalo mínimo entre reps: fijo al principio y, con unas cuantas, una fracción del ritmo de la serie. */
    private fun minIntervalMs(): Long {
        // Con pocas reps la mediana del ritmo no es fiable (descartaría una rep rápida pero buena)
        if (accepted.size < ADAPTIVE_MIN_REPS) return MIN_INTERVAL_MS
        val intervals = accepted.zipWithNext { a, b -> (b.atMs - a.atMs).toFloat() }
        return max(MIN_INTERVAL_MS, (median(intervals) * ADAPTIVE_INTERVAL_FRACTION).toLong())
    }

    /**
     * Confianza del recuento: alta con un ritmo y unos picos regulares y casi nada descartado; baja con muy pocas reps,
     * un ritmo muy irregular o muchos picos descartados.
     */
    fun confidence(): RepConfidence {
        val n = accepted.size
        if (n < 2) return RepConfidence.LOW
        val intervals = accepted.zipWithNext { a, b -> (b.atMs - a.atMs).toFloat() }
        val cvInterval = coefficientOfVariation(intervals)
        val cvPeak = coefficientOfVariation(accepted.map { it.peak })
        val rejectedRatio = rejected.toFloat() / (n + rejected)
        val regular = cvInterval < HIGH_CV_INTERVAL && cvPeak < HIGH_CV_PEAK
        return when {
            cvInterval > LOW_CV || rejectedRatio > LOW_REJECTED -> RepConfidence.LOW
            n >= 3 && regular && rejected <= 1 -> RepConfidence.HIGH
            else -> RepConfidence.MEDIUM
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 1.2f
        const val WARMUP_MS = 800L
        const val MIN_PEAK_MS = 60L
        const val MIN_INTERVAL_MS = 600L
        private const val ADAPTIVE_INTERVAL_FRACTION = 0.45f
        private const val ADAPTIVE_MIN_REPS = 4
        private const val CALIBRATION_THRESHOLD = 0.5f
        private const val RESET_THRESHOLD = 0.3f
        private const val END_FRACTION = 0.4f
        private const val OUTLIER_FACTOR = 2.5f
        private const val MIN_CALIBRATION_PEAK = 0.3f
        const val MIN_CALIBRATION_REPS = 3
        private const val MAX_CALIBRATION_SPREAD = 0.6f
        private const val LOW_CV = 0.6f
        private const val LOW_REJECTED = 0.4f
        private const val HIGH_CV_INTERVAL = 0.35f
        private const val HIGH_CV_PEAK = 0.45f

        private fun median(values: List<Float>): Float {
            if (values.isEmpty()) return 0f
            val s = values.sorted()
            return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
        }

        /** Desviación típica / media (0 si no hay datos o la media es 0). */
        private fun coefficientOfVariation(values: List<Float>): Float {
            val mean = values.average()
            if (values.size < 2 || mean == 0.0) return 0f
            val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            return (sd / abs(mean)).toFloat()
        }
    }
}
