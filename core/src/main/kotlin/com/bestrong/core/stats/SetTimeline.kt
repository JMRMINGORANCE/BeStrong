package com.bestrong.core.stats

import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet

/** Tramo de tiempo de una serie hecha, para exportarla (Health Connect la muestra como segmento). */
data class SetWindow(val exercise: WorkoutExercise, val set: WorkoutSet, val startEpochMs: Long, val endEpochMs: Long)

object SetTimeline {
    /** Lo que se supone que dura una serie cuando solo se sabe cuándo se marcó. */
    const val DEFAULT_SET_MS = 40_000L

    /**
     * Tramos sin solaparse y dentro del entreno: cada serie acaba cuando se marcó y empieza
     * [DEFAULT_SET_MS] antes (o al acabar la anterior, si fue más tarde). El cardio con cronómetro
     * usa su inicio real. Las series sin hora de marcado (entrenos antiguos o importados) se omiten.
     */
    fun windows(workout: Workout): List<SetWindow> {
        val start = workout.startedAtEpochMs
        val end = workout.finishedAtEpochMs ?: return emptyList()
        var previousEnd = start
        return workout.exercises
            .flatMap { we -> we.completedSets.mapNotNull { s -> s.completedAtEpochMs?.let { Triple(we, s, it) } } }
            .sortedBy { it.third }
            .mapNotNull { (we, s, doneAt) ->
                val to = doneAt.coerceIn(start, end)
                val from = maxOf(previousEnd, s.startedAtEpochMs ?: (to - DEFAULT_SET_MS), start)
                if (to <= from) return@mapNotNull null
                previousEnd = to
                SetWindow(we, s, from, to)
            }
    }
}
