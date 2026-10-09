package com.bestrong.core.sensors

import kotlin.math.abs

/**
 * Velocidad de la barra a partir de la aceleración vertical de la muñeca (m/s², sin gravedad).
 * Se integra la aceleración en cada fase de subida (velocidad positiva) y se toma la media.
 * Es una estimación: el reloj va en la muñeca, no en la barra.
 */
object BarVelocity {
    /** Velocidad media concéntrica (m/s) de una repetición; [dtSec] = intervalo entre muestras. */
    fun meanConcentricVelocity(verticalAccel: List<Float>, dtSec: Float): Double {
        if (verticalAccel.size < 3 || dtSec <= 0f) return 0.0
        // Quita la deriva: media cero en la ventana de la repetición
        val bias = verticalAccel.average().toFloat()
        var v = 0.0
        var sum = 0.0
        var n = 0
        verticalAccel.forEach { a ->
            v += (a - bias) * dtSec
            if (v > 0) {
                sum += v
                n++
            }
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** Pérdida de velocidad (0..1) de la última rep respecto a la más rápida de la serie. */
    fun velocityLoss(velocities: List<Double>): Double {
        val best = velocities.maxOrNull() ?: return 0.0
        if (best <= 0) return 0.0
        return ((best - velocities.last()) / best).coerceIn(0.0, 1.0)
    }

    /**
     * Repeticiones en recámara estimadas por la pérdida de velocidad (relación aproximada
     * de la literatura de entrenamiento basado en velocidad):
     * <10 % → 4+, 10–20 % → 3, 20–30 % → 2, 30–40 % → 1, ≥40 % → 0.
     */
    fun estimatedRir(velocities: List<Double>): Int? {
        if (velocities.size < 2) return null
        val loss = velocityLoss(velocities)
        return when {
            loss < 0.10 -> 4
            loss < 0.20 -> 3
            loss < 0.30 -> 2
            loss < 0.40 -> 1
            else -> 0
        }
    }

    /** RPE equivalente (10 − RIR). */
    fun rpeFromRir(rir: Int): Double = (10 - rir).coerceIn(6, 10).toDouble()

    /**
     * Calibración del contador: con los picos de aceleración de unas reps de prueba,
     * el umbral queda al 55 % del pico medio (ni muy sensible ni muy duro).
     */
    fun calibratedThreshold(peaks: List<Float>, default: Float = 1.2f): Float {
        val valid = peaks.filter { abs(it) > 0.3f }
        if (valid.size < 2) return default
        return (valid.average() * 0.55).toFloat().coerceIn(0.4f, 4f)
    }
}
