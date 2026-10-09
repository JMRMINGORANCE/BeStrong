package com.bestrong.core.training

/** Cómo llegas hoy: 0–100 a partir del sueño, el pulso en reposo y cómo te sentiste en el último entreno. */
data class ReadinessReport(val score: Int, val sleepHours: Double?, val restingHr: Int?, val baselineRestingHr: Int?) {
    val level: Level get() = when {
        score >= 75 -> Level.HIGH
        score >= 50 -> Level.NORMAL
        else -> Level.LOW
    }

    enum class Level { HIGH, NORMAL, LOW }
}

object Readiness {
    /**
     * - Sueño: 8 h = 100 %, cada hora menos resta (menos de 5 h = 0 %).
     * - Pulso en reposo: igual o por debajo de tu media = 100 %; cada ppm por encima resta un 8 %.
     * - Última valoración (energía y dolor) si existe.
     * Los datos que falten no cuentan (se reparte el peso entre los que haya).
     */
    fun score(sleepHours: Double?, restingHr: Int?, baselineRestingHr: Int?, lastRating: com.bestrong.core.model.SessionRating? = null): ReadinessReport {
        val parts = mutableListOf<Pair<Double, Double>>() // (valor 0..1, peso)
        sleepHours?.let { parts += ((it - 5.0) / 3.0).coerceIn(0.0, 1.0) to 0.45 }
        if (restingHr != null && baselineRestingHr != null && baselineRestingHr > 0) {
            parts += (1.0 - (restingHr - baselineRestingHr).coerceAtLeast(0) * 0.08).coerceIn(0.0, 1.0) to 0.35
        }
        lastRating?.let { r -> parts += (((r.energy - 1) / 4.0) * 0.6 + ((5 - r.pain) / 4.0) * 0.4).coerceIn(0.0, 1.0) to 0.20 }
        val total = parts.sumOf { it.second }
        val score = if (total == 0.0) 70 else (parts.sumOf { it.first * it.second } / total * 100).toInt()
        return ReadinessReport(score, sleepHours, restingHr, baselineRestingHr)
    }

    /** Ajuste sugerido de la carga del día: −10 % si estás bajo, igual si normal, +2,5 % si muy bien. */
    fun loadFactor(report: ReadinessReport): Double = when (report.level) {
        ReadinessReport.Level.LOW -> 0.90
        ReadinessReport.Level.NORMAL -> 1.0
        ReadinessReport.Level.HIGH -> 1.025
    }
}

object Nutrition {
    /** Proteína diaria orientativa: 1,6 g/kg (mantener) a 2,2 g/kg (volumen o definición). */
    fun proteinRangeGrams(bodyWeightKg: Double): IntRange = (bodyWeightKg * 1.6).toInt()..(bodyWeightKg * 2.2).toInt()

    /** Agua orientativa durante el entreno: ~500 ml por hora más 250 ml por cada 30 min extra de calor/cardio. */
    fun waterDuringWorkoutMl(minutes: Int): Int = ((minutes / 60.0) * 500).toInt().coerceAtLeast(250)
}
