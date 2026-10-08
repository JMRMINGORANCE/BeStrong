package com.bestrong.core.sync

import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Contrato compartido para la Wearable Data Layer API entre móvil y reloj.
 */
object SyncContract {
    /** DataItem con las rutinas (móvil → reloj). */
    const val PATH_ROUTINES = "/bestrong/routines"
    /** Prefijo de DataItems con entrenamientos terminados (reloj → móvil); se añade el id. */
    const val PATH_FINISHED_WORKOUT_PREFIX = "/bestrong/finished-workout/"
    /** DataItem con el historial reciente (móvil → reloj) para "anterior", tile y complicación. */
    const val PATH_RECENT_HISTORY = "/bestrong/recent-history"
    const val KEY_PAYLOAD = "payload"
    const val KEY_TIMESTAMP = "ts"

    /** Deep link que abre el reloj desde el móvil (RemoteActivityHelper). */
    const val DEEP_LINK_SCHEME = "bestrong"
    const val DEEP_LINK_HOST = "start"
    const val DEEP_LINK_PARAM_ROUTINE = "routine"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val workoutList = ListSerializer(Workout.serializer())
    private val routineList = ListSerializer(Routine.serializer())

    fun encodeWorkout(workout: Workout): ByteArray = json.encodeToString(Workout.serializer(), workout).encodeToByteArray()
    fun decodeWorkout(bytes: ByteArray): Workout = json.decodeFromString(Workout.serializer(), bytes.decodeToString())

    fun encodeWorkouts(workouts: List<Workout>): ByteArray = json.encodeToString(workoutList, workouts).encodeToByteArray()
    fun decodeWorkouts(bytes: ByteArray): List<Workout> = json.decodeFromString(workoutList, bytes.decodeToString())

    fun encodeRoutines(routines: List<Routine>): ByteArray = json.encodeToString(routineList, routines).encodeToByteArray()
    fun decodeRoutines(bytes: ByteArray): List<Routine> = json.decodeFromString(routineList, bytes.decodeToString())

    /**
     * Versión ligera del historial para el reloj: solo las series completadas de los
     * últimos [limit] entrenamientos, para no superar el límite de 100 KB de un DataItem.
     */
    fun compactHistory(history: List<Workout>, limit: Int = 30): List<Workout> =
        history.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.take(limit)
            .map { w -> w.copy(notes = "", exercises = w.exercises.map { it.copy(notes = "", sets = it.completedSets) }) }
}
