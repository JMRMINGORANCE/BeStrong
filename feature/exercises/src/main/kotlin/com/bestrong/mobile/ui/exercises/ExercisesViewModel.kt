package com.bestrong.mobile.ui.exercises

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.ExerciseProgressPoint
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.training.Injuries
import com.bestrong.core.training.Progression
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.util.newId
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.ExerciseImage
import com.bestrong.mobile.data.ExerciseMediaStore
import com.bestrong.mobile.data.MediaItem
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.ui.components.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseFilter(
    val query: String = "",
    val muscle: MuscleGroup? = null,
    val equipment: Equipment? = null,
    val favoritesOnly: Boolean = false,
    /** Solo peso corporal o bandas (entrenar en casa sin material). */
    val noEquipment: Boolean = false,
    val sort: ExerciseSort = ExerciseSort.RECENT,
)

/** Orden de la biblioteca: recientes primero (como Hevy), alfabético o los que más haces. */
enum class ExerciseSort { RECENT, NAME, MOST_USED }

data class ExerciseDetailState(
    val exercise: Exercise,
    val records: Map<RecordType, PersonalRecord>,
    val progress: List<ExerciseProgressPoint>,
    val recentSessions: List<Workout>,
    /** Variantes enlazadas (incluido el propio ejercicio). */
    val variants: List<Exercise> = emptyList(),
    val includeVariants: Boolean = false,
    /** Mejor peso por número de reps (curva de fuerza). */
    val strengthCurve: Map<Int, Double> = emptyMap(),
    /** Qué toca la próxima vez (sobrecarga progresiva con el incremento de este ejercicio). */
    val nextTarget: ProgressionSuggestion? = null,
    /** Notas de ese día por entreno (sin las fijas de la rutina o del programa, que se repiten siempre). */
    val sessionNotes: Map<String, String> = emptyMap(),
)

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    @ApplicationContext private val context: Context,
    private val media: ExerciseMediaStore,
    private val settingsStore: SettingsStore,
    private val planning: PlanningStore,
) : ViewModel() {

    val favorites: StateFlow<Set<String>> = settingsStore.settings.map { it.favoriteExercises }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value.favoriteExercises)

    fun toggleFavorite(id: String) = settingsStore.update { s ->
        s.copy(favoriteExercises = if (id in s.favoriteExercises) s.favoriteExercises - id else s.favoriteExercises + id)
    }

    // --- Imagen de wger y notas de técnica (foto/vídeo) -------------------------------------

    val mediaVersion: StateFlow<Int> = media.version
    fun image(exerciseId: String): ExerciseImage? = media.images[exerciseId]
    fun mediaItems(exerciseId: String): List<MediaItem> = media.items(exerciseId)
    fun note(exerciseId: String): String = media.note(exerciseId)
    fun saveNote(exerciseId: String, text: String) = viewModelScope.launch { media.saveNote(exerciseId, text) }
    fun addMedia(exerciseId: String, uri: Uri) = viewModelScope.launch { runCatching { media.add(exerciseId, uri) } }
    fun deleteMedia(item: MediaItem) = media.delete(item)

    val filter = MutableStateFlow(ExerciseFilter())

    /** Ejercicios usados recientemente, primero (como Hevy). */
    val results: StateFlow<List<Exercise>> = combine(repo.exercises, filter, repo.history, favorites, planning.state) { all, f, history, favs, plan ->
        val found = ExerciseLibrary.search(all, f.query, f.muscle, f.equipment) { it.displayName(context) }
            .filter { !f.favoritesOnly || it.id in favs }
            // Lesiones marcadas en Planificación: fuera los ejercicios que cargan esas zonas
            .filter { Injuries.isSafe(it, plan.injuries) }
            .filter { !f.noEquipment || it.equipment == Equipment.BODYWEIGHT || it.equipment == Equipment.BAND }
        when {
            f.sort == ExerciseSort.NAME -> {
                val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
                found.sortedWith(compareBy(collator) { it.displayName(context) })
            }
            f.sort == ExerciseSort.MOST_USED -> {
                val counts = history.flatMap { w -> w.exercises.map { it.exercise.id } }.groupingBy { it }.eachCount()
                found.sortedByDescending { counts[it.id] ?: 0 }
            }
            f.query.isBlank() && f.muscle == null && f.equipment == null -> {
                val recentIds = history.flatMap { w -> w.exercises.map { it.exercise.id } }.distinct().take(8)
                val recent = recentIds.mapNotNull { id -> found.firstOrNull { it.id == id } }
                recent + (found - recent.toSet())
            }
            else -> found
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val usageCount: StateFlow<Map<String, Int>> = repo.history.map { h ->
        h.flatMap { w -> w.exercises.map { it.exercise.id } }.groupingBy { it }.eachCount()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Por ejercicio: fecha de la última sesión y su mejor serie (para elegir de un vistazo). */
    val lastSession: StateFlow<Map<String, Pair<Long, WorkoutSet>>> = repo.history.map { h ->
        val result = HashMap<String, Pair<Long, WorkoutSet>>()
        h.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.forEach { w ->
            w.exercises.forEach { we ->
                val best = we.completedSets.filter { it.type != com.bestrong.core.model.SetType.WARMUP }
                    .maxWithOrNull(compareBy({ it.estimatedOneRepMax }, { it.weightKg }, { it.reps }, { it.durationSec }))
                if (best != null) result.putIfAbsent(we.exercise.id, w.startedAtEpochMs to best)
            }
        }
        result
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun detail(exerciseId: String, includeVariants: Boolean = false): StateFlow<ExerciseDetailState?> = combine(repo.exercises, repo.history, settingsStore.settings, repo.routines) { all, fullHistory, settings, routines ->
        val ex = all.firstOrNull { it.id == exerciseId } ?: return@combine null
        val family = ExerciseLibrary.familyOf(exerciseId)
        val history = if (includeVariants) WorkoutStats.mergeVariants(fullHistory, exerciseId, family) else fullHistory
        ExerciseDetailState(
            variants = family.mapNotNull { id -> all.firstOrNull { it.id == id } },
            includeVariants = includeVariants,
            strengthCurve = Strength.strengthCurve(exerciseId, history),
            exercise = ex,
            records = WorkoutStats.personalRecords(history)[exerciseId].orEmpty(),
            progress = WorkoutStats.exerciseProgress(exerciseId, history),
            recentSessions = history.filter { w -> w.exercises.any { it.exercise.id == exerciseId } }.take(20),
            nextTarget = Progression.suggest(ex, fullHistory, settings.stepKgFor(ex.id)),
            sessionNotes = sessionNotes(exerciseId, history, routines),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Una nota es «de ese día» si no es la de ninguna rutina para el ejercicio y cambia respecto a la
     * sesión anterior (las de los programas se copian igual en cada sesión).
     */
    private fun sessionNotes(exerciseId: String, history: List<Workout>, routines: List<com.bestrong.core.model.Routine>): Map<String, String> {
        val templates = routines.flatMap { it.exercises }.filter { it.exercise.id == exerciseId }.map { it.notes }.toSet()
        val withNotes = history.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.mapNotNull { w ->
            w.exercises.filter { it.exercise.id == exerciseId }.takeIf { it.isNotEmpty() }
                ?.let { list -> w.id to list.map { it.notes }.filter(String::isNotBlank).joinToString(" · ") }
        }
        // Una nota que se repite en muchas sesiones es la del programa (T1, AMRAP…), no una del día
        val repeated = withNotes.groupingBy { it.second }.eachCount().filterValues { it >= 3 }.keys
        return withNotes.withIndex().mapNotNull { (i, p) ->
            val (id, note) = p
            note.takeIf { it.isNotBlank() && it !in templates && it !in repeated && it != withNotes.getOrNull(i + 1)?.second }?.let { id to it }
        }.toMap()
    }

    fun createCustom(draft: Exercise): Exercise {
        val ex = draft.copy(id = "custom_${newId()}", isCustom = true)
        viewModelScope.launch { repo.saveCustomExercise(ex) }
        return ex
    }

    /** Corrige un ejercicio personalizado; las rutinas que lo usan se actualizan (el historial guarda lo que se hizo). */
    fun updateCustom(ex: Exercise) = viewModelScope.launch {
        repo.saveCustomExercise(ex)
        repo.routinesOnce().filter { r -> r.exercises.any { it.exercise.id == ex.id } }.forEach { r ->
            repo.saveRoutine(r.copy(exercises = r.exercises.map { if (it.exercise.id == ex.id) it.copy(exercise = ex) else it }))
        }
    }

    /** Cuántas rutinas usan el ejercicio (para avisar antes de borrarlo). */
    fun routinesUsing(exerciseId: String): Int = repo.routines.value.count { r -> r.exercises.any { it.exercise.id == exerciseId } }

    fun deleteCustom(id: String) = viewModelScope.launch { repo.deleteCustomExercise(id) }
}
