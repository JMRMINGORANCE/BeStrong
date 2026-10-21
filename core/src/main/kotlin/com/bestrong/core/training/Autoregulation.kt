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

    const val MAX_RPE = 10.0

    /** Tipos de serie tras los que se propone ajustar. */
    private val ADJUSTABLE = setOf(SetType.NORMAL, SetType.CLUSTER, SetType.FAILURE)

    /**
     * RPE a partir del cual se baja, con el objetivo [target]: como mucho 10 (con objetivo 9 un 10 sí es
     * pasarse); con objetivo 10 nunca, porque la serie a tope es lo buscado.
     */
    fun high(target: Double?): Double {
        val t = target ?: TARGET
        return if (t >= MAX_RPE) Double.MAX_VALUE else minOf(MAX_RPE, HIGH + (t - TARGET))
    }
    fun low(target: Double?): Double = LOW + ((target ?: TARGET) - TARGET)

    /** RPE máximo con el que una serie aún cuenta como «fácil» para subir peso: 9 sin objetivo, nunca 10 salvo buscándolo. */
    fun easyUpTo(target: Double?): Double {
        val t = target ?: TARGET
        return if (t >= MAX_RPE) MAX_RPE else minOf(t + 1, HIGH)
    }

    fun suggest(exercise: WorkoutExercise, completedSetId: String, stepKg: Double): RpeAdjustment? {
        // Solo con la serie ya hecha: un RPE puesto antes de marcarla no ajusta esa misma serie
        val done = exercise.sets.firstOrNull { it.id == completedSetId && it.completed } ?: return null
        val rpe = done.rpe ?: return null
        // Normales y cluster; tras una al fallo solo se sube (si se quedó lejos del fallo); un drop no cuenta
        if (done.type !in ADJUSTABLE || done.weightKg <= 0) return null
        val pending = exercise.sets.filter { !it.completed && it.type != SetType.WARMUP }
        if (pending.isEmpty()) return null
        val current = done.weightKg
        return when {
            done.type != SetType.FAILURE && rpe >= high(exercise.targetRpe) -> RpeAdjustment(round(current * 0.95, stepKg).coerceAtLeast(0.0), -1).takeIf { it.newWeightKg < current }
            rpe <= low(if (done.type == SetType.FAILURE) MAX_RPE else exercise.targetRpe) -> RpeAdjustment(current + stepKg, 1)
            else -> null
        }
    }

    /** Aplica el nuevo peso a las series pendientes de trabajo. */
    fun apply(exercise: WorkoutExercise, adjustment: RpeAdjustment): WorkoutExercise =
        // Los drops van por debajo a propósito: no se tocan
        exercise.copy(sets = exercise.sets.map { if (!it.completed && it.type in ADJUSTABLE) it.copy(weightKg = adjustment.newWeightKg) else it })

    private fun round(kg: Double, step: Double) = if (step <= 0) kg else floor(kg / step + 0.5) * step
}
