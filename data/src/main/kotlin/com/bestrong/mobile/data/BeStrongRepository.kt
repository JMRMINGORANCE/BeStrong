package com.bestrong.mobile.data

import com.bestrong.core.io.BackupData
import com.bestrong.core.io.Persistence
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.TrashedWorkout
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class BeStrongRepository @Inject constructor(private val dao: BeStrongDao, @ApplicationScope private val scope: CoroutineScope) {

    /**
     * Entrenos ya decodificados por id (con el JSON del que salieron): cada escritura en la tabla
     * vuelve a emitir toda la lista, pero solo se decodifican las filas nuevas o cambiadas.
     */
    private val decoded = HashMap<String, Pair<JsonKey, Workout>>()

    /** Huella del JSON de una fila (sin guardar el texto entero, que duplicaría la memoria). */
    private data class JsonKey(val length: Int, val hash: Long) {
        companion object {
            /** FNV-1a de 64 bits: con 32 bits una colisión (rarísima) devolvería un entreno sin actualizar. */
            fun of(text: String): JsonKey {
                var h = -0x340d631b7bdddcdbL
                for (c in text) {
                    h = (h xor c.code.toLong()) * 0x100000001b3L
                }
                return JsonKey(text.length, h)
            }
        }
    }

    private fun decodeRows(rows: List<WorkoutEntity>): List<Workout> = synchronized(decoded) {
        val result = rows.mapNotNull { row ->
            val key = JsonKey.of(row.json)
            decoded[row.id]?.takeIf { it.first == key }?.second
                ?: runCatching { Persistence.decodeWorkout(row.json) }.getOrNull()?.also { decoded[row.id] = key to it }
        }
        // Lo borrado sale de la caché
        decoded.keys.retainAll(rows.mapTo(HashSet()) { it.id })
        result
    }

    private val allHistory: StateFlow<List<Workout>> = dao.workouts()
        .map { decodeRows(it) }
        .flowOn(Dispatchers.Default)
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
    suspend fun activeOnce(): Workout? = currentActive()

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
        .map { h ->
            // Una pasada por historial (no una ordenación entera por cada ejercicio y recomposición)
            val last = WorkoutStats.lastSetsByExercise(h)
            val lookup: (String) -> List<WorkoutSet> = { id -> last[id].orEmpty() }
            lookup
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly) { _: String -> emptyList() }

    fun exercise(id: String): Exercise? = exercises.value.firstOrNull { it.id == id }

    val exercisesAndHistory = combine(exercises, history) { e, h -> e to h }

    // --- Entrenamiento activo ---------------------------------------------------------------

    private val activeMutex = Mutex()

    /** Cambio local: marca la hora de modificación para la sincronización con el reloj. */
    suspend fun setActive(workout: Workout?) = activeMutex.withLock {
        // Con un entreno nuevo ya no se ofrece recuperar el descartado
        if (workout != null) _discarded.value = null
        write(workout?.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }

    /** Último entreno descartado (en memoria): se puede recuperar mientras no se empiece otro. */
    private val _discarded = MutableStateFlow<Workout?>(null)
    val discarded: StateFlow<Workout?> = _discarded

    /** Descarta el entreno en curso guardándolo para poder deshacerlo. */
    /** [restEndsAtEpochMs]: fin del descanso en marcha al descartar, para retomarlo al deshacer. */
    suspend fun discardActive(restEndsAtEpochMs: Long? = null, restTotalSeconds: Int = 0) = activeMutex.withLock {
        _discarded.value = currentActive()
        discardedAt = System.currentTimeMillis()
        discardedRestEnd = restEndsAtEpochMs?.let { it to restTotalSeconds }
        write(null)
    }

    private var discardedRestEnd: Pair<Long, Int>? = null

    /** Fin y duración total del descanso que había al descartar el entreno recuperado (null si no había). */
    var restoredRestEnd: Pair<Long, Int>? = null
        private set

    private var discardedAt = 0L

    /** Descartado hace poco (pasado ese rato ya no se ofrece deshacer: el entreno quedaría viejo). */
    val recentlyDiscarded: Boolean get() = _discarded.value != null && System.currentTimeMillis() - discardedAt < UNDO_DISCARD_MS

    /** Recupera el entreno descartado si no hay otro en curso. Devuelve si se recuperó. */
    suspend fun restoreDiscarded(): Boolean = activeMutex.withLock {
        // El plazo ya se comprobó al ofrecer deshacer: aquí basta con que siga guardado
        val w = _discarded.value
        _discarded.value = null
        restoredRestEnd = discardedRestEnd
        discardedRestEnd = null
        if (w == null || currentActive() != null) return@withLock false
        write(w.copy(updatedAtEpochMs = System.currentTimeMillis()))
        true
    }

    fun forgetDiscarded() {
        _discarded.value = null
    }

    private suspend fun write(workout: Workout?) {
        if (workout == null) {
            dao.deleteValue(KEY_ACTIVE)
        } else {
            dao.putValue(KeyValueEntity(KEY_ACTIVE, Persistence.encodeWorkout(workout)))
        }
    }

    /**
     * Aplica un cambio sobre el último estado guardado (evita perder ediciones rápidas).
     * Devuelve el estado antes y después del cambio (null si no hay entreno activo).
     */
    suspend fun updateActive(block: (Workout) -> Workout): Pair<Workout, Workout>? = activeMutex.withLock {
        val current = currentActive() ?: return@withLock null
        val updated = block(current).copy(updatedAtEpochMs = System.currentTimeMillis())
        write(updated)
        current to updated
    }

    /** Lee y quita el entreno activo de una vez (al terminarlo no se pierde ningún cambio de última hora). */
    suspend fun takeActive(): Workout? = activeMutex.withLock {
        currentActive()?.also { write(null) }
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

    suspend fun updatePartnerActive(block: (Workout) -> Workout): Pair<Workout, Workout>? = activeMutex.withLock {
        val current = currentPartner() ?: return@withLock null
        val updated = block(current).copy(updatedAtEpochMs = System.currentTimeMillis())
        dao.putValue(KeyValueEntity(KEY_PARTNER, Persistence.encodeWorkout(updated)))
        current to updated
    }

    /** Como [takeActive], para el entreno de la pareja. */
    suspend fun takePartnerActive(): Workout? = activeMutex.withLock {
        currentPartner()?.also { dao.deleteValue(KEY_PARTNER) }
    }

    private suspend fun currentPartner(): Workout? =
        dao.valueOnce(KEY_PARTNER)?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() }

    private val _syncConflicts = MutableSharedFlow<List<WorkoutMerge.SetConflict>>(extraBufferCapacity = 4)

    /** Series editadas a la vez en móvil y reloj con valores distintos (se avisa en el entreno). */
    val syncConflicts: SharedFlow<List<WorkoutMerge.SetConflict>> = _syncConflicts

    /** Estado que llega del reloj: se fusiona serie a serie con el local (no se pierde ninguna serie hecha). */
    suspend fun applyRemoteActive(remote: Workout) = activeMutex.withLock {
        val local = currentActive()
        if (local != null) WorkoutMerge.conflicts(local, remote).takeIf { it.isNotEmpty() }?.let { _syncConflicts.tryEmit(it) }
        val merged = if (local == null) remote else WorkoutMerge.merge(local, remote)
        keepReplacedLocal(local, merged)
        if (dao.valueOnce(KEY_WATCH_KNOWN) != remote.id) dao.putValue(KeyValueEntity(KEY_WATCH_KNOWN, remote.id))
        if (merged != local) write(merged)
    }

    /**
     * Gana un entreno distinto del reloj (empezados a la vez sin conexión): el del móvil, si tiene series hechas y
     * el reloj nunca lo tuvo (si lo tuvo, el reloj ya lo terminó o lo descartó), se guarda terminado en el historial.
     */
    private suspend fun keepReplacedLocal(local: Workout?, merged: Workout) {
        if (local == null || local.id == merged.id || dao.valueOnce(KEY_WATCH_KNOWN) == local.id) return
        if (local.exercises.none { we -> we.sets.any { it.completed } }) return
        val finished = com.bestrong.core.session.WorkoutEditor.finish(local, System.currentTimeMillis())
        if (finished.exercises.isNotEmpty()) saveWorkout(finished)
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
        untrash(fresh.map { it.id }.toSet())
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

    suspend fun saveWorkout(workout: Workout) {
        dao.upsertWorkout(WorkoutEntity(workout.id, workout.startedAtEpochMs, Persistence.encodeWorkout(workout)))
        // Si vuelve por otro camino (el reloj lo reenvía, una copia…), ya no está en la papelera
        untrash(setOf(workout.id))
    }

    /**
     * Cambia de una vez (una transacción) los entrenos que cumplen [match], tuyos y de la pareja, con la hora de
     * cambio al día (si no, una copia más antigua podría ganar al combinar). En el ámbito de la app: sigue aunque
     * se cierre la pantalla desde la que se pidió (renombrar un gimnasio o a la pareja y salir con Atrás).
     */
    fun rewriteHistory(match: (Workout) -> Boolean, change: (Workout) -> Workout) = scope.launch {
        val now = System.currentTimeMillis()
        saveWorkouts(historyOnce(includePartner = true).filter(match).map { change(it).copy(updatedAtEpochMs = now) })
    }

    /** Varios entrenos de una vez (una transacción): p. ej. al renombrar un gimnasio. */
    suspend fun saveWorkouts(workouts: List<Workout>) {
        if (workouts.isEmpty()) return
        val rows = withContext(Dispatchers.Default) { workouts.map { WorkoutEntity(it.id, it.startedAtEpochMs, Persistence.encodeWorkout(it)) } }
        dao.upsertAll(emptyList(), rows)
    }

    suspend fun deleteWorkout(id: String) = dao.deleteWorkout(id)

    /** Pulso y calorías del reloj que llegaron antes de que el móvil guardase el entreno (JSON; uno a la vez). */
    suspend fun savePendingHealth(raw: String) = dao.putValue(KeyValueEntity(KEY_PENDING_HEALTH, raw))

    suspend fun takePendingHealth(): String? = dao.valueOnce(KEY_PENDING_HEALTH)?.also { dao.deleteValue(KEY_PENDING_HEALTH) }

    /** Un cambio de un entreno guardado a la vez (valorarlo y el pulso que llega del reloj no se pisan). */
    private val updateMutex = Mutex()

    /**
     * Cambia el entreno [id] sobre lo guardado ahora mismo, sin pisar otro cambio a la vez; si ya no existe (se
     * borró), no hace nada. Devuelve el entreno guardado o null.
     */
    suspend fun updateWorkout(id: String, change: suspend (Workout) -> Workout): Workout? = updateMutex.withLock {
        storedWorkout(id)?.let { change(it) }?.also { saveWorkout(it) }
    }

    /** El entreno tal como está guardado ahora (p. ej. con el pulso que el reloj añadió después). */
    suspend fun storedWorkout(id: String): Workout? =
        dao.workoutJson(id)?.let { raw -> withContext(Dispatchers.Default) { runCatching { Persistence.decodeWorkout(raw) }.getOrNull() } }

    private val trashSerializer = ListSerializer(TrashedWorkout.serializer())

    /** Una sola lectura-escritura de la papelera a la vez: dos borrados seguidos no se pisan. */
    private val trashMutex = Mutex()

    /** Entrenos borrados en los últimos 30 días, el último primero (para recuperarlos desde Perfil). */
    val trash: StateFlow<List<TrashedWorkout>> = dao.value(KEY_TRASH)
        .map { raw -> TrashedWorkout.fresh(decodeTrash(raw), System.currentTimeMillis()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun decodeTrash(raw: String?): List<TrashedWorkout> =
        raw?.let { runCatching { json.decodeFromString(trashSerializer, it) }.getOrNull() }.orEmpty()

    private suspend fun saveTrash(list: List<TrashedWorkout>) =
        dao.putValue(KeyValueEntity(KEY_TRASH, json.encodeToString(trashSerializer, list)))

    private suspend fun trashOnce(): List<TrashedWorkout> = TrashedWorkout.fresh(decodeTrash(dao.valueOnce(KEY_TRASH)), System.currentTimeMillis())

    /** Borra un entreno dejándolo 30 días en la papelera. */
    suspend fun trashWorkout(workout: Workout) = trashMutex.withLock {
        val now = System.currentTimeMillis()
        saveTrash(TrashedWorkout.keep(trashOnce(), listOf(TrashedWorkout(now, workout)), now))
        dao.deleteWorkout(workout.id)
    }

    /** Vuelve a poner un entreno de la papelera en el historial; devuelve el entreno (null si ya no estaba). */
    suspend fun restoreFromTrash(id: String): Workout? = trashMutex.withLock {
        val list = trashOnce()
        val trashed = list.firstOrNull { it.workout.id == id }?.workout
        // Si ya volvió al historial por otro camino (el reloj, una copia), se queda esa versión, que es más nueva
        val workout = storedWorkout(id) ?: trashed?.also { dao.upsertWorkout(WorkoutEntity(it.id, it.startedAtEpochMs, Persistence.encodeWorkout(it))) }
        saveTrash(list.filterNot { it.workout.id == id })
        workout
    }

    /** Quita de la papelera sin recuperar: «Borrar para siempre» (o todo, con [ids] null). */
    suspend fun purgeTrash(ids: Set<String>?) = trashMutex.withLock {
        saveTrash(if (ids == null) emptyList() else trashOnce().filterNot { it.workout.id in ids })
    }

    private suspend fun untrash(ids: Set<String>) {
        // Mirando lo guardado (no [trash], que al arrancar aún puede estar vacío) y sin decodificar si no hace falta
        val raw = dao.valueOnce(KEY_TRASH) ?: return
        if (ids.none { raw.contains(it) }) return
        trashMutex.withLock { saveTrash(trashOnce().filterNot { it.workout.id in ids }) }
    }

    /** Borrado masivo: una consulta por bloque en vez de una transacción (y un recálculo) por entreno. */
    suspend fun deleteWorkouts(ids: List<String>) = ids.chunked(500).forEach { dao.deleteWorkouts(it) }

    suspend fun historyOnce(includePartner: Boolean = false): List<Workout> {
        val rows = dao.workoutsOnce()
        // Decodificar miles de entrenos no debe hacerse en el hilo principal
        return withContext(Dispatchers.Default) {
            decodeRows(rows).filter { includePartner || it.athlete == null }
        }
    }

    // --- Rutinas -----------------------------------------------------------------------------

    /** [keepPosition]: deshacer un borrado la devuelve a su sitio en vez de al final. */
    suspend fun saveRoutine(routine: Routine, keepPosition: Boolean = false) {
        val position = if (keepPosition || routines.value.any { it.id == routine.id }) routine.position else (routines.value.maxOfOrNull { it.position } ?: -1) + 1
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

    /** Medidas leídas de la base de datos ahora mismo (para trabajos en segundo plano, sin esperar al StateFlow). */
    suspend fun measurementsOnce(): List<BodyMeasurement> =
        dao.measurements().first().mapNotNull { runCatching { json.decodeFromString(BodyMeasurement.serializer(), it.json) }.getOrNull() }

    private companion object {
        const val KEY_ACTIVE = "active_workout"
        const val KEY_PARTNER = "partner_workout"
        const val KEY_PROGRAM = "active_program"
        const val KEY_TRASH = "trash"
        const val KEY_WATCH_KNOWN = "watch_known_workout"
        const val KEY_PENDING_HEALTH = "pending_watch_health"
    }
}

/** Cuánto se puede deshacer «Descartar entreno». */
private const val UNDO_DISCARD_MS = 60_000L
