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

/**
 * 12_345.0 → "12.345" (en idiomas con coma decimal) o "12 345" (espacio fino en el resto: en inglés «12.345»
 * se leería como doce coma tres, y la coma chocaría con los decimales de los pesos).
 */
fun formatVolume(kg: Double, locale: java.util.Locale = java.util.Locale.getDefault()): String {
    val n = kg.roundToLong()
    val separator = if (java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',') "." else "\u202F"
    val digits = kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(separator).reversed()
    return if (n < 0) "-$digits" else digits
}

/** Objetivo en km con los decimales que tenga, hasta dos: «20», «12,5», «12,04» (en el idioma del móvil). */
fun formatGoalKm(km: Double): String = when {
    km % 1.0 == 0.0 -> "%.0f".format(km)
    Math.round(km * 100) % 10 == 0L -> "%.1f".format(km)
    else -> "%.2f".format(km)
}

/** Km hechos con los mismos decimales que el objetivo (al menos uno): «12,04 de 12,04» y no «12,0 de 12,04». */
fun formatKmLike(km: Double, goalKm: Double?): String =
    // Truncado: 11,96 no se enseña como «12,0 de 12» con la barra sin llenar
    if (goalKm != null && Math.round(goalKm * 100) % 10 != 0L) {
        "%.2f".format(Math.floor(km * 100 + 1e-6) / 100)
    } else {
        "%.1f".format(Math.floor(km * 10 + 1e-6) / 10)
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

/** Cambio de distancia por debajo del cual se considera la misma (el GPS o el paso varían unos metros). */
private const val SAME_DISTANCE_M = 5.0

/** Distancia con signo: en metros por debajo de 1 km («+20 m», del trineo o el granjero) y en km por encima («+0.50 km»). */
fun signedDistance(deltaMeters: Double): String {
    val abs = kotlin.math.abs(deltaMeters)
    val sign = when {
        abs < SAME_DISTANCE_M -> return "±0 m"
        deltaMeters > 0 -> "+"
        else -> "−"
    }
    return sign + if (abs < 1000) "${kotlin.math.round(abs).toInt()} m" else "%.2f km".format(abs / 1000)
}

/** Cardio o distancia frente a la vez anterior: (cambio para el color, texto); por tiempo si no hay distancia. */
fun distanceComparison(c: com.bestrong.core.stats.WorkoutStats.ExerciseComparison): Pair<Double, String> = when {
    !c.hasDistance -> c.durationDelta.toDouble() to signedDuration(c.durationDelta)
    // Lo que el texto da por igual tampoco se pinta de verde
    kotlin.math.abs(c.distanceDelta) < SAME_DISTANCE_M -> 0.0 to signedDistance(c.distanceDelta)
    else -> c.distanceDelta to signedDistance(c.distanceDelta)
}

/** Cómo se mide el ritmo en cada cardio: min/km (correr, andar…), /500 m (remo) o km/h (bici). */
enum class PaceStyle {
    PER_KM,
    PER_500M,
    SPEED,
    ;

    companion object {
        fun of(exerciseId: String?): PaceStyle = when (exerciseId) {
            "rowing_machine" -> PER_500M
            "cycling", "stationary_bike" -> SPEED
            else -> PER_KM
        }
    }
}

/**
 * Ritmo con [durationSec] y [distanceM] según [style]: «5:30/km», «2:05/500 m» o «28.5 km/h»; null sin alguno de
 * los dos o con menos de 100 m (no es fiable).
 */
fun paceText(durationSec: Int, distanceM: Double, style: PaceStyle = PaceStyle.PER_KM): String? {
    if (durationSec <= 0 || distanceM < MIN_PACE_DISTANCE_M) return null
    fun minSec(seconds: Double): String = kotlin.math.round(seconds).toLong().let { "%d:%02d".format(it / 60, it % 60) }
    return when (style) {
        PaceStyle.PER_KM -> minSec(durationSec / (distanceM / 1000)) + "/km"
        PaceStyle.PER_500M -> minSec(durationSec / (distanceM / 500)) + "/500 m"
        PaceStyle.SPEED -> "%.1f km/h".format(distanceM / 1000 / (durationSec / 3600.0))
    }
}

private const val MIN_PACE_DISTANCE_M = 100.0
