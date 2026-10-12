package com.bestrong.wear.session

import android.content.Context
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.training.HeartRateTracker
import com.bestrong.wear.ApplicationScope
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.health.ExerciseService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tipo de sesión de cardio libre (el id coincide con el ejercicio de la biblioteca). */
enum class CardioType(val exerciseId: String) { RUNNING("running"), WALKING("walking"), CYCLING("cycling") }

data class CardioState(
    val type: CardioType,
    val startedAtEpochMs: Long,
    val distanceM: Double = 0.0,
    val speedMps: Double = 0.0,
    val heartRate: Int? = null,
    /** Media ponderada por tiempo y máximo (ver [HeartRateTracker]). */
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
) {

    /** Ritmo medio en segundos por km (0 si aún no hay distancia). */
    fun paceSecPerKm(nowEpochMs: Long): Long {
        val km = distanceM / 1000.0
        return if (km < 0.01) 0 else (((nowEpochMs - startedAtEpochMs) / 1000.0) / km).toLong()
    }
}

/**
 * Sesión de cardio libre con el GPS del Galaxy Watch Ultra (carrera, caminata o bici).
 * Las métricas llegan de Health Services a través de [ExerciseService]; al terminar se guarda
 * como un entreno con un ejercicio de distancia y se envía al móvil.
 */
@Singleton
class CardioSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WearStore,
    private val phoneSync: PhoneSync,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<CardioState?>(null)
    val state: StateFlow<CardioState?> = _state.asStateFlow()

    private val _finished = MutableStateFlow<Workout?>(null)
    val finished: StateFlow<Workout?> = _finished.asStateFlow()

    val isActive: Boolean get() = _state.value != null

    private var hr = HeartRateTracker(0)

    fun start(type: CardioType) {
        if (_state.value != null) return
        _finished.value = null
        val now = System.currentTimeMillis()
        hr = HeartRateTracker(now)
        _state.value = CardioState(type, now)
        ExerciseService.start(context)
    }

    fun onDistance(totalM: Double) = _state.update { it?.copy(distanceM = totalM) }
    fun onSpeed(mps: Double) = _state.update { it?.copy(speedMps = mps) }
    fun onCalories(kcal: Double) = _state.update { it?.copy(calories = kcal.toInt()) }

    /** Cada lectura con su hora real (las que llegan en lote con la pantalla apagada también). */
    fun onHeartRate(bpm: Int, atEpochMs: Long) {
        if (_state.value == null) return
        hr.add(bpm, atEpochMs)
        _state.update { it?.copy(heartRate = hr.latest, avgHeartRate = hr.average, maxHeartRate = hr.max) }
    }

    fun finish() {
        val s = _state.value ?: return
        _state.value = null
        ExerciseService.stop(context)
        val now = System.currentTimeMillis()
        val exercise = ExerciseLibrary.byId(s.type.exerciseId) ?: return
        val durationSec = ((now - s.startedAtEpochMs) / 1000).toInt()
        if (durationSec < 30) return
        val workout = Workout(
            name = com.bestrong.wear.ui.cardioName(context, s.type),
            startedAtEpochMs = s.startedAtEpochMs,
            finishedAtEpochMs = now,
            source = WorkoutSource.WATCH,
            avgHeartRate = s.avgHeartRate,
            maxHeartRate = s.maxHeartRate,
            heartRateSamples = hr.samples(),
            calories = s.calories,
            exercises = listOf(
                WorkoutExercise(
                    exercise = exercise,
                    sets = listOf(WorkoutSet(reps = 0, durationSec = durationSec, distanceM = s.distanceM, completed = true, completedAtEpochMs = now)),
                ),
            ),
        )
        store.addLocalWorkout(workout)
        _finished.value = workout
        scope.launch { phoneSync.sendFinishedWorkout(workout) }
    }

    fun discard() {
        _state.value = null
        ExerciseService.stop(context)
    }

    fun dismissFinished() {
        _finished.value = null
    }
}
