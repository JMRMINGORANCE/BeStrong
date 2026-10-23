package com.bestrong.mobile.ui.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.library.Alternatives
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.SessionRating
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.ExerciseGoals
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.Autoregulation
import com.bestrong.core.training.NextTimeSuggestion
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.Progression
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.training.RestPolicy
import com.bestrong.core.training.RpeAdjustment
import com.bestrong.core.training.Tempo
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.WatchMetricsStore
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.ExerciseMediaStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.health.EnergyCalculator
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.RestState
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.workout.Announcer
import com.bestrong.mobile.workout.RestTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FinishedSummary(
    val workout: Workout,
    val records: List<PersonalRecord>,
    val comparisons: List<WorkoutStats.ExerciseComparison> = emptyList(),
    val imbalances: List<Pair<String, Double>> = emptyList(),
    val nextTime: List<NextTimeSuggestion> = emptyList(),
    /** Rutina de la que salió el entreno, si este cambió su estructura (para ofrecer actualizarla). */
    val routineToUpdate: Routine? = null,
    /** El entreno tal y como estaba al terminar, con las series sin hacer: lo que se pasa a la rutina. */
    val planned: Workout? = null,
    /** Objetivo semanal (días) que se acaba de cumplir con este entreno; null si no. */
    val weeklyGoalReached: Int? = null,
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val settingsStore: SettingsStore,
    private val restTimer: RestTimerController,
    private val metricsStore: WatchMetricsStore,
    private val energy: EnergyCalculator,
    private val actions: WorkoutActions,
    private val announcer: Announcer,
    @ApplicationContext private val appContext: Context,
    private val savedState: SavedStateHandle,
    private val media: ExerciseMediaStore,
) : ViewModel() {

    /** Modo pareja: true = se está viendo y editando el entreno de la otra persona (sobrevive a la muerte del proceso). */
    // (y lo sabe WorkoutActions: las teclas de volumen marcan la serie de quien se ve)
    // Solo mientras exista el de la pareja: cerrado fuera de esta pantalla, los cambios van a tu entreno, no a ninguno
    val partnerMode: StateFlow<Boolean> = combine(savedState.getStateFlow(KEY_PARTNER_MODE, false), repo.partnerWorkout) { on, p -> on && p != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Las teclas de volumen marcan la serie de la pareja solo con su entreno a la vista (no desde Inicio). */
    fun onScreen(visible: Boolean) = actions.partnerOnScreen.update { visible && (savedState[KEY_PARTNER_MODE] ?: false) }

    private fun setPartnerMode(on: Boolean) = savedState.set(KEY_PARTNER_MODE, on.also { actions.partnerOnScreen.value = it })

    val workout: StateFlow<Workout?> = combine(repo.activeWorkout, repo.partnerWorkout, partnerMode) { me, partner, isPartner ->
        // Sin entreno de la pareja (se cerró desde Inicio, el reloj o al terminar el tuyo): el tuyo, no «sin entreno»
        if (isPartner && partner != null) partner else me
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repo.activeWorkout.value)

    /** Cambia entre tu entreno y el de tu pareja; la primera vez copia tus ejercicios con las series sin hacer. */
    fun switchAthlete(partner: Boolean) = viewModelScope.launch {
        if (partner && repo.partnerWorkout.value == null) {
            val name = settings.value.partnerName.ifBlank { return@launch }
            val mine = repo.activeWorkout.value
            val now = System.currentTimeMillis()
            // Sus pesos de la última vez (o su sugerencia); si nunca hizo el ejercicio, los tuyos sin hacer
            val theirs = actions.prefillLookup(athlete = name)
            val copy = (mine ?: WorkoutEditor.empty(now)).let { w ->
                w.copy(
                    id = com.bestrong.core.util.newId(),
                    name = "$name · ${w.name}",
                    startedAtEpochMs = now,
                    updatedAtEpochMs = now,
                    athlete = name,
                    // Lo suyo empieza de cero: ni tus pausas, ni tu pulso, ni tu valoración o notas
                    pausedAtEpochMs = null,
                    pausedTotalMs = 0,
                    heartRateSamples = emptyList(),
                    avgHeartRate = null,
                    maxHeartRate = null,
                    calories = null,
                    rating = null,
                    notes = "",
                    deletedIds = emptyMap(),
                    exercises = w.exercises.map { we ->
                        val sets = theirs(we.exercise.id).filter { it.type != com.bestrong.core.model.SetType.WARMUP }
                            .map { it.asPending() }
                            .ifEmpty { we.sets.map { it.asPending() } }
                        we.copy(id = com.bestrong.core.util.newId(), sets = sets)
                    },
                )
            }
            repo.setPartnerActive(copy)
            // La hora límite ya puesta también vale para el entreno del compañero recién creado
            if (savedState.get<Long>(KEY_DEADLINE) != null) {
                savedState[KEY_DEADLINE_WORKOUT] = listOfNotNull(savedState.get<String>(KEY_DEADLINE_WORKOUT)?.takeIf { it.isNotBlank() }, copy.id).joinToString(",")
            }
        }
        setPartnerMode(partner)
    }

    /** Historial de quien entrena ahora: el tuyo o, en modo pareja, el suyo (con el nombre de su entreno en curso). */
    private val athleteHistory: StateFlow<List<Workout>> = combine(repo.history, repo.partnerHistory, partnerMode, repo.partnerWorkout, settingsStore.settings) { me, partner, isPartner, pw, _ ->
        historyOf(isPartner, me, partner, pw)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        // Ya el primer valor de quien entrena (tras reiniciar en modo pareja, el suyo)
        historyOf(partnerMode.value, repo.history.value, repo.partnerHistory.value, repo.partnerWorkout.value),
    )

    private fun historyOf(isPartner: Boolean, me: List<Workout>, partner: List<Workout>, pw: Workout?): List<Workout> =
        if (isPartner) partner.filter { it.athlete == (pw?.athlete ?: settingsStore.settings.value.partnerAthlete) } else me

    /** «La vez anterior» de cada ejercicio: en este gimnasio si allí ya se hizo (como el relleno), si no la última. */
    val previousSets: StateFlow<(String) -> List<WorkoutSet>> = combine(
        repo.previousSetsLookup,
        athleteHistory,
        partnerMode,
        settingsStore.settings.map { s -> s.activeGym.name.takeIf { s.gyms.size > 1 } }.distinctUntilChanged(),
    ) { mine, history, isPartner, gym ->
        // En modo pareja, la suya (tu búsqueda ya preparada solo vale para ti)
        val all: (String) -> List<WorkoutSet> = if (isPartner) WorkoutStats.lastSetsByExercise(history).let { last -> { id: String -> last[id].orEmpty() } } else mine
        if (gym == null) {
            all
        } else {
            val there = WorkoutStats.lastSetsByExercise(history.filter { it.gymName == gym })
            val lookup: (String) -> List<WorkoutSet> = { id -> there[id] ?: all(id) }
            lookup
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, repo.previousSetsLookup.value)
    val settings: StateFlow<UserSettings> = settingsStore.settings
    val rest: StateFlow<RestState?> = restTimer.rest
    val restEndedAt: StateFlow<Long?> = restTimer.restEndedAt
    fun dismissOverrun() = restTimer.clearOverrun()

    /**
     * Hora a la que hay que acabar (la pone el usuario) del entreno en pantalla; sobrevive a que Android
     * cierre la app. Va unida a ese entreno: al terminarlo por cualquier vía (Inicio, reloj…) deja de valer.
     */
    val deadline: StateFlow<Long?> = combine(
        savedState.getStateFlow<Long?>(KEY_DEADLINE, null),
        savedState.getStateFlow<String?>(KEY_DEADLINE_WORKOUT, null),
        workout,
    ) { at, ids, w -> at.takeIf { w != null && ids?.split(',')?.contains(w.id) == true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Quita series pendientes del final hasta que la estimación quepa en la hora límite (se puede deshacer). */
    fun fitToDeadline() {
        val limit = deadline.value ?: return
        val partner = partnerMode.value
        val restEnds = restTimer.rest.value?.endsAtEpochMs
        val warmup = settings.value.warmupRestSeconds
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val budget = ((limit - now) / 1000).toInt().coerceAtLeast(0)
            val (before, after) = update(partner) { w ->
                WorkoutEditor.trimToFit(w, budget) { WorkoutEditor.estimatedRemainingSeconds(it, nowEpochMs = now, warmupRestSeconds = warmup, restEndsAtEpochMs = restEnds) }
            } ?: return@launch
            // Se compara lo que importa: la hora de modificación siempre cambia
            if (before.exercises == after.exercises) return@launch
            _events.tryEmit(WorkoutEvent.Undoable(UndoKind.TRIM, partner, before.id, restore = WorkoutEditor.undoRemoval(before, after)))
        }
    }

    fun setDeadline(epochMs: Long?) {
        // Vale para tu entreno y el del compañero (la hora de irse es la misma)
        savedState[KEY_DEADLINE_WORKOUT] = listOfNotNull(repo.activeWorkout.value?.id, repo.partnerWorkout.value?.id).joinToString(",")
        savedState[KEY_DEADLINE] = epochMs
    }
    val watchMetrics: StateFlow<LiveMetrics?> = metricsStore.metrics

    /** Sugerencia de progresión por ejercicio (id de ejercicio → sugerencia). */
    val suggestions: StateFlow<Map<String, ProgressionSuggestion>> = combine(
        workout.map { w -> w?.exercises?.map { it.exercise }.orEmpty() }.distinctUntilChanged(),
        athleteHistory,
        settings,
    ) { exercises, history, s ->
        if (!s.progressionEnabled) {
            emptyMap()
        } else {
            // Con los mismos ajustes que el relleno (discos, tope semanal, subida que no se pierde), para no contradecirlo
            // Lo hecho en este gimnasio, como el relleno (si allí ya se hizo el ejercicio)
            val gym = s.activeGym.name.takeIf { s.gyms.size > 1 }
            exercises.mapNotNull { ex ->
                val base = WorkoutStats.atGym(history, gym, ex.id)
                Progression.suggest(ex, base, s.stepKgFor(ex.id), repRange = s.repRangeFor(ex.id))
                    ?.let { sug ->
                        val previous = WorkoutStats.previousSets(ex.id, base)
                        ex.id to sug.copy(sets = actions.loadable(ex, sug.sets, sug.kind, previous, history), cappedByWeek = actions.cappedByWeek(ex, sug.sets, history))
                    }
            }.toMap()
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 1RM estimado reciente de los ejercicios del entreno (para el peso por RPE), calculado fuera del hilo principal. */
    val recentOneRepMax: StateFlow<Map<String, Double>> = combine(
        workout.map { w -> w?.exercises?.map { it.exercise.id }.orEmpty().distinct() }.distinctUntilChanged(),
        athleteHistory,
    ) { ids, history ->
        // De quien entrena (en modo pareja, el suyo)
        val now = System.currentTimeMillis()
        ids.mapNotNull { id -> com.bestrong.core.stats.RpeLoad.recentOneRepMax(id, history, now)?.let { id to it } }.toMap()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Nota fija de cada ejercicio (altura del asiento, agarre…): id de ejercicio → nota. */
    val pinnedNotes: StateFlow<Map<String, String>> = combine(
        workout.map { w -> w?.exercises?.map { it.exercise.id }.orEmpty() }.distinctUntilChanged(),
        media.version,
    ) { ids, _ -> ids.distinct().associateWith { media.note(it) }.filterValues { it.isNotBlank() } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Cambia el gimnasio activo: las sugerencias y el peso por RPE se recalculan con su material, y las series
     * pendientes con peso se ajustan a lo que hay en el nuevo (se puede deshacer).
     */
    fun setActiveGym(id: String) {
        val previousGym = settings.value.activeGym.id
        if (previousGym == id) return
        settingsStore.update { it.copy(activeGymId = id) }
        val partner = partnerMode.value
        viewModelScope.launch {
            // Los dos entrenos (el tuyo y el de la pareja) están en el mismo gimnasio: un solo «deshacer» para todo
            val visible = refitActive(partner)
            val hidden = refitActive(!partner)
            if (visible == null && hidden == null) return@launch
            // Sin entreno a la vista (solo el otro), el aviso va sobre el otro
            val onVisible = visible != null || workout.value != null
            val target = if (onVisible) partner else !partner
            val workoutId = (if (target) repo.partnerWorkout.value else repo.activeWorkout.value)?.id ?: return@launch
            _events.tryEmit(
                WorkoutEvent.Undoable(
                    UndoKind.GYM, target, workoutId,
                    alsoUndo = {
                        if (onVisible) hidden?.let { restore -> update(!partner) { w -> restore(w) } }
                        // Solo si sigue en este gimnasio (si después se cambió a otro, no se vuelve atrás dos veces)
                        settingsStore.update { if (it.activeGymId == id) it.copy(activeGymId = previousGym) else it }
                    },
                    restore = { w -> (if (onVisible) visible else hidden)?.invoke(w) ?: w },
                ),
            )
        }
    }

    /** Ajusta lo pendiente de uno de los dos entrenos; devuelve cómo deshacerlo (null si no cambió nada). */
    private suspend fun refitActive(partner: Boolean): ((Workout) -> Workout)? {
        val (before, after) = update(partner) { w -> w.copy(exercises = w.exercises.map { e -> refitToGym(e, settings.value) }) } ?: return null
        val old = before.exercises.flatMap { it.sets }.associateBy { it.id }
        val changed = after.exercises.flatMap { it.sets }.filter { old[it.id] != null && old[it.id] != it }.associateBy { it.id }
        return changed.takeIf { it.isNotEmpty() }?.let { c ->
            { w: Workout -> w.copy(exercises = w.exercises.map { we -> we.copy(sets = we.sets.map { s -> if (c[s.id] == s) old.getValue(s.id) else s }) }) }
        }
    }

    /** Incremento propio del ejercicio en kg; null vuelve al de la unidad. */
    fun setIncrement(exerciseId: String, kg: Double?) = settingsStore.update { s ->
        s.copy(exerciseIncrementKg = if (kg == null) s.exerciseIncrementKg - exerciseId else s.exerciseIncrementKg + (exerciseId to kg))
    }

    fun savePinnedNote(exerciseId: String, text: String) = viewModelScope.launch { media.saveNote(exerciseId, text.trim()) }

    /** Por ejercicio: (mejor 1RM estimado, peso máximo) del historial, para marcar récords en vivo. */
    val bests: StateFlow<Map<String, Pair<Double, Double>>> = athleteHistory.map { h ->
        // En modo pareja se compara con las marcas de la pareja, no con las tuyas
        WorkoutStats.personalRecords(h).mapValues { (_, r) ->
            (r[RecordType.BEST_ONE_REP_MAX]?.value ?: 0.0) to (r[RecordType.HEAVIEST_WEIGHT]?.value ?: 0.0)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _summary = MutableStateFlow<FinishedSummary?>(null)
    val summary: StateFlow<FinishedSummary?> = _summary.asStateFlow()

    /** Índice del ejercicio al que desplazar la lista (siguiente de la superserie, etc.). */
    private val _focusExercise = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusExercise: SharedFlow<Int> = _focusExercise

    private fun edit(block: (Workout) -> Workout) = viewModelScope.launch { update(block = block) }

    /** Cambio bajo el cerrojo del repositorio: devuelve el estado antes y después (null sin entreno). */
    private suspend fun update(partner: Boolean = partnerMode.value, block: (Workout) -> Workout): Pair<Workout, Workout>? =
        if (partner) repo.updatePartnerActive(block) else repo.updateActive(block)

    fun rename(name: String) = edit { it.copy(name = name.ifBlank { it.name }) }

    /** Notas del entreno («dormí mal, hombro cargado»), también durante la sesión. */
    fun setWorkoutNotes(notes: String) = edit { it.copy(notes = notes.trim()) }

    fun addExercises(ids: List<String>) {
        val exercises = ids.mapNotNull { repo.exercise(it) }
        // En modo pareja, con su historial
        val athlete = (repo.partnerWorkout.value?.athlete ?: settings.value.partnerName).takeIf { partnerMode.value && it.isNotBlank() }
        val lookup = actions.prefillLookup(athlete = athlete)
        val history = repo.history.value
        // Descanso automático según el tipo de ejercicio (o el que usaste la última vez)
        edit { WorkoutEditor.addExercises(it, exercises, lookup, restFor = { ex -> RestPolicy.restFor(ex, history) }) }
    }

    // --- Deshacer --------------------------------------------------------------------------

    /** Avisos con acción: deshacer un borrado o aplicar un ajuste por RPE. */
    sealed interface WorkoutEvent {
        /** Borrado que se puede deshacer: [restore] vuelve a meter solo lo borrado en su sitio. */
        class Undoable(
            val kind: UndoKind,
            val partner: Boolean,
            val workoutId: String,
            /** Aviso que va detrás (p. ej. objetivo conseguido) y que solo se muestra si no se deshace. */
            val followUp: WorkoutEvent? = null,
            /** Lo demás que se deshace a la vez (el otro entreno, el gimnasio activo…). */
            val alsoUndo: (suspend () -> Unit)? = null,
            val restore: (Workout) -> Workout,
        ) : WorkoutEvent
        data class RpeSuggestion(val weId: String, val exercise: Exercise, val adjustment: RpeAdjustment) : WorkoutEvent

        /** La serie recién hecha alcanza el objetivo de 1RM que el usuario se puso en ese ejercicio. */
        data class GoalReached(val exercise: Exercise, val goal: String) : WorkoutEvent
    }
    enum class UndoKind { SET, EXERCISE, COPY, COMPLETE, TRIM, RPE_LOAD, GYM }

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkoutEvent> = _events

    /** Conflictos de sincronización con el reloj (misma serie con valores distintos). */
    val syncConflicts = repo.syncConflicts

    /** Deshace el borrado de ese aviso: se recupera lo borrado sin perder lo que haya cambiado después. */
    fun undo(event: WorkoutEvent.Undoable) {
        viewModelScope.launch {
            update(event.partner) { w -> if (w.id == event.workoutId) event.restore(w) else w }
            event.alsoUndo?.invoke()
        }
    }

    fun removeExercise(weId: String) {
        val partner = partnerMode.value
        viewModelScope.launch {
            val (before, after) = update(partner) { WorkoutEditor.removeExercise(it, weId) } ?: return@launch
            if (before.exercises.none { it.id == weId }) return@launch
            _events.tryEmit(WorkoutEvent.Undoable(UndoKind.EXERCISE, partner, before.id, restore = WorkoutEditor.undoRemoval(before, after)))
        }
    }
    fun moveExercise(from: Int, to: Int) = edit { WorkoutEditor.moveExercise(it, from, to) }
    fun setRestSeconds(weId: String, seconds: Int) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(restSeconds = seconds) } }
    fun setExerciseNotes(weId: String, notes: String) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(notes = notes) } }
    fun addSet(weId: String) = edit { WorkoutEditor.addSet(it, weId) }

    /** Las últimas [limit] sesiones con ese ejercicio (la más reciente primero), del atleta en pantalla. */
    fun historyFor(exerciseId: String, limit: Int = 5): List<Pair<Workout, com.bestrong.core.model.WorkoutExercise>> {
        return athleteHistory.value.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.mapNotNull { w ->
            w.exercises.firstOrNull { it.exercise.id == exerciseId && it.completedSets.isNotEmpty() }?.let { w to it }
        }.take(limit)
    }

    fun addBackOff(weId: String) = addLighter(weId, WorkoutEditor::addBackOff)
    fun addDropSet(weId: String) = addLighter(weId) { w, id, step -> WorkoutEditor.addDropSet(w, id, step) }

    /** Drop set o descarga; si con ese peso no se puede bajar más (barra casi vacía), se avisa en vez de no hacer nada. */
    private fun addLighter(weId: String, add: (Workout, String, Double) -> Workout) {
        val current = workout.value ?: return
        val exerciseId = current.exercises.firstOrNull { it.id == weId }?.exercise?.id ?: return
        val step = settings.value.stepKgFor(exerciseId)
        if (add(current, weId, step) == current) {
            // Sin peso apuntado todavía no hay de dónde bajar: se dice eso, no que no se pueda bajar más
            val noWeight = current.exercises.first { it.id == weId }.sets.none { it.type != SetType.WARMUP && it.weightKg > 0 }
            android.widget.Toast.makeText(appContext, if (noWeight) R.string.enter_weight_first else R.string.cannot_go_lighter, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            edit { add(it, weId, step) }
        }
    }
    fun removeSet(weId: String, setId: String) {
        val partner = partnerMode.value
        viewModelScope.launch {
            val (before, _) = update(partner) { WorkoutEditor.removeSet(it, weId, setId) } ?: return@launch
            val sets = before.exercises.firstOrNull { it.id == weId }?.sets ?: return@launch
            val index = sets.indexOfFirst { it.id == setId }
            val removed = sets.getOrNull(index) ?: return@launch
            _events.tryEmit(
                WorkoutEvent.Undoable(UndoKind.SET, partner, before.id) { w ->
                    // Con id nuevo y sin quitar la marca de borrado: el reloj ya la tiene, y con el id de antes la fusión la
                    // volvería a quitar
                    WorkoutEditor.updateExercise(w, weId) { we ->
                        if (we.sets.any { it.id == setId }) we else we.copy(sets = we.sets.toMutableList().apply { add(index.coerceAtMost(size), removed.copy(id = com.bestrong.core.util.newId())) })
                    }
                },
            )
        }
    }

    /** Copia la carga a las siguientes series; se puede deshacer (vuelven las que no se hayan tocado después). */
    fun copyToFollowing(weId: String, setId: String) {
        val partner = partnerMode.value
        viewModelScope.launch {
            val (before, after) = update(partner) { WorkoutEditor.copyToFollowing(it, weId, setId) } ?: return@launch
            val old = before.exercises.firstOrNull { it.id == weId }?.sets?.associateBy { it.id } ?: return@launch
            val copied = after.exercises.firstOrNull { it.id == weId }?.sets?.filter { old[it.id] != null && old[it.id] != it }?.associateBy { it.id } ?: return@launch
            if (copied.isEmpty()) return@launch
            _events.tryEmit(
                WorkoutEvent.Undoable(UndoKind.COPY, partner, before.id) { w ->
                    WorkoutEditor.updateExercise(w, weId) { we ->
                        we.copy(sets = we.sets.map { s -> if (copied[s.id] == s) old.getValue(s.id) else s })
                    }
                },
            )
        }
    }
    fun duplicateSet(weId: String, setId: String) = edit { WorkoutEditor.duplicateSet(it, weId, setId) }

    /** Marca de golpe las series que faltan; arranca el descanso como una serie normal y se puede deshacer. */
    fun completeRemaining(weId: String) {
        val partner = partnerMode.value
        viewModelScope.launch {
            val (before, after) = update(partner) { WorkoutEditor.completeRemaining(it, weId) } ?: return@launch
            val old = before.exercises.firstOrNull { it.id == weId }?.sets?.associateBy { it.id } ?: return@launch
            val marked = after.exercises.firstOrNull { it.id == weId }?.sets?.filter { old[it.id] != null && old[it.id] != it }?.associateBy { it.id } ?: return@launch
            if (marked.isEmpty()) return@launch
            // Si ya hay un descanso en marcha (p. ej. se completa un ejercicio olvidado), no se reinicia
            val startsRest = restTimer.rest.value == null
            if (startsRest) onSetCompleted(after, after.exercises.indexOfFirst { it.id == weId })
            // Al deshacer solo se para el descanso que arrancó esto (no otro que vino después)
            val startedEnd = if (startsRest) restTimer.rest.value?.endsAtEpochMs else null
            // El objetivo también se celebra si se llega marcando de golpe (las marcadas a la vez no cuentan
            // como «ya lo tenías»), pero solo si no se deshace
            val goal = if (partner) {
                null
            } else {
                after.exercises.firstOrNull { it.id == weId }?.exercise?.tracking?.let { t ->
                    marked.values.maxByOrNull { ExerciseGoals.valueOf(it, t) }?.let { goalEvent(after, weId, it.id, marked.keys) }
                }
            }
            _events.tryEmit(
                // Cada aviso guarda su propio descanso: dos «marcar las que faltan» seguidos no se pisan
                WorkoutEvent.Undoable(
                    UndoKind.COMPLETE,
                    partner,
                    before.id,
                    followUp = goal,
                    alsoUndo = startedEnd?.let { end -> { if (restTimer.rest.value?.endsAtEpochMs == end) restTimer.skip() } },
                ) { w ->
                    WorkoutEditor.updateExercise(w, weId) { we -> we.copy(sets = we.sets.map { s -> if (marked[s.id] == s) old.getValue(s.id) else s }) }
                },
            )
        }
    }
    fun setType(weId: String, setId: String, type: SetType) = edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(type = type) } }

    /** RPE de cada serie completada en la última versión vista del entreno (para ver los que llegan del reloj). */
    private var seenRpes: Map<String, Double?>? = null
    private var seenRpeWorkoutId: String? = null

    /** RPE puestos en el móvil (ya avisados al ponerlos): id de serie → valor. */
    private val localRpes = mutableMapOf<String, Double?>()

    /** Avisos pendientes por serie: al ir pasando el RPE en el reloj (6, 7, 8…) solo cuenta el último. */
    private val rpePromptJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /**
     * Si llega del reloj un RPE nuevo en la última serie completada de un ejercicio, se propone el mismo ajuste
     * que al anotarlo en el móvil. La primera versión del entreno solo se recuerda.
     */
    private fun onWorkoutForRpe(w: Workout?) {
        val sets = w?.exercises.orEmpty().flatMap { we -> we.sets.filter { it.completed }.map { we to it } }
        val now = sets.associate { (_, s) -> s.id to s.rpe }
        // Otro entreno (pareja, uno nuevo): se empieza a mirar desde cero, sin avisar de lo que ya traía
        val before = seenRpes.takeIf { w?.id == seenRpeWorkoutId }
        if (before == null) {
            // Los avisos pendientes eran del otro entreno
            rpePromptJobs.values.forEach { it.cancel() }
            rpePromptJobs.clear()
        }
        seenRpes = now
        seenRpeWorkoutId = w?.id
        if (before == null) return
        // Solo un RPE que cambia en una serie que ya estaba hecha: el que llega junto con la serie es la estimación
        // por velocidad del reloj (y una serie que se desmarca y se vuelve a marcar no es un RPE nuevo)
        sets.filter { (_, s) -> before.containsKey(s.id) && s.rpe != null && before[s.id] != s.rpe && localRpes[s.id] != s.rpe }
            .forEach { (we, s) -> scheduleRpePrompt(we.id, s.id) }
    }

    private fun scheduleRpePrompt(weId: String, setId: String) {
        rpePromptJobs.remove(setId)?.cancel()
        rpePromptJobs[setId] = viewModelScope.launch {
            kotlinx.coroutines.delay(RPE_PROMPT_DELAY_MS)
            rpePromptJobs.remove(setId)
            // Con el entreno de ese momento: sigue siendo la última serie hecha y su RPE es el definitivo
            val we = workout.value?.exercises?.firstOrNull { it.id == weId } ?: return@launch
            val last = we.sets.lastOrNull { it.completed }
            // Ya no es la última serie hecha, o el RPE definitivo lo puso el móvil (que ya avisó al ponerlo)
            if (last?.id != setId || localRpes[setId] == last.rpe) return@launch
            Autoregulation.suggest(we, setId, settings.value.stepKgFor(we.exercise.id))?.let {
                _events.tryEmit(WorkoutEvent.RpeSuggestion(we.id, we.exercise, it))
            }
        }
    }

    fun setRpe(weId: String, setId: String, rpe: Double?) {
        localRpes[setId] = rpe
        val current = workout.value ?: return
        val updated = WorkoutEditor.updateSet(current, weId, setId) { s -> s.copy(rpe = rpe) }
        edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(rpe = rpe) } }
        // Autorregulación: con RPE muy alto o muy bajo, proponer ajustar las series que quedan
        rpeSuggestion(updated.exercises.firstOrNull { it.id == weId } ?: return, setId)
    }

    private fun rpeSuggestion(we: com.bestrong.core.model.WorkoutExercise, setId: String) =
        Autoregulation.suggest(we, setId, settings.value.stepKgFor(we.exercise.id))?.let { _events.tryEmit(WorkoutEvent.RpeSuggestion(we.id, we.exercise, it)) }

    fun applyRpeAdjustment(weId: String, adjustment: RpeAdjustment) = edit { w ->
        WorkoutEditor.updateExercise(w, weId) { Autoregulation.apply(it, adjustment) }
    }

    // --- Pausa y sustituciones -------------------------------------------------------------

    fun togglePause() {
        val now = System.currentTimeMillis()
        val paused = workout.value?.isPaused == true
        if (!paused) restTimer.skipFor(workout.value?.id)
        edit { if (it.isPaused) WorkoutEditor.resume(it, now) else WorkoutEditor.pause(it, now) }
    }

    /** Alternativas al ejercicio (mismo músculo), con el material del gimnasio activo primero. */
    fun alternatives(weId: String): List<Exercise> {
        val we = workout.value?.exercises?.firstOrNull { it.id == weId } ?: return emptyList()
        val available = settings.value.activeGym.equipment
        val hidden = settings.value.hiddenExercises
        return Alternatives.forExercise(we.exercise, repo.exercises.value).filter { it.id !in hidden }.sortedBy { if (it.equipment in available) 0 else 1 }
    }

    fun substitute(weId: String, exerciseId: String) {
        val to = repo.exercise(exerciseId) ?: return
        val previousOfNew = previousSets.value(exerciseId)
        val step = settings.value.stepKgFor(exerciseId)
        // El peso convertido, a lo que hay en el gimnasio (34 kg en mancuernas de 2 en 2, no 35)
        edit { w -> WorkoutEditor.substitute(w, weId, to, previousOfNew, step).let { r -> r.copy(exercises = r.exercises.map { e -> if (e.exercise.id == to.id) refitToGym(e, settings.value) else e }) } }
    }
    fun linkWithNext(weId: String) = edit { WorkoutEditor.linkWithNext(it, weId) }
    fun unlinkSuperset(weId: String) = edit { WorkoutEditor.unlinkSuperset(it, weId) }

    /** Peso y reps se propagan a las series pendientes siguientes que tenían el mismo valor. */
    fun updateSet(weId: String, setId: String, weightKg: Double? = null, reps: Int? = null, duration: Int? = null) = edit { w ->
        val propagated = if (weightKg != null || reps != null) WorkoutEditor.updateSetPropagating(w, weId, setId, weightKg, reps) else w
        if (duration == null) propagated else WorkoutEditor.updateSet(propagated, weId, setId) { s -> s.copy(durationSec = duration) }
    }

    /** Botones ±: el incremento de la unidad (2,5 kg / 5 lb) o 1 rep. */
    fun stepWeight(weId: String, setId: String, sign: Int) = edit { w ->
        val we = w.exercises.firstOrNull { it.id == weId }
        val old = we?.sets?.firstOrNull { it.id == setId } ?: return@edit w
        val allowNegative = we.exercise.tracking == com.bestrong.core.model.TrackingType.REPS_ONLY
        val stepped = WorkoutEditor.stepWeight(w, weId, setId, sign * settings.value.stepKgFor(we.exercise.id), allowNegative)
        val newKg = stepped.exercises.first { it.id == weId }.sets.first { it.id == setId }.weightKg
        WorkoutEditor.updateSetPropagating(w, weId, old.id, weightKg = newKg)
    }

    fun stepReps(weId: String, setId: String, sign: Int) = edit { w ->
        val old = w.exercises.firstOrNull { it.id == weId }?.sets?.firstOrNull { it.id == setId } ?: return@edit w
        WorkoutEditor.updateSetPropagating(w, weId, setId, reps = (old.reps + sign).coerceAtLeast(0))
    }

    /** Cambios de otros campos de la serie (nota, lado derecho, distancia, con ayuda). */
    fun updateSetFields(weId: String, setId: String, block: (WorkoutSet) -> WorkoutSet) = edit { w ->
        WorkoutEditor.updateSet(w, weId, setId, block)
    }

    /**
     * Pone en cada serie normal pendiente el peso para sus reps al RPE objetivo (una pirámide 12/10/8 lleva tres
     * pesos), redondeado al material y con el tope semanal, como el relleno. Se puede deshacer.
     */
    fun applyRpeLoads(weId: String) {
        val partner = partnerMode.value
        val s = settings.value
        val e1rms = recentOneRepMax.value
        val history = athleteHistory.value
        viewModelScope.launch {
            val (before, after) = update(partner) { w ->
                WorkoutEditor.updateExercise(w, weId) { e -> withRpeLoads(e, e1rms[e.exercise.id], s, history, actions) }
            } ?: return@launch
            val old = before.exercises.firstOrNull { it.id == weId }?.sets?.associateBy { it.id } ?: return@launch
            val changed = after.exercises.firstOrNull { it.id == weId }?.sets?.filter { old[it.id] != null && old[it.id] != it }?.associateBy { it.id } ?: return@launch
            if (changed.isEmpty()) return@launch
            _events.tryEmit(
                WorkoutEvent.Undoable(UndoKind.RPE_LOAD, partner, before.id) { w ->
                    WorkoutEditor.updateExercise(w, weId) { we -> we.copy(sets = we.sets.map { set -> if (changed[set.id] == set) old.getValue(set.id) else set }) }
                },
            )
        }
    }

    /** Añade series de aproximación antes de la primera serie de trabajo. */
    fun addWarmup(weId: String) = edit { w ->
        // Con la barra del ejercicio (EZ, hexagonal…) si tiene una propia
        val ex = w.exercises.firstOrNull { it.id == weId }?.exercise
        val gym = settings.value.gymFor(ex?.id.orEmpty())
        val unit = settings.value.unit
        // La barra solo con barra (mancuernas, máquinas y poleas empiezan sin ella), y pesos que se puedan montar
        val barbell = ex?.equipment == Equipment.BARBELL || ex?.equipment == Equipment.SMITH
        val bar = if (barbell) unit.toKg(com.bestrong.core.training.GymProfile.barIn(unit, gym.barKg) + unit.fromKg(gym.collarKg)) else 0.0
        WorkoutEditor.addWarmup(w, weId, unit, bar) { kg -> ex?.let { gym.nearestLoad(kg, it.equipment, unit) } ?: kg }
    }

    /** «8-12» o null (sin rango: progresión normal). */
    fun setRepRange(exerciseId: String, range: String?) = settingsStore.update { s ->
        s.copy(exerciseRepRange = if (range == null) s.exerciseRepRange - exerciseId else s.exerciseRepRange + (exerciseId to range))
    }

    /** null = la barra del gimnasio. */
    fun setExerciseBar(exerciseId: String, kg: Double?) = settingsStore.update { s ->
        s.copy(exerciseBarKg = if (kg == null) s.exerciseBarKg - exerciseId else s.exerciseBarKg + (exerciseId to kg))
    }

    /** Tempo de la repetición (p. ej. "3-1-1-0"); vacío lo quita. */
    fun setTempo(weId: String, tempo: String) = edit { w ->
        WorkoutEditor.updateExercise(w, weId) { it.copy(tempo = Tempo.parse(tempo)?.toString()) }
    }

    /** Aplica la sugerencia de progresión a las series pendientes. */
    fun applySuggestion(weId: String, suggestion: ProgressionSuggestion) = edit { w ->
        WorkoutEditor.updateExercise(w, weId) { we ->
            var i = 0
            we.copy(
                // Cada serie efectiva con la suya de la sugerencia (también las ya hechas cuentan para el orden)
                sets = we.sets.map { s ->
                    if (s.type == SetType.WARMUP) return@map s
                    val sug = suggestion.sets.getOrNull(i++)
                    if (s.completed || sug == null) s else s.copy(weightKg = sug.weightKg, reps = sug.reps, durationSec = sug.durationSec)
                },
            )
        }
    }

    /** Cronómetro de cardio: en el reloj, además, cambia la sesión de pesas a la de esa máquina. */
    fun startCardio(weId: String, setId: String) {
        // Solo el descanso de quien arranca el cronómetro (el de la pareja, o el tuyo, sigue)
        restTimer.skipFor(workout.value?.id)
        edit { WorkoutEditor.startCardio(it, weId, setId) }
    }

    fun toggleSet(weId: String, setId: String) {
        // El objetivo es tuyo: con el compañero en pantalla no se celebra
        val partner = partnerMode.value
        viewModelScope.launch {
            // Se decide con el estado real del cambio (bajo el cerrojo), no con una copia que puede ir atrasada
            val (before, updated) = update(partner) { WorkoutEditor.toggleSetCompleted(it, weId, setId) } ?: return@launch
            val index = updated.exercises.indexOfFirst { it.id == weId }
            fun done(w: Workout) = w.exercises.getOrNull(index)?.sets?.firstOrNull { it.id == setId }?.completed == true
            if (index >= 0 && !done(before) && done(updated)) {
                onSetCompleted(updated, index)
                // RPE puesto antes de marcarla: la propuesta de ajuste llega al marcarla
                rpeSuggestion(updated.exercises[index], setId)
                if (!partner) goalEvent(updated, weId, setId)?.let { _events.tryEmit(it) }
            } else if (unticked(before, updated, setId) && wasLastCompleted(before, setId)) {
                // Marcada por error y desmarcada: el descanso que lanzó ya no tiene sentido (solo el de este entreno)
                restTimer.skipFor(updated.id)
            }
        }
    }

    /** Aviso si esta serie es la primera que llega al objetivo del ejercicio (no si ya lo tenías); null si no. */
    private fun goalEvent(w: Workout, weId: String, setId: String, markedTogether: Set<String> = setOf(setId)): WorkoutEvent.GoalReached? {
        val we = w.exercises.firstOrNull { it.id == weId }
        val set = we?.sets?.firstOrNull { it.id == setId }
        // Sin las marcas cargadas todavía no se sabe si ya lo tenías: mejor no celebrar de más
        val loaded = bests.value.isNotEmpty() || repo.history.value.isEmpty()
        val goal = we?.takeIf { loaded }?.let { settings.value.exerciseGoalsKg[it.exercise.id] }
        if (we == null || set == null || goal == null) return null
        val tracking = we.exercise.tracking
        val value = ExerciseGoals.valueOf(set, tracking)
        val historyBest = if (tracking == TrackingType.WEIGHT_REPS) {
            bests.value[we.exercise.id]?.first ?: 0.0
        } else {
            ExerciseGoals.points(repo.history.value, we.exercise.id, tracking).maxOfOrNull { it.second } ?: 0.0
        }
        val todayBest = w.exercises.filter { it.exercise.id == we.exercise.id }.flatMap { it.completedSets }
            .filter { it.id != setId && it.id !in markedTogether }.maxOfOrNull { ExerciseGoals.valueOf(it, tracking) } ?: 0.0
        return if (value >= goal && maxOf(historyBest, todayBest) < goal) WorkoutEvent.GoalReached(we.exercise, goalText(we.exercise, goal)) else null
    }

    /** El objetivo como se ve en la app: peso (con el total de mancuernas si toca), reps o tiempo. */
    private fun goalText(exercise: Exercise, goal: Double): String = when (exercise.tracking) {
        TrackingType.WEIGHT_REPS -> {
            val factor = if (settings.value.dumbbellTotal && exercise.equipment == Equipment.DUMBBELL) 2 else 1
            settings.value.unit.format(goal * factor, withUnit = true)
        }
        TrackingType.DURATION -> formatDuration(goal.toLong())
        else -> "${goal.toInt()} " + appContext.getString(R.string.reps_short)
    }

    /** Descanso, aviso por voz y desplazamiento al siguiente ejercicio tras completar una serie. */
    private fun onSetCompleted(updated: Workout, index: Int) {
        val (next, restSeconds) = WorkoutEditor.afterSetCompleted(updated, index, settings.value.warmupRestSeconds)
        if (restSeconds > 0) restTimer.start(restSeconds, updated.id) else restTimer.skipFor(updated.id)
        // Aviso por voz: cuánto descansar y qué viene (la serie del ejercicio al que se salta, no la primera de la lista)
        val upcoming = next?.let { i -> updated.exercises.getOrNull(i)?.sets?.firstOrNull { !it.completed }?.let { i to it } }
            ?: WorkoutEditor.nextPendingSet(updated)
        upcoming?.let { (i, s) ->
            val we = updated.exercises[i]
            val name = we.exercise.displayName(appContext)
            // Como en pantalla: con «suma de las dos mancuernas», el peso del par
            val set = describeSet(s, we.exercise.tracking, settings.value.unit, withPace = false, weightFactor = actions.weightFactor(we.exercise))
            // En una superserie no hay descanso: no se dice «descansa 0:00»
            announcer.say(
                if (restSeconds > 0) {
                    appContext.getString(R.string.tts_next, formatDuration(restSeconds.toLong()), name, set)
                } else {
                    appContext.getString(R.string.tts_next_no_rest, name, set)
                },
            )
        }
        if (next != null && next != index) _focusExercise.tryEmit(next)
    }

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)

    /** El entreno en texto (series, RPE), como «Compartir como texto» del historial. */
    fun shareText(workout: Workout): String = actions.shareText(workout) { it.displayName(appContext) }
    fun skipRest() = restTimer.skip()
    fun startTimedSet() = restTimer.skipFor(workout.value?.id)

    fun finish() {
        if (partnerMode.value) {
            // Entreno de la pareja: se guarda aparte (no cuenta para tus récords ni estadísticas)
            viewModelScope.launch {
                actions.closePartner()
                setPartnerMode(false)
            }
            return
        }
        viewModelScope.launch {
            // Se lee y se quita bajo el cerrojo: no se pierde ninguna serie marcada en el último momento
            val current = repo.takeActive() ?: return@launch
            // Si la pareja seguía con el suyo, se guarda también: tras terminar el tuyo no habría cómo volver a él
            actions.closePartner()
            val metrics = metricsStore.metrics.value?.takeIf { it.workoutId == current.id }
            val ended = WorkoutEditor.finish(current, System.currentTimeMillis()).copy(
                avgHeartRate = metrics?.avgHeartRate ?: current.avgHeartRate,
                maxHeartRate = metrics?.maxHeartRate ?: current.maxHeartRate,
            ).let(actions::withGym)
            // Calorías totales y activas: las del reloj si las hay; si no, pulso o estimación personalizada
            val finished = energy.complete(ended, watchKcal = metrics?.calories ?: current.calories)
            restTimer.skip()
            if (finished.exercises.isEmpty()) return@launch
            repo.saveWorkout(finished)
            // El pulso y las calorías del reloj pueden haber llegado mientras se terminaba: desde aquí, lo guardado
            actions.applyPendingWatchHealth(finished.id)
            val saved = repo.storedWorkout(finished.id) ?: finished
            savedState[KEY_SUMMARY_ID] = saved.id
            val history = repo.historyOnce()
            _summary.value = withContext(Dispatchers.Default) { buildSummary(saved, history) }
            // Se compara con lo planificado (series sin hacer incluidas): saltarse una serie por cansancio no cambia la rutina
            changedRoutine(current)?.let { r -> _summary.value = _summary.value?.copy(routineToUpdate = r, planned = current) }
            actions.exportToHealthConnectIfEnabled(saved)
        }
    }

    fun discard() = viewModelScope.launch {
        if (partnerMode.value) {
            actions.discardPartner()
            setPartnerMode(false)
            return@launch
        }
        // Se puede deshacer desde Inicio durante unos segundos (con el descanso tal como iba)
        val rest = restTimer.rest.value
        restTimer.skip()
        repo.discardActive(rest?.endsAtEpochMs, rest?.totalSeconds ?: 0)
        actions.closePartnerOnDiscard()
    }

    fun saveSummaryAsRoutine() {
        val s = _summary.value ?: return
        viewModelScope.launch { repo.saveRoutine(Routine.fromWorkout(s.workout)) }
    }

    /** Pasa a la rutina los cambios hechos en el entreno (ejercicios, series, descansos…). */
    fun updateRoutineFromSummary() {
        val s = _summary.value
        val routine = s?.routineToUpdate
        val planned = s?.planned
        if (routine == null || planned == null) return
        _summary.value = s.copy(routineToUpdate = null)
        viewModelScope.launch { repo.saveRoutine(routine.updatedFrom(planned)) }
    }

    private suspend fun changedRoutine(finished: Workout): Routine? =
        finished.routineId?.let { repo.routine(it) }?.takeIf { it.structureDiffersFrom(finished) }

    /** Guarda la valoración de la sesión recién terminada. */
    fun rateWorkout(rating: SessionRating) {
        val s = _summary.value ?: return
        val rated = s.workout.copy(rating = rating)
        _summary.value = s.copy(workout = rated)
        // Sobre lo guardado: el reloj puede haber añadido el pulso y las calorías después de terminar
        // (si se borró desde el historial con el resumen abierto, no vuelve)
        viewModelScope.launch { repo.updateWorkout(rated.id) { it.copy(rating = rating) } }
    }

    fun skipExercise(weId: String) = edit { WorkoutEditor.skipExercise(it, weId) }

    fun dismissSummary() {
        _summary.value = null
        savedState.remove<String>(KEY_SUMMARY_ID)
    }

    private fun buildSummary(finished: Workout, history: List<Workout>) = FinishedSummary(
        finished,
        WorkoutStats.newRecordsIn(finished, history),
        WorkoutStats.compareWithPrevious(finished, history),
        WorkoutStats.imbalances(finished),
        // Lo mismo que se rellenará al empezar la próxima vez (no una regla aparte que a veces lo contradecía)
        actions.nextTimeAfter(finished, history),
        weeklyGoalReached = weeklyGoalReachedBy(finished, history),
    )

    /** Si con este entreno se llega justo al objetivo de días de la semana (el del programa o el propio). */
    private fun weeklyGoalReachedBy(finished: Workout, history: List<Workout>): Int? {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(finished.startedAtEpochMs).atZone(zone).toLocalDate()
        val goal = repo.activeProgram.value?.weeklyGoal ?: settings.value.weeklyGoal
        // Un entreno olvidado abierto desde hace días no cuenta para hoy; uno que cruza la medianoche
        // del domingo sí (se compara con cuándo terminó, no con el lunes)
        // Las pausas no cuentan (una sesión partida por la comida sigue siendo la de hoy), pero uno
        // pausado y olvidado días tampoco vale
        val wallClock = (finished.finishedAtEpochMs ?: System.currentTimeMillis()) - finished.startedAtEpochMs
        if (finished.durationSeconds() * 1000L > STALE_WORKOUT_MS || wallClock > MAX_WALL_CLOCK_MS) return null
        val others = history.filter { it.id != finished.id }
        val before = Periodization.trainedDaysOfWeek(others, day, zone).size
        val after = Periodization.trainedDaysOfWeek(others + finished, day, zone).size
        return goal.takeIf { goal in (before + 1)..after }
    }

    init {
        viewModelScope.launch { workout.collect(::onWorkoutForRpe) }
        // Resumen sin cerrar cuando el sistema mató el proceso: se reconstruye desde el historial
        savedState.get<String>(KEY_SUMMARY_ID)?.let { id ->
            viewModelScope.launch {
                val history = repo.historyOnce()
                history.firstOrNull { it.id == id }?.let {
                    if (_summary.value == null) _summary.value = buildSummary(it, history)
                }
            }
        }
    }

    private companion object {
        /** Espera antes de proponer un ajuste por un RPE del reloj (se cambia tocando varias veces). */
        const val RPE_PROMPT_DELAY_MS = 2_000L

        /** Más de esto abierto: entreno olvidado, no se felicita por el objetivo semanal. */
        const val STALE_WORKOUT_MS = 6 * 60 * 60 * 1000L
        const val MAX_WALL_CLOCK_MS = 24 * 60 * 60 * 1000L
        const val KEY_PARTNER_MODE = "partner_mode"
        const val KEY_SUMMARY_ID = "summary_workout_id"
        const val KEY_DEADLINE = "deadline"
        const val KEY_DEADLINE_WORKOUT = "deadline_workout_id"
    }
}

/** Si [setId] fue la última serie hecha (la que lanzó el descanso en curso). */
private fun wasLastCompleted(w: Workout, setId: String): Boolean {
    val sets = w.exercises.flatMap { it.sets }.filter { it.completed }
    val mine = sets.firstOrNull { it.id == setId }?.completedAtEpochMs ?: return false
    return sets.none { it.id != setId && (it.completedAtEpochMs ?: 0) > mine }
}

private fun unticked(before: Workout, after: Workout, setId: String): Boolean {
    fun done(w: Workout) = w.exercises.any { we -> we.sets.any { it.id == setId && it.completed } }
    return done(before) && !done(after)
}
