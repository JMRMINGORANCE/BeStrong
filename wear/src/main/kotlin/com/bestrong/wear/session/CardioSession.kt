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
        // Con menos de 100 m (GPS recién enganchado) el ritmo no dice nada: salía «1h 06m /km»
        return if (km < 0.1) 0 else (((nowEpochMs - startedAtEpochMs) / 1000.0) / km).toLong()
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
    /** Se guarda en [WearStore]: si el proceso muere a mitad de la carrera, se retoma. */
    private val _state = MutableStateFlow(store.activeCardio)
    val state: StateFlow<CardioState?> = _state.asStateFlow()

    private val _finished = MutableStateFlow<Workout?>(null)
    val finished: StateFlow<Workout?> = _finished.asStateFlow()

    val isActive: Boolean get() = _state.value != null

    private var hr = HeartRateTracker(_state.value?.startedAtEpochMs ?: 0)

    /** Pulso guardado antes de un reinicio del proceso (el nuevo contador empieza vacío). */
    private var restoredHr = _state.value?.let { it.avgHeartRate to it.maxHeartRate }

    /** Cuándo se retomó tras el reinicio: la media guardada pesa lo que duró hasta entonces. */
    private var restoredAt = if (_state.value != null) System.currentTimeMillis() else 0L
    private var lastSavedAt = 0L

    /** Distancia y calorías de antes si Health Services empezó un ejercicio nuevo (los suyos vuelven a cero). */
    private var baseM = 0.0
    private var baseKcal = 0

    /** Antes de empezar un ejercicio nuevo de Health Services con la carrera ya en marcha: se suma a lo que había. */
    fun rebase() {
        _state.value?.let {
            baseM = it.distanceM
            baseKcal = it.calories ?: 0
        }
    }

    fun start(type: CardioType) {
        if (_state.value != null) return
        _finished.value = null
        val now = System.currentTimeMillis()
        hr = HeartRateTracker(now)
        restoredHr = null
        restoredAt = 0L
        baseM = 0.0
        baseKcal = 0
        _state.value = CardioState(type, now)
        save(force = true)
        ExerciseService.start(context)
    }

    fun onDistance(totalM: Double) = changed { it.copy(distanceM = baseM + totalM) }
    fun onSpeed(mps: Double) = changed { it.copy(speedMps = mps) }
    fun onCalories(kcal: Double) = changed { it.copy(calories = baseKcal + kcal.toInt()) }

    /** Media de toda la carrera tras un reinicio: la guardada y la nueva, cada una según el tiempo que cubre. */
    private fun averageWithRestored(current: Int?, startedAt: Long, at: Long): Int? {
        val before = restoredHr?.first
        if (before == null || current == null || restoredAt == 0L) return current ?: before
        val beforeMs = (restoredAt - startedAt).coerceAtLeast(0)
        val afterMs = (at - restoredAt).coerceAtLeast(1)
        return ((before * beforeMs + current * afterMs) / (beforeMs + afterMs)).toInt()
    }

    /** Cada lectura con su hora real (las que llegan en lote con la pantalla apagada también). */
    fun onHeartRate(bpm: Int, atEpochMs: Long) {
        if (_state.value == null) return
        hr.add(bpm, atEpochMs)
        changed {
            it.copy(
                heartRate = hr.latest,
                avgHeartRate = averageWithRestored(hr.average, it.startedAtEpochMs, atEpochMs),
                maxHeartRate = listOfNotNull(hr.max, restoredHr?.second).maxOrNull(),
            )
        }
    }

    private fun changed(block: (CardioState) -> CardioState) {
        _state.update { it?.let(block) }
        save()
    }

    /** Guarda como mucho cada 10 s (las métricas llegan cada segundo). */
    private fun save(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSavedAt < SAVE_EVERY_MS) return
        lastSavedAt = now
        store.activeCardio = _state.value
    }

    fun finish() {
        val s = _state.value ?: return
        _state.value = null
        restoredHr = null
        store.activeCardio = null
        // Si hay un entreno de pesas abierto sigue necesitando el servicio (pulso, primer plano)
        if (store.activeWorkout == null) ExerciseService.stop(context)
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
        restoredHr = null
        store.activeCardio = null
        // Si hay un entreno de pesas abierto sigue necesitando el servicio (pulso, primer plano)
        if (store.activeWorkout == null) ExerciseService.stop(context)
    }

    fun dismissFinished() {
        _finished.value = null
    }

    private companion object {
        const val SAVE_EVERY_MS = 10_000L
    }
}
