package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise

/** Qué hacer la próxima vez con un ejercicio: subir, mantener o bajar la carga. */
data class NextTimeSuggestion(val exerciseId: String, val exerciseName: String, val weightKg: Double, val reps: Int, val direction: Int)

/**
 * Doble progresión sencilla al terminar el entreno:
 * - todas las series de trabajo con las reps de la primera y RPE < 9,5 → subir un escalón;
 * - alguna serie a RPE ≥ 9,5 con menos reps que la primera → bajar un 5 %;
 * - en otro caso, repetir el mismo peso e intentar sumar reps.
 */
object NextTime {
    fun suggest(we: WorkoutExercise, stepKg: Double): NextTimeSuggestion? {
        if (we.exercise.tracking != TrackingType.WEIGHT_REPS) return null
        val work = we.sets.filter { it.completed && it.type != SetType.WARMUP && it.countsForRecords && it.weightKg > 0 }
        if (work.isEmpty()) return null
        val top = work.maxOf { it.weightKg }
        val target = work.first().reps
        val maxRpe = work.mapNotNull { it.rpe }.maxOrNull()
        val allReps = work.all { it.reps >= target }
        val name = we.exercise.name
        return when {
            allReps && (maxRpe == null || maxRpe < Autoregulation.HIGH) -> NextTimeSuggestion(we.exercise.id, name, top + stepKg, target, 1)
            !allReps && maxRpe != null && maxRpe >= Autoregulation.HIGH -> {
                val lower = if (stepKg > 0) Math.floor(top * 0.95 / stepKg + 0.5) * stepKg else top * 0.95
                NextTimeSuggestion(we.exercise.id, name, lower.coerceAtMost(top - stepKg).coerceAtLeast(0.0), target, -1)
            }
            else -> NextTimeSuggestion(we.exercise.id, name, top, target, 0)
        }
    }

    fun forWorkout(workout: Workout, stepKg: Double): List<NextTimeSuggestion> = forWorkout(workout) { stepKg }

    /** Con incremento propio por ejercicio (mancuernas de 2 kg, máquinas de 5 kg…). */
    fun forWorkout(workout: Workout, stepFor: (exerciseId: String) -> Double): List<NextTimeSuggestion> =
        workout.exercises.mapNotNull { suggest(it, stepFor(it.exercise.id)) }
}
