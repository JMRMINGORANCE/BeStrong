package com.bestrong.core.session

import com.bestrong.core.model.Exercise
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.model.WorkoutSource

/**
 * Operaciones inmutables sobre el entrenamiento en curso. Las usan tanto el móvil como el reloj.
 */
object WorkoutEditor {

    fun empty(nowEpochMs: Long, source: WorkoutSource = WorkoutSource.PHONE, name: String = defaultName(nowEpochMs)) =
        Workout(name = name, startedAtEpochMs = nowEpochMs, source = source)

    fun defaultName(nowEpochMs: Long): String {
        val hour = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(java.time.ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..11 -> "Entrenamiento de mañana"
            in 12..15 -> "Entrenamiento de mediodía"
            in 16..20 -> "Entrenamiento de tarde"
            else -> "Entrenamiento de noche"
        }
    }

    fun addExercises(workout: Workout, exercises: List<Exercise>, previousSets: (String) -> List<WorkoutSet> = { emptyList() }): Workout =
        workout.copy(
            exercises = workout.exercises + exercises.map { ex ->
                val prev = previousSets(ex.id)
                val sets = if (prev.isNotEmpty()) {
                    prev.map { it.copy(id = com.bestrong.core.util.newId(), completed = false, rpe = null) }
                } else {
                    List(3) { WorkoutSet(reps = if (ex.tracking == TrackingType.DURATION) 0 else 10, durationSec = if (ex.tracking == TrackingType.DURATION) 30 else 0) }
                }
                WorkoutExercise(exercise = ex, sets = sets)
            },
        )

    fun removeExercise(workout: Workout, workoutExerciseId: String): Workout =
        workout.copy(exercises = workout.exercises.filterNot { it.id == workoutExerciseId })

    fun moveExercise(workout: Workout, from: Int, to: Int): Workout {
        if (from !in workout.exercises.indices || to !in workout.exercises.indices) return workout
        val list = workout.exercises.toMutableList()
        list.add(to, list.removeAt(from))
        return workout.copy(exercises = list)
    }

    fun replaceExercise(workout: Workout, workoutExerciseId: String, exercise: Exercise): Workout =
        updateExercise(workout, workoutExerciseId) { it.copy(exercise = exercise) }

    fun updateExercise(workout: Workout, workoutExerciseId: String, block: (WorkoutExercise) -> WorkoutExercise): Workout =
        workout.copy(exercises = workout.exercises.map { if (it.id == workoutExerciseId) block(it) else it })

    /** Añade una serie copiando la última (peso y reps). */
    fun addSet(workout: Workout, workoutExerciseId: String): Workout = updateExercise(workout, workoutExerciseId) { we ->
        val last = we.sets.lastOrNull()
        we.copy(
            sets = we.sets + WorkoutSet(
                weightKg = last?.weightKg ?: 0.0,
                reps = last?.reps ?: 10,
                durationSec = last?.durationSec ?: 0,
                type = if (last?.type == SetType.WARMUP) SetType.NORMAL else last?.type ?: SetType.NORMAL,
            ),
        )
    }

    fun removeSet(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateExercise(workout, workoutExerciseId) { we -> we.copy(sets = we.sets.filterNot { it.id == setId }) }

    fun updateSet(workout: Workout, workoutExerciseId: String, setId: String, block: (WorkoutSet) -> WorkoutSet): Workout =
        updateExercise(workout, workoutExerciseId) { we -> we.copy(sets = we.sets.map { if (it.id == setId) block(it) else it }) }

    fun toggleSetCompleted(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateSet(workout, workoutExerciseId, setId) { it.copy(completed = !it.completed) }

    /** Cicla Normal → Calentamiento → Drop → Fallo → Normal. */
    fun cycleSetType(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateSet(workout, workoutExerciseId, setId) {
            val next = when (it.type) {
                SetType.NORMAL -> SetType.WARMUP
                SetType.WARMUP -> SetType.DROP
                SetType.DROP -> SetType.FAILURE
                SetType.FAILURE -> SetType.NORMAL
            }
            it.copy(type = next)
        }

    // --- Superseries ------------------------------------------------------------------------

    /** Une el ejercicio con el siguiente en una superserie (o lo añade a la del anterior). */
    fun linkWithNext(workout: Workout, workoutExerciseId: String): Workout {
        val i = workout.exercises.indexOfFirst { it.id == workoutExerciseId }
        if (i < 0 || i >= workout.exercises.lastIndex) return workout
        val a = workout.exercises[i]
        val b = workout.exercises[i + 1]
        val group = a.supersetId ?: b.supersetId ?: com.bestrong.core.util.newId()
        val oldB = b.supersetId
        return workout.copy(
            exercises = workout.exercises.map {
                when {
                    it.id == a.id || it.id == b.id -> it.copy(supersetId = group)
                    oldB != null && it.supersetId == oldB -> it.copy(supersetId = group)
                    else -> it
                }
            },
        )
    }

    /** Saca el ejercicio de su superserie; si queda uno solo en el grupo, el grupo desaparece. */
    fun unlinkSuperset(workout: Workout, workoutExerciseId: String): Workout {
        val group = workout.exercises.firstOrNull { it.id == workoutExerciseId }?.supersetId ?: return workout
        val cleared = workout.exercises.map { if (it.id == workoutExerciseId) it.copy(supersetId = null) else it }
        val remaining = cleared.count { it.supersetId == group }
        return workout.copy(exercises = if (remaining <= 1) cleared.map { if (it.supersetId == group) it.copy(supersetId = null) else it } else cleared)
    }

    /** Índices de los ejercicios de la superserie de [index] (solo él mismo si no tiene). */
    fun supersetGroup(workout: Workout, index: Int): List<Int> {
        val group = workout.exercises.getOrNull(index)?.supersetId ?: return listOf(index)
        return workout.exercises.indices.filter { workout.exercises[it].supersetId == group }
    }

    /**
     * Qué hacer tras completar una serie del ejercicio [index]:
     * - en superserie, pasar al siguiente ejercicio del grupo con series pendientes sin descanso;
     * - al cerrar la ronda (o fuera de superserie), descansar el tiempo del ejercicio.
     * Devuelve (índice del siguiente ejercicio o null si se terminó todo, segundos de descanso).
     */
    fun afterSetCompleted(workout: Workout, index: Int): Pair<Int?, Int> {
        val group = supersetGroup(workout, index)
        val current = workout.exercises.getOrNull(index) ?: return null to 0
        if (group.size > 1) {
            val pos = group.indexOf(index)
            val doneHere = current.sets.count { it.completed }
            // Siguiente del grupo que aún no ha hecho la serie de esta ronda
            val nextInRound = (group.drop(pos + 1)).firstOrNull { i ->
                val ex = workout.exercises[i]
                ex.sets.count { it.completed } < doneHere && ex.sets.any { !it.completed }
            }
            if (nextInRound != null) return nextInRound to 0
            val firstPending = group.firstOrNull { i -> workout.exercises[i].sets.any { !it.completed } }
            if (firstPending != null) return firstPending to workout.exercises[group.last()].restSeconds
        } else if (current.sets.any { !it.completed }) {
            return index to current.restSeconds
        }
        val next = nextPendingSet(workout)?.first
        return next to if (next == null) 0 else current.restSeconds
    }

    /** Siguiente serie pendiente: (índice de ejercicio, serie) o null si está todo hecho. */
    fun nextPendingSet(workout: Workout): Pair<Int, WorkoutSet>? {
        workout.exercises.forEachIndexed { i, we ->
            we.sets.firstOrNull { !it.completed }?.let { return i to it }
        }
        return null
    }

    /**
     * Termina el entrenamiento: descarta las series no completadas y los ejercicios vacíos.
     */
    fun finish(workout: Workout, nowEpochMs: Long): Workout = workout.copy(
        finishedAtEpochMs = nowEpochMs,
        exercises = workout.exercises
            .map { it.copy(sets = it.completedSets) }
            .filter { it.sets.isNotEmpty() },
    )
}
