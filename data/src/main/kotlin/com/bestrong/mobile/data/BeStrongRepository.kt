package com.bestrong.mobile.data

import com.bestrong.core.io.BackupData
import com.bestrong.core.io.Persistence
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SyncContract.json
import com.bestrong.core.sync.WorkoutMerge
import com.bestrong.core.training.ActiveProgram
import com.bestrong.mobile.data.db.BeStrongDao
import com.bestrong.mobile.data.db.CustomExerciseEntity
import com.bestrong.mobile.data.db.KeyValueEntity
import com.bestrong.mobile.data.db.MeasurementEntity
import com.bestrong.mobile.data.db.RoutineEntity
import com.bestrong.mobile.data.db.WorkoutEntity
import com.bestrong.mobile.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class BeStrongRepository @Inject constructor(private val dao: BeStrongDao, @ApplicationScope scope: CoroutineScope) {

    private val allHistory: StateFlow<List<Workout>> = dao.workouts()
        .map { list -> list.mapNotNull { runCatching { Persistence.decodeWorkout(it.json) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Tu historial (sin los entrenos de la pareja, que no cuentan para tus estadísticas). */
    val history: StateFlow<List<Workout>> = allHistory.map { l -> l.filter { it.athlete == null } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Entrenos hechos en modo pareja. */
    val partnerHistory: StateFlow<List<Workout>> = allHistory.map { l -> l.filter { it.athlete != null } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val routines: StateFlow<List<Routine>> = dao.routines()
        .map { list -> list.mapNotNull { runCatching { Persistence.decodeRoutine(it.json) }.getOrNull() } }
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
        .map { s -> s?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Entreno en curso leído de la base de datos (en arranque en frío [activeWorkout] aún vale null). */
    suspend fun activeOnce(): Workout? =
        dao.valueOnce(KEY_ACTIVE)?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() }

    /** Programa con progresión que sigue el usuario (null = ninguno). */
    val activeProgram: StateFlow<ActiveProgram?> = dao.value(KEY_PROGRAM)
        .map { s -> s?.let { runCatching { json.decodeFromString(ActiveProgram.serializer(), it) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setActiveProgram(program: ActiveProgram?) {
        if (program == null) {
            dao.deleteValue(KEY_PROGRAM)
        } else {
            dao.putValue(KeyValueEntity(KEY_PROGRAM, json.encodeToString(ActiveProgram.serializer(), program)))
        }
    }

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
        if (workout == null) {
            dao.deleteValue(KEY_ACTIVE)
        } else {
            dao.putValue(KeyValueEntity(KEY_ACTIVE, Persistence.encodeWorkout(workout)))
        }
    }

    /** Aplica un cambio sobre el último estado guardado (evita perder ediciones rápidas). */
    suspend fun updateActive(block: (Workout) -> Workout) = activeMutex.withLock {
        val current = currentActive() ?: return@withLock
        write(block(current).copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    private suspend fun currentActive(): Workout? =
        dao.valueOnce(KEY_ACTIVE)?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() }

    // --- Modo pareja: segundo entreno activo en el mismo móvil -------------------------------

    val partnerWorkout: StateFlow<Workout?> = dao.value(KEY_PARTNER)
        .map { s -> s?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setPartnerActive(workout: Workout?) = activeMutex.withLock {
        if (workout == null) dao.deleteValue(KEY_PARTNER) else dao.putValue(KeyValueEntity(KEY_PARTNER, Persistence.encodeWorkout(workout)))
    }

    suspend fun updatePartnerActive(block: (Workout) -> Workout) = activeMutex.withLock {
        val current = dao.valueOnce(KEY_PARTNER)?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() } ?: return@withLock
        dao.putValue(KeyValueEntity(KEY_PARTNER, Persistence.encodeWorkout(block(current).copy(updatedAtEpochMs = System.currentTimeMillis()))))
    }

    private val _syncConflicts = MutableSharedFlow<List<WorkoutMerge.SetConflict>>(extraBufferCapacity = 4)

    /** Series editadas a la vez en móvil y reloj con valores distintos (se avisa en el entreno). */
    val syncConflicts: SharedFlow<List<WorkoutMerge.SetConflict>> = _syncConflicts

    /** Estado que llega del reloj: se fusiona serie a serie con el local (no se pierde ninguna serie hecha). */
    suspend fun applyRemoteActive(remote: Workout) = activeMutex.withLock {
        val local = currentActive()
        if (local != null) WorkoutMerge.conflicts(local, remote).takeIf { it.isNotEmpty() }?.let { _syncConflicts.tryEmit(it) }
        val merged = if (local == null) remote else WorkoutMerge.merge(local, remote)
        if (merged != local) write(merged)
    }

    /** El reloj terminó o descartó el entreno: lo quitamos sin guardarlo (el terminado llega aparte). */
    suspend fun clearRemoteActive(workoutId: String?) = activeMutex.withLock {
        val local = currentActive() ?: return@withLock
        if (workoutId == null || local.id == workoutId) write(null)
    }

    // --- Importación ---------------------------------------------------------------------------

    /** Importa entrenamientos (p. ej. desde CSV) evitando duplicados. Devuelve cuántos se añadieron. */
    suspend fun importWorkouts(workouts: List<Workout>, newExercises: List<Exercise>): Int {
        val existing = historyOnce(includePartner = true)
        val ids = existing.map { it.id }.toSet()
        val keys = existing.map { it.startedAtEpochMs to it.name }.toSet()
        val fresh = workouts.filter { it.id !in ids && (it.startedAtEpochMs to it.name) !in keys }
        // Todo de una vez (una transacción): rápido con miles de entrenos y sin importaciones a medias
        val (exerciseRows, workoutRows) = withContext(Dispatchers.Default) {
            newExercises.map { customExerciseEntity(it) } to fresh.map { WorkoutEntity(it.id, it.startedAtEpochMs, Persistence.encodeWorkout(it)) }
        }
        dao.upsertAll(exerciseRows, workoutRows)
        return fresh.size
    }

    // --- Copia de seguridad ------------------------------------------------------------------

    suspend fun backupData(): BackupData = BackupData(
        history = historyOnce(includePartner = true),
        routines = routinesOnce(),
        customExercises = customExercises.first(),
        measurements = measurements.first(),
        activeProgram = activeProgramOnce(),
    )

    private suspend fun activeProgramOnce(): ActiveProgram? =
        dao.valueOnce(KEY_PROGRAM)?.let { runCatching { json.decodeFromString(ActiveProgram.serializer(), it) }.getOrNull() }

    /** Restaura una copia sin borrar nada: añade lo que no exista (por id). Devuelve cuántos elementos se añadieron. */
    suspend fun restore(data: BackupData): Int {
        val customIds = customExercises.first().map { it.id }.toSet()
        val newExercises = data.customExercises.filter { it.id !in customIds }
        val workouts = importWorkouts(data.history, newExercises)
        val routineIds = routinesOnce().map { it.id }.toSet()
        val newRoutines = data.routines.filter { it.id !in routineIds }
        addRoutines(newRoutines)
        val measurementIds = measurements.value.map { it.id }.toSet()
        val newMeasurements = data.measurements.filter { it.id !in measurementIds }
        newMeasurements.forEach { saveMeasurement(it) }
        // El programa de la copia solo si ahora no se sigue ninguno
        val program = data.activeProgram?.takeIf { activeProgramOnce() == null }
        program?.let { setActiveProgram(it) }
        return workouts + newRoutines.size + newExercises.size + newMeasurements.size + (if (program != null) 1 else 0)
    }

    // --- Historial ---------------------------------------------------------------------------

    suspend fun saveWorkout(workout: Workout) =
        dao.upsertWorkout(WorkoutEntity(workout.id, workout.startedAtEpochMs, Persistence.encodeWorkout(workout)))

    suspend fun deleteWorkout(id: String) = dao.deleteWorkout(id)

    suspend fun historyOnce(includePartner: Boolean = false): List<Workout> {
        val rows = dao.workoutsOnce()
        // Decodificar miles de entrenos no debe hacerse en el hilo principal
        return withContext(Dispatchers.Default) {
            rows.mapNotNull { runCatching { Persistence.decodeWorkout(it.json) }.getOrNull() }
                .filter { includePartner || it.athlete == null }
        }
    }

    // --- Rutinas -----------------------------------------------------------------------------

    suspend fun saveRoutine(routine: Routine) {
        val position = if (routines.value.any { it.id == routine.id }) routine.position else (routines.value.maxOfOrNull { it.position } ?: -1) + 1
        val r = routine.copy(position = position)
        dao.upsertRoutine(RoutineEntity(r.id, r.position, Persistence.encodeRoutine(r)))
    }

    /** Guarda varias rutinas nuevas al final de la lista (p. ej. al añadir un programa). */
    suspend fun addRoutines(list: List<Routine>) {
        var next = (routinesOnce().maxOfOrNull { it.position } ?: -1) + 1
        list.forEach { r ->
            val placed = r.copy(position = next++)
            dao.upsertRoutine(RoutineEntity(placed.id, placed.position, Persistence.encodeRoutine(placed)))
        }
    }

    suspend fun deleteRoutine(id: String) = dao.deleteRoutine(id)

    suspend fun routine(id: String): Routine? = routinesOnce().firstOrNull { it.id == id }

    suspend fun routinesOnce(): List<Routine> {
        val rows = dao.routinesOnce()
        return withContext(Dispatchers.Default) { rows.mapNotNull { runCatching { Persistence.decodeRoutine(it.json) }.getOrNull() } }
    }

    // --- Ejercicios personalizados ----------------------------------------------------------

    suspend fun saveCustomExercise(exercise: Exercise) = dao.upsertCustomExercise(customExerciseEntity(exercise))

    private fun customExerciseEntity(exercise: Exercise) =
        CustomExerciseEntity(exercise.id, json.encodeToString(Exercise.serializer(), exercise.copy(isCustom = true)))

    suspend fun deleteCustomExercise(id: String) = dao.deleteCustomExercise(id)

    // --- Medidas -----------------------------------------------------------------------------

    suspend fun saveMeasurement(m: BodyMeasurement) =
        dao.upsertMeasurement(MeasurementEntity(m.id, m.dateEpochMs, json.encodeToString(BodyMeasurement.serializer(), m)))

    suspend fun deleteMeasurement(id: String) = dao.deleteMeasurement(id)

    private companion object {
        const val KEY_ACTIVE = "active_workout"
        const val KEY_PARTNER = "partner_workout"
        const val KEY_PROGRAM = "active_program"
    }
}
