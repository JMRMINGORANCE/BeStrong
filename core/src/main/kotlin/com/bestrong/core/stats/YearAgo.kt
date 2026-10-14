package com.bestrong.core.stats

import com.bestrong.core.model.Exercise
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** «Hace un año»: la mejor serie de un ejercicio entonces frente a tu mejor reciente. */
data class YearAgoComparison(
    val exercise: Exercise,
    val thenWeightKg: Double,
    val thenReps: Int,
    val nowWeightKg: Double,
    val nowReps: Int,
    /** Cambio del 1RM estimado (fracción: 0,21 = +21 %). */
    val change: Double,
)

object YearAgo {
    private const val WINDOW_DAYS = 7L
    private const val RECENT_DAYS = 30L

    /**
     * Ejercicios con peso que hiciste hace un año (±[WINDOW_DAYS] días) y también en los últimos [RECENT_DAYS]:
     * mejor serie de entonces frente a la mejor reciente, el que más ha mejorado primero.
     */
    fun compare(history: List<Workout>, today: LocalDate, zone: ZoneId): List<YearAgoComparison> {
        fun dateOf(w: Workout) = Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone).toLocalDate()
        val center = today.minusYears(1)
        val finished = history.filter { it.isFinished }
        val then = finished.filter { val d = dateOf(it); !d.isBefore(center.minusDays(WINDOW_DAYS)) && !d.isAfter(center.plusDays(WINDOW_DAYS)) }
        val recent = finished.filter { !dateOf(it).isBefore(today.minusDays(RECENT_DAYS)) }
        fun bests(ws: List<Workout>) = ws.flatMap { w -> w.exercises.filter { it.exercise.tracking == TrackingType.WEIGHT_REPS } }
            .groupBy { it.exercise.id }
            .mapValues { (_, list) -> list.first().exercise to list.flatMap { it.completedSets }.filter { it.countsForRecords }.maxByOrNull { it.estimatedOneRepMax } }
            .filterValues { it.second != null && it.second!!.estimatedOneRepMax > 0 }
        val nowBests = bests(recent)
        return bests(then).mapNotNull { (id, pair) ->
            val (ex, old) = pair
            val new = nowBests[id]?.second ?: return@mapNotNull null
            YearAgoComparison(ex, old!!.weightKg, old.reps, new.weightKg, new.reps, new.estimatedOneRepMax / old.estimatedOneRepMax - 1)
        }.sortedByDescending { it.change }
    }
}
