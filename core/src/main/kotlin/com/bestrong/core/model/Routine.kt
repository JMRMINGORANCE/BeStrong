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
    /** Carpeta en Inicio («Bloque de fuerza», «Casa»…); null = sin carpeta. */
    val folder: String? = null,
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
        /**
         * Para repetir un entreno: lo que se hizo (no las series saltadas) y sin las notas de ese día,
         * que eran de esa sesión ([templateNote] = la nota fija de la rutina de origen, si la hay).
         */
        fun repeatOf(workout: Workout, keepAllSets: Boolean = false, templateNote: (WorkoutExercise) -> String = { "" }): Routine = fromWorkout(
            workout.copy(
                exercises = workout.exercises.map {
                    // Con rutina de origen se repite su estructura completa (saltarse una serie no la cambia)
                    it.copy(notes = templateNote(it), sets = if (keepAllSets) it.sets else it.completedSets.ifEmpty { it.sets })
                },
            ),
        )

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

/**
 * Superseries coherentes: cada grupo son ejercicios seguidos (si el mismo id reaparece más abajo,
 * ese tramo pasa a ser otro grupo) y un «grupo» de un solo ejercicio deja de ser superserie.
 */
fun Routine.normalizedSupersets(): Routine {
    val runs = mutableListOf<MutableList<Int>>()
    exercises.forEachIndexed { i, re ->
        val prev = exercises.getOrNull(i - 1)
        if (re.supersetId != null && prev?.supersetId == re.supersetId) runs.last().add(i) else runs.add(mutableListOf(i))
    }
    val seen = mutableSetOf<String>()
    val ids = arrayOfNulls<String>(exercises.size)
    runs.forEach { run ->
        val id = exercises[run.first()].supersetId
        if (id != null && run.size > 1) {
            val groupId = if (seen.add(id)) id else newId()
            run.forEach { ids[it] = groupId }
        }
    }
    val fixed = exercises.mapIndexed { i, re -> if (re.supersetId == ids[i]) re else re.copy(supersetId = ids[i]) }
    return if (fixed == exercises) this else copy(exercises = fixed)
}

/** Índices del tramo seguido de la misma superserie que contiene [index] (solo él si no tiene). */
private fun Routine.blockAt(index: Int): IntRange {
    val id = exercises[index].supersetId ?: return index..index
    var start = index
    var end = index
    while (start > 0 && exercises[start - 1].supersetId == id) start--
    while (end < exercises.lastIndex && exercises[end + 1].supersetId == id) end++
    return start..end
}

/**
 * Mueve el ejercicio [from] un puesto arriba ([down] = false) o abajo sin romper superseries: dentro de
 * su superserie cambia de orden con el vecino; si no, se mueve su bloque entero (él o toda su superserie)
 * al otro lado del bloque vecino.
 */
fun Routine.moveKeepingSupersets(from: Int, down: Boolean): Routine {
    val neighbour = if (down) from + 1 else from - 1
    if (from !in exercises.indices || neighbour !in exercises.indices) return this
    val list = exercises.toMutableList()
    val own = blockAt(from)
    if (neighbour in own) {
        list.add(neighbour, list.removeAt(from))
    } else {
        val other = blockAt(neighbour)
        val moved = exercises.slice(own)
        val passed = exercises.slice(other)
        val reordered = if (down) passed + moved else moved + passed
        reordered.forEachIndexed { k, re -> list[minOf(own.first, other.first) + k] = re }
    }
    return copy(exercises = list)
}
