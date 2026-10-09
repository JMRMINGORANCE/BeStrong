package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.WorkoutExercise
import kotlin.math.floor

/** Ajuste propuesto para las series que quedan del ejercicio tras anotar el RPE de una serie. */
data class RpeAdjustment(val newWeightKg: Double, val direction: Int)

/**
 * Autorregulación por RPE (objetivo ≈ 8, es decir, 2 reps en recámara):
 * - RPE ≥ 9,5 → bajar un 5 % las series pendientes,
 * - RPE ≤ 7 → subir un escalón (2,5 kg / 5 lb).
 * Devuelve null si no hay nada que ajustar.
 */
object Autoregulation {
    const val HIGH = 9.5
    const val LOW = 7.0

    fun suggest(exercise: WorkoutExercise, completedSetId: String, stepKg: Double): RpeAdjustment? {
        val done = exercise.sets.firstOrNull { it.id == completedSetId } ?: return null
        val rpe = done.rpe ?: return null
        if (done.type == SetType.WARMUP || done.weightKg <= 0) return null
        val pending = exercise.sets.filter { !it.completed && it.type != SetType.WARMUP }
        if (pending.isEmpty()) return null
        val current = done.weightKg
        return when {
            rpe >= HIGH -> RpeAdjustment(round(current * 0.95, stepKg).coerceAtLeast(0.0), -1).takeIf { it.newWeightKg < current }
            rpe <= LOW -> RpeAdjustment(current + stepKg, 1)
            else -> null
        }
    }

    /** Aplica el nuevo peso a las series pendientes de trabajo. */
    fun apply(exercise: WorkoutExercise, adjustment: RpeAdjustment): WorkoutExercise =
        exercise.copy(sets = exercise.sets.map { if (!it.completed && it.type != SetType.WARMUP) it.copy(weightKg = adjustment.newWeightKg) else it })

    private fun round(kg: Double, step: Double) = if (step <= 0) kg else floor(kg / step + 0.5) * step
}
