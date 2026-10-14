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
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.ProgramTexts
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
    /** Ver también los ejercicios ocultos (para volver a mostrarlos). */
    val showHidden: Boolean = false,
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
    /** RPE medio por sesión (fecha, RPE, mejor 1RM del día), para ver si cada vez cuesta más. */
    val rpeTrend: List<Triple<Long, Double, Double>> = emptyList(),
    /** Mejor valor por sesión para el objetivo (1RM, reps o segundos según el ejercicio). */
    val goalPoints: List<Pair<Long, Double>> = emptyList(),
    /** 1RM estimado reciente (con las reps en recámara del RPE), el mismo que usa el entreno. */
    val recentOneRepMax: Double? = null,
    /** Cardio: la más larga en distancia y en tiempo, y el mejor ritmo. */
    val cardioBests: com.bestrong.core.stats.CardioBests? = null,
)

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val repo: BeStrongRepository,
    @ApplicationContext private val context: Context,
    private val media: ExerciseMediaStore,
    private val settingsStore: SettingsStore,
    private val planning: PlanningStore,
    private val actions: com.bestrong.mobile.WorkoutActions,
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

    /**
     * La biblioteca debe volver a la lista (cerrando la ficha que hubiera abierta). Es un estado, no un
     * evento: la pantalla puede suscribirse después de que se pida y lo limpia al atenderlo.
     */
    private val _showList = MutableStateFlow(false)
    val showList: StateFlow<Boolean> = _showList
    fun listShown() {
        _showList.value = false
    }

    /** Abre la biblioteca con solo ese músculo (nombre del enum; se ignora si no existe). Se mantiene el orden elegido. */
    fun showMuscle(name: String) {
        val muscle = MuscleGroup.entries.firstOrNull { it.name == name } ?: return
        filter.value = filter.value.copy(query = "", muscle = muscle, equipment = null, favoritesOnly = false, noEquipment = false, showHidden = false)
        _showList.value = true
    }

    /** Rangos de repeticiones por ejercicio («8-12»). */
    val repRanges: StateFlow<Map<String, String>> = settingsStore.settings.map { it.exerciseRepRange }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value.exerciseRepRange)

    val plateauSnoozed: StateFlow<Map<String, Double>> = settingsStore.settings.map { it.plateauSnoozed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value.plateauSnoozed)

    /** Probar otro rango por estancamiento: el aviso descansa hasta dar tiempo al nuevo rango ([sessions]: las que hay). */
    fun tryRangeForPlateau(exerciseId: String, range: String, sessions: Int) {
        setRepRange(exerciseId, range)
        settingsStore.update { s -> s.copy(plateauSnoozed = s.plateauSnoozed + (exerciseId to sessions.toDouble())) }
    }

    fun setRepRange(exerciseId: String, range: String?) = settingsStore.update { s ->
        s.copy(exerciseRepRange = if (range == null) s.exerciseRepRange - exerciseId else s.exerciseRepRange + (exerciseId to range))
    }

    /** Objetivos de 1RM por ejercicio (kg). */
    val goals: StateFlow<Map<String, Double>> = settingsStore.settings.map { it.exerciseGoalsKg }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value.exerciseGoalsKg)

    /** Mancuernas con «suma de las dos»: el objetivo se ve y se escribe como el total del par. */
    /** Ajustes en vivo (para que la tabla de cargas cambie con el gimnasio o las mancuernas sumadas). */
    val settings: StateFlow<com.bestrong.mobile.data.UserSettings> = settingsStore.settings

    /** Peso que se puede usar para [kg] en este ejercicio (gimnasio activo o su incremento) con los ajustes [s]. */
    fun roundLoad(exercise: Exercise, kg: Double, s: com.bestrong.mobile.data.UserSettings): Double {
        return com.bestrong.core.stats.RpeLoad.roundToLoadable(kg, s.unit, s.stepKgFor(exercise.id), s.gymFor(exercise.id), exercise.equipment)
    }

    fun weightFactor(exercise: Exercise): Int = if (settingsStore.settings.value.dumbbellTotal && exercise.equipment == Equipment.DUMBBELL) 2 else 1

    /** null quita el objetivo. */
    fun setGoal(exerciseId: String, kg: Double?) = settingsStore.update { s ->
        s.copy(exerciseGoalsKg = if (kg == null) s.exerciseGoalsKg - exerciseId else s.exerciseGoalsKg + (exerciseId to kg))
    }

    /** Ejercicios ocultos por el usuario. */
    val hidden: StateFlow<Set<String>> = settingsStore.settings.map { it.hiddenExercises }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsStore.settings.value.hiddenExercises)

    /** Cuántos ocultos existen todavía (los personalizados borrados no cuentan). */
    val hiddenCount: StateFlow<Int> = combine(hidden, repo.exercises) { h, all -> all.count { it.id in h } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggleHidden(id: String) = settingsStore.update { s ->
        s.copy(hiddenExercises = if (id in s.hiddenExercises) s.hiddenExercises - id else s.hiddenExercises + id)
    }

    private val marks = combine(favorites, hidden) { f, h -> f to h }

    /** Ejercicios usados recientemente, primero (como Hevy). */
    val results: StateFlow<List<Exercise>> = combine(repo.exercises, filter, repo.history, marks, planning.state) { all, f, history, (favs, hiddenIds), plan ->
        val found = ExerciseLibrary.search(all, f.query, f.muscle, f.equipment) { it.displayName(context) }
            .filter { !f.favoritesOnly || it.id in favs }
            // Ocultos: fuera de la lista, pero si los buscas por nombre aparecen (si no, parecería que no existen)
            .filter { if (f.showHidden) it.id in hiddenIds else it.id !in hiddenIds || f.query.isNotBlank() }
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

    fun detail(exerciseId: String, includeVariants: Boolean = false): StateFlow<ExerciseDetailState?> = combine(repo.exercises, repo.history, settingsStore.settings, repo.routines, planning.state) { all, fullHistory, settings, routines, _ ->
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
            // Solo el propio ejercicio (no las variantes), como en el entreno
            recentOneRepMax = com.bestrong.core.stats.RpeLoad.recentOneRepMax(exerciseId, fullHistory, System.currentTimeMillis()),
            recentSessions = history.filter { w -> w.exercises.any { it.exercise.id == exerciseId } }.take(20),
            // Como en el entreno: ajustada al gimnasio y al tope semanal (con su aviso)
            nextTarget = Progression.suggest(ex, fullHistory, settings.stepKgFor(ex.id), repRange = settings.repRangeFor(ex.id))
                ?.let { sug -> sug.copy(sets = actions.loadable(ex, sug.sets, sug.kind, history = fullHistory), cappedByWeek = actions.cappedByWeek(ex, sug.sets, fullHistory)) },
            sessionNotes = sessionNotes(exerciseId, history, routines),
            rpeTrend = Strength.rpeTrend(history, setOf(exerciseId)),
            goalPoints = com.bestrong.core.stats.ExerciseGoals.points(history, exerciseId, ex.tracking),
            cardioBests = if (ex.tracking == com.bestrong.core.model.TrackingType.CARDIO) com.bestrong.core.stats.CardioBestsCalc.of(exerciseId, history).takeUnless { it.isEmpty } else null,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** La frase de AMRAP en todos los idiomas de la app: las sesiones pudieron hacerse con otro idioma. */
    private val amrapNotes: List<String> by lazy {
        APP_LOCALES.map { tag ->
            val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(tag)) }
            context.createConfigurationContext(config).getString(com.bestrong.mobile.shared.R.string.program_amrap_note)
        } + ProgramTexts.ES.amrapNote + ProgramTexts.EN.amrapNote
    }

    /**
     * Una nota es «de ese día» si no es la de ninguna rutina para el ejercicio y cambia respecto a la
     * sesión anterior (las de los programas se copian igual en cada sesión).
     */
    private fun sessionNotes(exerciseId: String, history: List<Workout>, routines: List<com.bestrong.core.model.Routine>): Map<String, String> {
        val templates = routines.flatMap { it.exercises }.filter { it.exercise.id == exerciseId }.map { it.notes }.toSet()
        // (id, nota, ¿de programa?), de la sesión más reciente a la más antigua
        val withNotes = history.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.mapNotNull { w ->
            w.exercises.filter { it.exercise.id == exerciseId }.takeIf { it.isNotEmpty() }?.let { list ->
                Triple(w.id, list.map { it.notes }.filter(String::isNotBlank).joinToString(" · "), w.routineId?.startsWith(Periodization.ROUTINE_PREFIX) == true)
            }
        }
        // En los programas, la nota lleva el nivel (T1/T2/T3) y la frase de AMRAP: solo cuenta lo que añadió el usuario
        val generated = amrapNotes
        val cleaned = withNotes.map { (id, note, fromProgram) -> id to if (fromProgram) ProgramTexts.userPart(note, generated) else note }
        return cleaned.withIndex().mapNotNull { (i, t) ->
            val (id, note) = t
            note.takeIf { it.isNotBlank() && it !in templates && it != cleaned.getOrNull(i + 1)?.second }?.let { id to it }
        }.toMap()
    }

    fun createCustom(draft: Exercise): Exercise {
        val ex = draft.copy(id = "custom_${newId()}", isCustom = true)
        viewModelScope.launch { repo.saveCustomExercise(ex) }
        return ex
    }

    /** Corrige un ejercicio personalizado; las rutinas que lo usan se actualizan (el historial guarda lo que se hizo). */
    fun updateCustom(ex: Exercise) = viewModelScope.launch {
        val previous = repo.exercises.value.firstOrNull { it.id == ex.id }
        repo.saveCustomExercise(ex)
        // El objetivo cambia de significado (kg ↔ reps ↔ segundos, o par ↔ una mancuerna): se quita
        val dumbbellChange = previous != null && (previous.equipment == Equipment.DUMBBELL) != (ex.equipment == Equipment.DUMBBELL)
        if (previous != null && (previous.tracking != ex.tracking || dumbbellChange)) {
            settingsStore.update { s -> if (ex.id in s.exerciseGoalsKg) s.copy(exerciseGoalsKg = s.exerciseGoalsKg - ex.id) else s }
        }
        // El rango de repeticiones solo se usa con peso × reps
        if (previous?.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS && ex.tracking != com.bestrong.core.model.TrackingType.WEIGHT_REPS) {
            settingsStore.update { s -> if (ex.id in s.exerciseRepRange) s.copy(exerciseRepRange = s.exerciseRepRange - ex.id) else s }
        }
        // La barra propia solo tiene sentido con barra
        if (previous?.equipment == Equipment.BARBELL && ex.equipment != Equipment.BARBELL) {
            settingsStore.update { s -> if (ex.id in s.exerciseBarKg) s.copy(exerciseBarKg = s.exerciseBarKg - ex.id) else s }
        }
        repo.routinesOnce().filter { r -> r.exercises.any { it.exercise.id == ex.id } }.forEach { r ->
            repo.saveRoutine(r.copy(exercises = r.exercises.map { if (it.exercise.id == ex.id) it.copy(exercise = ex) else it }))
        }
    }

    /** Cuántas rutinas usan el ejercicio (para avisar antes de borrarlo). */
    fun routinesUsing(exerciseId: String): Int = repo.routines.value.count { r -> r.exercises.any { it.exercise.id == exerciseId } }

    /** Guarda en [uri] el historial de ese ejercicio en CSV; [onDone] dice si salió bien. */
    fun exportExerciseCsv(uri: android.net.Uri, exerciseId: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val csv = kotlinx.coroutines.withContext(Dispatchers.Default) { com.bestrong.core.io.WorkoutCsv.exportExercise(repo.historyOnce(), exerciseId) }
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("no stream")
            }
        }.isSuccess
        onDone(ok)
    }

    fun deleteCustom(id: String) = viewModelScope.launch {
        repo.deleteCustomExercise(id)
        settingsStore.update { s -> if (id in s.hiddenExercises) s.copy(hiddenExercises = s.hiddenExercises - id) else s }
    }
}

/** Idiomas en los que está traducida la app. */
private val APP_LOCALES = listOf("es", "en", "ca", "gl", "pt", "fr", "de")
