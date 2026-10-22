package com.bestrong.mobile.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.training.Periodization
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.ui.components.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val actions: WorkoutActions,
    @ApplicationContext private val context: Context,
    private val settingsStore: com.bestrong.mobile.data.SettingsStore,
) : ViewModel() {
    val history: StateFlow<List<Workout>> = repo.history

    /** Récords que batió cada entreno (el 🏆 de la lista), en una sola pasada cronológica. */
    // Cada uno contra su propio historial: los entrenos de la pareja también llevan su 🏆 (como en su detalle)
    val recordCounts: StateFlow<Map<String, Int>> = combine(repo.history, repo.partnerHistory) { h, p ->
        (WorkoutStats.newRecordsInEach(h) { true } + p.groupBy { it.athlete }.values.flatMap { WorkoutStats.newRecordsInEach(it) { true }.entries }.associate { it.toPair() })
            .mapValues { it.value.size }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Modo pareja: mostrar también los entrenos de la otra persona. */
    val showPartner = MutableStateFlow(false)

    /** Solo los entrenos de la pareja (el chip «Pareja»; «Los dos» es [showPartner]). */
    val onlyPartner = MutableStateFlow(false)

    /** Los entrenos según el chip Yo / Pareja / Los dos (calendario, mapa de calor y gimnasios lo siguen). */
    private val viewed: kotlinx.coroutines.flow.Flow<List<Workout>> = combine(repo.history, repo.partnerHistory, showPartner, onlyPartner) { h, p, sp, op ->
        when {
            // Sin entrenos de la pareja (p. ej. se borró el último) el chip ya no sale: vuelve a los tuyos
            op && p.isNotEmpty() -> p
            sp -> h + p
            else -> h
        }
    }

    val trainingDays: StateFlow<Set<LocalDate>> = viewed
        .map { WorkoutStats.trainingDays(it, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Búsqueda por entreno, ejercicio o nota. */
    val query = MutableStateFlow("")

    val partnerHistory: StateFlow<List<Workout>> = repo.partnerHistory

    /** Día elegido en el calendario (null = todos). */
    val selectedDay = MutableStateFlow<LocalDate?>(null)

    init {
        // Sin entrenos de la pareja el chip desaparece: «solo la pareja» no puede quedarse puesto
        viewModelScope.launch { repo.partnerHistory.collect { if (it.isEmpty()) onlyPartner.value = false } }
    }

    /** Gimnasio elegido (null = todos). */
    val gymFilter = MutableStateFlow<String?>(null)

    /** Solo los entrenos de una rutina («todos mis días de pierna» sin que la búsqueda saque otros). */
    val routineFilter = MutableStateFlow<String?>(null)

    /** Las rutinas más hechas (id, nombre actual), para el filtro de un toque; la fila sale con dos o más. */
    val routineChips: StateFlow<List<Pair<String, String>>> = combine(repo.history, repo.routines) { h, rs ->
        val byId = rs.associateBy { it.id }
        h.mapNotNull { it.routineId }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { (id, _) -> byId[id]?.let { id to it.name } }.take(MAX_ROUTINE_CHIPS)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Gimnasios que aparecen en el historial (el filtro solo sale con dos o más). */
    val gymNames: StateFlow<List<String>> = viewed.map { list ->
        // Los gimnasios de los entrenos que se ven (con la pareja, también los suyos)
        list.mapNotNull { it.gymName }.distinct().sorted()
    }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Texto de búsqueda ya normalizado por entreno (se rehace solo si el entreno cambia). */
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    /** Idioma con el que se llenó la caché (los nombres de ejercicio cambian con él). */
    @Volatile private var searchCacheLanguage: String? = null

    private fun searchTextOf(w: Workout): String {
        val lang = context.resources.configuration.locales[0].language
        if (lang != searchCacheLanguage) {
            searchCache.clear()
            searchCacheLanguage = lang
        }
        searchCache[w.id]?.takeIf { it.first == w.updatedAtEpochMs }?.let { return it.second }
        return WorkoutStats.searchText(w) { it.displayName(context) }.also { searchCache[w.id] = w.updatedAtEpochMs to it }
    }

    val filtered: StateFlow<List<Workout>> = combine(
        repo.history,
        repo.partnerHistory,
        combine(query.debounce { if (it.isEmpty()) 0L else 150L }, combine(gymFilter, routineFilter) { g, r -> g to r }, onlyPartner) { q, g, op -> Triple(q, g, op) },
        showPartner,
        selectedDay,
    ) { h, p, (q, filters, op), sp, day ->
        val (gym, routineId) = filters
        // Buscando su nombre salen sus entrenos aunque no se estén enseñando los de la pareja
        // (sin tildes, como el resto de la búsqueda: «ines» encuentra a «Inés»)
        val partnerSearched = q.isNotBlank() && p.any { WorkoutStats.plainText(it.athlete.orEmpty()).contains(WorkoutStats.plainText(q.trim())) }
        val mine = when {
            op && p.isNotEmpty() -> p.sortedByDescending { it.startedAtEpochMs }
            sp || partnerSearched -> (h + p).sortedByDescending { it.startedAtEpochMs }
            else -> h
        }
        val byGym = if (gym == null) mine else mine.filter { it.gymName == gym }
        val all = if (routineId == null) byGym else byGym.filter { it.routineId == routineId }
        val zone = ZoneId.systemDefault()
        val ofDay = if (day == null) all else all.filter { java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() == day }
        val plainQ = WorkoutStats.plainText(q.trim())
        if (plainQ.isEmpty()) ofDay else ofDay.filter { searchTextOf(it).contains(plainQ) }
        // Empieza con el historial ya cargado (sin filtros es lo mismo): al abrir no parpadea «ningún entreno coincide»
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.history.value)

    /** Entrenos por día del año actual (mapa de calor). */
    val heatmap: StateFlow<Map<LocalDate, Int>> = viewed
        .map { WorkoutStats.dailyCounts(it, LocalDate.now().year, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Récords de ese entreno: recorre todo el historial, así que fuera del hilo principal. */
    suspend fun recordsIn(workout: Workout): List<PersonalRecord> =
        withContext(Dispatchers.Default) {
            // Frente al historial de quien lo hizo (tus marcas no son las de tu pareja)
            val same = if (workout.athlete != null) repo.partnerHistory.value.filter { it.athlete == workout.athlete } else history.value
            WorkoutStats.newRecordsIn(workout, same)
        }

    /**
     * Ejercicio a ejercicio, frente a la sesión anterior del mismo atleta (la tuya o la de tu pareja), por id de
     * ejercicio. Fuera del hilo principal.
     */
    suspend fun comparisonsFor(workout: Workout): Map<String, WorkoutStats.ExerciseComparison> = withContext(Dispatchers.Default) {
        val same = if (workout.athlete != null) repo.partnerHistory.value.filter { it.athlete == workout.athlete } else history.value
        WorkoutStats.compareWithPrevious(workout, same).associateBy { it.exerciseId }
    }

    /** Último entreno borrado, para poder deshacer. */
    private val _deleted = MutableStateFlow<Workout?>(null)
    val deleted: StateFlow<Workout?> = _deleted

    /** Borrado en curso: «deshacer» espera a que acabe (si no, Health Connect podría quedarse sin la sesión). */
    private var deleteJob: kotlinx.coroutines.Job? = null

    fun delete(id: String) {
        // A la papelera (30 días) además del «deshacer» del aviso
        val workout = (repo.history.value + repo.partnerHistory.value).firstOrNull { it.id == id } ?: return
        _deleted.value = workout
        deleteJob = viewModelScope.launch {
            repo.trashWorkout(workout)
            actions.deleteFromHealthConnectIfEnabled(workout)
        }
    }

    /** Entreno tal como estaba antes de cambiarle el gimnasio (para deshacerlo); null = nada que deshacer. */
    private val _gymEdit = MutableStateFlow<Workout?>(null)
    val gymEdit: StateFlow<Workout?> = _gymEdit

    /** Cambia dónde se hizo un entreno pasado; se puede deshacer desde el aviso. */
    fun changeGym(workout: Workout, gymName: String?) {
        if (workout.gymName == gymName) return
        _gymEdit.value = workout
        gymSetTo = gymName
        editWorkout(workout) { it.copy(gymName = gymName) }
    }

    /** El gimnasio que se puso en el último cambio (el deshacer solo actúa si sigue siendo ese). */
    private var gymSetTo: String? = null

    /** Vuelve a poner solo el gimnasio de antes, sobre el entreno tal como está ahora (no se pierden otros cambios). */
    fun undoGymEdit() = viewModelScope.launch {
        val before = _gymEdit.value ?: return@launch
        _gymEdit.value = null
        val current = repo.historyOnce(includePartner = true).firstOrNull { it.id == before.id } ?: return@launch
        if (current.gymName == gymSetTo) editWorkout(current) { it.copy(gymName = before.gymName) }
    }

    fun clearGymEdit() {
        _gymEdit.value = null
    }

    /** Entreno tal como estaba antes de borrarle una serie (para deshacerlo); null = nada que deshacer. */
    private val _setRemoved = MutableStateFlow<Workout?>(null)
    val setRemoved: StateFlow<Workout?> = _setRemoved

    /** Borra una serie de un entreno pasado; si era la última, se borra el entreno (con su propio «deshacer»). */
    fun removePastSet(workout: Workout, workoutExerciseId: String, setId: String) {
        if (WorkoutEditor.cleanupFinished(WorkoutEditor.removeSet(workout, workoutExerciseId, setId)).exercises.isNotEmpty()) _setRemoved.value = workout
        editWorkout(workout) { WorkoutEditor.removeSet(it, workoutExerciseId, setId) }
    }

    fun undoSetRemoved() {
        val before = _setRemoved.value ?: return
        _setRemoved.value = null
        editWorkout(before) { before }
    }

    fun clearSetRemoved() {
        _setRemoved.value = null
    }

    fun undoDelete() = viewModelScope.launch {
        deleteJob?.join()
        _deleted.value?.let { w ->
            // Si la papelera ya no lo tiene (se vació entre medias), se guarda el del aviso
            val restored = repo.restoreFromTrash(w.id) ?: w.also { repo.saveWorkout(it) }
            if (restored.athlete == null) actions.exportToHealthConnectIfEnabled(restored)
        }
        _deleted.value = null
    }

    fun clearDeleted() {
        _deleted.value = null
    }

    /** Corrige un entreno pasado; si se queda sin series, se borra (con deshacer). */
    fun editWorkout(workout: Workout, block: (Workout) -> Workout) = viewModelScope.launch {
        val edited = WorkoutEditor.cleanupFinished(block(workout)).copy(updatedAtEpochMs = System.currentTimeMillis())
        if (edited.exercises.isEmpty()) {
            _deleted.value = workout
            // «Deshacer» espera a este borrado, como al borrar desde la lista
            deleteJob = launch {
                repo.trashWorkout(workout)
                actions.deleteFromHealthConnectIfEnabled(workout)
            }
        } else {
            // Sobre lo guardado (el pulso del reloj o la valoración pueden haber llegado después de abrirlo)
            val updated = repo.updateWorkout(workout.id) { WorkoutEditor.cleanupFinished(block(it)).copy(updatedAtEpochMs = edited.updatedAtEpochMs) }
            // Las calorías dependen de la duración y del cardio: corregidos, se recalculan (las del reloj se respetan)
            val saved = updated?.let { u ->
                val kcal = actions.withEnergy(u)
                if (kcal.calories == u.calories && kcal.activeCalories == u.activeCalories) {
                    u
                } else {
                    repo.updateWorkout(u.id) { it.copy(calories = kcal.calories, activeCalories = kcal.activeCalories, caloriesSource = kcal.caloriesSource) }
                }
            }
            // Corregido aquí, corregido en Health Connect: se quita lo de antes (un bloque de cardio borrado no
            // dejaría su distancia) y se exporta de nuevo
            if (saved != null && saved.athlete == null) {
                actions.deleteFromHealthConnectIfEnabled(workout)
                actions.exportToHealthConnectIfEnabled(saved)
            }
        }
    }

    val routines: StateFlow<List<Routine>> = repo.routines

    /**
     * Registra un entreno pasado a partir de una rutina: series completadas con los pesos de la
     * última vez (o los de la rutina). Devuelve el id para abrirlo y ajustar lo que haga falta.
     */
    /** Quita todos los filtros del historial (búsqueda, gimnasio, día y «solo la pareja»). */
    fun clearFilters() {
        query.value = ""
        gymFilter.value = null
        routineFilter.value = null
        selectedDay.value = null
        onlyPartner.value = false
    }

    /** Guarda en [uri] los entrenos de la lista tal como se ven (con sus filtros) en CSV; [onDone] dice si salió bien. */
    fun exportFiltered(uri: android.net.Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val list = filtered.value
        val ok = runCatching {
            val csv = withContext(Dispatchers.Default) { com.bestrong.core.io.WorkoutCsv.export(list) }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("no stream") }
        }.isSuccess
        onDone(ok)
    }

    /** Nombre de la pareja si hay entrenos suyos (para apuntar uno pasado a su nombre). */
    /** Mancuernas: el peso se ve y se escribe como el total del par (×2) si así está en Ajustes. */
    fun weightFactor(exercise: com.bestrong.core.model.Exercise): Int =
        if (exercise.equipment == com.bestrong.core.model.Equipment.DUMBBELL && settingsStore.settings.value.dumbbellTotal) 2 else 1

    fun partnerName(): String? = settingsStore.settings.value.partnerAthlete.takeIf { repo.partnerHistory.value.isNotEmpty() }

    fun logPast(routine: Routine, startMs: Long, endMs: Long, forPartner: Boolean, onLogged: (String) -> Unit) = viewModelScope.launch {
        val athlete = partnerName().takeIf { forPartner }
        // Pesos de la última sesión anterior a esa fecha de quien lo hizo (no la sugerencia de progresión de hoy)
        val own = if (athlete != null) repo.partnerHistory.value.filter { it.athlete == athlete } else repo.historyOnce()
        val before = own.filter { it.isFinished && it.startedAtEpochMs < startMs }
        val w = routine.toWorkout(startMs, { id -> WorkoutStats.previousSets(id, before) })
        val done = w.copy(
            athlete = athlete,
            // Como los de la pareja empezados en directo: «Ana · Pierna»
            name = athlete?.let { "$it · ${w.name}" } ?: w.name,
            finishedAtEpochMs = endMs,
            updatedAtEpochMs = System.currentTimeMillis(),
            exercises = w.exercises.map { we -> we.copy(sets = we.sets.map { it.copy(completed = true, completedAtEpochMs = endMs) }) },
        )
        repo.saveWorkout(done)
        // Como al copiarlo o editarlo: también a Health Connect
        if (done.athlete == null) actions.exportToHealthConnectIfEnabled(done)
        onLogged(done.id)
    }

    /** Copia un entreno pasado, tal cual, a otro día (el que se hizo igual y no se apuntó). */
    fun copyToDay(workout: Workout, startMs: Long, endMs: Long, exerciseIds: Set<String>) = viewModelScope.launch {
        val copy = WorkoutEditor.copyToDay(workout, startMs, endMs, System.currentTimeMillis(), exerciseIds)
        if (copy.exercises.isEmpty()) return@launch
        repo.saveWorkout(copy)
        _copied.value = copy.id
        if (copy.athlete == null) actions.exportToHealthConnectIfEnabled(copy)
    }

    /** Id de la copia recién hecha (aviso con «Abrir»); null cuando ya se enseñó. */
    private val _copied = MutableStateFlow<String?>(null)
    val copied: StateFlow<String?> = _copied

    fun clearCopied() {
        _copied.value = null
    }

    /** Cambia la fecha, la hora y la duración de un entreno pasado. */
    fun retime(workout: Workout, startMs: Long, endMs: Long, name: String = workout.name, notes: String = workout.notes) = editWorkout(workout) { w ->
        val shift = startMs - w.startedAtEpochMs
        // La duración que se escribe es la de entreno: las pausas ya no cuentan aparte (y se guardan también el
        // nombre y las notas cambiados en el mismo diálogo)
        w.copy(
            name = name,
            notes = notes,
            startedAtEpochMs = startMs,
            finishedAtEpochMs = endMs,
            pausedTotalMs = 0,
            pausedAtEpochMs = null,
            exercises = w.exercises.map { we ->
                we.copy(sets = we.sets.map { s -> s.copy(completedAtEpochMs = s.completedAtEpochMs?.plus(shift), startedAtEpochMs = s.startedAtEpochMs?.plus(shift)) })
            },
        )
    }

    /** Añade una serie hecha (copia de la última) a un ejercicio de un entreno pasado. */
    fun addPastSet(workout: Workout, weId: String) = editWorkout(workout) { w ->
        val added = WorkoutEditor.addSet(w, weId)
        WorkoutEditor.updateExercise(added, weId) { we -> we.copy(sets = we.sets.dropLast(1) + we.sets.last().copy(completed = true)) }
    }

    /** Rutinas por id, para saber qué descanso tiene planificado cada ejercicio. */
    val routinesById: StateFlow<Map<String, com.bestrong.core.model.Routine>> = repo.routines.map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Pone en la rutina de ese entreno el descanso real que hiciste en ese ejercicio. [occurrence] dice
     * cuál de las apariciones del ejercicio es (una rutina puede repetirlo, p. ej. series de descarga).
     */
    fun useRealRest(workout: Workout, exerciseId: String, occurrence: Int, seconds: Int, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val routine = workout.routineId?.let { repo.routine(it) }
        val target = routine?.exercises?.filter { it.exercise.id == exerciseId }?.getOrNull(occurrence)
        if (routine == null || target == null) return@launch onDone(false)
        repo.saveRoutine(
            routine.copy(exercises = routine.exercises.map { if (it.id == target.id) it.copy(restSeconds = seconds) else it }),
            keepPosition = true,
        )
        onDone(true)
    }

    /** El entreno en texto (series, RPE y notas), como el informe semanal para el entrenador. */
    /** Nombres de los perfiles de gimnasio (para corregir dónde se hizo un entreno). */
    val gymProfileNames: StateFlow<List<String>> = settingsStore.settings.map { s -> s.gyms.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settings.value.gyms.map { it.name })

    fun shareText(workout: Workout): String = settingsStore.settings.value.let { prefs ->
        com.bestrong.core.io.CoachReport.text(
            listOf(workout.copy(finishedAtEpochMs = workout.finishedAtEpochMs ?: workout.startedAtEpochMs)),
            fromEpochMs = 0,
            unit = prefs.unit,
            exerciseName = { it.displayName(context) },
            dumbbellTotal = prefs.dumbbellTotal,
        )
    }

    fun saveAsRoutine(workout: Workout) = viewModelScope.launch { repo.saveRoutine(Routine.fromWorkout(workout)) }

    /** Repite un entrenamiento pasado como entrenamiento activo. */
    fun repeat(workout: Workout, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() == null) {
            val template = workout.routineId?.let { repo.routine(it) }
            actions.useRoutineGym(template, workout.gymName)
            val fromProgram = workout.routineId?.startsWith(Periodization.ROUTINE_PREFIX) == true
            // Notas: las fijas de la rutina; en un programa (rutina generada) se conservan las de la sesión
            // Programa: su estructura y las notas de esa entrada (las genera el programa); rutina: la suya
            // Rellenar recorre el historial varias veces por ejercicio: fuera del hilo principal
            val again = withContext(Dispatchers.Default) {
                Routine.repeatOf(workout, keepAllSets = template != null || fromProgram) { we ->
                    if (fromProgram) we.notes else template?.exercises?.firstOrNull { it.exercise.id == we.exercise.id }?.notes.orEmpty()
                }
                    .toWorkout(System.currentTimeMillis(), actions.prefillLookup())
            }
            // Una sesión de programa repetida no cuenta como el siguiente día del programa
            repo.startActive(again.copy(routineId = workout.routineId?.takeUnless { it.startsWith(Periodization.ROUTINE_PREFIX) }))
        }
        onStarted()
    }
}

private const val MAX_ROUTINE_CHIPS = 5
