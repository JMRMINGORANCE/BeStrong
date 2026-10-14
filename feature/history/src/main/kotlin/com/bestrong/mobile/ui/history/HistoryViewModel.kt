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

    val trainingDays: StateFlow<Set<LocalDate>> = repo.history
        .map { WorkoutStats.trainingDays(it, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Búsqueda por entreno, ejercicio o nota. */
    val query = MutableStateFlow("")

    /** Modo pareja: mostrar también los entrenos de la otra persona. */
    val showPartner = MutableStateFlow(false)
    val partnerHistory: StateFlow<List<Workout>> = repo.partnerHistory

    /** Día elegido en el calendario (null = todos). */
    val selectedDay = MutableStateFlow<LocalDate?>(null)

    /** Gimnasio elegido (null = todos). */
    val gymFilter = MutableStateFlow<String?>(null)

    /** Gimnasios que aparecen en el historial (el filtro solo sale con dos o más). */
    val gymNames: StateFlow<List<String>> = combine(repo.history, repo.partnerHistory, showPartner) { h, p, sp ->
        // Con los de la pareja a la vista, también sus gimnasios
        (if (sp) h + p else h).mapNotNull { it.gymName }.distinct().sorted()
    }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filtered: StateFlow<List<Workout>> = combine(
        repo.history,
        repo.partnerHistory,
        combine(query.debounce { if (it.isEmpty()) 0L else 150L }, gymFilter) { q, g -> q to g },
        showPartner,
        selectedDay,
    ) { h, p, (q, gym), sp, day ->
        val mine = if (sp) (h + p).sortedByDescending { it.startedAtEpochMs } else h
        val all = if (gym == null) mine else mine.filter { it.gymName == gym }
        val zone = ZoneId.systemDefault()
        val ofDay = if (day == null) all else all.filter { java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() == day }
        WorkoutStats.search(ofDay, q) { it.displayName(context) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entrenos por día del año actual (mapa de calor). */
    val heatmap: StateFlow<Map<LocalDate, Int>> = repo.history
        .map { WorkoutStats.dailyCounts(it, LocalDate.now().year, ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Récords de ese entreno: recorre todo el historial, así que fuera del hilo principal. */
    suspend fun recordsIn(workout: Workout): List<PersonalRecord> =
        withContext(Dispatchers.Default) { WorkoutStats.newRecordsIn(workout, history.value) }

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

    fun delete(id: String) = viewModelScope.launch {
        _deleted.value = repo.history.value.firstOrNull { it.id == id }
        repo.deleteWorkout(id)
    }

    /** Entreno tal como estaba antes de cambiarle el gimnasio (para deshacerlo); null = nada que deshacer. */
    private val _gymEdit = MutableStateFlow<Workout?>(null)
    val gymEdit: StateFlow<Workout?> = _gymEdit

    /** Cambia dónde se hizo un entreno pasado; se puede deshacer desde el aviso. */
    fun changeGym(workout: Workout, gymName: String?) {
        if (workout.gymName == gymName) return
        _gymEdit.value = workout
        editWorkout(workout) { it.copy(gymName = gymName) }
    }

    fun undoGymEdit() = viewModelScope.launch {
        _gymEdit.value?.let { repo.saveWorkout(it.copy(updatedAtEpochMs = System.currentTimeMillis())) }
        _gymEdit.value = null
    }

    fun clearGymEdit() {
        _gymEdit.value = null
    }

    fun undoDelete() = viewModelScope.launch {
        _deleted.value?.let { repo.saveWorkout(it) }
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
            repo.deleteWorkout(workout.id)
        } else {
            repo.saveWorkout(edited)
        }
    }

    val routines: StateFlow<List<Routine>> = repo.routines

    /**
     * Registra un entreno pasado a partir de una rutina: series completadas con los pesos de la
     * última vez (o los de la rutina). Devuelve el id para abrirlo y ajustar lo que haga falta.
     */
    fun logPast(routine: Routine, startMs: Long, endMs: Long, onLogged: (String) -> Unit) = viewModelScope.launch {
        // Pesos de la última sesión anterior a esa fecha (no la sugerencia de progresión de hoy)
        val before = repo.historyOnce().filter { it.isFinished && it.startedAtEpochMs < startMs }
        val w = routine.toWorkout(startMs, { id -> WorkoutStats.previousSets(id, before) })
        val done = w.copy(
            finishedAtEpochMs = endMs,
            updatedAtEpochMs = System.currentTimeMillis(),
            exercises = w.exercises.map { we -> we.copy(sets = we.sets.map { it.copy(completed = true, completedAtEpochMs = endMs) }) },
        )
        repo.saveWorkout(done)
        onLogged(done.id)
    }

    /** Cambia la fecha, la hora y la duración de un entreno pasado. */
    fun retime(workout: Workout, startMs: Long, endMs: Long) = editWorkout(workout) { w ->
        val shift = startMs - w.startedAtEpochMs
        // La duración que se escribe es la de entreno: las pausas ya no cuentan aparte
        w.copy(
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
            val again = Routine.repeatOf(workout, keepAllSets = template != null || fromProgram) { we ->
                if (fromProgram) we.notes else template?.exercises?.firstOrNull { it.exercise.id == we.exercise.id }?.notes.orEmpty()
            }
                .toWorkout(System.currentTimeMillis(), actions.prefillLookup())
            // Una sesión de programa repetida no cuenta como el siguiente día del programa
            repo.setActive(again.copy(routineId = workout.routineId?.takeUnless { it.startsWith(Periodization.ROUTINE_PREFIX) }))
        }
        onStarted()
    }
}
