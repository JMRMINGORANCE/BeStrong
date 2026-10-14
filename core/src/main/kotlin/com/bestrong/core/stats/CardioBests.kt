package com.bestrong.core.stats

import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet

/** Mejores marcas de un ejercicio de cardio: la serie más larga en distancia y en tiempo, y la de mejor ritmo. */
data class CardioBests(val longestDistance: WorkoutSet?, val longestTime: WorkoutSet?, val bestPace: WorkoutSet?) {
    val isEmpty: Boolean get() = longestDistance == null && longestTime == null
}

object CardioBestsCalc {
    /** Distancia mínima para el mejor ritmo: un sprint de 200 m no es «tu ritmo». */
    private const val MIN_PACE_DISTANCE_M = 1000.0

    fun of(exerciseId: String, history: List<Workout>): CardioBests {
        val sets = history.filter { it.isFinished }
            .flatMap { w -> w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets } }
        return CardioBests(
            longestDistance = sets.filter { it.distanceM > 0 }.maxByOrNull { it.distanceM },
            longestTime = sets.filter { it.durationSec > 0 }.maxByOrNull { it.durationSec },
            bestPace = sets.filter { it.distanceM >= MIN_PACE_DISTANCE_M && it.durationSec > 0 }.minByOrNull { it.durationSec / it.distanceM },
        )
    }
}
