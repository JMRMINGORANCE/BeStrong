package com.bestrong.mobile.ui.workout

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.AppContainer
import com.bestrong.mobile.ui.components.RestState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FinishedSummary(val workout: Workout, val records: List<PersonalRecord>)

class ActiveWorkoutViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val workout: StateFlow<Workout?> = repo.activeWorkout
    val previousSets: StateFlow<(String) -> List<WorkoutSet>> = repo.previousSetsLookup
    val settings = container.settings.settings

    private val _rest = MutableStateFlow<RestState?>(null)
    val rest: StateFlow<RestState?> = _rest.asStateFlow()

    private val _summary = MutableStateFlow<FinishedSummary?>(null)
    val summary: StateFlow<FinishedSummary?> = _summary.asStateFlow()

    private var restJob: Job? = null

    private fun edit(block: (Workout) -> Workout) {
        viewModelScope.launch { repo.updateActive(block) }
    }

    fun rename(name: String) = edit { it.copy(name = name.ifBlank { it.name }) }
    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    fun addExercises(ids: List<String>) {
        val exercises = ids.mapNotNull { repo.exercise(it) }
        val lookup = previousSets.value
        edit { WorkoutEditor.addExercises(it, exercises, lookup) }
    }

    fun removeExercise(weId: String) = edit { WorkoutEditor.removeExercise(it, weId) }
    fun moveExercise(from: Int, to: Int) = edit { WorkoutEditor.moveExercise(it, from, to) }
    fun setRestSeconds(weId: String, seconds: Int) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(restSeconds = seconds) } }
    fun setExerciseNotes(weId: String, notes: String) = edit { w -> WorkoutEditor.updateExercise(w, weId) { it.copy(notes = notes) } }
    fun addSet(weId: String) = edit { WorkoutEditor.addSet(it, weId) }
    fun removeSet(weId: String, setId: String) = edit { WorkoutEditor.removeSet(it, weId, setId) }
    fun cycleSetType(weId: String, setId: String) = edit { WorkoutEditor.cycleSetType(it, weId, setId) }

    fun updateSet(weId: String, setId: String, weight: Double? = null, reps: Int? = null, duration: Int? = null) = edit { w ->
        WorkoutEditor.updateSet(w, weId, setId) { s ->
            s.copy(weightKg = weight ?: s.weightKg, reps = reps ?: s.reps, durationSec = duration ?: s.durationSec)
        }
    }

    fun toggleSet(weId: String, setId: String) {
        val current = workout.value ?: return
        val we = current.exercises.firstOrNull { it.id == weId } ?: return
        val set = we.sets.firstOrNull { it.id == setId } ?: return
        edit { WorkoutEditor.toggleSetCompleted(it, weId, setId) }
        if (!set.completed && we.restSeconds > 0) startRest(we.restSeconds)
    }

    fun startRest(seconds: Int) {
        _rest.value = RestState(System.currentTimeMillis() + seconds * 1000L, seconds)
        scheduleRestEnd()
    }

    fun adjustRest(deltaSeconds: Int) {
        val r = _rest.value ?: return
        val endsAt = (r.endsAtEpochMs + deltaSeconds * 1000L).coerceAtLeast(System.currentTimeMillis())
        _rest.value = RestState(endsAt, (r.totalSeconds + deltaSeconds).coerceAtLeast(1))
        scheduleRestEnd()
    }

    fun skipRest() {
        restJob?.cancel()
        _rest.value = null
    }

    private fun scheduleRestEnd() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            val r = _rest.value ?: return@launch
            delay((r.endsAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0))
            _rest.value = null
            if (settings.value.vibrateOnRestEnd) vibrate()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            container.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            container.context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
    }

    fun finish() {
        val current = workout.value ?: return
        viewModelScope.launch {
            val finished = WorkoutEditor.finish(current, System.currentTimeMillis())
            skipRest()
            repo.setActive(null)
            if (finished.exercises.isEmpty()) return@launch
            repo.saveWorkout(finished)
            val history = repo.historyOnce()
            _summary.value = FinishedSummary(finished, WorkoutStats.newRecordsIn(finished, history))
            container.exportToHealthConnectIfEnabled(finished)
        }
    }

    fun discard() = viewModelScope.launch {
        skipRest()
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
