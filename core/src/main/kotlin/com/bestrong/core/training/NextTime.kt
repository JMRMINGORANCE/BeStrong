package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise

/** Qué hacer la próxima vez con un ejercicio: subir, mantener o bajar la carga. */
data class NextTimeSuggestion(val exerciseId: String, val exerciseName: String, val weightKg: Double, val reps: Int, val direction: Int)

/**
 * Doble progresión sencilla al terminar el entreno:
 * - todas las series con el peso más alto con las reps de la primera de ellas y RPE < 9,5 → subir un escalón
 *   (las de descarga, más ligeras, no cuentan);
 * - alguna serie a RPE ≥ 9,5 con menos reps que la primera → bajar un 5 %;
 * - en otro caso, repetir el mismo peso e intentar sumar reps.
 */
object NextTime {
    fun suggest(we: WorkoutExercise, stepKg: Double): NextTimeSuggestion? {
        if (we.exercise.tracking != TrackingType.WEIGHT_REPS) return null
        val work = we.sets.filter { it.completed && it.type != SetType.WARMUP && it.countsForRecords && it.weightKg > 0 }
        if (work.isEmpty()) return null
        val top = work.maxOf { it.weightKg }
        // Solo las series con el peso más alto: las de descarga (más ligeras, más o menos reps) no marcan el objetivo
        val topSets = work.filter { it.weightKg >= top }
        val target = topSets.first().reps
        val maxRpe = topSets.mapNotNull { it.rpe }.maxOrNull()
        val allReps = topSets.all { it.reps >= target } && hasEvidence(topSets)
        val name = we.exercise.name
        val high = Autoregulation.high(we.targetRpe)
        return when {
            allReps && (maxRpe == null || maxRpe < high) -> NextTimeSuggestion(we.exercise.id, name, top + stepKg, target, 1)
            !allReps && maxRpe != null && maxRpe >= high -> NextTimeSuggestion(we.exercise.id, name, lowered(top, stepKg), target, -1)
            else -> NextTimeSuggestion(we.exercise.id, name, top, target, 0)
        }
    }

    /** Un 5 % menos, redondeado al escalón y al menos un escalón por debajo. */
    private fun lowered(top: Double, stepKg: Double): Double {
        val lower = if (stepKg > 0) Math.floor(top * 0.95 / stepKg + 0.5) * stepKg else top * 0.95
        return lower.coerceAtMost(top - stepKg).coerceAtLeast(0.0)
    }

    /** Con una sola serie pesada (subiendo en pirámide) y sin RPE no hay con qué comparar: no se sube a ciegas. */
    private fun hasEvidence(topSets: List<com.bestrong.core.model.WorkoutSet>): Boolean = topSets.size >= 2 || topSets.any { it.rpe != null }

    fun forWorkout(workout: Workout, stepKg: Double): List<NextTimeSuggestion> = forWorkout(workout) { stepKg }

    /** Con incremento propio por ejercicio (mancuernas de 2 kg, máquinas de 5 kg…). */
    fun forWorkout(workout: Workout, stepFor: (exerciseId: String) -> Double): List<NextTimeSuggestion> =
        workout.exercises.mapNotNull { suggest(it, stepFor(it.exercise.id)) }
}
