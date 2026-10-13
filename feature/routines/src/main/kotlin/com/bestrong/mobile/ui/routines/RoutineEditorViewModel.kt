package com.bestrong.mobile.ui.routines

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.training.Tempo
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.shared.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    /** Argumento de navegación "id" (null = rutina nueva). */
    private val routineId: String? = savedStateHandle.get<String>("id")

    private val _routine = MutableStateFlow(
        routineId?.let { id -> repo.routines.value.firstOrNull { it.id == id } } ?: Routine(name = ""),
    )
    val routine: StateFlow<Routine> = _routine.asStateFlow()
    val isNew = routineId == null

    /** Rutina tal y como se abrió, para saber si hay cambios sin guardar al salir. */
    private var original: Routine = _routine.value
    val hasChanges: Boolean get() = _routine.value != original

    init {
        // Si la app se reabrió desde cero, las rutinas aún no estaban cargadas: las leemos de la BD
        if (routineId != null && _routine.value.id != routineId) {
            viewModelScope.launch {
                repo.routine(routineId)?.let {
                    _routine.value = it
                    original = it
                }
            }
        }
    }

    /** Une el ejercicio con el siguiente en superserie (o lo saca si ya lo está). */
    fun toggleSupersetWithNext(id: String) = _routine.update { r ->
        val i = r.exercises.indexOfFirst { it.id == id }
        if (i < 0 || i >= r.exercises.lastIndex) return@update r
        val a = r.exercises[i]
        val b = r.exercises[i + 1]
        if (a.supersetId != null && a.supersetId == b.supersetId) {
            r.copy(exercises = r.exercises.map { if (it.id == b.id) it.copy(supersetId = null) else it })
                .let { nr -> if (nr.exercises.count { it.supersetId == a.supersetId } <= 1) nr.copy(exercises = nr.exercises.map { if (it.supersetId == a.supersetId) it.copy(supersetId = null) else it }) else nr }
        } else {
            val group = a.supersetId ?: b.supersetId ?: com.bestrong.core.util.newId()
            r.copy(exercises = r.exercises.map { if (it.id == a.id || it.id == b.id) it.copy(supersetId = group) else it })
        }
    }

    fun rename(name: String) = _routine.update { it.copy(name = name) }
    fun setNotes(notes: String) = _routine.update { it.copy(notes = notes) }
    fun setEmoji(emoji: String?) = _routine.update { it.copy(emoji = emoji) }
    fun setFolder(folder: String) = _routine.update { it.copy(folder = folder.trim().ifEmpty { null }) }

    /** Carpetas que ya existen, para elegirlas sin volver a escribirlas. */
    val folders: List<String> get() = repo.routines.value.mapNotNull { it.folder?.takeIf(String::isNotBlank) }.distinct().sorted()

    fun addExercises(ids: List<String>) {
        val lookup = repo.previousSetsLookup.value
        _routine.update { r ->
            r.copy(
                exercises = r.exercises + ids.mapNotNull { repo.exercise(it) }.map { ex ->
                    val prev = lookup(ex.id)
                    val sets = if (prev.isNotEmpty()) {
                        prev.map { RoutineSet(it.weightKg, it.reps, it.durationSec, it.type) }
                    } else if (ex.tracking == TrackingType.CARDIO) {
                        listOf(RoutineSet(reps = 0, durationSec = TrackingType.DEFAULT_CARDIO_SEC))
                    } else {
                        List(3) { RoutineSet(reps = if (ex.tracking == TrackingType.DURATION) 0 else 10, durationSec = if (ex.tracking == TrackingType.DURATION) 30 else 0) }
                    }
                    RoutineExercise(exercise = ex, sets = sets)
                },
            )
        }
    }

    private fun updateExercise(id: String, block: (RoutineExercise) -> RoutineExercise) =
        _routine.update { r -> r.copy(exercises = r.exercises.map { if (it.id == id) block(it) else it }) }

    fun removeExercise(id: String) = _routine.update { r -> r.copy(exercises = r.exercises.filterNot { it.id == id }) }

    fun moveExercise(from: Int, to: Int) = _routine.update { r ->
        if (from !in r.exercises.indices || to !in r.exercises.indices) {
            r
        } else {
            r.copy(exercises = r.exercises.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun addSet(id: String) = updateExercise(id) { re -> re.copy(sets = re.sets + (re.sets.lastOrNull() ?: RoutineSet())) }
    fun removeSet(id: String, index: Int) = updateExercise(id) { re -> re.copy(sets = re.sets.filterIndexed { i, _ -> i != index }) }
    fun updateSet(id: String, index: Int, block: (RoutineSet) -> RoutineSet) =
        updateExercise(id) { re -> re.copy(sets = re.sets.mapIndexed { i, s -> if (i == index) block(s) else s }) }
    fun setRest(id: String, seconds: Int) = updateExercise(id) { it.copy(restSeconds = seconds) }
    fun setExerciseNotes(id: String, notes: String) = updateExercise(id) { it.copy(notes = notes) }

    /** Tempo («3-1-1-0»); vacío o no válido lo quita. */
    fun setTempo(id: String, tempo: String) = updateExercise(id) { it.copy(tempo = Tempo.parse(tempo)?.toString()) }

    /** Tipo de serie como en el entreno: normal → calentamiento → drop → al fallo → normal. */
    fun cycleSetType(id: String, index: Int) = updateSet(id, index) { s ->
        s.copy(
            type = when (s.type) {
                SetType.NORMAL -> SetType.WARMUP
                SetType.WARMUP -> SetType.DROP
                SetType.DROP -> SetType.FAILURE
                else -> SetType.NORMAL
            },
        )
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val r = _routine.value
        repo.saveRoutine(r.copy(name = r.name.ifBlank { context.getString(R.string.routine_untitled) }))
        onSaved()
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repo.deleteRoutine(_routine.value.id)
        onDeleted()
    }
}
