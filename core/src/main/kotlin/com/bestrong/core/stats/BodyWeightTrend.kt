package com.bestrong.core.stats

/** Tendencia del peso corporal: media de los últimos 7 días frente a la de los 7 anteriores. */
object BodyWeightTrend {
    private const val WEEK_MS = 7 * 86_400_000L

    data class Trend(val averageKg: Double, val changeKg: Double?)

    /** [points]: (fecha, kg). Null si no hay pesadas en la última semana. */
    fun of(points: List<Pair<Long, Double>>, nowEpochMs: Long): Trend? {
        fun avg(from: Long, to: Long) = points.filter { it.first in from until to }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
        val current = avg(nowEpochMs - WEEK_MS, nowEpochMs + 1) ?: return null
        val previous = avg(nowEpochMs - 2 * WEEK_MS, nowEpochMs - WEEK_MS)
        return Trend(current, previous?.let { current - it })
    }
}

/** Cómo vas hacia un peso objetivo: lo que falta (kg, con signo hacia el objetivo) y cuándo llegarías. */
data class BodyWeightGoalProgress(val remainingKg: Double, val reached: Boolean, val etaEpochMs: Long?)

object BodyWeightGoal {
    /** Margen para dar el objetivo por cumplido (el peso baila con el agua y la comida). */
    private const val TOLERANCE_KG = 0.3

    /** Semanas recientes con las que se estima la fecha (un volumen de hace años no dice nada del ritmo de hoy). */
    private const val TREND_WEEKS = 12L

    /**
     * [points] (fecha, kg) ordenados. Sirve para bajar y para subir: la dirección la marca [startKg], el peso
     * cuando se puso el objetivo (si no se guardó, el actual). Cumplido al cruzarlo o quedarse a menos de 0,3 kg.
     */
    fun progress(points: List<Pair<Long, Double>>, goalKg: Double, startKg: Double? = null, nowEpochMs: Long = System.currentTimeMillis()): BodyWeightGoalProgress? {
        if (points.isEmpty()) return null
        val current = points.last().second
        val losing = (startKg ?: current) > goalKg
        val crossed = if (losing) current <= goalKg else current >= goalKg
        val reached = crossed || kotlin.math.abs(current - goalKg) <= TOLERANCE_KG
        // Para bajar se invierte el signo: la predicción busca una línea que sube
        val sign = if (losing) -1.0 else 1.0
        val recent = points.filter { it.first >= nowEpochMs - TREND_WEEKS * 7 * 86_400_000L }
        val eta = if (reached) null else Strength.predictDate(recent.map { it.first to it.second * sign }, goalKg * sign, nowEpochMs)
        return BodyWeightGoalProgress(remainingKg = kotlin.math.abs(current - goalKg), reached = reached, etaEpochMs = eta)
    }
}
