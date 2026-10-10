package com.bestrong.core.sync

import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet

/**
 * Fusión del entreno en curso cuando se edita a la vez en el móvil y en el reloj.
 *
 * Regla principal: **nunca se pierde una serie completada** en cualquiera de los dos lados.
 * - Si son entrenos distintos (otro id), gana el más reciente.
 * - Nombre, notas y orden de ejercicios: los del más reciente.
 * - Ejercicios y series se emparejan por id. Una serie completada en un lado queda completada,
 *   con los valores del lado en que se completó (o del más reciente si ambos la completaron).
 * - Un ejercicio o serie que solo existe en el lado antiguo se conserva si tiene trabajo
 *   completado; si no, se asume que el lado reciente lo borró.
 */
object WorkoutMerge {

    fun merge(a: Workout, b: Workout): Workout {
        if (a.id != b.id) return if (b.updatedAtEpochMs >= a.updatedAtEpochMs) b else a
        val (older, newer) = if (b.updatedAtEpochMs >= a.updatedAtEpochMs) a to b else b to a
        val olderById = older.exercises.associateBy { it.id }
        val newerIds = newer.exercises.map { it.id }.toSet()

        val merged = newer.exercises.map { ne -> olderById[ne.id]?.let { mergeExercise(it, ne) } ?: ne }.toMutableList()
        // Ejercicios que solo están en el lado antiguo: se conservan si tienen series hechas
        older.exercises.forEachIndexed { index, oe ->
            if (oe.id !in newerIds && oe.sets.any { it.completed }) {
                merged.add(index.coerceAtMost(merged.size), oe)
            }
        }
        return newer.copy(
            exercises = merged,
            updatedAtEpochMs = maxOf(a.updatedAtEpochMs, b.updatedAtEpochMs),
            avgHeartRate = newer.avgHeartRate ?: older.avgHeartRate,
            maxHeartRate = newer.maxHeartRate ?: older.maxHeartRate,
            calories = newer.calories ?: older.calories,
        )
    }

    private fun mergeExercise(older: WorkoutExercise, newer: WorkoutExercise): WorkoutExercise {
        val olderById = older.sets.associateBy { it.id }
        val newerIds = newer.sets.map { it.id }.toSet()
        val sets = newer.sets.map { ns -> olderById[ns.id]?.let { mergeSet(it, ns) } ?: ns }.toMutableList()
        older.sets.forEachIndexed { index, os ->
            if (os.id !in newerIds && os.completed) sets.add(index.coerceAtMost(sets.size), os)
        }
        return newer.copy(sets = sets)
    }

    /** Serie completada en los dos dispositivos con valores distintos (se quedó la más reciente). */
    data class SetConflict(val exerciseName: String, val setIndex: Int, val kept: WorkoutSet, val discarded: WorkoutSet)

    /**
     * Conflictos que [merge] resolverá descartando datos: la misma serie completada en el móvil
     * y en el reloj con peso, repeticiones o duración distintos. Sirve para avisar al usuario.
     */
    fun conflicts(a: Workout, b: Workout): List<SetConflict> {
        if (a.id != b.id) return emptyList()
        val (older, newer) = if (b.updatedAtEpochMs >= a.updatedAtEpochMs) a to b else b to a
        val olderSets = older.exercises.flatMap { e -> e.sets.map { it.id to it } }.toMap()
        return newer.exercises.flatMap { e ->
            e.sets.mapIndexedNotNull { i, ns ->
                val os = olderSets[ns.id] ?: return@mapIndexedNotNull null
                val differs = os.weightKg != ns.weightKg || os.reps != ns.reps || os.durationSec != ns.durationSec
                if (ns.completed && os.completed && differs) SetConflict(e.exercise.name, i, ns, os) else null
            }
        }
    }

    private fun mergeSet(older: WorkoutSet, newer: WorkoutSet): WorkoutSet = when {
        newer.completed -> newer
        older.completed -> older
        else -> newer
    }
}
