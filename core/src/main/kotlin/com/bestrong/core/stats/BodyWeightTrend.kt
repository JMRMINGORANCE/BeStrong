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
