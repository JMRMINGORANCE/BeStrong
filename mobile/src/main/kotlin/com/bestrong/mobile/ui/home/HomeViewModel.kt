package com.bestrong.mobile.ui.home

import com.bestrong.mobile.ui.components.uiLanguage
import com.bestrong.core.library.TrainingProgram
import com.bestrong.core.library.Programs
import com.bestrong.mobile.ui.components.defaultWorkoutName
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.bestrong.mobile.R
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val routines: StateFlow<List<Routine>> = repo.routines
    val active: StateFlow<Workout?> = repo.activeWorkout
    val history: StateFlow<List<Workout>> = repo.history

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun startEmpty(onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeWorkout.value == null) repo.setActive(WorkoutEditor.empty(System.currentTimeMillis(), name = defaultWorkoutName(context, System.currentTimeMillis())))
        onStarted()
    }

    fun startRoutine(routine: Routine, onStarted: () -> Unit) = viewModelScope.launch {
        if (repo.activeWorkout.value != null) {
            _messages.tryEmit(context.getString(R.string.msg_workout_already_active))
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
            _messages.tryEmit(context.getString(R.string.msg_routine_missing))
            return@launch
        }
        startRoutine(routine, onStarted)
    }

    fun startOnWatch(routine: Routine?) = viewModelScope.launch {
        watchSync.pushRoutines(repo.routines.value)
        val ok = watchSync.startOnWatch(routine?.id)
        _messages.tryEmit(context.getString(if (ok) R.string.msg_opening_on_watch else R.string.msg_no_watch))
    }

    fun duplicate(routine: Routine) = viewModelScope.launch {
        repo.saveRoutine(routine.copy(id = com.bestrong.core.util.newId(), name = context.getString(R.string.routine_copy_name, routine.name)))
    }

    val programs: List<TrainingProgram> = Programs.all

    /** Crea las rutinas del programa elegido en el idioma de la app. */
    fun addProgram(program: TrainingProgram) = viewModelScope.launch {
        val routines = program.toRoutines(context.uiLanguage())
        repo.addRoutines(routines)
        _messages.tryEmit(context.getString(R.string.msg_program_added, program.name(context.uiLanguage()), routines.size))
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
