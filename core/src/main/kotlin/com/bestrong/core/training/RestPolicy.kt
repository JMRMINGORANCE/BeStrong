package com.bestrong.core.training

import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise

/**
 * Descanso automático según el tipo de ejercicio:
 * - básicos pesados con barra (multiarticulares de pierna, espalda y empuje): 3 min,
 * - otros multiarticulares (mancuernas, máquinas, poleas): 2 min,
 * - aislamiento: 90 s,
 * - abdominales, gemelos, antebrazos y ejercicios por tiempo: 60 s.
 * Si en el historial cambiaste el descanso de ese ejercicio, se usa el que pusiste la última vez.
 */
object RestPolicy {
    const val HEAVY = 180
    const val COMPOUND = 120
    const val ISOLATION = 90
    const val SHORT = 60

    private val smallMuscles = setOf(MuscleGroup.ABS, MuscleGroup.CALVES, MuscleGroup.FOREARMS, MuscleGroup.CARDIO)
    private val armMuscles = setOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS)

    fun defaultFor(exercise: Exercise): Int {
        val compound = exercise.secondaryMuscles.any { it !in smallMuscles } && exercise.primaryMuscle !in armMuscles
        return when {
            exercise.tracking == TrackingType.DURATION || exercise.primaryMuscle in smallMuscles -> SHORT
            compound && exercise.equipment == Equipment.BARBELL -> HEAVY
            compound -> COMPOUND
            else -> ISOLATION
        }
    }

    /** Descanso para [exercise]: el último que usaste si lo cambiaste, si no el predeterminado. */
    fun restFor(exercise: Exercise, history: List<Workout>): Int =
        history.asSequence()
            .filter { it.isFinished }
            .sortedByDescending { it.startedAtEpochMs }
            .mapNotNull { w -> w.exercises.firstOrNull { it.exercise.id == exercise.id } }
            .firstOrNull()
            ?.restSeconds
            ?.takeIf { it != WorkoutExercise.DEFAULT_REST_SECONDS }
            ?: defaultFor(exercise)
}
