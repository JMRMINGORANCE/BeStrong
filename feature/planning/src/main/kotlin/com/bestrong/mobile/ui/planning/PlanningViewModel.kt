package com.bestrong.mobile.ui.planning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.io.CalendarEvent
import com.bestrong.core.io.Ics
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
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.PlanningState
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.appLanguage
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
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /** Ejercicios ocultos en la biblioteca: el selector de programas tampoco los propone. */
    val hiddenExercises: Set<String> get() = settings.settings.value.hiddenExercises

    /** Hora de entrenar habitual (la de los recordatorios): la que se usa al añadir al calendario o exportar. */
    val usualTime: java.time.LocalTime get() = settings.settings.value.let { java.time.LocalTime.of(it.reminderHour, it.reminderMinute) }

    /** Fecha de hoy mirada cada hora: con Planificar abierto pasada la medianoche, la semana y el mesociclo avanzan. */
    private val dayTick = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(LocalDate.now(ZoneId.systemDefault()))
            kotlinx.coroutines.delay(java.util.concurrent.TimeUnit.HOURS.toMillis(1))
        }
    }.distinctUntilChanged()

    val ui: StateFlow<PlanningUi> = combine(store.state, repo.history, repo.routines, dayTick) { s, history, routines, today ->
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
        // Se filtra antes de emparejar superseries: así ninguna se queda con un solo ejercicio
        RoutineGenerator.generate(
            days, equipment, goal, minutes, context.appLanguage(),
            // Ni lesiones ni ejercicios ocultos en la biblioteca
            allowed = { it.id !in hiddenExercises && com.bestrong.core.training.Injuries.isSafe(it, store.state.value.injuries) },
            // Nombres de los días como en el creador de programas (traducidos a todos los idiomas)
            dayLabels = mapOf(
                "full" to context.getString(R.string.day_full),
                "upper" to context.getString(R.string.day_upper),
                "lower" to context.getString(R.string.day_lower),
                "push" to context.getString(R.string.day_push),
                "pull" to context.getString(R.string.day_pull),
                "legs" to context.getString(R.string.day_legs),
            ),
        )

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

    /**
     * La semana de [weekStart] copiada en las [weeks] siguientes (1 = la próxima; 4 = un mes de mesociclo).
     * Las que ya tenían su propio plan se dejan como están (copiar 8 semanas no pisa lo ya organizado).
     */
    fun duplicateWeek(weekStart: LocalDate, weeks: Int = 1) {
        val n = weeks.coerceIn(1, MAX_DUPLICATE_WEEKS)
        val targets = (1..n).map { weekStart.plusWeeks(it.toLong()).toEpochDay() }
        // Solo rutinas que siguen existiendo: una semana con rutinas borradas (todo «Descanso») está libre
        // (ni archivadas: el resto de la app tampoco las cuenta como planificadas)
        val alive = repo.routines.value.filterNot { it.archived }.map { it.id }.toSet()
        fun planned(day: Long) = store.state.value.weekPlans[day]?.days?.values?.any { it in alive } == true
        val kept = targets.count(::planned)
        store.update { s ->
            val plan = s.planFor(weekStart).let { p -> p.copy(days = p.days.filterValues { it in alive }) }
            s.copy(weekPlans = s.weekPlans + targets.filterNot(::planned).associateWith { plan })
        }
        val copied = context.resources.getQuantityString(R.plurals.plan_week_duplicated_n, n - kept, n - kept)
        _messages.tryEmit(if (kept > 0) copied + ". " + context.resources.getQuantityString(R.plurals.plan_week_kept_n, kept, kept) else copied)
    }

    fun estimateMinutes(routine: Routine): Int = RoutineTime.estimateMinutes(routine)

    /** Exporta la semana planificada como .ics y la abre con la app de calendario (sin permisos). */
    fun exportWeekToCalendar(weekStart: LocalDate) {
        val plan = store.state.value.planFor(weekStart)
        val routines = ui.value.routines
        val events = DayOfWeek.entries.mapNotNull { d ->
            val r = plan.routineFor(d)?.let { id -> routines.firstOrNull { it.id == id } } ?: return@mapNotNull null
            CalendarEvent(weekStart.plusDays((d.value - 1).toLong()), "BeStrong · ${r.name}", RoutineTime.estimateMinutes(r), r.exercises.joinToString { com.bestrong.core.library.ExerciseLibrary.displayName(it.exercise, context.uiLanguage()) })
        }
        if (events.isEmpty()) return
        runCatching {
            val text = Ics.generate(events, usualTime, ZoneId.systemDefault())
            val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
            val file = java.io.File(dir, "bestrong-week.ics").apply { writeText(text) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(android.content.Intent.createChooser(view, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

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
        // Si es el que se sigue y ahora tiene un básico sin training max, se le pone el sugerido (si no, 0 kg en cada serie)
        viewModelScope.launch {
            val a = repo.activeProgramOnce()?.takeIf { it.programId == p.id } ?: return@launch
            val added = p.mainLifts.filter { it !in a.trainingMaxKg }
                .mapNotNull { id -> com.bestrong.core.training.Periodization.suggestedTrainingMax(id, repo.historyOnce())?.let { id to it } }
            if (added.isNotEmpty()) repo.setActiveProgram(a.copy(trainingMaxKg = a.trainingMaxKg + added))
        }
    }

    /** Borra un programa propio; si era el activo, se deja de seguir (si no, quedaba activo sin sesiones ni forma de pararlo). */
    fun deleteProgram(id: String) {
        store.deleteProgram(id)
        viewModelScope.launch { if (repo.activeProgramOnce()?.programId == id) repo.setActiveProgram(null) }
    }

    fun exerciseName(id: String): String = ExerciseLibrary.byId(id)?.let { com.bestrong.core.library.ExerciseLibrary.displayName(it, context.uiLanguage()) } ?: id

    fun previousBest(id: String): Double? = WorkoutStats.personalRecords(repo.history.value)[id]?.get(com.bestrong.core.stats.RecordType.BEST_ONE_REP_MAX)?.value
}

/** Hasta cuántas semanas seguidas se copia una (un mesociclo largo). */
private const val MAX_DUPLICATE_WEEKS = 8
