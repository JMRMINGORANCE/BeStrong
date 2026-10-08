package com.bestrong.mobile.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.library.Programs
import com.bestrong.core.library.TrainingProgram
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.PeriodizedPrograms
import com.bestrong.core.training.ProgramSession
import com.bestrong.mobile.AppSync
import com.bestrong.mobile.R
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.sync.WatchSync
import com.bestrong.mobile.ui.components.defaultWorkoutName
import com.bestrong.mobile.ui.components.uiLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val actions: WorkoutActions,
    private val watchSync: WatchSync,
    private val settingsStore: SettingsStore,
    private val appSync: AppSync,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Sesión que toca hoy del programa activo (con las cargas ya calculadas). */
    val today: StateFlow<ProgramSession?> = combine(repo.activeProgram, repo.history) { active, history ->
        active?.let { Periodization.nextSession(it, history, context.uiLanguage()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Si no hay programa: la rutina que lleva más tiempo sin hacerse. */
    val suggestedRoutine: StateFlow<Routine?> = combine(repo.routines, repo.history) { routines, history ->
        val last = history.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        routines.filter { it.exercises.isNotEmpty() }.minByOrNull { last[it.id] ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Semana actual: días entrenados frente al objetivo y adherencia de las 8 semanas anteriores. */
    val week: StateFlow<WeekProgress> = combine(repo.history, repo.activeProgram, settingsStore.settings) { history, active, s ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val goal = active?.weeklyGoal ?: s.weeklyGoal
        val since = active?.let { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
        WeekProgress(
            trainedDays = Periodization.trainedDaysOfWeek(history, today, zone),
            goal = goal,
            adherence = Periodization.adherence(history, goal, today, zone, since = since),
            today = today.dayOfWeek,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekProgress())

    val periodizedPrograms: List<PeriodizedProgram> = PeriodizedPrograms.all

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
        if (repo.activeWorkout.value != null) {
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
        if (repo.activeWorkout.value == null) repo.setActive(WorkoutEditor.empty(System.currentTimeMillis(), name = defaultWorkoutName(context, System.currentTimeMillis())))
        onStarted()
    }

    fun startRoutine(routine: Routine, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeWorkout.value != null) {
            _messages.tryEmit(context.getString(R.string.msg_workout_already_active))
            onStarted()
            return@launch
        }
        repo.setActive(routine.toWorkout(System.currentTimeMillis(), actions.prefillLookup()))
        onStarted()
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
        appSync.pushToWatchNow()
        val ok = watchSync.startOnWatch(routine?.id)
        _messages.tryEmit(context.getString(if (ok) R.string.msg_opening_on_watch else R.string.msg_no_watch))
    }

    fun duplicate(routine: Routine) = viewModelScope.launch {
        repo.saveRoutine(routine.copy(id = com.bestrong.core.util.newId(), name = context.getString(R.string.routine_copy_name, routine.name)))
    }

    val programs: List<TrainingProgram> = Programs.all

    /** Crea las rutinas del programa elegido en el idioma de la app. */
    fun addProgram(program: TrainingProgram) = viewModelScope.launch {
        val routines = program.toRoutines(context.uiLanguage())
        repo.addRoutines(routines)
        _messages.tryEmit(context.getString(R.string.msg_program_added, program.name(context.uiLanguage()), routines.size))
    }

    fun delete(routine: Routine) = viewModelScope.launch { repo.deleteRoutine(routine.id) }

    fun move(routine: Routine, delta: Int) = viewModelScope.launch {
        val list = repo.routines.value.toMutableList()
        val i = list.indexOfFirst { it.id == routine.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@launch
        list.add(j, list.removeAt(i))
        list.forEachIndexed { pos, r -> repo.saveRoutine(r.copy(position = pos)) }
    }
}

data class WeekProgress(
    val trainedDays: Set<DayOfWeek> = emptySet(),
    val goal: Int = 3,
    val adherence: Double? = null,
    val today: DayOfWeek = DayOfWeek.MONDAY,
)
