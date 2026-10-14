package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.WorkoutExercise
import kotlin.math.floor

/** Ajuste propuesto para las series que quedan del ejercicio tras anotar el RPE de una serie. */
data class RpeAdjustment(val newWeightKg: Double, val direction: Int)

/**
 * Autorregulación por RPE respecto al objetivo del ejercicio (8 si no tiene, es decir, 2 reps en recámara):
 * - RPE ≥ objetivo + 1,5 (9,5) → bajar un 5 % las series pendientes,
 * - RPE ≤ objetivo − 1 (7) → subir un escalón (2,5 kg / 5 lb).
 * Devuelve null si no hay nada que ajustar.
 */
object Autoregulation {
    const val TARGET = 8.0
    const val HIGH = 9.5
    const val LOW = 7.0

    /** RPE a partir del cual se baja, con el objetivo [target] (con 10 nunca: es una serie a tope buscada). */
    fun high(target: Double?): Double = HIGH + ((target ?: TARGET) - TARGET)
    fun low(target: Double?): Double = LOW + ((target ?: TARGET) - TARGET)

    fun suggest(exercise: WorkoutExercise, completedSetId: String, stepKg: Double): RpeAdjustment? {
        val done = exercise.sets.firstOrNull { it.id == completedSetId } ?: return null
        val rpe = done.rpe ?: return null
        if (done.type == SetType.WARMUP || done.weightKg <= 0) return null
        val pending = exercise.sets.filter { !it.completed && it.type != SetType.WARMUP }
        if (pending.isEmpty()) return null
        val current = done.weightKg
        return when {
            rpe >= high(exercise.targetRpe) -> RpeAdjustment(round(current * 0.95, stepKg).coerceAtLeast(0.0), -1).takeIf { it.newWeightKg < current }
            rpe <= low(exercise.targetRpe) -> RpeAdjustment(current + stepKg, 1)
            else -> null
        }
    }

    /** Aplica el nuevo peso a las series pendientes de trabajo. */
    fun apply(exercise: WorkoutExercise, adjustment: RpeAdjustment): WorkoutExercise =
        exercise.copy(sets = exercise.sets.map { if (!it.completed && it.type != SetType.WARMUP) it.copy(weightKg = adjustment.newWeightKg) else it })

    private fun round(kg: Double, step: Double) = if (step <= 0) kg else floor(kg / step + 0.5) * step
}
