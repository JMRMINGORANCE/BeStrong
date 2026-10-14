package com.bestrong.mobile.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.io.RoutineLink
import com.bestrong.core.library.Programs
import com.bestrong.core.library.TrainingProgram
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Fatigue
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.PeriodizedPrograms
import com.bestrong.core.training.ProgramSession
import com.bestrong.core.training.Readiness
import com.bestrong.core.training.ReadinessReport
import com.bestrong.core.training.missedSessions
import com.bestrong.mobile.WatchPush
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.Intake
import com.bestrong.mobile.data.IntakeStore
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.health.HealthConnectReader
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.sync.WatchSync
import com.bestrong.mobile.ui.components.defaultWorkoutName
import com.bestrong.mobile.ui.components.programTexts
import com.bestrong.mobile.ui.components.uiLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val actions: WorkoutActions,
    private val watchSync: WatchSync,
    private val settingsStore: SettingsStore,
    private val watchPush: WatchPush,
    private val planning: PlanningStore,
    private val healthReader: HealthConnectReader,
    private val intakeStore: IntakeStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Preparación del día (sueño y pulso en reposo de Health Connect + última valoración). */
    private val _readiness = MutableStateFlow<ReadinessReport?>(null)
    val readiness: StateFlow<ReadinessReport?> = _readiness
    val healthConnected = MutableStateFlow(false)

    fun refreshReadiness() = viewModelScope.launch {
        val snap = runCatching { healthReader.read() }.getOrNull()
        healthConnected.value = snap != null && (snap.sleepHours != null || snap.restingHr != null)
        val lastRating = repo.history.value.firstOrNull { it.rating != null }?.rating
        _readiness.value = Readiness.score(snap?.sleepHours, snap?.restingHr, snap?.baselineRestingHr, lastRating)
        intakeStore.refresh()
    }

    init {
        refreshReadiness()
    }

    // --- Proteína y agua -----------------------------------------------------------------------

    val intake: StateFlow<Intake> = intakeStore.today
    fun addIntake(proteinG: Int = 0, waterMl: Int = 0) = intakeStore.add(proteinG, waterMl)

    /** Peso corporal más reciente (para el objetivo de proteína). */
    val bodyWeightKg: StateFlow<Double?> = repo.measurements.map { m -> m.sortedByDescending { it.dateEpochMs }.firstNotNullOfOrNull { it.weightKg } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sesión que toca hoy del programa activo (con las cargas ya calculadas). */
    val today: StateFlow<ProgramSession?> = combine(repo.activeProgram, repo.history, planning.state) { active, history, plan ->
        val session = active?.let { Periodization.nextSession(it, history, context.uiLanguage(), plan.customPrograms, context.programTexts()) }
        // Semana de descarga del mesociclo: la sesión se aligera sola
        if (session != null && plan.mesocycle?.isDeloadWeek(LocalDate.now()) == true) session.copy(routine = Fatigue.deload(session.routine)) else session
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ajustes (gimnasio activo para el selector de arriba). */
    val settings: StateFlow<com.bestrong.mobile.data.UserSettings> = settingsStore.settings

    fun setActiveGym(id: String) = settingsStore.update { it.copy(activeGymId = id) }

    /** Id → nombre de los gimnasios (para enseñar el de cada rutina; los borrados no salen). */
    val gymNames: StateFlow<Map<String, String>> = settingsStore.settings.map { s -> s.gyms.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settings.value.gyms.associate { it.id to it.name })

    /** Si no hay programa: la rutina que lleva más tiempo sin hacerse. */
    val suggestedRoutine: StateFlow<Routine?> = combine(repo.routines, repo.history) { routines, history ->
        val last = history.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        routines.filter { it.exercises.isNotEmpty() && !it.archived }.minByOrNull { last[it.id] ?: 0L }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Semana actual: días entrenados frente al objetivo y adherencia de las 8 semanas anteriores. */
    val week: StateFlow<WeekProgress> = combine(repo.history, repo.activeProgram, settingsStore.settings) { history, active, s ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val goal = active?.weeklyGoal ?: s.weeklyGoal
        // Solo cuentan las semanas desde que se usa la app (o desde que empezó el programa)
        val firstWorkout = history.minOfOrNull { it.startedAtEpochMs }
        val since = (active?.startedAtEpochMs ?: firstWorkout)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        WeekProgress(
            trainedDays = Periodization.trainedDaysOfWeek(history, today, zone),
            goal = goal,
            adherence = if (history.isEmpty()) null else Periodization.adherence(history, goal, today, zone, since = since),
            today = today.dayOfWeek,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekProgress())

    /** Músculos que esta semana van por detrás de lo habitual (de jueves en adelante). */
    val laggingMuscles: StateFlow<List<Pair<com.bestrong.core.model.MuscleGroup, Double>>> = combine(repo.history, settingsStore.settings) { history, s ->
        val zone = ZoneId.systemDefault()
        com.bestrong.core.stats.WorkoutStats.laggingMuscles(history, LocalDate.now(zone), zone, minSets = s.weeklySetsTarget.toDouble())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mínimo de series semanales por músculo que se ha puesto el usuario. */
    val weeklySetsTarget: Int get() = settingsStore.settings.value.weeklySetsTarget

    /** Programas predefinidos más los creados por el usuario. */
    val periodizedPrograms: List<PeriodizedProgram> get() = PeriodizedPrograms.all + planning.state.value.customPrograms

    /** Entreno abierto sin cambios desde hace más de 3 h (se olvidó terminar). */
    fun forgottenWorkout(now: Long = System.currentTimeMillis()): Workout? =
        repo.activeWorkout.value?.takeIf { WorkoutEditor.isForgotten(it, now) }

    /** Lo termina a la hora del último cambio y lo guarda en el historial. */
    fun finishForgotten() = viewModelScope.launch {
        val w = repo.activeWorkout.value ?: return@launch
        val finished = WorkoutEditor.finishAtLastActivity(w)
        repo.setActive(null)
        if (finished.exercises.isNotEmpty()) repo.saveWorkout(finished)
        _messages.tryEmit(context.getString(R.string.msg_forgotten_saved))
    }

    fun discardActive() = viewModelScope.launch { repo.discardActive() }

    /** Entreno recién descartado (para el aviso con «Deshacer»). */
    val discarded: StateFlow<com.bestrong.core.model.Workout?> = repo.discarded

    fun restoreDiscarded(onRestored: () -> Unit) = viewModelScope.launch { if (actions.restoreDiscarded()) onRestored() }

    fun forgetDiscarded() = repo.forgetDiscarded()

    /** Peso de hoy apuntado desde Inicio (va a Medidas y, si se exporta, a Health Connect). */
    fun logBodyWeight(kg: Double) = viewModelScope.launch {
        actions.saveMeasurement(com.bestrong.core.model.BodyMeasurement(dateEpochMs = System.currentTimeMillis(), weightKg = kg))
    }

    fun discardRecent(): Boolean = repo.recentlyDiscarded

    /** Training max sugerido (90 % del mejor 1RM estimado) para preparar un programa. */
    fun suggestedTrainingMax(exerciseId: String): Double? = Periodization.suggestedTrainingMax(exerciseId, repo.history.value)

    fun activateProgram(program: PeriodizedProgram, trainingMaxKg: Map<String, Double>) = viewModelScope.launch {
        repo.setActiveProgram(ActiveProgram(program.id, System.currentTimeMillis(), trainingMaxKg, weeklyGoal = program.daysPerWeek))
        _messages.tryEmit(context.getString(R.string.msg_program_started, program.name(context.uiLanguage())))
    }

    fun stopProgram() = viewModelScope.launch { repo.setActiveProgram(null) }

    fun setWeeklyGoal(goal: Int) = settingsStore.update { it.copy(weeklyGoal = goal.coerceIn(1, 7)) }

    /** Empieza la sesión del programa con las cargas prescritas (sin sustituirlas por la última vez). */
    fun startProgramSession(session: ProgramSession, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() != null) {
            _messages.tryEmit(context.getString(R.string.msg_workout_already_active))
            onStarted()
            return@launch
        }
        repo.setActive(session.routine.toWorkout(System.currentTimeMillis()))
        onStarted()
    }

    val routines: StateFlow<List<Routine>> = repo.routines
    val active: StateFlow<Workout?> = repo.activeWorkout
    val history: StateFlow<List<Workout>> = repo.history

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun startEmpty(onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() == null) repo.setActive(WorkoutEditor.empty(System.currentTimeMillis(), name = defaultWorkoutName(context, System.currentTimeMillis())))
        onStarted()
    }

    /** Repite el último entreno (mismos ejercicios y series, con los pesos que tocan hoy). */
    fun repeatLast(onStarted: () -> Unit) = viewModelScope.launch {
        val last = repo.history.value.firstOrNull { it.isFinished } ?: return@launch
        if (repo.activeOnce() == null) {
            val template = last.routineId?.let { repo.routine(it) }
            actions.useRoutineGym(template)
            val fromProgram = last.routineId?.startsWith(Periodization.ROUTINE_PREFIX) == true
            // Notas: las fijas de la rutina; en un programa (rutina generada) se conservan las de la sesión
            // Programa: su estructura y las notas de esa entrada (las genera el programa); rutina: la suya
            val again = Routine.repeatOf(last, keepAllSets = template != null || fromProgram) { we ->
                if (fromProgram) we.notes else template?.exercises?.firstOrNull { it.exercise.id == we.exercise.id }?.notes.orEmpty()
            }
                .toWorkout(System.currentTimeMillis(), actions.prefillLookup())
            // Una sesión de programa repetida no cuenta como el siguiente día del programa
            repo.setActive(again.copy(routineId = last.routineId?.takeUnless { it.startsWith(Periodization.ROUTINE_PREFIX) }))
        }
        onStarted()
    }

    fun startRoutine(routine: Routine, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() != null) {
            _messages.tryEmit(context.getString(R.string.msg_workout_already_active))
            onStarted()
            return@launch
        }
        // Su gimnasio pasa a ser el activo antes de rellenar: los pesos salen con su material
        actions.useRoutineGym(routine)
        repo.setActive(routine.toWorkout(System.currentTimeMillis(), actions.prefillLookup()))
        onStarted()
    }

    /** Rutina recibida por enlace o QR, pendiente de que el usuario confirme la importación. */
    private val _pendingImport = MutableStateFlow<Routine?>(null)
    val pendingImport: StateFlow<Routine?> = _pendingImport

    /** Un enlace puede llegar de cualquier web o app: se pregunta antes de guardarla. */
    fun importRoutineLink(link: String) {
        _pendingImport.value = RoutineLink.decode(link) ?: return
    }

    /** Confirmada: se añade a «Mis rutinas». */
    fun confirmImport() = viewModelScope.launch {
        val routine = _pendingImport.value ?: return@launch
        _pendingImport.value = null
        repo.saveRoutine(routine)
        _messages.tryEmit(context.getString(R.string.routine_imported, routine.name))
    }

    fun dismissImport() {
        _pendingImport.value = null
    }

    /** Desde el widget: empezar una rutina por id. */
    fun startRoutineById(id: String, onStarted: () -> Unit) = viewModelScope.launch {
        val routine = repo.routine(id)
        if (routine == null) {
            _messages.tryEmit(context.getString(R.string.msg_routine_missing))
            return@launch
        }
        startRoutine(routine, onStarted)
    }

    fun startOnWatch(routine: Routine?) = viewModelScope.launch {
        watchPush.pushToWatchNow()
        val ok = watchSync.startOnWatch(routine?.id)
        _messages.tryEmit(context.getString(if (ok) R.string.msg_opening_on_watch else R.string.msg_no_watch))
    }

    /**
     * Rutinas activas que llevan más de [STALE_DAYS] días sin hacerse (con días sin usar), la más olvidada
     * primero: candidatas a archivar. Las que nunca se hicieron no cuentan (pueden ser nuevas).
     */
    val staleRoutines: StateFlow<List<Pair<Routine, Int>>> = combine(repo.routines, repo.history) { routines, history ->
        val now = System.currentTimeMillis()
        val last = history.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        routines.filter { !it.archived }.mapNotNull { r ->
            val days = last[r.id]?.let { ((now - it) / 86_400_000L).toInt() } ?: return@mapNotNull null
            (r to days).takeIf { days > STALE_DAYS }
        }.sortedByDescending { it.second }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** «Hace un año»: los ejercicios que hacías entonces y ahora, el que más ha mejorado primero (máx. 3). */
    val yearAgo: StateFlow<List<com.bestrong.core.stats.YearAgoComparison>> = repo.history.map { history ->
        val zone = ZoneId.systemDefault()
        com.bestrong.core.stats.YearAgo.compare(history, LocalDate.now(zone), zone).take(3)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sesión planificada saltada: el día, la rutina y si se puede pasar a mañana (de esta semana y libre). */
    data class MissedSession(val day: java.time.DayOfWeek, val routine: Routine, val canMoveToTomorrow: Boolean)

    /** La última sesión planificada de esta semana que se pasó sin entrenar ni hacerse después, si la hay. */
    val missedSession: StateFlow<MissedSession?> = combine(planning.state, repo.history, repo.routines) { plan, history, routines ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val trained = com.bestrong.core.stats.WorkoutStats.trainingDays(history, zone)
        val doneOn = history.filter { it.isFinished && it.routineId != null }
            .groupBy({ it.routineId!! }, { java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() })
        val week = plan.planFor(weekStart)
        val tomorrow = today.plusDays(1)
        val canMove = tomorrow.dayOfWeek != java.time.DayOfWeek.MONDAY && week.routineFor(tomorrow.dayOfWeek) == null
        week.missedSessions(weekStart, today, trained) { id, since -> doneOn[id].orEmpty().any { !it.isBefore(since) } }
            .firstNotNullOfOrNull { (day, id) -> routines.firstOrNull { it.id == id && !it.archived }?.let { MissedSession(day, it, canMove) } }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pasa la sesión saltada a mañana (solo si mañana es de esta semana y está libre). */
    fun moveMissedToTomorrow(day: java.time.DayOfWeek) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        if (tomorrow.dayOfWeek == java.time.DayOfWeek.MONDAY) return
        planning.update { s ->
            val plan = s.planFor(weekStart)
            if (plan.routineFor(tomorrow.dayOfWeek) != null) s else s.copy(weekPlans = s.weekPlans + (weekStart.toEpochDay() to plan.move(day, tomorrow.dayOfWeek)))
        }
    }

    /** Archivar (o recuperar) sin borrar: el historial y la rutina se conservan. */
    fun setArchived(routine: Routine, archived: Boolean) = viewModelScope.launch {
        repo.saveRoutine(routine.copy(archived = archived), keepPosition = true)
    }

    fun duplicate(routine: Routine) = viewModelScope.launch {
        repo.saveRoutine(routine.copy(id = com.bestrong.core.util.newId(), name = context.getString(R.string.routine_copy_name, routine.name), archived = false))
    }

    val programs: List<TrainingProgram> = Programs.all

    /** Crea las rutinas del programa elegido en el idioma de la app. */
    fun addProgram(program: TrainingProgram) = viewModelScope.launch {
        val routines = program.toRoutines(context.uiLanguage())
        repo.addRoutines(routines)
        _messages.tryEmit(context.getString(R.string.msg_program_added, program.name(context.uiLanguage()), routines.size))
    }

    /** Última rutina borrada, para poder deshacerlo desde el aviso. */
    private val _deletedRoutine = MutableStateFlow<Routine?>(null)
    val deletedRoutine: StateFlow<Routine?> = _deletedRoutine

    private var forgetDeleted: kotlinx.coroutines.Job? = null

    fun delete(routine: Routine) = viewModelScope.launch {
        _deletedRoutine.value = routine
        // El aviso dura unos segundos: pasado ese rato ya no se ofrece deshacer (ni al volver a Inicio)
        forgetDeleted?.cancel()
        forgetDeleted = viewModelScope.launch {
            kotlinx.coroutines.delay(UNDO_WINDOW_MS)
            if (_deletedRoutine.value?.id == routine.id) _deletedRoutine.value = null
        }
        repo.deleteRoutine(routine.id)
    }

    /** Vuelve a guardar la rutina tal cual (mismo id y posición). */
    fun undoDelete() {
        // Se toma ya (no dentro de la corrutina): el aviso limpia el estado justo después
        val routine = _deletedRoutine.value ?: return
        _deletedRoutine.value = null
        viewModelScope.launch { repo.saveRoutine(routine, keepPosition = true) }
    }

    fun clearDeleted() {
        _deletedRoutine.value = null
    }

    fun move(routine: Routine, delta: Int) = viewModelScope.launch {
        val list = repo.routines.value.toMutableList()
        val i = list.indexOfFirst { it.id == routine.id }

        // Se mueve dentro de su carpeta (en Inicio cada carpeta se ve aparte): el vecino es el de la misma carpeta
        fun folderOf(r: Routine) = r.folder?.trim()?.takeIf(String::isNotEmpty)
        // Las archivadas van en su propia sección: no se intercambia con una que no se ve
        // (las archivadas se ven todas juntas, sin carpetas)
        val sameFolder = list.indices.filter { list[it].archived == routine.archived && (routine.archived || folderOf(list[it]) == folderOf(routine)) }
        val j = sameFolder.getOrNull(sameFolder.indexOf(i) + delta) ?: return@launch
        if (i < 0) return@launch
        list[i] = list[j].also { list[j] = list[i] }
        list.forEachIndexed { pos, r -> repo.saveRoutine(r.copy(position = pos)) }
    }
}

private const val UNDO_WINDOW_MS = 10_000L

data class WeekProgress(
    val trainedDays: Set<DayOfWeek> = emptySet(),
    val goal: Int = 3,
    val adherence: Double? = null,
    val today: DayOfWeek = DayOfWeek.MONDAY,
)

/** Días sin hacer una rutina a partir de los cuales se propone archivarla. */
private const val STALE_DAYS = 60
