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
import com.bestrong.core.training.NextTime
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
    val partnerMode: StateFlow<Boolean> = savedState.getStateFlow(KEY_PARTNER_MODE, false)

    private fun setPartnerMode(on: Boolean) {
        savedState[KEY_PARTNER_MODE] = on
    }

    val workout: StateFlow<Workout?> = combine(repo.activeWorkout, repo.partnerWorkout, partnerMode) { me, partner, isPartner ->
        if (isPartner) partner else me
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repo.activeWorkout.value)

    /** Cambia entre tu entreno y el de tu pareja; la primera vez copia tus ejercicios con las series sin hacer. */
    fun switchAthlete(partner: Boolean) = viewModelScope.launch {
        if (partner && repo.partnerWorkout.value == null) {
            val name = settings.value.partnerName.ifBlank { return@launch }
            val mine = repo.activeWorkout.value
            val now = System.currentTimeMillis()
            val copy = (mine ?: WorkoutEditor.empty(now)).let { w ->
                w.copy(
                    id = com.bestrong.core.util.newId(),
                    name = "$name · ${w.name}",
                    startedAtEpochMs = now,
                    athlete = name,
                    exercises = w.exercises.map { we ->
                        we.copy(id = com.bestrong.core.util.newId(), sets = we.sets.map { it.asPending() })
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
    val previousSets: StateFlow<(String) -> List<WorkoutSet>> = repo.previousSetsLookup
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
            if (before == after) return@launch
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
        repo.history,
        settings,
    ) { exercises, history, s ->
        if (!s.progressionEnabled) {
            emptyMap()
        } else {
            // Con los mismos ajustes que el relleno (discos, tope semanal, subida que no se pierde), para no contradecirlo
            exercises.mapNotNull { ex ->
                Progression.suggest(ex, history, s.stepKgFor(ex.id), repRange = s.repRangeFor(ex.id))
                    ?.let { sug -> ex.id to sug.copy(sets = actions.loadable(ex, sug.sets, sug.kind, history = history)) }
            }.toMap()
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Nota fija de cada ejercicio (altura del asiento, agarre…): id de ejercicio → nota. */
    val pinnedNotes: StateFlow<Map<String, String>> = combine(
        workout.map { w -> w?.exercises?.map { it.exercise.id }.orEmpty() }.distinctUntilChanged(),
        media.version,
    ) { ids, _ -> ids.distinct().associateWith { media.note(it) }.filterValues { it.isNotBlank() } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Incremento propio del ejercicio en kg; null vuelve al de la unidad. */
    fun setIncrement(exerciseId: String, kg: Double?) = settingsStore.update { s ->
        s.copy(exerciseIncrementKg = if (kg == null) s.exerciseIncrementKg - exerciseId else s.exerciseIncrementKg + (exerciseId to kg))
    }

    fun savePinnedNote(exerciseId: String, text: String) {
        viewModelScope.launch { media.saveNote(exerciseId, text.trim()) }
    }

    /** Por ejercicio: (mejor 1RM estimado, peso máximo) del historial, para marcar récords en vivo. */
    val bests: StateFlow<Map<String, Pair<Double, Double>>> = combine(repo.history, repo.partnerHistory, partnerMode, settings) { me, partner, isPartner, s ->
        // En modo pareja se compara con las marcas de la pareja, no con las tuyas
        val h = if (isPartner) partner.filter { it.athlete == s.partnerName } else me
        WorkoutStats.personalRecords(h).mapValues { (_, r) ->
            (r[RecordType.BEST_ONE_REP_MAX]?.value ?: 0.0) to (r[RecordType.HEAVIEST_WEIGHT]?.value ?: 0.0)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _summary = MutableStateFlow<FinishedSummary?>(null)
    val summary: StateFlow<FinishedSummary?> = _summary.asStateFlow()

    /** Índice del ejercicio al que desplazar la lista (siguiente de la superserie, etc.). */
    private val _focusExercise = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusExercise: SharedFlow<Int> = _focusExercise

    private fun edit(block: (Workout) -> Workout) {
        viewModelScope.launch { update(block = block) }
    }

    /** Cambio bajo el cerrojo del repositorio: devuelve el estado antes y después (null sin entreno). */
    private suspend fun update(partner: Boolean = partnerMode.value, block: (Workout) -> Workout): Pair<Workout, Workout>? =
        if (partner) repo.updatePartnerActive(block) else repo.updateActive(block)

    fun rename(name: String) = edit { it.copy(name = name.ifBlank { it.name }) }

    /** Notas del entreno («dormí mal, hombro cargado»), también durante la sesión. */
    fun setWorkoutNotes(notes: String) = edit { it.copy(notes = notes.trim()) }

    fun addExercises(ids: List<String>) {
        val exercises = ids.mapNotNull { repo.exercise(it) }
        val lookup = actions.prefillLookup()
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
            val restore: (Workout) -> Workout,
        ) : WorkoutEvent
        data class RpeSuggestion(val weId: String, val exercise: Exercise, val adjustment: RpeAdjustment) : WorkoutEvent

        /** La serie recién hecha alcanza el objetivo de 1RM que el usuario se puso en ese ejercicio. */
        data class GoalReached(val exercise: Exercise, val goal: String) : WorkoutEvent
    }
    enum class UndoKind { SET, EXERCISE, COPY, COMPLETE, TRIM }

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkoutEvent> = _events

    /** Conflictos de sincronización con el reloj (misma serie con valores distintos). */
    val syncConflicts = repo.syncConflicts

    /** Fin del descanso que arrancó el último «marcar las que faltan» (para pararlo si se deshace). */
    private var pendingRestUndo: Long? = null

    /** Deshace el borrado de ese aviso: se recupera lo borrado sin perder lo que haya cambiado después. */
    fun undo(event: WorkoutEvent.Undoable) {
        if (event.kind == UndoKind.COMPLETE) {
            pendingRestUndo?.let { end -> if (restTimer.rest.value?.endsAtEpochMs == end) restTimer.skip() }
            pendingRestUndo = null
        }
        viewModelScope.launch { update(event.partner) { w -> if (w.id == event.workoutId) event.restore(w) else w } }
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
        val history = if (partnerMode.value) repo.partnerHistory.value.filter { it.athlete == settings.value.partnerName } else repo.history.value
        return history.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.mapNotNull { w ->
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
                    WorkoutEditor.updateExercise(w, weId) { we ->
                        if (we.sets.any { it.id == setId }) we else we.copy(sets = we.sets.toMutableList().apply { add(index.coerceAtMost(size), removed) })
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
                WorkoutEvent.Undoable(UndoKind.COMPLETE, partner, before.id, followUp = goal) { w ->
                    WorkoutEditor.updateExercise(w, weId) { we -> we.copy(sets = we.sets.map { s -> if (marked[s.id] == s) old.getValue(s.id) else s }) }
                },
            )
            pendingRestUndo = startedEnd
        }
    }
    fun setType(weId: String, setId: String, type: SetType) = edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(type = type) } }
    fun setRpe(weId: String, setId: String, rpe: Double?) {
        val current = workout.value ?: return
        val updated = WorkoutEditor.updateSet(current, weId, setId) { s -> s.copy(rpe = rpe) }
        edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(rpe = rpe) } }
        // Autorregulación: con RPE muy alto o muy bajo, proponer ajustar las series que quedan
        val we = updated.exercises.firstOrNull { it.id == weId } ?: return
        Autoregulation.suggest(we, setId, settings.value.stepKgFor(we.exercise.id))?.let {
            _events.tryEmit(WorkoutEvent.RpeSuggestion(weId, we.exercise, it))
        }
    }

    fun applyRpeAdjustment(weId: String, adjustment: RpeAdjustment) = edit { w ->
        WorkoutEditor.updateExercise(w, weId) { Autoregulation.apply(it, adjustment) }
    }

    // --- Pausa y sustituciones -------------------------------------------------------------

    fun togglePause() {
        val now = System.currentTimeMillis()
        val paused = workout.value?.isPaused == true
        if (!paused) restTimer.skip()
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
        val previousOfNew = WorkoutStats.previousSets(exerciseId, repo.history.value)
        val step = settings.value.stepKgFor(exerciseId)
        edit { WorkoutEditor.substitute(it, weId, to, previousOfNew, step) }
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

    /** Añade series de aproximación antes de la primera serie de trabajo. */
    fun addWarmup(weId: String) = edit { w ->
        // Con la barra del ejercicio (EZ, hexagonal…) si tiene una propia
        val exerciseId = w.exercises.firstOrNull { it.id == weId }?.exercise?.id.orEmpty()
        val gym = settings.value.gymFor(exerciseId)
        val bar = com.bestrong.core.training.GymProfile.barIn(settings.value.unit, gym.barKg) + settings.value.unit.fromKg(gym.collarKg)
        WorkoutEditor.addWarmup(w, weId, settings.value.unit, settings.value.unit.toKg(bar))
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
                sets = we.sets.map { s ->
                    if (s.completed || s.type == SetType.WARMUP) {
                        s
                    } else {
                        suggestion.sets.getOrNull(i++)?.let { sug -> s.copy(weightKg = sug.weightKg, reps = sug.reps, durationSec = sug.durationSec) } ?: s
                    }
                },
            )
        }
    }

    /** Cronómetro de cardio: en el reloj, además, cambia la sesión de pesas a la de esa máquina. */
    fun startCardio(weId: String, setId: String) {
        restTimer.skip()
        edit { WorkoutEditor.startCardio(it, weId, setId) }
    }

    fun toggleSet(weId: String, setId: String) {
        // El objetivo es tuyo: con el compañero en pantalla no se celebra
        val partner = partnerMode.value
        viewModelScope.launch {
            // Se decide con el estado real del cambio (bajo el cerrojo), no con una copia que puede ir atrasada
            val (before, updated) = update { WorkoutEditor.toggleSetCompleted(it, weId, setId) } ?: return@launch
            val index = updated.exercises.indexOfFirst { it.id == weId }
            fun done(w: Workout) = w.exercises.getOrNull(index)?.sets?.firstOrNull { it.id == setId }?.completed == true
            if (index >= 0 && !done(before) && done(updated)) {
                onSetCompleted(updated, index)
                if (!partner) goalEvent(updated, weId, setId)?.let { _events.tryEmit(it) }
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
        if (restSeconds > 0) restTimer.start(restSeconds) else restTimer.skip()
        // Aviso por voz: cuánto descansar y qué viene
        WorkoutEditor.nextPendingSet(updated)?.let { (i, s) ->
            val we = updated.exercises[i]
            announcer.say(
                appContext.getString(
                    R.string.tts_next,
                    formatDuration(restSeconds.toLong()),
                    we.exercise.displayName(appContext),
                    describeSet(s, we.exercise.tracking, settings.value.unit),
                ),
            )
        }
        if (next != null && next != index) _focusExercise.tryEmit(next)
    }

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)
    fun skipRest() = restTimer.skip()

    fun finish() {
        if (partnerMode.value) {
            // Entreno de la pareja: se guarda aparte (no cuenta para tus récords ni estadísticas)
            viewModelScope.launch {
                val current = repo.takePartnerActive() ?: return@launch
                val finished = WorkoutEditor.finish(current, System.currentTimeMillis())
                setPartnerMode(false)
                if (finished.exercises.isNotEmpty()) repo.saveWorkout(finished)
            }
            return
        }
        viewModelScope.launch {
            // Se lee y se quita bajo el cerrojo: no se pierde ninguna serie marcada en el último momento
            val current = repo.takeActive() ?: return@launch
            val metrics = metricsStore.metrics.value?.takeIf { it.workoutId == current.id }
            val ended = WorkoutEditor.finish(current, System.currentTimeMillis()).copy(
                avgHeartRate = metrics?.avgHeartRate ?: current.avgHeartRate,
                maxHeartRate = metrics?.maxHeartRate ?: current.maxHeartRate,
            )
            // Calorías totales y activas: las del reloj si las hay; si no, pulso o estimación personalizada
            val finished = energy.complete(ended, watchKcal = metrics?.calories ?: current.calories)
            restTimer.skip()
            if (finished.exercises.isEmpty()) return@launch
            repo.saveWorkout(finished)
            savedState[KEY_SUMMARY_ID] = finished.id
            val history = repo.historyOnce()
            _summary.value = withContext(Dispatchers.Default) { buildSummary(finished, history) }
            // Se compara con lo planificado (series sin hacer incluidas): saltarse una serie por cansancio no cambia la rutina
            changedRoutine(current)?.let { r -> _summary.value = _summary.value?.copy(routineToUpdate = r, planned = current) }
            actions.exportToHealthConnectIfEnabled(finished)
        }
    }

    fun discard() = viewModelScope.launch {
        if (partnerMode.value) {
            repo.setPartnerActive(null)
            setPartnerMode(false)
            return@launch
        }
        // Se puede deshacer desde Inicio durante unos segundos (con el descanso tal como iba)
        val rest = restTimer.rest.value
        restTimer.skip()
        repo.discardActive(rest?.endsAtEpochMs, rest?.totalSeconds ?: 0)
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
        viewModelScope.launch { repo.saveWorkout(rated) }
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
        NextTime.forWorkout(finished) { settings.value.stepKgFor(it) },
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
        /** Más de esto abierto: entreno olvidado, no se felicita por el objetivo semanal. */
        const val STALE_WORKOUT_MS = 6 * 60 * 60 * 1000L
        const val MAX_WALL_CLOCK_MS = 24 * 60 * 60 * 1000L
        const val KEY_PARTNER_MODE = "partner_mode"
        const val KEY_SUMMARY_ID = "summary_workout_id"
        const val KEY_DEADLINE = "deadline"
        const val KEY_DEADLINE_WORKOUT = "deadline_workout_id"
    }
}
