package com.bestrong.core.stats

import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Franja del día en que empezó un entreno. */
enum class DayPart { MORNING, AFTERNOON, EVENING }

/** Rendimiento medio en una franja: % de tu mejor 1RM estimado de cada ejercicio (1,0 = tu mejor marca). */
data class DayPartStat(val part: DayPart, val sessions: Int, val relativeStrength: Double)

object TimeOfDay {
    private const val NOON = 12
    private const val EVENING = 18
    private const val EARLY = 5

    fun partOf(hour: Int): DayPart = when {
        hour in EARLY until NOON -> DayPart.MORNING
        hour in NOON until EVENING -> DayPart.AFTERNOON
        else -> DayPart.EVENING
    }

    /**
     * Cómo rindes según la hora: para cada entreno de los [weeks] últimas semanas, el 1RM estimado de cada
     * ejercicio frente a tu mejor de ese ejercicio en el periodo, y la media por franja. Solo franjas con
     * al menos [minSessions] entrenos.
     */
    fun stats(history: List<Workout>, zone: ZoneId, nowEpochMs: Long, weeks: Int = 12, minSessions: Int = 3): List<DayPartStat> =
        relativeBy(history, zone, nowEpochMs, weeks, minSessions) { partOf(it.hour) }
            .map { (part, n, rel) -> DayPartStat(part, n, rel) }

    /** Lo mismo por día de la semana (lunes, martes…), el mejor primero. */
    fun byWeekday(history: List<Workout>, zone: ZoneId, nowEpochMs: Long, weeks: Int = 12, minSessions: Int = 3): List<Triple<DayOfWeek, Int, Double>> =
        relativeBy(history, zone, nowEpochMs, weeks, minSessions) { it.dayOfWeek }

    /** (grupo, entrenos, % medio de tu mejor marca) agrupando cada entreno por [key] de su hora de inicio. */
    private fun <K> relativeBy(
        history: List<Workout>,
        zone: ZoneId,
        nowEpochMs: Long,
        weeks: Int,
        minSessions: Int,
        key: (java.time.ZonedDateTime) -> K,
    ): List<Triple<K, Int, Double>> {
        val since = nowEpochMs - weeks * 7L * 86_400_000L
        val recent = history.filter { it.isFinished && it.startedAtEpochMs >= since }

        // Solo peso × reps: en dominadas lastradas el «1RM» saldría del lastre
        fun bestOf(w: Workout) = w.exercises.filter { it.exercise.tracking == TrackingType.WEIGHT_REPS }
            .associate { we -> we.exercise.id to (we.completedSets.filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0) }
            .filterValues { it > 0 }
        val best = mutableMapOf<String, Double>()
        recent.forEach { w -> bestOf(w).forEach { (id, e) -> best.merge(id, e, ::maxOf) } }
        return recent.mapNotNull { w ->
            val ratios = bestOf(w).mapNotNull { (id, e) -> best[id]?.let { e / it } }
            if (ratios.isEmpty()) null else key(Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone)) to ratios.average()
        }.groupBy({ it.first }, { it.second })
            .filterValues { it.size >= minSessions }
            .map { (k, values) -> Triple(k, values.size, values.average()) }
            .sortedByDescending { it.third }
    }
}
