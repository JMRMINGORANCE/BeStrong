package com.bestrong.mobile.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.Progression
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.training.WarmupGenerator
import com.bestrong.mobile.WatchMetricsStore
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.workout.RestTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.ui.components.RestState
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

data class FinishedSummary(val workout: Workout, val records: List<PersonalRecord>)

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    settingsStore: SettingsStore,
    private val restTimer: RestTimerController,
    private val metricsStore: WatchMetricsStore,
    private val actions: WorkoutActions,
) : ViewModel() {

    val workout: StateFlow<Workout?> = repo.activeWorkout
    val previousSets: StateFlow<(String) -> List<WorkoutSet>> = repo.previousSetsLookup
    val settings: StateFlow<UserSettings> = settingsStore.settings
    val rest: StateFlow<RestState?> = restTimer.rest
    val watchMetrics: StateFlow<LiveMetrics?> = metricsStore.metrics

    /** Sugerencia de progresión por ejercicio (id de ejercicio → sugerencia). */
    val suggestions: StateFlow<Map<String, ProgressionSuggestion>> = combine(
        workout.map { w -> w?.exercises?.map { it.exercise }.orEmpty() }.distinctUntilChanged(),
        repo.history,
        settings,
    ) { exercises, history, s ->
        if (!s.progressionEnabled) emptyMap()
        else exercises.mapNotNull { ex -> Progression.suggest(ex, history, s.unit.stepKg())?.let { ex.id to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _summary = MutableStateFlow<FinishedSummary?>(null)
    val summary: StateFlow<FinishedSummary?> = _summary.asStateFlow()

    /** Índice del ejercicio al que desplazar la lista (siguiente de la superserie, etc.). */
    private val _focusExercise = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusExercise: SharedFlow<Int> = _focusExercise

    private fun edit(block: (Workout) -> Workout) {
        viewModelScope.launch { repo.updateActive(block) }
    }

    fun rename(name: String) = edit { it.copy(name = name.ifBlank { it.name }) }

    fun addExercises(ids: List<String>) {
        val exercises = ids.mapNotNull { repo.exercise(it) }
        val lookup = actions.prefillLookup()
        edit { WorkoutEditor.addExercises(it, exercises, lookup) }
    }

    fun removeExercise(weId: String) = edit { WorkoutEditor.removeExercise(it, weId) }
    fun moveExercise(from: Int, to: Int) = edit { WorkoutEditor.moveExercise(it, from, to) }
    fun setRestSeconds(weId: String, seconds: Int) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(restSeconds = seconds) } }
    fun setExerciseNotes(weId: String, notes: String) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(notes = notes) } }
    fun addSet(weId: String) = edit { WorkoutEditor.addSet(it, weId) }
    fun removeSet(weId: String, setId: String) = edit { WorkoutEditor.removeSet(it, weId, setId) }
    fun setType(weId: String, setId: String, type: SetType) = edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(type = type) } }
    fun setRpe(weId: String, setId: String, rpe: Double?) = edit { WorkoutEditor.updateSet(it, weId, setId) { s -> s.copy(rpe = rpe) } }
    fun linkWithNext(weId: String) = edit { WorkoutEditor.linkWithNext(it, weId) }
    fun unlinkSuperset(weId: String) = edit { WorkoutEditor.unlinkSuperset(it, weId) }

    fun updateSet(weId: String, setId: String, weightKg: Double? = null, reps: Int? = null, duration: Int? = null) = edit { w ->
        WorkoutEditor.updateSet(w, weId, setId) { s ->
            s.copy(weightKg = weightKg ?: s.weightKg, reps = reps ?: s.reps, durationSec = duration ?: s.durationSec)
        }
    }

    /** Añade series de aproximación antes de la primera serie de trabajo. */
    fun addWarmup(weId: String) = edit { w ->
        WorkoutEditor.updateExercise(w, weId) { we ->
            val working = we.sets.firstOrNull { it.type != SetType.WARMUP }?.weightKg ?: return@updateExercise we
            val warmups = WarmupGenerator.generate(working, settings.value.unit)
            we.copy(sets = warmups + we.sets.filter { it.type != SetType.WARMUP || it.completed })
        }
    }

    /** Aplica la sugerencia de progresión a las series pendientes. */
    fun applySuggestion(weId: String, suggestion: ProgressionSuggestion) = edit { w ->
        WorkoutEditor.updateExercise(w, weId) { we ->
            var i = 0
            we.copy(sets = we.sets.map { s ->
                if (s.completed || s.type == SetType.WARMUP) s
                else suggestion.sets.getOrNull(i++)?.let { sug -> s.copy(weightKg = sug.weightKg, reps = sug.reps, durationSec = sug.durationSec) } ?: s
            })
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
            if (next != null && next != index) _focusExercise.tryEmit(next)
        }
    }

    fun adjustRest(deltaSeconds: Int) = restTimer.adjust(deltaSeconds)
    fun skipRest() = restTimer.skip()

    fun finish() {
        val current = workout.value ?: return
        viewModelScope.launch {
            val metrics = metricsStore.metrics.value?.takeIf { it.workoutId == current.id }
            val finished = WorkoutEditor.finish(current, System.currentTimeMillis()).copy(
                avgHeartRate = metrics?.avgHeartRate ?: current.avgHeartRate,
                maxHeartRate = metrics?.maxHeartRate ?: current.maxHeartRate,
                calories = metrics?.calories ?: current.calories,
            )
            restTimer.skip()
            repo.setActive(null)
            if (finished.exercises.isEmpty()) return@launch
            repo.saveWorkout(finished)
            val history = repo.historyOnce()
            _summary.value = FinishedSummary(finished, WorkoutStats.newRecordsIn(finished, history))
            actions.exportToHealthConnectIfEnabled(finished)
        }
    }

    fun discard() = viewModelScope.launch {
        restTimer.skip()
        repo.setActive(null)
    }

    fun saveSummaryAsRoutine() {
        val s = _summary.value ?: return
        viewModelScope.launch { repo.saveRoutine(Routine.fromWorkout(s.workout)) }
    }

    fun dismissSummary() {
        _summary.value = null
    }
}
