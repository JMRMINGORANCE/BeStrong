package com.bestrong.mobile.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.TrackingType
import com.bestrong.mobile.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutineEditorViewModel(private val container: AppContainer, routineId: String?) : ViewModel() {
    private val repo = container.repository

    private val _routine = MutableStateFlow(
        routineId?.let { id -> repo.routines.value.firstOrNull { it.id == id } } ?: Routine(name = ""),
    )
    val routine: StateFlow<Routine> = _routine.asStateFlow()
    val isNew = routineId == null

    fun rename(name: String) = _routine.update { it.copy(name = name) }
    fun setNotes(notes: String) = _routine.update { it.copy(notes = notes) }

    fun addExercises(ids: List<String>) {
        val lookup = repo.previousSetsLookup.value
        _routine.update { r ->
            r.copy(exercises = r.exercises + ids.mapNotNull { repo.exercise(it) }.map { ex ->
                val prev = lookup(ex.id)
                val sets = if (prev.isNotEmpty()) prev.map { RoutineSet(it.weightKg, it.reps, it.durationSec, it.type) }
                else List(3) { RoutineSet(reps = if (ex.tracking == TrackingType.DURATION) 0 else 10, durationSec = if (ex.tracking == TrackingType.DURATION) 30 else 0) }
                RoutineExercise(exercise = ex, sets = sets)
            })
        }
    }

    private fun updateExercise(id: String, block: (RoutineExercise) -> RoutineExercise) =
        _routine.update { r -> r.copy(exercises = r.exercises.map { if (it.id == id) block(it) else it }) }

    fun removeExercise(id: String) = _routine.update { r -> r.copy(exercises = r.exercises.filterNot { it.id == id }) }

    fun moveExercise(from: Int, to: Int) = _routine.update { r ->
        if (from !in r.exercises.indices || to !in r.exercises.indices) r
        else r.copy(exercises = r.exercises.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun addSet(id: String) = updateExercise(id) { re -> re.copy(sets = re.sets + (re.sets.lastOrNull() ?: RoutineSet())) }
    fun removeSet(id: String, index: Int) = updateExercise(id) { re -> re.copy(sets = re.sets.filterIndexed { i, _ -> i != index }) }
    fun updateSet(id: String, index: Int, block: (RoutineSet) -> RoutineSet) =
        updateExercise(id) { re -> re.copy(sets = re.sets.mapIndexed { i, s -> if (i == index) block(s) else s }) }
    fun setRest(id: String, seconds: Int) = updateExercise(id) { it.copy(restSeconds = seconds) }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val r = _routine.value
        repo.saveRoutine(r.copy(name = r.name.ifBlank { "Rutina sin nombre" }))
        onSaved()
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repo.deleteRoutine(_routine.value.id)
        onDeleted()
    }
}
