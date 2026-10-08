package com.bestrong.wear.session

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.health.ExerciseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthMetrics(
    val heartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
)

data class Rest(val endsAtEpochMs: Long, val totalSeconds: Int)

data class Summary(val workout: Workout, val records: List<PersonalRecord>)

/** Campo que ajusta el bisel/corona. */
enum class RotaryTarget { WEIGHT, REPS }

/**
 * Estado del entrenamiento en el reloj. Vive en el proceso (lo mantiene vivo el
 * [ExerciseService] en primer plano) y se persiste en [WearStore] tras cada cambio.
 */
class WorkoutSession(
    private val context: Context,
    private val store: WearStore,
    private val phoneSync: PhoneSync,
    private val scope: CoroutineScope,
) {
    private val _workout = MutableStateFlow(store.activeWorkout)
    val workout: StateFlow<Workout?> = _workout.asStateFlow()

    private val _exerciseIndex = MutableStateFlow(0)
    val exerciseIndex: StateFlow<Int> = _exerciseIndex.asStateFlow()

    private val _rest = MutableStateFlow<Rest?>(null)
    val rest: StateFlow<Rest?> = _rest.asStateFlow()

    private val _metrics = MutableStateFlow(HealthMetrics())
    val metrics: StateFlow<HealthMetrics> = _metrics.asStateFlow()

    private val _rotaryTarget = MutableStateFlow(RotaryTarget.WEIGHT)
    val rotaryTarget: StateFlow<RotaryTarget> = _rotaryTarget.asStateFlow()

    private val _summary = MutableStateFlow<Summary?>(null)
    val summary: StateFlow<Summary?> = _summary.asStateFlow()

    private var restJob: Job? = null
    private val hrSamples = mutableListOf<Int>()

    init {
        // Si el proceso murió con un entrenamiento activo, retomamos el servicio
        if (_workout.value != null) ExerciseService.start(context)
        _exerciseIndex.value = _workout.value?.let { w -> WorkoutEditor.nextPendingSet(w)?.first } ?: 0
    }

    private fun set(w: Workout?) {
        _workout.value = w
        store.activeWorkout = w
    }

    private fun edit(block: (Workout) -> Workout) {
        _workout.value?.let { set(block(it)) }
    }

    private fun previous(exerciseId: String): List<WorkoutSet> = WorkoutStats.previousSets(exerciseId, store.history.value)

    fun previousFor(exerciseId: String): List<WorkoutSet> = previous(exerciseId)

    // --- Ciclo de vida -----------------------------------------------------------------------

    fun startEmpty() {
        if (_workout.value != null) return
        begin(WorkoutEditor.empty(System.currentTimeMillis(), WorkoutSource.WATCH))
    }

    fun startRoutine(routine: Routine) {
        if (_workout.value != null) return
        begin(routine.toWorkout(System.currentTimeMillis(), ::previous, WorkoutSource.WATCH))
    }

    private fun begin(w: Workout) {
        _summary.value = null
        _exerciseIndex.value = 0
        _metrics.value = HealthMetrics()
        hrSamples.clear()
        set(w)
        ExerciseService.start(context)
    }

    fun finish() {
        val w = _workout.value ?: return
        val m = _metrics.value
        val finished = WorkoutEditor.finish(w, System.currentTimeMillis()).copy(
            avgHeartRate = m.avgHeartRate,
            maxHeartRate = m.maxHeartRate,
            calories = m.calories,
        )
        stopRest()
        set(null)
        ExerciseService.stop(context)
        if (finished.exercises.isEmpty()) return
        val records = WorkoutStats.newRecordsIn(finished, store.history.value + finished)
        store.addLocalWorkout(finished)
        _summary.value = Summary(finished, records)
        scope.launch { phoneSync.sendFinishedWorkout(finished) }
    }

    fun discard() {
        stopRest()
        set(null)
        ExerciseService.stop(context)
    }

    fun dismissSummary() {
        _summary.value = null
    }

    // --- Edición -----------------------------------------------------------------------------

    fun selectExercise(index: Int) {
        val size = _workout.value?.exercises?.size ?: 0
        if (size > 0) _exerciseIndex.value = index.coerceIn(0, size - 1)
    }

    fun nextExercise() = selectExercise(_exerciseIndex.value + 1)
    fun previousExercise() = selectExercise(_exerciseIndex.value - 1)

    fun addExercise(exercise: Exercise) {
        edit { WorkoutEditor.addExercises(it, listOf(exercise), ::previous) }
        _workout.value?.let { _exerciseIndex.value = it.exercises.lastIndex }
    }

    fun addSet() {
        val we = currentExercise() ?: return
        edit { WorkoutEditor.addSet(it, we.id) }
    }

    fun setRotaryTarget(target: RotaryTarget) {
        _rotaryTarget.value = target
    }

    fun currentExercise() = _workout.value?.exercises?.getOrNull(_exerciseIndex.value)

    /** Serie que se está editando: la primera pendiente del ejercicio actual. */
    fun currentSet(): WorkoutSet? = currentExercise()?.sets?.firstOrNull { !it.completed }

    /** Bisel: un paso = 2,5 kg (o 1 rep / 5 s según el campo y el tipo de ejercicio). */
    fun rotaryStep(direction: Int) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        edit { w ->
            WorkoutEditor.updateSet(w, we.id, s.id) { set ->
                when {
                    we.exercise.tracking == TrackingType.DURATION -> set.copy(durationSec = (set.durationSec + 5 * direction).coerceAtLeast(0))
                    _rotaryTarget.value == RotaryTarget.WEIGHT -> set.copy(weightKg = (set.weightKg + 2.5 * direction).coerceAtLeast(0.0))
                    else -> set.copy(reps = (set.reps + direction).coerceAtLeast(0))
                }
            }
        }
    }

    fun adjustReps(delta: Int) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        edit { w -> WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(reps = (it.reps + delta).coerceAtLeast(0)) } }
    }

    /** Completa la serie actual y arranca el descanso. Devuelve true si empieza un descanso. */
    fun completeSet(): Boolean {
        val we = currentExercise() ?: return false
        val s = currentSet() ?: return false
        edit { WorkoutEditor.toggleSetCompleted(it, we.id, s.id) }
        haptic(longArrayOf(0, 60))
        // Si el ejercicio está terminado, pasa al siguiente con series pendientes
        val updated = currentExercise()
        if (updated != null && updated.sets.all { it.completed }) {
            _workout.value?.let { w -> WorkoutEditor.nextPendingSet(w)?.let { (i, _) -> _exerciseIndex.value = i } }
        }
        return if (we.restSeconds > 0 && WorkoutEditor.nextPendingSet(_workout.value ?: return false) != null) {
            startRest(we.restSeconds)
            true
        } else false
    }

    // --- Descanso ----------------------------------------------------------------------------

    private fun startRest(seconds: Int) {
        _rest.value = Rest(System.currentTimeMillis() + seconds * 1000L, seconds)
        scheduleRestEnd()
    }

    fun adjustRest(delta: Int) {
        val r = _rest.value ?: return
        _rest.value = Rest((r.endsAtEpochMs + delta * 1000L).coerceAtLeast(System.currentTimeMillis()), (r.totalSeconds + delta).coerceAtLeast(1))
        scheduleRestEnd()
    }

    fun stopRest() {
        restJob?.cancel()
        _rest.value = null
    }

    private fun scheduleRestEnd() {
        restJob?.cancel()
        restJob = scope.launch {
            val r = _rest.value ?: return@launch
            val remaining = r.endsAtEpochMs - System.currentTimeMillis()
            if (remaining > 10_000) {
                delay(remaining - 10_000)
                haptic(longArrayOf(0, 40)) // aviso: quedan 10 s
                delay(10_000)
            } else {
                delay(remaining.coerceAtLeast(0))
            }
            _rest.value = null
            haptic(longArrayOf(0, 300, 150, 300, 150, 300))
        }
    }

    private fun haptic(pattern: LongArray) {
        val vm = ContextCompat.getSystemService(context, VibratorManager::class.java) ?: return
        vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // --- Métricas (llamado desde ExerciseService) --------------------------------------------

    fun onHeartRate(bpm: Int) {
        if (bpm <= 0) return
        hrSamples += bpm
        _metrics.update {
            it.copy(heartRate = bpm, avgHeartRate = hrSamples.average().toInt(), maxHeartRate = hrSamples.max())
        }
    }

    fun onCalories(kcal: Double) {
        _metrics.update { it.copy(calories = kcal.toInt()) }
    }
}
