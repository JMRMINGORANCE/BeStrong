package com.bestrong.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val actions: WorkoutActions,
) : ViewModel() {
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
            val again = Routine.fromWorkout(workout).toWorkout(System.currentTimeMillis(), actions.prefillLookup())
            repo.setActive(again.copy(routineId = workout.routineId))
        }
        onStarted()
    }
}
