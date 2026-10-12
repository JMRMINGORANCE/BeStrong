package com.bestrong.core.training

import com.bestrong.core.model.HeartRateSample
import kotlin.math.roundToInt

/**
 * Pulso durante el entreno a partir de las muestras del reloj.
 *
 * - Media **ponderada por tiempo**: cada lectura cuenta lo que dura hasta la siguiente (con un
 *   tope de [maxGapMs], para que un hueco sin sensor no pese de más). Una media simple de
 *   muestras se sesga cuando llegan a ritmos distintos (en lotes con la pantalla apagada).
 * - Máximo de todas las lecturas (también de las que llegan en lote).
 * - Serie reducida a una muestra cada [bucketSec] s (la media de cada tramo) para guardarla
 *   y exportarla a Health Connect sin ocupar demasiado.
 */
class HeartRateTracker(
    private val startedAtEpochMs: Long,
    private val bucketSec: Int = 10,
    private val maxGapMs: Long = 30_000,
) {
    private var hasLast = false
    private var lastBpm = 0
    private var lastAtMs = 0L
    private var weightedSum = 0.0
    private var weightMs = 0L
    private var maxBpm = 0
    private var count = 0
    private val buckets = sortedMapOf<Int, IntArray>() // tramo → [suma, n]

    /** Añade una lectura; las desordenadas o anteriores a la última se ignoran para la media. */
    fun add(bpm: Int, atEpochMs: Long) {
        if (bpm !in 25..250) return
        if (hasLast && atEpochMs > lastAtMs) {
            val dt = (atEpochMs - lastAtMs).coerceAtMost(maxGapMs)
            weightedSum += lastBpm.toDouble() * dt
            weightMs += dt
        }
        if (!hasLast || atEpochMs >= lastAtMs) {
            hasLast = true
            lastBpm = bpm
            lastAtMs = atEpochMs
        }
        if (bpm > maxBpm) maxBpm = bpm
        count++
        val offset = ((atEpochMs - startedAtEpochMs) / 1000).toInt().coerceAtLeast(0)
        val b = buckets.getOrPut(offset / bucketSec) { IntArray(2) }
        b[0] += bpm
        b[1] += 1
    }

    val latest: Int? get() = lastBpm.takeIf { hasLast }

    /** Media ponderada por tiempo (o la única lectura si solo hay una). */
    val average: Int?
        get() = when {
            count == 0 -> null
            weightMs == 0L -> lastBpm
            else -> (weightedSum / weightMs).roundToInt()
        }

    val max: Int? get() = maxBpm.takeIf { it > 0 }

    /** Serie para guardar con el entreno: un punto por tramo con la media de sus lecturas. */
    fun samples(): List<HeartRateSample> =
        buckets.map { (bucket, v) -> HeartRateSample(bucket * bucketSec, (v[0].toDouble() / v[1]).roundToInt()) }
}

/** Calorías de un entreno de fuerza cuando no hay reloj que las mida. */
object Energy {
    /**
     * MET del Compendio de Actividades Físicas (2024) para entrenamiento de fuerza con varios
     * ejercicios (8–15 repeticiones): 3,5. Es una estimación conservadora.
     */
    const val STRENGTH_MET = 3.5

    /** kcal = MET × peso (kg) × horas activas (sin pausas). Null si falta el peso. */
    fun estimateStrengthKcal(activeSeconds: Long, bodyWeightKg: Double?, met: Double = STRENGTH_MET): Int? {
        if (bodyWeightKg == null || bodyWeightKg <= 0 || activeSeconds <= 0) return null
        return (met * bodyWeightKg * activeSeconds / 3600.0).roundToInt()
    }
}
