package com.bestrong.core.stats

import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet

/**
 * Qué mide el objetivo de cada tipo de ejercicio: 1RM estimado (kg) con peso, repeticiones en una serie
 * sin peso (dominadas, flexiones) y segundos en los de tiempo (plancha). Cardio y distancia no tienen.
 */
object ExerciseGoals {
    fun supports(tracking: TrackingType): Boolean = tracking == TrackingType.WEIGHT_REPS || tracking == TrackingType.REPS_ONLY || tracking == TrackingType.DURATION

    /** Valor de una serie para el objetivo (0 si no cuenta: calentamientos, asistidas…). */
    fun valueOf(set: WorkoutSet, tracking: TrackingType): Double = when {
        !set.countsForRecords -> 0.0
        tracking == TrackingType.WEIGHT_REPS -> set.estimatedOneRepMax
        // Como el récord de reps: por lado (el peor si es unilateral) y sin asistencia (peso negativo)
        tracking == TrackingType.REPS_ONLY -> if (set.weightKg < 0) 0.0 else (set.repsRight?.let { minOf(set.reps, it) } ?: set.reps).toDouble()
        tracking == TrackingType.DURATION -> set.durationSec.toDouble()
        else -> 0.0
    }

    /** Mejor valor de cada sesión (fecha, valor), de la más antigua a la más reciente. */
    fun points(history: List<Workout>, exerciseId: String, tracking: TrackingType): List<Pair<Long, Double>> =
        history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.mapNotNull { w ->
            w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets }
                .maxOfOrNull { valueOf(it, tracking) }?.takeIf { it > 0 }?.let { w.startedAtEpochMs to it }
        }
}
