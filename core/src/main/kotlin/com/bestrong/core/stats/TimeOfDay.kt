package com.bestrong.core.stats

import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
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
    fun stats(history: List<Workout>, zone: ZoneId, nowEpochMs: Long, weeks: Int = 12, minSessions: Int = 3): List<DayPartStat> {
        val since = nowEpochMs - weeks * 7L * 86_400_000L
        val recent = history.filter { it.isFinished && it.startedAtEpochMs >= since }
        val best = mutableMapOf<String, Double>()
        recent.forEach { w ->
            w.exercises.filter { it.exercise.tracking == TrackingType.WEIGHT_REPS }.forEach { we ->
                val e = we.completedSets.filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
                if (e > 0) best.merge(we.exercise.id, e, ::maxOf)
            }
        }
        return recent.mapNotNull { w ->
            // Solo peso × reps: en dominadas lastradas el «1RM» saldría del lastre
            val ratios = w.exercises.filter { it.exercise.tracking == TrackingType.WEIGHT_REPS }.mapNotNull { we ->
                val e = we.completedSets.filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
                best[we.exercise.id]?.takeIf { e > 0 && it > 0 }?.let { e / it }
            }
            if (ratios.isEmpty()) null else partOf(Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone).hour) to ratios.average()
        }.groupBy({ it.first }, { it.second })
            .filterValues { it.size >= minSessions }
            .map { (part, values) -> DayPartStat(part, values.size, values.average()) }
            .sortedByDescending { it.relativeStrength }
    }
}
