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
import com.bestrong.mobile.ui.components.appLanguage
import com.bestrong.mobile.ui.components.defaultWorkoutName
import com.bestrong.mobile.ui.components.programTexts
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val savedState: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    /** Descanso en marcha (se ve en la tarjeta del entreno en curso sin tener que abrirlo). */
    val rest: StateFlow<com.bestrong.mobile.ui.components.RestState?> = actions.rest

    /** Preparación del día (sueño y pulso en reposo de Health Connect + última valoración). */
    private val _readiness = MutableStateFlow<ReadinessReport?>(null)
    val readiness: StateFlow<ReadinessReport?> = _readiness
    val healthConnected = MutableStateFlow(false)

    fun refreshReadiness() = viewModelScope.launch {
        val snap = runCatching { healthReader.read() }.getOrNull()
        healthConnected.value = snap != null && (snap.sleepHours != null || snap.restingHr != null)
        // Solo una valoración reciente (48 h): una de hace semanas no dice cómo estás hoy
        val cutoff = System.currentTimeMillis() - READINESS_RATING_MS
        val lastRating = repo.history.value.firstOrNull { it.rating != null && it.startedAtEpochMs >= cutoff }?.rating
        // Sin ningún dato (ni sueño, ni pulso en reposo, ni valoración) no hay nota: antes salía un «70 · Normal» inventado
        val anyData = snap?.sleepHours != null || snap?.restingHr != null || lastRating != null
        _readiness.value = if (anyData) Readiness.score(snap?.sleepHours, snap?.restingHr, snap?.baselineRestingHr, lastRating) else null
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

    /** Cada hora: lo que depende de la fecha (la semana del cardio) cambia al pasar de domingo a lunes sin otro aviso. */
    private val hourTick = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(HOUR_MS)
        }
    }

    /**
     * Cambia al pasar la medianoche (mirado cada hora): con Inicio a la vista toda la noche (un plegable en su soporte),
     * la sesión, la sugerencia y la semana no se quedan en las de ayer.
     */
    private val dayTick = hourTick.map { LocalDate.now() }.distinctUntilChanged()

    /** Sesión que toca hoy del programa activo (con las cargas ya calculadas). */
    val today: StateFlow<ProgramSession?> = combine(repo.activeProgram, repo.history, planning.state, dayTick, settingsStore.settings) { active, history, plan, _, s ->
        // Semana de descarga del mesociclo: la sesión se aligera sola
        active?.let {
            Periodization.sessionWithDeload(it, history, context.appLanguage(), plan.customPrograms, context.programTexts(), plan.mesocycle, context.getString(R.string.deload_label))
        }?.let { session ->
            // Con lo que se puede montar en tu gimnasio (como al empezar): 67,5 kg en libras no es 148,8 sino 150
            session.copy(
                routine = session.routine.copy(
                    exercises = session.routine.exercises.map { re ->
                        val gym = s.gymFor(re.exercise.id)
                        re.copy(sets = re.sets.map { if (it.weightKg > 0) it.copy(weightKg = gym.nearestLoad(it.weightKg, re.exercise.equipment, s.unit)) else it })
                    },
                ),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ajustes (gimnasio activo para el selector de arriba). */
    val settings: StateFlow<com.bestrong.mobile.data.UserSettings> = settingsStore.settings

    fun setActiveGym(id: String) = settingsStore.update { it.copy(activeGymId = id) }

    /** Cardio de esta semana (metros, segundos) y el objetivo de km, para la tarjeta de Inicio. */
    data class CardioWeek(val meters: Double, val seconds: Long, val goalKm: Double?, val goalMin: Int? = null, val streak: Int = 0) {
        /** Se enseña si hay cardio esta semana o un objetivo puesto. */
        val worthShowing: Boolean get() = goalKm != null || goalMin != null || meters > 0 || seconds > 0
    }

    val cardioWeek: StateFlow<CardioWeek> = combine(repo.history, settingsStore.settings, hourTick) { history, s, _ ->
        val zone = ZoneId.systemDefault()
        val monday = LocalDate.now(zone).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val (m, sec) = com.bestrong.core.stats.CardioTotals.since(history, monday.atStartOfDay(zone).toInstant().toEpochMilli())
        // Racha de semanas cumpliendo el objetivo (hasta un año atrás)
        val goalKm = s.weeklyCardioKm
        val goalMin = s.weeklyCardioMin
        val streak = if (goalKm == null && goalMin == null) {
            0
        } else {
            val starts = (0L until 52L).map { monday.minusWeeks(it).atStartOfDay(zone).toInstant().toEpochMilli() }
            com.bestrong.core.stats.CardioTotals.goalStreak(history, starts) { meters, seconds ->
                if (goalMin != null) seconds / 60 >= goalMin else meters / 1000 >= (goalKm ?: 0.0)
            }
        }
        CardioWeek(m, sec, goalKm, goalMin, streak)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardioWeek(0.0, 0, null))

    /** Objetivo de cardio: en km o en minutos (poner uno quita el otro); los dos null = sin objetivo. */
    fun setWeeklyCardioGoal(km: Double?, minutes: Int?) = settingsStore.update { it.copy(weeklyCardioKm = km, weeklyCardioMin = minutes) }

    val cardioNudge: StateFlow<Boolean> = settingsStore.settings.map { it.cardioNudge }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settings.value.cardioNudge)

    fun setCardioNudge(on: Boolean) = settingsStore.update { it.copy(cardioNudge = on) }

    val cardioNudgeDay: StateFlow<Int> = settingsStore.settings.map { it.cardioNudgeDay }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settings.value.cardioNudgeDay)

    fun setCardioNudgeDay(day: Int) = settingsStore.update { it.copy(cardioNudgeDay = day.coerceIn(1, 7)) }

    /** Objetivo escrito a mano («12,5» km o «200» min); devuelve false si el número no vale. */
    fun setCustomCardioGoal(text: String, inMinutes: Boolean): Boolean {
        // Solo números normales: «12,5» o «12.5» km, minutos enteros (nada de «1e3» ni separadores de miles)
        val pattern = if (inMinutes) Regex("\\d{1,5}") else Regex("\\d{1,4}([.,]\\d{1,2})?")
        val value = text.trim().takeIf { pattern.matches(it) }?.replace(',', '.')?.toDoubleOrNull()
        val valid = value != null && (if (inMinutes) value in 5.0..10_000.0 else value in 0.5..1_000.0)
        if (valid) setWeeklyCardioGoal(value.takeUnless { inMinutes }, value?.toInt()?.takeIf { inMinutes })
        return valid
    }

    /** Id → nombre de los gimnasios (para enseñar el de cada rutina; los borrados no salen). */
    val gymNames: StateFlow<Map<String, String>> = settingsStore.settings.map { s -> s.gyms.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.settings.value.gyms.associate { it.id to it.name })

    /** Si no hay programa: la rutina que lleva más tiempo sin hacerse. */
    val suggestedRoutine: StateFlow<Routine?> = combine(repo.routines, repo.history, planning.state, dayTick) { routines, history, plan, _ ->
        val last = history.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        val usable = routines.filter { it.exercises.isNotEmpty() && !it.archived }
        // La planificada para hoy (si aún no se ha hecho hoy); si no, la que lleva más tiempo sin hacerse
        val today = LocalDate.now()
        val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val planned = plan.planFor(today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))).routineFor(today.dayOfWeek)
        usable.firstOrNull { it.id == planned && (last[it.id] ?: 0L) < startOfToday } ?: usable.minByOrNull { last[it.id] ?: 0L }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Rutina → días de esta semana en que está planificada (para enseñarlo en su tarjeta). */
    val plannedDays: StateFlow<Map<String, List<DayOfWeek>>> = combine(planning.state, repo.routines, dayTick) { plan, routines, _ ->
        val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Solo rutinas que siguen existiendo (una borrada o archivada no deja días «planificados» fantasma)
        val alive = routines.filterNot { it.archived }.map { it.id }.toSet()
        plan.planFor(monday).days.entries.filter { it.value in alive }.groupBy({ it.value }, { DayOfWeek.of(it.key) }).mapValues { (_, d) -> d.sorted() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Semana actual: días entrenados frente al objetivo y adherencia de las 8 semanas anteriores. */
    val week: StateFlow<WeekProgress> = combine(
        repo.history,
        repo.activeProgram,
        settingsStore.settings,
        combine(planning.state, repo.routines, ::Pair),
        dayTick,
    ) { history, active, s, (plan, routines), _ ->
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
            streak = com.bestrong.core.stats.WorkoutStats.weeklyStreak(history.filter { it.isFinished }, today, zone),
            // Días con una rutina planificada (Planificar o el programa a medida)
            // (solo rutinas que siguen existiendo: una borrada no deja el día marcado)
            plannedDays = plan.planFor(today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).days
                .filterValues { id -> routines.any { it.id == id && !it.archived } }.keys.map { DayOfWeek.of(it) }.toSet(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekProgress())

    /** Músculos que esta semana van por detrás de lo habitual (de jueves en adelante). */
    val laggingMuscles: StateFlow<List<Pair<com.bestrong.core.model.MuscleGroup, Double>>> = combine(repo.history, settingsStore.settings, dayTick) { history, s, _ ->
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
        // De una vez y bajo el cerrojo (lo marca terminado: un mensaje atrasado del reloj no lo resucita)
        val w = repo.takeActive() ?: return@launch
        actions.closePartner(withGym = false)
        // Sin sello de gimnasio: pudo quedar abierto días y el activo de ahora no tiene por qué ser aquel
        // Como al terminarlo en el entreno: calorías, lo del reloj que llegara y Health Connect
        val finished = actions.withEnergy(WorkoutEditor.finishAtLastActivity(w))
        val saved = finished.exercises.isNotEmpty()
        if (saved) {
            repo.saveWorkout(finished)
            actions.applyPendingWatchHealth(finished.id)
            actions.exportToHealthConnectIfEnabled(finished)
        }
        // Sin series hechas no queda nada que guardar: no se dice «guardado»
        message(context.getString(if (saved) R.string.msg_forgotten_saved else R.string.msg_forgotten_empty))
    }

    fun discardActive() = viewModelScope.launch {
        // Con el descanso tal como iba: «Deshacer» lo recupera (como al descartar desde el entreno)
        val r = actions.rest.value
        repo.discardActive(r?.endsAtEpochMs, r?.totalSeconds ?: 0)
        actions.closePartnerOnDiscard()
    }

    /** Entreno recién descartado (para el aviso con «Deshacer»). */
    val discarded: StateFlow<com.bestrong.core.model.Workout?> = repo.discarded

    fun restoreDiscarded(onRestored: () -> Unit) = viewModelScope.launch { if (actions.restoreDiscarded()) onRestored() }

    fun forgetDiscarded() = repo.forgetDiscarded()

    /** Peso de hoy apuntado desde Inicio (va a Medidas y, si se exporta, a Health Connect). */
    fun logBodyWeight(kg: Double) = viewModelScope.launch {
        // En la medida de hoy si ya hay una (p. ej. solo la cintura), no en otra
        actions.saveWeighIn(kg)
    }

    fun discardRecent(): Boolean = repo.recentlyDiscarded

    /** Training max sugerido (90 % del mejor 1RM estimado) para preparar un programa. */
    fun suggestedTrainingMax(exerciseId: String): Double? = Periodization.suggestedTrainingMax(exerciseId, repo.history.value)

    fun activateProgram(program: PeriodizedProgram, trainingMaxKg: Map<String, Double>) = viewModelScope.launch {
        repo.setActiveProgram(ActiveProgram(program.id, System.currentTimeMillis(), trainingMaxKg, weeklyGoal = program.daysPerWeek))
        message(context.getString(R.string.msg_program_started, program.name(context.appLanguage())))
    }

    fun stopProgram() = viewModelScope.launch { repo.setActiveProgram(null) }

    /** Sin rutinas activas (mirado en la base de datos: la lista en memoria puede no haber llegado). */
    suspend fun hasNoRoutines(): Boolean = repo.routinesOnce().none { !it.archived }

    /** Ya se abrió (o no hacía falta) «Crea tu programa» tras la bienvenida. */
    fun clearPendingBuilder() = settingsStore.update { it.copy(pendingBuilder = false) }

    fun setWeeklyGoal(goal: Int) = settingsStore.update { it.copy(weeklyGoal = goal.coerceIn(1, 7)) }

    /** Empieza la sesión del programa con las cargas prescritas (sin sustituirlas por la última vez). */
    fun startProgramSession(session: ProgramSession, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() != null) {
            alreadyActive()
            onStarted()
            return@launch
        }
        // Las cargas del programa salen de porcentajes (en kg): se llevan a un peso montable con tus discos y tu
        // unidad (en libras, 67,5 kg = 148,8 lb no se puede cargar)
        val prefs = settingsStore.settings.value
        val workout = session.routine.toWorkout(System.currentTimeMillis()).let { w ->
            w.copy(
                exercises = w.exercises.map { we ->
                    val gym = prefs.gymFor(we.exercise.id)
                    we.copy(sets = we.sets.map { set -> if (set.weightKg > 0) set.copy(weightKg = gym.nearestLoad(set.weightKg, we.exercise.equipment, prefs.unit)) else set })
                },
            )
        }
        repo.startActive(workout)
        onStarted()
    }

    val routines: StateFlow<List<Routine>> = repo.routines
    val active: StateFlow<Workout?> = repo.activeWorkout
    val history: StateFlow<List<Workout>> = repo.history

    /** El último entreno de cada rutina (una pasada por el historial, no una por tarjeta). */
    val lastByRoutine: StateFlow<Map<String, Workout>> = repo.history
        .map { h -> buildMap { h.forEach { w -> w.routineId?.let { if (it !in this) put(it, w) } } } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Con buzón: un aviso que llega con Inicio fuera de pantalla (enlace, widget) se muestra al volver, pero
    // solo si es reciente (uno de hace horas, como «ya hay un entreno en curso», confundiría)
    private val _messages = kotlinx.coroutines.channels.Channel<Pair<String, Long>>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val messages: kotlinx.coroutines.flow.Flow<String> = _messages.receiveAsFlow()
        .filter { (_, at) -> System.currentTimeMillis() - at < MESSAGE_TTL_MS }
        .map { it.first }

    private fun message(text: String) = _messages.trySend(text to System.currentTimeMillis())

    /** Aviso que sobrevive a abrir el entreno en curso (el snackbar de Inicio se cancela al salir de la pantalla). */
    private fun alreadyActive() = android.widget.Toast.makeText(context, R.string.msg_workout_already_active, android.widget.Toast.LENGTH_LONG).show()

    fun startEmpty(onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() == null) repo.startActive(WorkoutEditor.empty(System.currentTimeMillis(), name = defaultWorkoutName(context, System.currentTimeMillis()))) else alreadyActive()
        onStarted()
    }

    /** Repite el último entreno (mismos ejercicios y series, con los pesos que tocan hoy). */
    fun repeatLast(onStarted: () -> Unit) = viewModelScope.launch {
        val last = repo.history.value.firstOrNull { it.isFinished } ?: return@launch
        if (repo.activeOnce() == null) {
            val template = last.routineId?.let { repo.routine(it) }
            actions.useRoutineGym(template, last.gymName)
            val fromProgram = last.routineId?.startsWith(Periodization.ROUTINE_PREFIX) == true
            // Notas: las fijas de la rutina; en un programa (rutina generada) se conservan las de la sesión
            // Programa: su estructura y las notas de esa entrada (las genera el programa); rutina: la suya
            // Rellenar recorre el historial varias veces por ejercicio: fuera del hilo principal (si no, el toque se traba)
            val again = withContext(Dispatchers.Default) {
                Routine.repeatOf(last, keepAllSets = template != null || fromProgram) { we ->
                    if (fromProgram) we.notes else template?.exercises?.firstOrNull { it.exercise.id == we.exercise.id }?.notes.orEmpty()
                }
                    .toWorkout(System.currentTimeMillis(), actions.prefillLookup())
            }
            // Una sesión de programa repetida no cuenta como el siguiente día del programa
            repo.startActive(again.copy(routineId = last.routineId?.takeUnless { it.startsWith(Periodization.ROUTINE_PREFIX) }))
        }
        onStarted()
    }

    fun startRoutine(routine: Routine, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeOnce() != null) {
            alreadyActive()
            onStarted()
            return@launch
        }
        // Su gimnasio pasa a ser el activo antes de rellenar: los pesos salen con su material
        actions.useRoutineGym(routine)
        val history = repo.history.value
        repo.startActive(
            withContext(Dispatchers.Default) {
                // El 2.º bloque del mismo ejercicio también con lo hecho en este gimnasio (como el 1.º)
                val gym = actions.currentGymName()
                routine.toWorkout(
                    System.currentTimeMillis(),
                    actions.prefillLookup(),
                    previousBlock = { id, n -> com.bestrong.core.stats.WorkoutStats.previousBlock(id, n, com.bestrong.core.stats.WorkoutStats.atGym(history, gym, id)) },
                )
            },
        )
        onStarted()
    }

    /** Rutina recibida por enlace o QR, pendiente de que el usuario confirme la importación. */
    // Se guarda el enlace (no la rutina): si Android cierra la app con el diálogo abierto, al volver sigue ahí
    val pendingImport: StateFlow<Routine?> = savedState.getStateFlow<String?>(KEY_PENDING_LINK, null)
        .map { link -> link?.let(RoutineLink::decode) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Un enlace puede llegar de cualquier web o app: se pregunta antes de guardarla. */
    fun importRoutineLink(link: String) {
        // Si no se puede leer (cortado o de una versión más nueva), se dice: antes la app se abría sin más
        val routine = RoutineLink.decode(link)
        if (routine == null) message(context.getString(R.string.routine_link_invalid)) else savedState[KEY_PENDING_LINK] = link
    }

    /** Confirmada: se añade a «Mis rutinas». */
    fun confirmImport() = viewModelScope.launch {
        val routine = pendingImport.value ?: return@launch
        savedState[KEY_PENDING_LINK] = null
        // El mismo enlace o QR otra vez (mismo nombre, ejercicios y series) no duplica la rutina
        // Toda la rutina (descansos, tempo, RPE, notas…): una versión cambiada del mismo enlace sí se importa
        fun key(r: Routine) = r.copy(id = "", position = 0, archived = false, gymId = null, folder = null)
        if (repo.routinesOnce().any { !it.archived && key(it) == key(routine) }) {
            message(context.getString(R.string.routine_already_imported, routine.name))
            return@launch
        }
        // Sus ejercicios propios (o que esta versión no conoce) pasan a tu biblioteca: si no, no salen en el
        // selector, no se pueden editar ni tienen sugerencia de progresión
        routine.exercises.map { it.exercise }.distinctBy { it.id }.filter { repo.exercise(it.id) == null }
            .forEach { repo.saveCustomExercise(it.copy(isCustom = true)) }
        repo.saveRoutine(routine)
        message(context.getString(R.string.routine_imported, routine.name))
    }

    fun dismissImport() {
        savedState[KEY_PENDING_LINK] = null
    }

    /** Desde el widget: empezar una rutina por id. */
    fun startRoutineById(id: String, onStarted: () -> Unit) = viewModelScope.launch {
        val routine = repo.routine(id)
        if (routine == null) {
            message(context.getString(R.string.msg_routine_missing))
            return@launch
        }
        startRoutine(routine, onStarted)
    }

    fun startOnWatch(routine: Routine?) = viewModelScope.launch {
        watchPush.pushToWatchNow()
        val ok = watchSync.startOnWatch(routine?.id)
        message(context.getString(if (ok) R.string.msg_opening_on_watch else R.string.msg_no_watch))
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
    val yearAgo: StateFlow<List<com.bestrong.core.stats.YearAgoComparison>> = combine(repo.history, dayTick) { history, _ ->
        val zone = ZoneId.systemDefault()
        com.bestrong.core.stats.YearAgo.compare(history, LocalDate.now(zone), zone).take(3)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sesión planificada saltada: el día, la rutina y si se puede pasar a mañana (de esta semana y libre). */
    data class MissedSession(val day: java.time.DayOfWeek, val routine: Routine, val canMoveToTomorrow: Boolean)

    /** La última sesión planificada de esta semana que se pasó sin entrenar ni hacerse después, si la hay. */
    val missedSession: StateFlow<MissedSession?> = combine(planning.state, repo.history, repo.routines, dayTick) { plan, history, routines, _ ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val trained = com.bestrong.core.stats.WorkoutStats.trainingDays(history, zone)
        val doneOn = history.filter { it.isFinished && it.routineId != null }
            .groupBy({ it.routineId!! }, { java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() })
        val week = plan.planFor(weekStart)
        val tomorrow = today.plusDays(1)
        // Mañana libre también si lo que tenía era una rutina ya borrada (el calendario lo enseña como descanso)
        val canMove = tomorrow.dayOfWeek != java.time.DayOfWeek.MONDAY && week.routineFor(tomorrow.dayOfWeek)?.let { id -> routines.any { it.id == id } } != true
        // Hecha en cualquier día de esta semana (también antes de su día): no se salta lo que se adelantó
        week.missedSessions(weekStart, today, trained) { id, _ -> doneOn[id].orEmpty().any { !it.isBefore(weekStart) } }
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
            val taken = plan.routineFor(tomorrow.dayOfWeek)?.let { id -> repo.routines.value.any { it.id == id } } == true
            if (taken) s else s.copy(weekPlans = s.weekPlans + (weekStart.toEpochDay() to plan.move(day, tomorrow.dayOfWeek)))
        }
    }

    /** Archivar (o recuperar) sin borrar: el historial y la rutina se conservan. */
    fun setArchived(routine: Routine, archived: Boolean) = viewModelScope.launch {
        // Sobre la guardada: un cambio llegado del reloj mientras tanto no se pierde
        repo.saveRoutine((repo.routine(routine.id) ?: routine).copy(archived = archived), keepPosition = true)
    }

    /** Un cambio de orden a la vez, sobre lo guardado: dos toques rápidos no se pisan. */
    private val moveMutex = kotlinx.coroutines.sync.Mutex()

    fun duplicate(routine: Routine) = viewModelScope.launch {
        repo.saveRoutine(routine.copy(id = com.bestrong.core.util.newId(), name = context.getString(R.string.routine_copy_name, routine.name), archived = false))
    }

    val programs: List<TrainingProgram> = Programs.all

    /** Programa a medida (sin guardar): para enseñarlo antes de crearlo. Sin los ejercicios que no van con tus lesiones. */
    fun buildProgram(
        days: Int,
        goal: com.bestrong.core.training.Goal,
        kit: Kit,
        minutes: Int,
        level: com.bestrong.core.training.Level = com.bestrong.core.training.Level.INTERMEDIATE,
    ): com.bestrong.core.training.GeneratedProgram {
        val injuries = planning.state.value.injuries
        val res = context.resources
        val goalLabel = context.getString(GOAL_LABELS.getValue(goal))
        val texts = com.bestrong.core.training.ProgramGenerator.Texts(
            name = context.getString(R.string.program_generated_name, goalLabel, res.getQuantityString(R.plurals.days_count, days, days)),
            describe = { split, m, a -> context.getString(R.string.program_generated_desc, split, m.sets, m.reps, m.restSeconds, a.sets, a.reps, a.restSeconds) },
            dayLabels = mapOf(
                "full" to context.getString(R.string.day_full),
                "upper" to context.getString(R.string.day_upper),
                "lower" to context.getString(R.string.day_lower),
                "push" to context.getString(R.string.day_push),
                "pull" to context.getString(R.string.day_pull),
                "legs" to context.getString(R.string.day_legs),
            ),
        )
        return com.bestrong.core.training.ProgramGenerator.build(
            days,
            goal,
            kit.equipment,
            minutes,
            context.appLanguage(),
            // Sin gimnasio no hay barra de dominadas ni paralelas (son «peso corporal», pero hace falta dónde colgarse)
            allowed = { com.bestrong.core.training.Injuries.isSafe(it, injuries) && (kit == Kit.GYM || it.id !in NEEDS_BAR) },
            texts = texts,
            level = level,
        )
    }

    /** Guarda las rutinas del programa a medida (en su carpeta). */
    fun addGenerated(
        program: com.bestrong.core.training.GeneratedProgram,
        goal: com.bestrong.core.training.Goal,
        /** Días de la semana elegidos (1 = lunes): las rutinas se ponen en el calendario de las próximas semanas. */
        weekdays: List<Int> = emptyList(),
    ) = viewModelScope.launch {
        // Si ya hay una carpeta con ese nombre (el mismo programa otra vez), la nueva lleva « 2», « 3»…
        val folders = routines.value.mapNotNull { it.folder }.toSet()
        val folder = generateSequence(2) { it + 1 }.map { "${program.name} $it" }.let { more -> if (program.name !in folders) program.name else more.first { it !in folders } }
        repo.addRoutines(program.routines.map { it.copy(folder = folder) })
        val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        // Los días que ocupaban rutinas ya borradas o archivadas quedan libres para el programa nuevo
        val alive = repo.routinesOnce().filterNot { it.archived }.map { it.id }.toSet()
        planning.update { st ->
            st.copy(weekPlans = com.bestrong.core.training.ProgramGenerator.schedule(st.weekPlans, program.routines.map { it.id }, weekdays, monday, from = LocalDate.now(), exists = { it in alive }))
        }
        // El objetivo de la semana pasa a ser los días del programa, y se recuerda el objetivo elegido
        settingsStore.update { it.copy(weeklyGoal = (weekdays.size.takeIf { n -> n > 0 } ?: program.routines.size).coerceIn(1, 7), trainingGoal = goal) }
        message(context.getString(R.string.msg_program_added, folder, program.routines.size))
    }

    /** Crea las rutinas del programa elegido en el idioma de la app. */
    fun addProgram(program: TrainingProgram) = viewModelScope.launch {
        // Como el programa a medida: en su carpeta (« 2», « 3» si ya está) y con su objetivo de días a la semana
        val name = program.name(context.appLanguage())
        val folders = routines.value.mapNotNull { it.folder }.toSet()
        val folder = if (name !in folders) name else generateSequence(2) { it + 1 }.map { "$name $it" }.first { it !in folders }
        val created = program.toRoutines(context.appLanguage()).map { it.copy(folder = folder) }
        repo.addRoutines(created)
        settingsStore.update { it.copy(weeklyGoal = program.daysPerWeek.coerceIn(1, 7)) }
        message(context.getString(R.string.msg_program_added, folder, created.size))
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

    fun move(routine: Routine, delta: Int) = viewModelScope.launch { moveMutex.withLock { moveNow(routine, delta) } }

    private suspend fun moveNow(routine: Routine, delta: Int) {
        val list = repo.routinesOnce().toMutableList()
        val i = list.indexOfFirst { it.id == routine.id }

        // Se mueve dentro de su carpeta (en Inicio cada carpeta se ve aparte): el vecino es el de la misma carpeta
        fun folderOf(r: Routine) = r.folder?.trim()?.takeIf(String::isNotEmpty)
        // Las archivadas van en su propia sección: no se intercambia con una que no se ve
        // (las archivadas se ven todas juntas, sin carpetas)
        val sameFolder = list.indices.filter { list[it].archived == routine.archived && (routine.archived || folderOf(list[it]) == folderOf(routine)) }
        val j = sameFolder.getOrNull(sameFolder.indexOf(i) + delta) ?: return
        if (i < 0) return
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
    /** Semanas seguidas entrenando. */
    val streak: Int = 0,
    /** Días de esta semana con algo planificado. */
    val plannedDays: Set<DayOfWeek> = emptySet(),
)

/** Cada cuánto se revisa lo que depende de la fecha. */
private const val HOUR_MS = 60 * 60 * 1000L

/** Días sin hacer una rutina a partir de los cuales se propone archivarla. */
private const val STALE_DAYS = 60

/** Nombre de cada objetivo. */
val GOAL_LABELS = mapOf(
    com.bestrong.core.training.Goal.HYPERTROPHY to R.string.goal_muscle,
    com.bestrong.core.training.Goal.STRENGTH to R.string.goal_strength,
    com.bestrong.core.training.Goal.FAT_LOSS to R.string.goal_fat_loss,
    com.bestrong.core.training.Goal.ENDURANCE to R.string.goal_endurance,
    com.bestrong.core.training.Goal.GENERAL to R.string.goal_general,
)

/** Material con el que se entrena: decide qué ejercicios entran en el programa a medida. */
enum class Kit(val equipment: Set<com.bestrong.core.model.Equipment>) {
    GYM(com.bestrong.core.model.Equipment.entries.toSet()),

    // Solo mancuernas (y el cuerpo): una kettlebell no se da por hecha
    DUMBBELLS(setOf(com.bestrong.core.model.Equipment.DUMBBELL, com.bestrong.core.model.Equipment.BODYWEIGHT)),
    BODYWEIGHT(setOf(com.bestrong.core.model.Equipment.BODYWEIGHT)),
}

private const val MESSAGE_TTL_MS = 15_000L

/** Una valoración de sesión cuenta para la disposición de hoy solo si es de las últimas 48 h. */
private const val READINESS_RATING_MS = 48 * 60 * 60 * 1000L

private const val KEY_PENDING_LINK = "pending_routine_link"

/** Ejercicios de peso corporal que necesitan una barra o paralelas (no se proponen sin gimnasio). */
private val NEEDS_BAR = setOf("pull_up", "chin_up", "dips", "hanging_leg_raise", "negative_pull_up", "scapular_pull_up")
