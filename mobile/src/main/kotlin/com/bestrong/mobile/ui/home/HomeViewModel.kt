package com.bestrong.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.sync.WatchSync
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val actions: WorkoutActions,
    private val watchSync: WatchSync,
) : ViewModel() {

    val routines: StateFlow<List<Routine>> = repo.routines
    val active: StateFlow<Workout?> = repo.activeWorkout
    val history: StateFlow<List<Workout>> = repo.history

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun startEmpty(onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeWorkout.value == null) repo.setActive(WorkoutEditor.empty(System.currentTimeMillis()))
        onStarted()
    }

    fun startRoutine(routine: Routine, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeWorkout.value != null) {
            _messages.tryEmit("Ya tienes un entrenamiento en curso")
            onStarted()
            return@launch
        }
        repo.setActive(routine.toWorkout(System.currentTimeMillis(), actions.prefillLookup()))
        onStarted()
    }

    /** Desde el widget: empezar una rutina por id. */
    fun startRoutineById(id: String, onStarted: () -> Unit) = viewModelScope.launch {
        val routine = repo.routine(id)
        if (routine == null) {
            _messages.tryEmit("La rutina ya no existe")
            return@launch
        }
        startRoutine(routine, onStarted)
    }

    fun startOnWatch(routine: Routine?) = viewModelScope.launch {
        watchSync.pushRoutines(repo.routines.value)
        val ok = watchSync.startOnWatch(routine?.id)
        _messages.tryEmit(if (ok) "Abriendo BeStrong en tu Galaxy Watch" else "No se encontró ningún reloj conectado")
    }

    fun duplicate(routine: Routine) = viewModelScope.launch {
        repo.saveRoutine(routine.copy(id = com.bestrong.core.util.newId(), name = "${routine.name} (copia)"))
    }

    fun delete(routine: Routine) = viewModelScope.launch { repo.deleteRoutine(routine.id) }

    fun move(routine: Routine, delta: Int) = viewModelScope.launch {
        val list = repo.routines.value.toMutableList()
        val i = list.indexOfFirst { it.id == routine.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@launch
        list.add(j, list.removeAt(i))
        list.forEachIndexed { pos, r -> repo.saveRoutine(r.copy(position = pos)) }
    }
}
