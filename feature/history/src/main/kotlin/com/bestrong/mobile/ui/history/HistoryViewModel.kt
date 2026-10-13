package com.bestrong.mobile.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.ui.components.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    private val actions: WorkoutActions,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val history: StateFlow<List<Workout>> = repo.history

    val trainingDays: StateFlow<Set<LocalDate>> = repo.history
        .map { WorkoutStats.trainingDays(it, ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Búsqueda por entreno, ejercicio o nota. */
    val query = MutableStateFlow("")

    /** Modo pareja: mostrar también los entrenos de la otra persona. */
    val showPartner = MutableStateFlow(false)
    val partnerHistory: StateFlow<List<Workout>> = repo.partnerHistory

    val filtered: StateFlow<List<Workout>> = combine(repo.history, repo.partnerHistory, query, showPartner) { h, p, q, sp ->
        val all = if (sp) (h + p).sortedByDescending { it.startedAtEpochMs } else h
        WorkoutStats.search(all, q) { it.displayName(context) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entrenos por día del año actual (mapa de calor). */
    val heatmap: StateFlow<Map<LocalDate, Int>> = repo.history
        .map { WorkoutStats.dailyCounts(it, LocalDate.now().year, ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun recordsIn(workout: Workout): List<PersonalRecord> = WorkoutStats.newRecordsIn(workout, history.value)

    /** Último entreno borrado, para poder deshacer. */
    private val _deleted = MutableStateFlow<Workout?>(null)
    val deleted: StateFlow<Workout?> = _deleted

    fun delete(id: String) = viewModelScope.launch {
        _deleted.value = repo.history.value.firstOrNull { it.id == id }
        repo.deleteWorkout(id)
    }

    fun undoDelete() = viewModelScope.launch {
        _deleted.value?.let { repo.saveWorkout(it) }
        _deleted.value = null
    }

    fun clearDeleted() {
        _deleted.value = null
    }

    /** Corrige un entreno pasado; si se queda sin series, se borra (con deshacer). */
    fun editWorkout(workout: Workout, block: (Workout) -> Workout) = viewModelScope.launch {
        val edited = WorkoutEditor.cleanupFinished(block(workout)).copy(updatedAtEpochMs = System.currentTimeMillis())
        if (edited.exercises.isEmpty()) {
            _deleted.value = workout
            repo.deleteWorkout(workout.id)
        } else {
            repo.saveWorkout(edited)
        }
    }

    val routines: StateFlow<List<Routine>> = repo.routines

    /**
     * Registra un entreno pasado a partir de una rutina: series completadas con los pesos de la
     * última vez (o los de la rutina). Devuelve el id para abrirlo y ajustar lo que haga falta.
     */
    fun logPast(routine: Routine, startMs: Long, endMs: Long, onLogged: (String) -> Unit) = viewModelScope.launch {
        val w = routine.toWorkout(startMs, actions.prefillLookup())
        val done = w.copy(
            finishedAtEpochMs = endMs,
            updatedAtEpochMs = System.currentTimeMillis(),
            exercises = w.exercises.map { we -> we.copy(sets = we.sets.map { it.copy(completed = true, completedAtEpochMs = endMs) }) },
        )
        repo.saveWorkout(done)
        onLogged(done.id)
    }

    /** Cambia la fecha, la hora y la duración de un entreno pasado. */
    fun retime(workout: Workout, startMs: Long, endMs: Long) = editWorkout(workout) { w ->
        val shift = startMs - w.startedAtEpochMs
        w.copy(
            startedAtEpochMs = startMs,
            finishedAtEpochMs = endMs,
            exercises = w.exercises.map { we ->
                we.copy(sets = we.sets.map { s -> s.copy(completedAtEpochMs = s.completedAtEpochMs?.plus(shift), startedAtEpochMs = s.startedAtEpochMs?.plus(shift)) })
            },
        )
    }

    /** Añade una serie hecha (copia de la última) a un ejercicio de un entreno pasado. */
    fun addPastSet(workout: Workout, weId: String) = editWorkout(workout) { w ->
        val added = WorkoutEditor.addSet(w, weId)
        WorkoutEditor.updateExercise(added, weId) { we -> we.copy(sets = we.sets.dropLast(1) + we.sets.last().copy(completed = true)) }
    }

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
