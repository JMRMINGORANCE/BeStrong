package com.bestrong.core.model

import com.bestrong.core.util.newId
import kotlinx.serialization.Serializable

@Serializable
data class RoutineSet(
    val weightKg: Double = 0.0,
    val reps: Int = 10,
    val durationSec: Int = 0,
    val type: SetType = SetType.NORMAL,
)

@Serializable
data class RoutineExercise(
    val id: String = newId(),
    val exercise: Exercise,
    val sets: List<RoutineSet> = List(3) { RoutineSet() },
    val restSeconds: Int = WorkoutExercise.DEFAULT_REST_SECONDS,
    val notes: String = "",
    val supersetId: String? = null,
    val tempo: String? = null,
)

@Serializable
data class Routine(
    val id: String = newId(),
    val name: String,
    val exercises: List<RoutineExercise> = emptyList(),
    val notes: String = "",
    val position: Int = 0,
    /** Emoji para reconocer la rutina de un vistazo (💪, 🦵…). */
    val emoji: String? = null,
) {
    /** Nombre con su emoji delante, si tiene. */
    val displayName: String get() = emoji?.let { "$it $name" } ?: name

    /**
     * Crea un entrenamiento a partir de la rutina. Si hay un historial previo del ejercicio,
     * los pesos/reps se rellenan con lo que se hizo la última vez (como hace Hevy).
     */
    fun toWorkout(
        nowEpochMs: Long,
        previousSets: (exerciseId: String) -> List<WorkoutSet> = { emptyList() },
        source: WorkoutSource = WorkoutSource.PHONE,
    ): Workout = Workout(
        name = name,
        routineId = id,
        startedAtEpochMs = nowEpochMs,
        source = source,
        exercises = exercises.map { re ->
            val previous = previousSets(re.exercise.id)
            WorkoutExercise(
                exercise = re.exercise,
                restSeconds = re.restSeconds,
                notes = re.notes,
                supersetId = re.supersetId,
                tempo = re.tempo,
                sets = re.sets.mapIndexed { i, rs ->
                    // Calentamientos con calentamientos y efectivas (normal, drop, fallo…) con efectivas
                    val warm = rs.type == SetType.WARMUP
                    val sameKindIndex = re.sets.take(i).count { (it.type == SetType.WARMUP) == warm }
                    val prev = previous.filter { (it.type == SetType.WARMUP) == warm }.getOrNull(sameKindIndex)
                    WorkoutSet(
                        weightKg = prev?.weightKg?.takeIf { it > 0 } ?: rs.weightKg,
                        reps = prev?.reps?.takeIf { it > 0 } ?: rs.reps,
                        durationSec = prev?.durationSec?.takeIf { it > 0 } ?: rs.durationSec,
                        type = rs.type,
                    )
                },
            )
        },
    )

    /**
     * Rutina con la estructura del entreno que se hizo a partir de ella (ejercicios, orden, series,
     * descansos…), conservando su identidad, nombre, emoji, notas y posición.
     */
    fun updatedFrom(workout: Workout): Routine = copy(exercises = exercisesOf(workout))

    /** Si el entreno cambió la estructura de la rutina (ejercicios, orden, nº de series, tipos o descansos). */
    fun structureDiffersFrom(workout: Workout): Boolean {
        fun RoutineExercise.shape() = listOf(exercise.id, sets.map { it.type }, restSeconds, supersetId)
        return exercises.map { it.shape() } != exercisesOf(workout).map { it.shape() }
    }

    companion object {
        /** Guarda un entrenamiento terminado como rutina nueva. */
        fun fromWorkout(workout: Workout, name: String = workout.name): Routine = Routine(
            name = name,
            exercises = exercisesOf(workout),
        )

        private fun exercisesOf(workout: Workout): List<RoutineExercise> = workout.exercises.map { we ->
            RoutineExercise(
                exercise = we.exercise,
                restSeconds = we.restSeconds,
                notes = we.notes,
                supersetId = we.supersetId,
                tempo = we.tempo,
                sets = we.sets.map { RoutineSet(it.weightKg, it.reps, it.durationSec, it.type) },
            )
        }
    }
}
