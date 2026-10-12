package com.bestrong.mobile.ui.workout

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.library.Alternatives
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.SessionRating
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.Autoregulation
import com.bestrong.core.training.NextTime
import com.bestrong.core.training.NextTimeSuggestion
import com.bestrong.core.training.Progression
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.training.RestPolicy
import com.bestrong.core.training.RpeAdjustment
import com.bestrong.core.training.Tempo
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.WatchMetricsStore
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinishedSummary(
    val workout: Workout,
    val records: List<PersonalRecord>,
    val comparisons: List<WorkoutStats.ExerciseComparison> = emptyList(),
    val imbalances: List<Pair<String, Double>> = emptyList(),
    val nextTime: List<NextTimeSuggestion> = emptyList(),
)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    settingsStore: SettingsStore,
    private val restTimer: RestTimerController,
    private val metricsStore: WatchMetricsStore,
    private val energy: EnergyCalculator,
    private val actions: WorkoutActions,
    private val announcer: Announcer,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** Modo pareja: true = se está viendo y editando el entreno de la otra persona. */
    val partnerMode = MutableStateFlow(false)

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
                        we.copy(id = com.bestrong.core.util.newId(), sets = we.sets.map { it.copy(id = com.bestrong.core.util.newId(), completed = false) })
                    },
                )
            }
            repo.setPartnerActive(copy)
        }
        partnerMode.value = partner
    }
    val previousSets: StateFlow<(String) -> List<WorkoutSet>> = repo.previousSetsLookup
    val settings: StateFlow<UserSettings> = settingsStore.settings
    val rest: StateFlow<RestState?> = restTimer.rest
    val restEndedAt: StateFlow<Long?> = restTimer.restEndedAt
    fun dismissOverrun() = restTimer.clearOverrun()
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
            exercises.mapNotNull { ex -> Progression.suggest(ex, history, s.unit.stepKg())?.let { ex.id to it } }.toMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _summary = MutableStateFlow<FinishedSummary?>(null)
    val summary: StateFlow<FinishedSummary?> = _summary.asStateFlow()

    /** Índice del ejercicio al que desplazar la lista (siguiente de la superserie, etc.). */
    private val _focusExercise = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusExercise: SharedFlow<Int> = _focusExercise

    private fun edit(block: (Workout) -> Workout) {
        viewModelScope.launch { if (partnerMode.value) repo.updatePartnerActive(block) else repo.updateActive(block) }
    }

    fun rename(name: String) = edit { it.copy(name = name.ifBlank { it.name }) }

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
        data class Undoable(val kind: UndoKind) : WorkoutEvent
        data class RpeSuggestion(val weId: String, val exercise: Exercise, val adjustment: RpeAdjustment) : WorkoutEvent
    }
    enum class UndoKind { SET, EXERCISE }

    private val _events = MutableSharedFlow<WorkoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WorkoutEvent> = _events

    /** Conflictos de sincronización con el reloj (misma serie con valores distintos). */
    val syncConflicts = repo.syncConflicts
    private var undoSnapshot: Workout? = null

    private fun editUndoable(kind: UndoKind, block: (Workout) -> Workout) {
        undoSnapshot = workout.value
        edit(block)
        _events.tryEmit(WorkoutEvent.Undoable(kind))
    }

    fun undo() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        // Se recupera lo borrado sin perder lo que haya cambiado después en otras series
        edit { current -> if (current.id == snapshot.id) snapshot.copy(updatedAtEpochMs = current.updatedAtEpochMs) else current }
    }

    fun removeExercise(weId: String) = editUndoable(UndoKind.EXERCISE) { WorkoutEditor.removeExercise(it, weId) }
    fun moveExercise(from: Int, to: Int) = edit { WorkoutEditor.moveExercise(it, from, to) }
    fun setRestSeconds(weId: String, seconds: Int) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(restSeconds = seconds) } }
    fun setExerciseNotes(weId: String, notes: String) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(notes = notes) } }
    fun addSet(weId: String) = edit { WorkoutEditor.addSet(it, weId) }
    fun removeSet(weId: String, setId: String) = editUndoable(UndoKind.SET) { WorkoutEditor.removeSet(it, weId, setId) }
    fun setType(weId: String, setId: String, type: SetType) = edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(type = type) } }
    fun setRpe(weId: String, setId: String, rpe: Double?) {
        val current = workout.value ?: return
        val updated = WorkoutEditor.updateSet(current, weId, setId) { s -> s.copy(rpe = rpe) }
        edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(rpe = rpe) } }
        // Autorregulación: con RPE muy alto o muy bajo, proponer ajustar las series que quedan
        val we = updated.exercises.firstOrNull { it.id == weId } ?: return
        Autoregulation.suggest(we, setId, settings.value.unit.stepKg())?.let {
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
        return Alternatives.forExercise(we.exercise, repo.exercises.value).sortedBy { if (it.equipment in available) 0 else 1 }
    }

    fun substitute(weId: String, exerciseId: String) {
        val to = repo.exercise(exerciseId) ?: return
        val previousOfNew = WorkoutStats.previousSets(exerciseId, repo.history.value)
        val step = settings.value.unit.stepKg()
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
        val stepped = WorkoutEditor.stepWeight(w, weId, setId, sign * settings.value.unit.stepKg(), allowNegative)
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
    fun addWarmup(weId: String) = edit { w -> WorkoutEditor.addWarmup(w, weId, settings.value.unit, settings.value.activeGym.barKg) }

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

    fun toggleSet(weId: String, setId: String) {
        val current = workout.value ?: return
        val index = current.exercises.indexOfFirst { it.id == weId }
        val set = current.exercises.getOrNull(index)?.sets?.firstOrNull { it.id == setId } ?: return
        val completing = !set.completed
        val updated = WorkoutEditor.toggleSetCompleted(current, weId, setId)
        edit { WorkoutEditor.toggleSetCompleted(it, weId, setId) }
        if (completing) {
            val (next, restSeconds) = WorkoutEditor.afterSetCompleted(updated, index)
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
    }

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)
    fun skipRest() = restTimer.skip()

    fun finish() {
        val current = workout.value ?: return
        if (partnerMode.value) {
            // Entreno de la pareja: se guarda aparte (no cuenta para tus récords ni estadísticas)
            viewModelScope.launch {
                val finished = WorkoutEditor.finish(current, System.currentTimeMillis())
                repo.setPartnerActive(null)
                partnerMode.value = false
                if (finished.exercises.isNotEmpty()) repo.saveWorkout(finished)
            }
            return
        }
        viewModelScope.launch {
            val metrics = metricsStore.metrics.value?.takeIf { it.workoutId == current.id }
            val ended = WorkoutEditor.finish(current, System.currentTimeMillis()).copy(
                avgHeartRate = metrics?.avgHeartRate ?: current.avgHeartRate,
                maxHeartRate = metrics?.maxHeartRate ?: current.maxHeartRate,
            )
            // Calorías totales y activas: las del reloj si las hay; si no, pulso o estimación personalizada
            val finished = energy.complete(ended, watchKcal = metrics?.calories ?: current.calories)
            restTimer.skip()
            repo.setActive(null)
            if (finished.exercises.isEmpty()) return@launch
            repo.saveWorkout(finished)
            val history = repo.historyOnce()
            _summary.value = FinishedSummary(
                finished,
                WorkoutStats.newRecordsIn(finished, history),
                WorkoutStats.compareWithPrevious(finished, history),
                WorkoutStats.imbalances(finished),
                NextTime.forWorkout(finished, settings.value.unit.stepKg()),
            )
            actions.exportToHealthConnectIfEnabled(finished)
        }
    }

    fun discard() = viewModelScope.launch {
        if (partnerMode.value) {
            repo.setPartnerActive(null)
            partnerMode.value = false
            return@launch
        }
        restTimer.skip()
        repo.setActive(null)
    }

    fun saveSummaryAsRoutine() {
        val s = _summary.value ?: return
        viewModelScope.launch { repo.saveRoutine(Routine.fromWorkout(s.workout)) }
    }

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
    }
}
