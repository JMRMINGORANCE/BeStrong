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
 * - Lo borrado a propósito (en [Workout.deletedIds] de cualquiera de los dos) no vuelve, y desmarcar una serie
 *   después de completarla gana a esa marca (se compara cuándo se hizo cada cosa).
 */
object WorkoutMerge {

    fun merge(a: Workout, b: Workout): Workout {
        if (a.id != b.id) return if (b.updatedAtEpochMs >= a.updatedAtEpochMs) b else a
        val (older, newer) = if (b.updatedAtEpochMs >= a.updatedAtEpochMs) a to b else b to a
        val olderById = older.exercises.associateBy { it.id }
        val newerIds = newer.exercises.map { it.id }.toSet()

        val deleted = (a.deletedIds.keys + b.deletedIds.keys).associateWith { a.deletedIds[it].orEmpty() + b.deletedIds[it].orEmpty() }
        val merged = newer.exercises.map { ne -> olderById[ne.id]?.let { mergeExercise(it, ne, deleted) } ?: ne }.toMutableList()
        // Ejercicios que solo están en el lado antiguo: se conservan si tienen series hechas
        older.exercises.forEachIndexed { index, oe ->
            if (oe.id !in newerIds && !gone(oe, deleted) && oe.sets.any { it.completed }) {
                merged.add(index.coerceAtMost(merged.size), oe)
            }
        }
        return newer.copy(
            exercises = singleStopwatch(merged.filterNot { gone(it, deleted) }.map { we -> we.copy(sets = we.sets.filterNot { gone(it, deleted) }) }),
            deletedIds = deleted,
            // El tiempo en pausa solo crece: una pausa hecha en el otro dispositivo no se pierde
            pausedTotalMs = maxOf(a.pausedTotalMs, b.pausedTotalMs),
            updatedAtEpochMs = maxOf(a.updatedAtEpochMs, b.updatedAtEpochMs),
            avgHeartRate = newer.avgHeartRate ?: older.avgHeartRate,
            maxHeartRate = newer.maxHeartRate ?: older.maxHeartRate,
            calories = newer.calories ?: older.calories,
            heartRateSamples = newer.heartRateSamples.ifEmpty { older.heartRateSamples },
        )
    }

    /** Solo un cronómetro de cardio a la vez: si la fusión deja dos en marcha, sigue el último arrancado. */
    private fun singleStopwatch(exercises: List<WorkoutExercise>): List<WorkoutExercise> {
        val running = exercises.flatMap { we -> we.sets.filter { it.isRunning } }
        if (running.size < 2) return exercises
        val keep = running.maxBy { it.startedAtEpochMs ?: 0L }.id
        return exercises.map { we -> we.copy(sets = we.sets.map { if (it.isRunning && it.id != keep) it.copy(startedAtEpochMs = null) else it }) }
    }

    /** Borrado a propósito y sin series hechas que quien lo borró no conocía. */
    private fun gone(we: WorkoutExercise, deleted: Map<String, Set<String>>): Boolean =
        deleted[we.id]?.let { known -> we.sets.filter { it.completed }.all { it.id in known } } == true

    private fun gone(set: WorkoutSet, deleted: Map<String, Set<String>>): Boolean =
        deleted[set.id]?.let { known -> !set.completed || set.id in known } == true

    private fun mergeExercise(older: WorkoutExercise, newer: WorkoutExercise, deleted: Map<String, Set<String>>): WorkoutExercise {
        val olderById = older.sets.associateBy { it.id }
        val newerIds = newer.sets.map { it.id }.toSet()
        val sets = newer.sets.map { ns -> olderById[ns.id]?.let { mergeSet(it, ns) } ?: ns }.toMutableList()
        older.sets.forEachIndexed { index, os ->
            if (os.id !in newerIds && !gone(os, deleted) && os.completed) sets.add(index.coerceAtMost(sets.size), os)
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
                // Lo que de verdad queda (no siempre el más reciente: puede ganar una marca posterior a un desmarcado)
                val kept = mergeSet(os, ns)
                if (ns.completed && os.completed && differs) SetConflict(e.exercise.name, i, kept, if (kept === ns) os else ns) else null
            }
        }
    }

    private fun mergeSet(older: WorkoutSet, newer: WorkoutSet): WorkoutSet {
        // Desmarcada después de completarse (en cualquier lado): gana el desmarcado
        val uncompletedAt = listOfNotNull(older.uncompletedAtEpochMs, newer.uncompletedAtEpochMs).maxOrNull()
        fun stillDone(s: WorkoutSet) = s.completed && (uncompletedAt == null || (s.completedAtEpochMs ?: 0L) > uncompletedAt)
        return when {
            stillDone(newer) -> newer
            stillDone(older) -> older
            newer.completed || older.completed -> (if (!newer.completed) newer else newer.copy(completed = false, completedAtEpochMs = null, startedAtEpochMs = null))
                .copy(uncompletedAtEpochMs = uncompletedAt)
            // Sin hacer: gana la última edición de esta serie (girar la corona en el reloj no se pierde porque el
            // móvil cambiara otra cosa después). Un cronómetro de cardio arrancado en un lado no lo para el otro
            else -> {
                val edited = if ((older.editedAtEpochMs ?: 0L) > (newer.editedAtEpochMs ?: 0L)) older else newer
                edited.copy(startedAtEpochMs = newer.startedAtEpochMs ?: older.startedAtEpochMs)
            }
        }
    }
}
