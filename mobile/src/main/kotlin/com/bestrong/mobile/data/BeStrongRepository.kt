package com.bestrong.mobile.data

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SyncContract.json
import com.bestrong.mobile.data.db.BeStrongDao
import com.bestrong.mobile.data.db.CustomExerciseEntity
import com.bestrong.mobile.data.db.KeyValueEntity
import com.bestrong.mobile.data.db.MeasurementEntity
import com.bestrong.mobile.data.db.RoutineEntity
import com.bestrong.mobile.data.db.WorkoutEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BeStrongRepository(private val dao: BeStrongDao, scope: CoroutineScope) {

    val history: StateFlow<List<Workout>> = dao.workouts()
        .map { list -> list.mapNotNull { runCatching { json.decodeFromString(Workout.serializer(), it.json) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val routines: StateFlow<List<Routine>> = dao.routines()
        .map { list -> list.mapNotNull { runCatching { json.decodeFromString(Routine.serializer(), it.json) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val customExercises: Flow<List<Exercise>> = dao.customExercises()
        .map { list -> list.mapNotNull { runCatching { json.decodeFromString(Exercise.serializer(), it.json) }.getOrNull() } }

    /** Biblioteca integrada + ejercicios personalizados. */
    val exercises: StateFlow<List<Exercise>> = customExercises
        .map { custom -> (ExerciseLibrary.all + custom).sortedBy { it.name } }
        .stateIn(scope, SharingStarted.Eagerly, ExerciseLibrary.all.sortedBy { it.name })

    val measurements: StateFlow<List<BodyMeasurement>> = dao.measurements()
        .map { list -> list.mapNotNull { runCatching { json.decodeFromString(BodyMeasurement.serializer(), it.json) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeWorkout: StateFlow<Workout?> = dao.value(KEY_ACTIVE)
        .map { s -> s?.let { runCatching { json.decodeFromString(Workout.serializer(), it) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Últimas series de cada ejercicio, para la columna "Anterior". */
    val previousSetsLookup: StateFlow<(String) -> List<WorkoutSet>> = history
        .map { h -> { id: String -> WorkoutStats.previousSets(id, h) } }
        .stateIn(scope, SharingStarted.Eagerly) { _: String -> emptyList() }

    fun exercise(id: String): Exercise? = exercises.value.firstOrNull { it.id == id }

    val exercisesAndHistory = combine(exercises, history) { e, h -> e to h }

    // --- Entrenamiento activo ---------------------------------------------------------------

    private val activeMutex = Mutex()

    /** Cambio local: marca la hora de modificación para la sincronización con el reloj. */
    suspend fun setActive(workout: Workout?) = activeMutex.withLock { write(workout?.copy(updatedAtEpochMs = System.currentTimeMillis())) }

    private suspend fun write(workout: Workout?) {
        if (workout == null) dao.deleteValue(KEY_ACTIVE)
        else dao.putValue(KeyValueEntity(KEY_ACTIVE, json.encodeToString(Workout.serializer(), workout)))
    }

    /** Aplica un cambio sobre el último estado guardado (evita perder ediciones rápidas). */
    suspend fun updateActive(block: (Workout) -> Workout) = activeMutex.withLock {
        val current = currentActive() ?: return@withLock
        write(block(current).copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    private suspend fun currentActive(): Workout? =
        dao.valueOnce(KEY_ACTIVE)?.let { runCatching { json.decodeFromString(Workout.serializer(), it) }.getOrNull() }

    /** Estado que llega del reloj: solo se aplica si es más reciente que el local. */
    suspend fun applyRemoteActive(remote: Workout) = activeMutex.withLock {
        val local = currentActive()
        if (local == null || remote.updatedAtEpochMs > local.updatedAtEpochMs) write(remote)
    }

    /** El reloj terminó o descartó el entreno: lo quitamos sin guardarlo (el terminado llega aparte). */
    suspend fun clearRemoteActive(workoutId: String?) = activeMutex.withLock {
        val local = currentActive() ?: return@withLock
        if (workoutId == null || local.id == workoutId) write(null)
    }

    // --- Importación ---------------------------------------------------------------------------

    /** Importa entrenamientos (p. ej. desde CSV) evitando duplicados. Devuelve cuántos se añadieron. */
    suspend fun importWorkouts(workouts: List<Workout>, newExercises: List<Exercise>): Int {
        newExercises.forEach { saveCustomExercise(it) }
        val existing = historyOnce()
        val ids = existing.map { it.id }.toSet()
        val keys = existing.map { it.startedAtEpochMs to it.name }.toSet()
        val fresh = workouts.filter { it.id !in ids && (it.startedAtEpochMs to it.name) !in keys }
        fresh.forEach { saveWorkout(it) }
        return fresh.size
    }

    // --- Historial ---------------------------------------------------------------------------

    suspend fun saveWorkout(workout: Workout) =
        dao.upsertWorkout(WorkoutEntity(workout.id, workout.startedAtEpochMs, json.encodeToString(Workout.serializer(), workout)))

    suspend fun deleteWorkout(id: String) = dao.deleteWorkout(id)

    suspend fun historyOnce(): List<Workout> =
        dao.workoutsOnce().mapNotNull { runCatching { json.decodeFromString(Workout.serializer(), it.json) }.getOrNull() }

    // --- Rutinas -----------------------------------------------------------------------------

    suspend fun saveRoutine(routine: Routine) {
        val position = if (routines.value.any { it.id == routine.id }) routine.position else (routines.value.maxOfOrNull { it.position } ?: -1) + 1
        val r = routine.copy(position = position)
        dao.upsertRoutine(RoutineEntity(r.id, r.position, json.encodeToString(Routine.serializer(), r)))
    }

    suspend fun deleteRoutine(id: String) = dao.deleteRoutine(id)

    suspend fun routine(id: String): Routine? = routinesOnce().firstOrNull { it.id == id }

    suspend fun routinesOnce(): List<Routine> =
        dao.routinesOnce().mapNotNull { runCatching { json.decodeFromString(Routine.serializer(), it.json) }.getOrNull() }

    // --- Ejercicios personalizados ----------------------------------------------------------

    suspend fun saveCustomExercise(exercise: Exercise) =
        dao.upsertCustomExercise(CustomExerciseEntity(exercise.id, json.encodeToString(Exercise.serializer(), exercise.copy(isCustom = true))))

    suspend fun deleteCustomExercise(id: String) = dao.deleteCustomExercise(id)

    // --- Medidas -----------------------------------------------------------------------------

    suspend fun saveMeasurement(m: BodyMeasurement) =
        dao.upsertMeasurement(MeasurementEntity(m.id, m.dateEpochMs, json.encodeToString(BodyMeasurement.serializer(), m)))

    suspend fun deleteMeasurement(id: String) = dao.deleteMeasurement(id)

    private companion object {
        const val KEY_ACTIVE = "active_workout"
    }
}
