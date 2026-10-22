package com.bestrong.core.library

import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.WorkoutSet

/**
 * Alternativas equivalentes para cambiar de ejercicio cuando el material está ocupado
 * (mismo músculo principal), y conversión aproximada del peso para no perder la progresión.
 */
object Alternatives {
    /** Mismo músculo principal; primero los que comparten músculos secundarios, luego por material parecido. */
    fun forExercise(exercise: Exercise, all: List<Exercise>, available: Set<Equipment>? = null): List<Exercise> {
        // Primero las variantes del mismo movimiento (peso muerto → rumano), aunque su músculo principal sea otro
        val family = ExerciseLibrary.familyOf(exercise.id).toSet()
        return all.filter { it.id != exercise.id && it.tracking == exercise.tracking && (it.id in family || it.primaryMuscle == exercise.primaryMuscle) }
            .filter { available == null || it.equipment in available }
            .sortedWith(
                compareBy<Exercise> { if (it.id in family) 0 else 1 }
                    .thenByDescending { it.secondaryMuscles.intersect(exercise.secondaryMuscles.toSet()).size }
                    .thenBy { if (it.equipment == exercise.equipment) 0 else 1 },
            )
    }

    /** Factor aproximado de carga entre materiales (barra = 1). Mancuernas: peso de cada una. */
    private fun factor(e: Equipment): Double = when (e) {
        Equipment.BARBELL, Equipment.SMITH -> 1.0
        Equipment.DUMBBELL, Equipment.KETTLEBELL -> 0.4
        Equipment.MACHINE -> 1.1
        Equipment.CABLE -> 0.7
        else -> 0.0
    }

    /**
     * Series para el ejercicio nuevo: si ya lo hiciste antes se usan esas; si no, se convierten
     * las del ejercicio sustituido con el factor de material (redondeado a [stepKg]).
     */
    fun convertSets(from: Exercise, to: Exercise, sets: List<WorkoutSet>, previousOfNew: List<WorkoutSet>, stepKg: Double): List<WorkoutSet> {
        if (previousOfNew.isNotEmpty()) {
            // Calentamientos con calentamientos y efectivas con efectivas (si no, un calentamiento se lleva el peso de trabajo)
            val (prevWarm, prevWork) = previousOfNew.partition { it.type == com.bestrong.core.model.SetType.WARMUP }
            var warmIndex = 0
            var workIndex = 0
            return sets.map { s ->
                val warm = s.type == com.bestrong.core.model.SetType.WARMUP
                val pool = if (warm) prevWarm else prevWork
                val i = if (warm) warmIndex++ else workIndex++
                // Sin calentamientos (o sin efectivas) la otra vez: se usa lo que haya del ejercicio nuevo
                val p = pool.getOrNull(i) ?: pool.lastOrNull() ?: previousOfNew.last()
                // Las reps planeadas eran del ejercicio de antes: ya no sirven para comparar
                if (s.completed) s else s.copy(weightKg = p.weightKg, reps = p.reps, repsRight = p.repsRight.takeIf { to.unilateral }, durationSec = p.durationSec, targetReps = null)
            }
        }
        val ff = factor(from.equipment)
        val ft = factor(to.equipment)
        // El peso solo se convierte entre variantes del mismo movimiento: 140 kg de peso muerto no son 140 kg de remo
        val sameMovement = to.id in ExerciseLibrary.familyOf(from.id)
        if (ff == 0.0 || ft == 0.0 || !sameMovement) return sets.map { if (it.completed) it else it.copy(weightKg = 0.0, repsRight = it.repsRight.takeIf { to.unilateral }, targetReps = null) }
        return sets.map { s ->
            // Un ejercicio a dos lados no tiene «derecha»: si no, sus reps contarían en el volumen y saldría un desequilibrio
            if (s.completed) s else s.copy(weightKg = (Math.round(s.weightKg * ft / ff / stepKg) * stepKg).coerceAtLeast(0.0), repsRight = s.repsRight.takeIf { to.unilateral }, targetReps = null)
        }
    }
}
