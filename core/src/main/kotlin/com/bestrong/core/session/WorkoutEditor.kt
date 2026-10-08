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
        workout.copy(exercises = workout.exercises + exercises.map { ex ->
            val prev = previousSets(ex.id)
            val sets = if (prev.isNotEmpty()) prev.map { it.copy(id = com.bestrong.core.util.newId(), completed = false, rpe = null) }
            else List(3) { WorkoutSet(reps = if (ex.tracking == TrackingType.DURATION) 0 else 10, durationSec = if (ex.tracking == TrackingType.DURATION) 30 else 0) }
            WorkoutExercise(exercise = ex, sets = sets)
        })

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
        we.copy(sets = we.sets + WorkoutSet(
            weightKg = last?.weightKg ?: 0.0,
            reps = last?.reps ?: 10,
            durationSec = last?.durationSec ?: 0,
            type = if (last?.type == SetType.WARMUP) SetType.NORMAL else last?.type ?: SetType.NORMAL,
        ))
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
