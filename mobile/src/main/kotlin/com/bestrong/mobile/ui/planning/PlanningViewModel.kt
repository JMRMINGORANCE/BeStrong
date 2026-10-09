package com.bestrong.mobile.ui.planning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Routine
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.training.Calisthenics
import com.bestrong.core.training.Fatigue
import com.bestrong.core.training.FatigueReport
import com.bestrong.core.training.Goal
import com.bestrong.core.training.InjuryZone
import com.bestrong.core.training.Mesocycle
import com.bestrong.core.training.PeakWeek
import com.bestrong.core.training.Peaking
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.RoutineGenerator
import com.bestrong.core.training.RoutineTime
import com.bestrong.mobile.R
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.PlanningState
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.ui.components.uiLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Nivel actual en una cadena de calistenia y el siguiente paso. */
data class CalisthenicsLevel(val chain: String, val currentId: String?, val nextId: String)

data class PlanningUi(
    val state: PlanningState = PlanningState(),
    val fatigue: FatigueReport? = null,
    val deloadWeek: Boolean = false,
    val mesocycleWeek: Int? = null,
    val calisthenics: List<CalisthenicsLevel> = emptyList(),
    val peak: List<PeakWeek> = emptyList(),
    /** 1RM estimado de los básicos para el plan de pico. */
    val oneRepMaxes: Map<String, Double> = emptyMap(),
    val routines: List<Routine> = emptyList(),
    val weekStart: LocalDate = PlanningStore.monday(LocalDate.now()),
)

@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val store: PlanningStore,
    private val settings: SettingsStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val ui: StateFlow<PlanningUi> = combine(store.state, repo.history, repo.routines) { s, history, routines ->
        val today = LocalDate.now(ZoneId.systemDefault())
        val calis = Calisthenics.chains.map { (name, chain) ->
            // Nivel más alto de la cadena que ya se ha hecho
            val done = chain.lastOrNull { id -> history.any { w -> w.exercises.any { it.exercise.id == id && it.completedSets.isNotEmpty() } } }
            CalisthenicsLevel(name, done, done?.let { Calisthenics.next(it, history) } ?: chain.first())
        }
        PlanningUi(
            state = s,
            fatigue = Fatigue.assess(history, System.currentTimeMillis()),
            deloadWeek = s.mesocycle?.isDeloadWeek(today) == true,
            mesocycleWeek = s.mesocycle?.weekOf(today),
            calisthenics = calis,
            peak = s.meetEpochDay?.let { Peaking.plan(today, LocalDate.ofEpochDay(it)) }.orEmpty(),
            oneRepMaxes = Strength.bestOneRepMax(history).filterKeys { it in Strength.STANDARD_LIFTS },
            routines = routines,
            weekStart = PlanningStore.monday(today),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningUi())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    // --- Generador ---------------------------------------------------------------------------

    /** Material del gimnasio activo (para el generador). */
    val gymEquipment: StateFlow<Set<Equipment>> = settings.settings.map { it.activeGym.equipment }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun generate(days: Int, equipment: Set<Equipment>, goal: Goal, minutes: Int): List<Routine> =
        RoutineGenerator.generate(days, equipment, goal, minutes, context.uiLanguage())
            .map { r -> r.copy(exercises = r.exercises.filter { com.bestrong.core.training.Injuries.isSafe(it.exercise, store.state.value.injuries) }) }

    fun saveGenerated(routines: List<Routine>) = viewModelScope.launch {
        repo.addRoutines(routines)
        _messages.tryEmit(context.getString(R.string.plan_generated_saved, routines.size))
    }

    // --- Calendario --------------------------------------------------------------------------

    fun assign(weekStart: LocalDate, day: DayOfWeek, routineId: String?) = store.update { s ->
        s.copy(weekPlans = s.weekPlans + (weekStart.toEpochDay() to s.planFor(weekStart).assign(day, routineId)))
    }

    fun move(weekStart: LocalDate, from: DayOfWeek, to: DayOfWeek) = store.update { s ->
        s.copy(weekPlans = s.weekPlans + (weekStart.toEpochDay() to s.planFor(weekStart).move(from, to)))
    }

    /** Copia la semana en la siguiente. */
    fun duplicateWeek(weekStart: LocalDate) = store.update { s ->
        s.copy(weekPlans = s.weekPlans + (weekStart.plusWeeks(1).toEpochDay() to s.planFor(weekStart)))
    }.also { _messages.tryEmit(context.getString(R.string.plan_week_duplicated)) }

    fun estimateMinutes(routine: Routine): Int = RoutineTime.estimateMinutes(routine)

    fun setMinutesPerSession(min: Int) = store.update { it.copy(minutesPerSession = min.coerceIn(15, 180)) }

    // --- Mesociclo, pico, lesiones y límite ---------------------------------------------------

    fun startMesocycle(weeks: Int) = store.update { it.copy(mesocycle = Mesocycle(PlanningStore.monday(LocalDate.now()).toEpochDay(), weeks)) }
    fun stopMesocycle() = store.update { it.copy(mesocycle = null) }

    fun setMeetDate(date: LocalDate?) = store.update { it.copy(meetEpochDay = date?.toEpochDay()) }

    fun toggleInjury(zone: InjuryZone) = store.update { s -> s.copy(injuries = if (zone in s.injuries) s.injuries - zone else s.injuries + zone) }

    fun setWeeklyCap(cap: Double?) = store.update { it.copy(weeklyCap = cap) }

    // --- Programas propios -------------------------------------------------------------------

    fun saveProgram(p: PeriodizedProgram) {
        store.saveProgram(p)
        _messages.tryEmit(context.getString(R.string.plan_program_saved, p.nameEs))
    }

    fun deleteProgram(id: String) = store.deleteProgram(id)

    fun exerciseName(id: String): String = ExerciseLibrary.byId(id)?.let { com.bestrong.core.library.ExerciseLibrary.displayName(it, context.uiLanguage()) } ?: id

    fun previousBest(id: String): Double? = WorkoutStats.personalRecords(repo.history.value)[id]?.get(com.bestrong.core.stats.RecordType.BEST_ONE_REP_MAX)?.value
}
