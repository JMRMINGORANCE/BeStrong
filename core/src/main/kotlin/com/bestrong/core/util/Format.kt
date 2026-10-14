package com.bestrong.core.util

import kotlin.math.roundToLong

/** 80.0 → "80", 82.5 → "82,5" */
fun formatWeight(kg: Double): String {
    val rounded = (kg * 100).roundToLong() / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').replace('.', ',')
    }
}

/** 12_345.0 → "12.345" */
fun formatVolume(kg: Double): String {
    val n = kg.roundToLong()
    return n.toString().reversed().chunked(3).joinToString(".").reversed()
}

/** 3725 s → "1h 02m", 125 s → "2:05" */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%d:%02d".format(m, s)
}

/** Diferencia con signo para comparar: «+3», «−2», «±0». */
fun signedCount(delta: Int): String = when {
    delta > 0 -> "+$delta"
    delta < 0 -> "−${-delta}"
    else -> "±0"
}

/** Diferencia de tiempo con signo: «+0:30», «−1:05», «±0:00». */
fun signedDuration(deltaSeconds: Int): String = when {
    deltaSeconds > 0 -> "+"
    deltaSeconds < 0 -> "−"
    else -> "±"
} + formatDuration(kotlin.math.abs(deltaSeconds).toLong())

/** Qué se compara con la vez anterior según cómo se registra el ejercicio (cardio y distancia, nada). */
enum class ComparisonKind {
    VOLUME,
    REPS,
    TIME,

    /** Distancia (y, si no se anotó, el tiempo): ejercicios de distancia y cardio. */
    DISTANCE,
    NONE,
    ;

    companion object {
        fun of(tracking: com.bestrong.core.model.TrackingType?): ComparisonKind = when (tracking) {
            com.bestrong.core.model.TrackingType.REPS_ONLY -> REPS
            com.bestrong.core.model.TrackingType.DURATION -> TIME
            com.bestrong.core.model.TrackingType.WEIGHT_REPS, null -> VOLUME
            com.bestrong.core.model.TrackingType.DISTANCE, com.bestrong.core.model.TrackingType.CARDIO -> DISTANCE
            else -> NONE
        }
    }
}

/** Distancia con signo en km: «+0.50 km», «−1.20 km», «±0 km». */
fun signedDistance(deltaMeters: Double): String = when {
    kotlin.math.abs(deltaMeters) < 5 -> "±0 km"
    deltaMeters > 0 -> "+" + "%.2f km".format(deltaMeters / 1000)
    else -> "−" + "%.2f km".format(-deltaMeters / 1000)
}

/** Cardio o distancia frente a la vez anterior: (cambio para el color, texto); por tiempo si no hay distancia. */
fun distanceComparison(c: com.bestrong.core.stats.WorkoutStats.ExerciseComparison): Pair<Double, String> =
    if (c.hasDistance) c.distanceDelta to signedDistance(c.distanceDelta) else c.durationDelta.toDouble() to signedDuration(c.durationDelta)
