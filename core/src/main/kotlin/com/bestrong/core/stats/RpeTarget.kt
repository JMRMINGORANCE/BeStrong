package com.bestrong.core.stats

import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout

/** RPE medio de las series efectivas de un ejercicio frente al objetivo de la rutina. */
data class RpeVsTarget(val exerciseId: String, val exerciseName: String, val targetRpe: Double, val averageRpe: Double) {
    /** −1 más fácil de lo previsto, 0 en el objetivo (±0,5), 1 más duro. */
    val direction: Int
        get() = when {
            averageRpe > targetRpe + TOLERANCE -> 1
            averageRpe < targetRpe - TOLERANCE -> -1
            else -> 0
        }

    private companion object {
        const val TOLERANCE = 0.5
    }
}

object RpeTarget {
    /** Ejercicios de peso × reps con RPE objetivo y alguna serie normal completada con RPE anotado. */
    fun vsTarget(workout: Workout): List<RpeVsTarget> = workout.exercises.mapNotNull { we ->
        val target = we.targetRpe?.takeIf { we.exercise.tracking == TrackingType.WEIGHT_REPS } ?: return@mapNotNull null
        // Solo las series normales: un drop o una al fallo planificados van a RPE 10 a propósito
        val rpes = we.sets.filter { it.completed && it.type == SetType.NORMAL && it.countsForRecords }.mapNotNull { it.rpe }
        if (rpes.isEmpty()) return@mapNotNull null
        RpeVsTarget(we.exercise.id, we.exercise.name, target, rpes.average())
    }
}
