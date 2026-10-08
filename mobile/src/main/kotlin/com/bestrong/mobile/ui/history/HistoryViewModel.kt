package com.bestrong.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val history: StateFlow<List<Workout>> = repo.history

    val trainingDays: StateFlow<Set<LocalDate>> = repo.history
        .map { WorkoutStats.trainingDays(it, ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun recordsIn(workout: Workout): List<PersonalRecord> = WorkoutStats.newRecordsIn(workout, history.value)

    fun delete(id: String) = viewModelScope.launch { repo.deleteWorkout(id) }

    fun saveAsRoutine(workout: Workout) = viewModelScope.launch { repo.saveRoutine(Routine.fromWorkout(workout)) }

    /** Repite un entrenamiento pasado como entrenamiento activo. */
    fun repeat(workout: Workout, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeWorkout.value == null) {
            val again = Routine.fromWorkout(workout).toWorkout(System.currentTimeMillis(), container.prefillLookup())
            repo.setActive(again.copy(routineId = workout.routineId))
        }
        onStarted()
    }
}
