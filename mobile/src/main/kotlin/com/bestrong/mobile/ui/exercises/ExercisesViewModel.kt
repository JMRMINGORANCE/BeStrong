package com.bestrong.mobile.ui.exercises

import com.bestrong.mobile.ui.components.displayName
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.ExerciseProgressPoint
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.util.newId
import com.bestrong.mobile.data.BeStrongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseFilter(val query: String = "", val muscle: MuscleGroup? = null, val equipment: Equipment? = null)

data class ExerciseDetailState(
    val exercise: Exercise,
    val records: Map<RecordType, PersonalRecord>,
    val progress: List<ExerciseProgressPoint>,
    val recentSessions: List<Workout>,
)

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val filter = MutableStateFlow(ExerciseFilter())

    /** Ejercicios usados recientemente, primero (como Hevy). */
    val results: StateFlow<List<Exercise>> = combine(repo.exercises, filter, repo.history) { all, f, history ->
        val found = ExerciseLibrary.search(all, f.query, f.muscle, f.equipment) { it.displayName(context) }
        if (f.query.isBlank() && f.muscle == null && f.equipment == null) {
            val recentIds = history.flatMap { w -> w.exercises.map { it.exercise.id } }.distinct().take(8)
            val recent = recentIds.mapNotNull { id -> found.firstOrNull { it.id == id } }
            recent + (found - recent.toSet())
        } else found
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val usageCount: StateFlow<Map<String, Int>> = combine(repo.history, filter) { h, _ ->
        h.flatMap { w -> w.exercises.map { it.exercise.id } }.groupingBy { it }.eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun detail(exerciseId: String): StateFlow<ExerciseDetailState?> = combine(repo.exercises, repo.history) { all, history ->
        val ex = all.firstOrNull { it.id == exerciseId } ?: return@combine null
        ExerciseDetailState(
            exercise = ex,
            records = WorkoutStats.personalRecords(history)[exerciseId].orEmpty(),
            progress = WorkoutStats.exerciseProgress(exerciseId, history),
            recentSessions = history.filter { w -> w.exercises.any { it.exercise.id == exerciseId } }.take(20),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createCustom(name: String, muscle: MuscleGroup, equipment: Equipment, tracking: TrackingType): Exercise {
        val ex = Exercise(id = "custom_${newId()}", name = name.trim(), primaryMuscle = muscle, equipment = equipment, tracking = tracking, isCustom = true)
        viewModelScope.launch { repo.saveCustomExercise(ex) }
        return ex
    }

    fun deleteCustom(id: String) = viewModelScope.launch { repo.deleteCustomExercise(id) }
}
