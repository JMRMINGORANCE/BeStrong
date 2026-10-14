package com.bestrong.core.stats

import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet

/** Mejores marcas de un ejercicio de cardio: la serie más larga en distancia y en tiempo, y la de mejor ritmo. */
data class CardioBests(
    val longestDistance: WorkoutSet?,
    val longestTime: WorkoutSet?,
    val bestPace: WorkoutSet?,
    /** Totales (metros, segundos) de los últimos 7 y 30 días. */
    val week: Pair<Double, Int> = 0.0 to 0,
    val month: Pair<Double, Int> = 0.0 to 0,
) {
    val isEmpty: Boolean get() = longestDistance == null && longestTime == null
}

object CardioBestsCalc {
    /** Distancia mínima para el mejor ritmo: un sprint de 200 m no es «tu ritmo». */
    private const val MIN_PACE_DISTANCE_M = 1000.0

    private const val DAY_MS = 86_400_000L

    fun of(exerciseId: String, history: List<Workout>, nowEpochMs: Long = System.currentTimeMillis()): CardioBests {
        fun setsOf(ws: List<Workout>) = ws.filter { it.isFinished }
            .flatMap { w -> w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets } }
        val sets = setsOf(history)
        fun total(days: Long) = setsOf(history.filter { it.startedAtEpochMs >= nowEpochMs - days * DAY_MS })
            .let { s -> s.sumOf { it.distanceM } to s.sumOf { it.durationSec } }
        return CardioBests(
            longestDistance = sets.filter { it.distanceM > 0 }.maxByOrNull { it.distanceM },
            longestTime = sets.filter { it.durationSec > 0 }.maxByOrNull { it.durationSec },
            bestPace = sets.filter { it.distanceM >= MIN_PACE_DISTANCE_M && it.durationSec > 0 }.minByOrNull { it.durationSec / it.distanceM },
            week = total(7),
            month = total(30),
        )
    }
}
