package com.bestrong.wear.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.Progression
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.health.ExerciseService
import com.bestrong.wear.ApplicationScope
import com.bestrong.wear.health.RepCounter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
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
 * [ExerciseService] en primer plano), se persiste en [WearStore] tras cada cambio y se
 * comparte en vivo con el móvil (el cambio más reciente gana).
 */
@Singleton
class WorkoutSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WearStore,
    private val phoneSync: PhoneSync,
    @ApplicationScope private val scope: CoroutineScope,
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

    val repCounter = RepCounter(context)
    private val _repCounting = MutableStateFlow(store.repCounterEnabled && repCounter.isAvailable)
    val repCounting: StateFlow<Boolean> = _repCounting.asStateFlow()

    private var restJob: Job? = null
    private var pushJob: Job? = null
    private var lastMetricsSentAt = 0L
    private val hrSamples = mutableListOf<Int>()

    init {
        // Si el proceso murió con un entrenamiento activo, retomamos el servicio
        if (_workout.value != null) {
            ExerciseService.start(context)
            if (_repCounting.value) repCounter.start()
        }
        _exerciseIndex.value = _workout.value?.let { w -> WorkoutEditor.nextPendingSet(w)?.first } ?: 0
    }

    /** Cambio local: guarda, marca la hora y lo publica al móvil (agrupando cambios rápidos). */
    private fun set(w: Workout?) {
        val stamped = w?.copy(updatedAtEpochMs = System.currentTimeMillis())
        _workout.value = stamped
        store.activeWorkout = stamped
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(300)
            phoneSync.pushActive(stamped)
        }
    }

    private fun edit(block: (Workout) -> Workout) {
        _workout.value?.let { set(block(it)) }
    }

    private fun previous(exerciseId: String): List<WorkoutSet> = WorkoutStats.previousSets(exerciseId, store.history.value)

    /** Rellena con la sugerencia de progresión (si está activada en el móvil) o con la última sesión. */
    private fun prefill(exerciseId: String): List<WorkoutSet> {
        val s = store.settings.value
        if (s.progressionEnabled) {
            val ex = exerciseFor(exerciseId)
            if (ex != null) Progression.suggest(ex, store.history.value, s.unit.stepKg())?.let { return it.sets }
        }
        return previous(exerciseId)
    }

    private fun exerciseFor(id: String): Exercise? =
        com.bestrong.core.library.ExerciseLibrary.byId(id)
            ?: store.routines.value.flatMap { r -> r.exercises.map { it.exercise } }.firstOrNull { it.id == id }
            ?: store.history.value.flatMap { w -> w.exercises.map { it.exercise } }.firstOrNull { it.id == id }

    fun previousFor(exerciseId: String): List<WorkoutSet> = previous(exerciseId)

    // --- Ciclo de vida -----------------------------------------------------------------------

    fun startEmpty() {
        if (_workout.value != null) return
        val now = System.currentTimeMillis()
        begin(WorkoutEditor.empty(now, WorkoutSource.WATCH, com.bestrong.wear.ui.defaultWorkoutName(context, now)))
    }

    fun startRoutine(routine: Routine) {
        if (_workout.value != null) return
        begin(routine.toWorkout(System.currentTimeMillis(), ::prefill, WorkoutSource.WATCH))
    }

    private fun begin(w: Workout) {
        _summary.value = null
        _exerciseIndex.value = 0
        _metrics.value = HealthMetrics()
        hrSamples.clear()
        set(w)
        ExerciseService.start(context)
        if (_repCounting.value) repCounter.start()
    }

    fun finish() {
        val w = _workout.value ?: return
        val m = _metrics.value
        val finished = WorkoutEditor.finish(w, System.currentTimeMillis()).copy(
            avgHeartRate = m.avgHeartRate,
            maxHeartRate = m.maxHeartRate,
            calories = m.calories,
        )
        end()
        if (finished.exercises.isEmpty()) return
        val records = WorkoutStats.newRecordsIn(finished, store.history.value + finished)
        store.addLocalWorkout(finished)
        _summary.value = Summary(finished, records)
        scope.launch { phoneSync.sendFinishedWorkout(finished) }
    }

    fun discard() = end()

    private fun end() {
        stopRest()
        repCounter.stop()
        set(null)
        ExerciseService.stop(context)
    }

    fun dismissSummary() {
        _summary.value = null
    }

    // --- Sincronización en vivo con el móvil ------------------------------------------------

    /** Llega el entreno desde el móvil: lo adoptamos si es nuevo o más reciente. */
    fun applyRemote(remote: Workout) {
        val local = _workout.value
        if (local != null && remote.updatedAtEpochMs <= local.updatedAtEpochMs) return
        val isNew = local == null || local.id != remote.id
        _workout.value = remote
        store.activeWorkout = remote
        if (isNew) {
            _summary.value = null
            _metrics.value = HealthMetrics()
            hrSamples.clear()
            _exerciseIndex.value = WorkoutEditor.nextPendingSet(remote)?.first ?: 0
            // Empezado en el móvil: el reloj mide el pulso automáticamente
            ExerciseService.start(context)
            if (_repCounting.value) repCounter.start()
        }
    }

    /** El móvil terminó o descartó el entreno. */
    fun clearFromRemote() {
        if (_workout.value == null) return
        stopRest()
        repCounter.stop()
        _workout.value = null
        store.activeWorkout = null
        ExerciseService.stop(context)
    }

    // --- Edición -----------------------------------------------------------------------------

    fun selectExercise(index: Int) {
        val size = _workout.value?.exercises?.size ?: 0
        if (size > 0) _exerciseIndex.value = index.coerceIn(0, size - 1)
    }

    fun nextExercise() = selectExercise(_exerciseIndex.value + 1)
    fun previousExercise() = selectExercise(_exerciseIndex.value - 1)

    fun addExercise(exercise: Exercise) {
        edit { WorkoutEditor.addExercises(it, listOf(exercise), ::prefill) }
        _workout.value?.let { _exerciseIndex.value = it.exercises.lastIndex }
    }

    fun addSet() {
        val we = currentExercise() ?: return
        edit { WorkoutEditor.addSet(it, we.id) }
    }

    fun setRotaryTarget(target: RotaryTarget) {
        _rotaryTarget.value = target
    }

    fun setRepCounting(enabled: Boolean) {
        val on = enabled && repCounter.isAvailable
        _repCounting.value = on
        store.repCounterEnabled = on
        if (on && _workout.value != null) repCounter.start() else repCounter.stop()
    }

    fun currentExercise() = _workout.value?.exercises?.getOrNull(_exerciseIndex.value)

    /** Serie que se está editando: la primera pendiente del ejercicio actual. */
    fun currentSet(): WorkoutSet? = currentExercise()?.sets?.firstOrNull { !it.completed }

    /** Bisel: un paso = 2,5 kg / 5 lb (o 1 rep / 5 s según el campo y el tipo de ejercicio). */
    fun rotaryStep(direction: Int) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        val step = store.settings.value.unit.stepKg()
        edit { w ->
            WorkoutEditor.updateSet(w, we.id, s.id) { set ->
                when {
                    we.exercise.tracking == TrackingType.DURATION -> set.copy(durationSec = (set.durationSec + 5 * direction).coerceAtLeast(0))
                    _rotaryTarget.value == RotaryTarget.WEIGHT -> set.copy(weightKg = (set.weightKg + step * direction).coerceAtLeast(0.0))
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

    /**
     * Completa la serie actual. En superserie pasa al siguiente ejercicio del grupo sin descanso;
     * al cerrar la ronda arranca el descanso. Devuelve true si empieza un descanso.
     */
    fun completeSet(): Boolean {
        val w0 = _workout.value ?: return false
        val index = _exerciseIndex.value
        val we = currentExercise() ?: return false
        val s = currentSet() ?: return false
        val counted = repCounter.reps.value
        edit { w ->
            val withReps = if (_repCounting.value && counted > 0 && we.exercise.tracking != TrackingType.DURATION) {
                WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(reps = counted) }
            } else w
            WorkoutEditor.toggleSetCompleted(withReps, we.id, s.id)
        }
        repCounter.reset()
        haptic(longArrayOf(0, 60))
        val updated = _workout.value ?: w0
        val (next, restSeconds) = WorkoutEditor.afterSetCompleted(updated, index)
        next?.let { _exerciseIndex.value = it }
        return if (next != null && restSeconds > 0) {
            startRest(restSeconds)
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
        repCounter.reset()
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
            repCounter.reset()
            haptic(longArrayOf(0, 300, 150, 300, 150, 300))
        }
    }

    private fun haptic(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getSystemService(context, VibratorManager::class.java)?.defaultVibrator
        } else {
            ContextCompat.getSystemService(context, Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // --- Métricas (llamado desde ExerciseService) --------------------------------------------

    fun onHeartRate(bpm: Int) {
        if (bpm <= 0) return
        hrSamples += bpm
        _metrics.update {
            it.copy(heartRate = bpm, avgHeartRate = hrSamples.average().toInt(), maxHeartRate = hrSamples.max())
        }
        maybeSendMetrics()
    }

    fun onCalories(kcal: Double) {
        _metrics.update { it.copy(calories = kcal.toInt()) }
    }

    /** Envía las métricas al móvil como mucho cada 5 s. */
    private fun maybeSendMetrics() {
        val w = _workout.value ?: return
        val now = System.currentTimeMillis()
        if (now - lastMetricsSentAt < 5_000) return
        lastMetricsSentAt = now
        val m = _metrics.value
        scope.launch { phoneSync.sendMetrics(LiveMetrics(w.id, m.heartRate, m.avgHeartRate, m.maxHeartRate, m.calories, now)) }
    }
}
