package com.bestrong.core.training

/** Zonas de pulso y recuperación entre series. */
object HeartRate {
    /** Pulso máximo estimado (Tanaka): 208 − 0,7 × edad. */
    fun estimatedMax(age: Int): Int = (208 - 0.7 * age).toInt()

    /** Zona 1–5 según el % del pulso máximo (50-60, 60-70, 70-80, 80-90, 90+). 0 = por debajo. */
    fun zone(bpm: Int, maxHr: Int): Int {
        if (maxHr <= 0 || bpm <= 0) return 0
        val pct = bpm * 100 / maxHr
        return when {
            pct >= 90 -> 5
            pct >= 80 -> 4
            pct >= 70 -> 3
            pct >= 60 -> 2
            pct >= 50 -> 1
            else -> 0
        }
    }

    /**
     * Pulso al que se considera recuperado tras una serie: la mitad del camino entre el
     * pico de la serie y el pulso en reposo, y nunca por encima del 65 % del máximo.
     */
    fun recoveryTarget(peakBpm: Int, restingBpm: Int = 65, maxHr: Int = 190): Int {
        val half = restingBpm + (peakBpm - restingBpm) / 2
        return minOf(half, maxHr * 65 / 100).coerceAtLeast(restingBpm + 5)
    }

    /** ¿Ya puede hacer la siguiente serie? Tras un mínimo de [minRestSec] y con el pulso en el objetivo. */
    fun isRecovered(currentBpm: Int, peakBpm: Int, restedSec: Int, minRestSec: Int = 45, restingBpm: Int = 65, maxHr: Int = 190): Boolean =
        restedSec >= minRestSec && currentBpm in 1..recoveryTarget(peakBpm, restingBpm, maxHr)
}
