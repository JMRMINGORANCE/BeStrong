package com.bestrong.wear.session

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.sensors.BarVelocity
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.ExerciseProgressPoint
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.sync.WorkoutHealthData
import com.bestrong.core.sync.WorkoutMerge
import com.bestrong.core.training.HeartRate
import com.bestrong.core.training.HeartRateTracker
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.Progression
import com.bestrong.core.training.Tempo
import com.bestrong.core.training.VoiceSetParser
import com.bestrong.wear.ApplicationScope
import com.bestrong.wear.data.PhoneSync
import com.bestrong.wear.data.WearStore
import com.bestrong.wear.health.ExerciseService
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

data class Summary(
    val workout: Workout,
    val records: List<PersonalRecord>,
    val comparisons: List<WorkoutStats.ExerciseComparison> = emptyList(),
)

/** Velocidad de la última serie medida con el acelerómetro y RIR estimado. */
data class VelocityInfo(val lastVelocity: Double, val rir: Int?)

/** Vibraciones distintas por tipo de aviso (se reconocen sin mirar el reloj). */
object Haptics {
    val SET_DONE = longArrayOf(0, 60)
    val REST_WARNING = longArrayOf(0, 40)
    val REST_END = longArrayOf(0, 300, 150, 300, 150, 300)
    val RECOVERED = longArrayOf(0, 120, 100, 120)
    val RECORD = longArrayOf(0, 80, 60, 80, 60, 80, 60, 400)
    val VOICE_OK = longArrayOf(0, 30, 60, 30)
}

private const val RECORD_BANNER_MS = 5_000L

/** Campo que ajusta el bisel/corona. */
enum class RotaryTarget { WEIGHT, REPS }

/** Si la pantalla de serie enseña el peso: ejercicios con peso, o una serie con peso (también negativo, asistida). */
fun showsWeight(we: com.bestrong.core.model.WorkoutExercise, set: com.bestrong.core.model.WorkoutSet): Boolean =
    we.exercise.tracking == TrackingType.WEIGHT_REPS || set.weightKg != 0.0

/** Terminado ahora con lo medido por el reloj (pulso medio y máximo, calorías y la gráfica de pulso). */
private fun Workout.finishedWith(m: HealthMetrics, samples: List<com.bestrong.core.model.HeartRateSample>): Workout =
    WorkoutEditor.finish(this, System.currentTimeMillis()).copy(
        avgHeartRate = m.avgHeartRate,
        maxHeartRate = m.maxHeartRate,
        calories = m.calories,
        heartRateSamples = samples,
    )

/** Vibración con el patrón dado (fuera de la sesión: no depende de su estado). */
private fun Context.haptic(pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.getSystemService(this, VibratorManager::class.java)?.defaultVibrator
    } else {
        ContextCompat.getSystemService(this, Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
}

/** Asistidas (peso negativo, puesto en el móvil) siguen negativas; si no, no baja de 0. */
private fun nextWeight(current: Double, delta: Double): Double {
    val next = current + delta
    return if (current < 0 || next >= 0) next else 0.0
}

/** Paso del bisel en metros (paseo del granjero, trineo). */
private const val DISTANCE_STEP_M = 5.0

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

    /** Ajustes del móvil (incluye las notas fijas por ejercicio). */
    val settings: StateFlow<SharedSettings> get() = store.settings

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

    val repCounter = RepCounter(context, store.repThreshold)
    private val _lowPower = MutableStateFlow(store.lowPower)
    val lowPower: StateFlow<Boolean> = _lowPower.asStateFlow()
    private val _repCounting = MutableStateFlow(store.repCounterEnabled && repCounter.isAvailable && !store.lowPower)
    val repCounting: StateFlow<Boolean> = _repCounting.asStateFlow()

    private var restJob: Job? = null

    /** Declarado antes de init: el descanso restaurado ya lo usa. */
    private var restWakeLock: PowerManager.WakeLock? = null
    private var pushJob: Job? = null
    private var lastMetricsSentAt = 0L

    /** Pulso del entreno en curso: media ponderada por tiempo, máximo y serie para Health Connect. */
    private var hr = HeartRateTracker(_workout.value?.startedAtEpochMs ?: System.currentTimeMillis())

    /** Media y máximo de toda la sesión según Health Services (sobreviven si el proceso se reinicia). */
    private var platformHr: Pair<Int, Int>? = null

    init {
        // Si el proceso murió con un entrenamiento activo, se retoma. El servicio no se arranca
        // aquí (en segundo plano Android no lo permite): lo hace MainActivity al volver a la app
        if (_workout.value != null) {
            if (_repCounting.value) repCounter.start()
            // El descanso sigue contando hasta la misma hora si aún no ha terminado
            store.activeRest?.takeIf { it.endsAtEpochMs > System.currentTimeMillis() }?.let {
                _rest.value = it
                scheduleRestEnd()
            }
        }
        if (_rest.value == null) store.activeRest = null
        _exerciseIndex.value = _workout.value?.let { w -> WorkoutEditor.upcomingSet(w)?.first } ?: 0
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
        // Como en el móvil: con varios gimnasios, lo hecho en el activo si allí ya se hizo el ejercicio
        val base = WorkoutStats.atGym(store.history.value, s.activeGym?.name?.takeIf { s.multiGym }, exerciseId)
        val previous = WorkoutStats.previousSets(exerciseId, base)
        if (s.progressionEnabled) {
            val ex = exerciseFor(exerciseId)
            if (ex != null) {
                Progression.suggest(ex, base, s.stepKgFor(exerciseId), repRange = s.repRangeFor(exerciseId))?.let { sug ->
                    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
                    val lastWeekBest = WorkoutStats.bestWeightBetween(exerciseId, store.history.value, weekAgo, Long.MAX_VALUE)
                    return s.loadable(ex, sug, previous, lastWeekBest)
                }
            }
        }
        return previous
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
        // Las sesiones de un programa llevan las cargas calculadas: no se sustituyen por la última vez
        val lookup: (String) -> List<WorkoutSet> = if (routine.id.startsWith(Periodization.ROUTINE_PREFIX)) EMPTY_LOOKUP else ::prefill
        // El mismo ejercicio en otro bloque: cada uno con lo suyo de la última vez (como en el móvil)
        begin(routine.toWorkout(System.currentTimeMillis(), lookup, WorkoutSource.WATCH) { id, n -> WorkoutStats.previousBlock(id, n, store.history.value).takeIf { lookup != EMPTY_LOOKUP } })
    }

    private fun begin(start: Workout) {
        // Dónde se empieza (con varios gimnasios): así no depende del que tenga el móvil cuando llegue
        val s = store.settings.value
        val w = if (start.gymName == null && s.multiGym) start.copy(gymName = s.activeGym?.name) else start
        _summary.value = null
        _exerciseIndex.value = 0
        _metrics.value = HealthMetrics()
        hr = HeartRateTracker(w.startedAtEpochMs)
        platformHr = null
        resetHealthSegments()
        set(w)
        ExerciseService.start(context)
        if (_repCounting.value) repCounter.start()
    }

    fun finish() {
        val w = _workout.value ?: return
        val m = _metrics.value
        val finished = w.finishedWith(m, hr.samples())
        end()
        if (finished.exercises.isEmpty()) return
        val records = WorkoutStats.newRecordsIn(finished, store.history.value + finished)
        val comparisons = WorkoutStats.compareWithPrevious(finished, store.history.value)
        store.addLocalWorkout(finished)
        _summary.value = Summary(finished, records, comparisons)
        if (records.isNotEmpty()) context.haptic(Haptics.RECORD)
        scope.launch { phoneSync.sendFinishedWorkout(finished) }
    }

    fun discard() = end()

    private fun end() {
        _workout.value?.let { store.lastEndedWorkoutId = it.id }
        stopRest()
        // Terminar o descartar en mitad de una guía de tempo no la deja vibrando
        stopTempo()
        repCounter.stop()
        set(null)
        // Con una carrera en marcha el ejercicio de Health Services es de los dos: no se corta
        if (store.activeCardio == null) ExerciseService.stop(context)
    }

    private fun sendReplaced(local: Workout) {
        val m = _metrics.value
        val finished = local.finishedWith(m, hr.samples())
        stopRest()
        stopTempo()
        if (finished.exercises.isNotEmpty()) scope.launch { phoneSync.sendFinishedWorkout(finished) }
    }

    fun dismissSummary() {
        _summary.value = null
    }

    // --- Sincronización en vivo con el móvil ------------------------------------------------

    /** Llega el entreno desde el móvil: se fusiona serie a serie con el local (no se pierde ninguna serie hecha). */
    fun applyRemote(incoming: Workout) {
        val local = _workout.value
        // Recién terminado o descartado aquí: el eco atrasado del móvil no lo resucita
        if (local?.id != incoming.id && store.wasEnded(incoming.id)) return
        // Se lee antes de apuntar el que llega: ¿tuvo el móvil alguna vez el entreno local?
        val phoneKnew = store.phoneKnownWorkoutId
        if (phoneKnew != incoming.id) store.phoneKnownWorkoutId = incoming.id
        val remote = if (local == null) incoming else WorkoutMerge.merge(local, incoming)
        if (remote == local) return
        val isNew = local == null || local.id != remote.id
        if (isNew && local != null) wrapUp(local, knownToPhone = phoneKnew == local.id)
        _workout.value = remote
        store.activeWorkout = remote
        // Se sigue en el mismo ejercicio aunque el móvil haya borrado o movido otros
        if (!isNew) _exerciseIndex.value = keptExerciseIndex(local, remote, _exerciseIndex.value)
        if (isNew) startedFromPhone(remote)
    }

    /** Entreno nuevo llegado del móvil: se empieza a medir desde cero en el reloj. */
    private fun startedFromPhone(remote: Workout) {
        _summary.value = null
        _metrics.value = HealthMetrics()
        hr = HeartRateTracker(remote.startedAtEpochMs)
        platformHr = null
        resetHealthSegments()
        _exerciseIndex.value = WorkoutEditor.nextPendingSet(remote)?.first ?: 0
        // Empezado en el móvil: el reloj mide el pulso automáticamente (con la app en segundo
        // plano no se puede arrancar el servicio; lo hará MainActivity al abrirla)
        if (isInForeground()) ExerciseService.start(context)
        if (_repCounting.value) repCounter.start()
    }

    /** El móvil terminó o descartó el entreno. */
    fun clearFromRemote() {
        val w = _workout.value ?: return
        wrapUp(w, knownToPhone = true)
        // Sin marcarlo como terminado: el móvil puede deshacer el descarte y devolver el mismo entreno
        _workout.value = null
        store.activeWorkout = null
        // Con una carrera en marcha el ejercicio de Health Services es de los dos: no se corta
        if (store.activeCardio == null) ExerciseService.stop(context)
    }

    /**
     * Cierra en el reloj un entreno que el móvil sustituye o ya cerró: sin envíos ni descansos pendientes (su descanso
     * no sigue sonando en el siguiente). Si el móvil nunca lo tuvo (empezados a la vez sin conexión), lo hecho en el
     * reloj no se pierde: se le manda terminado; si lo tuvo, ya lo guardó allí y se le manda el pulso y las calorías.
     */
    private fun wrapUp(w: Workout, knownToPhone: Boolean) {
        if (!knownToPhone && w.exercises.any { we -> we.sets.any { it.completed } }) sendReplaced(w)
        val m = _metrics.value
        val samples = hr.samples()
        if (knownToPhone && (samples.isNotEmpty() || m.calories != null)) {
            val data = WorkoutHealthData(w.id, m.avgHeartRate, m.maxHeartRate, m.calories, samples)
            scope.launch { phoneSync.sendWorkoutHealth(data) }
        }
        // Un envío pendiente devolvería al móvil el entreno que acaba de cerrar
        pushJob?.cancel()
        pushJob = null
        stopTempo()
        stopRest()
        repCounter.stop()
    }

    private fun isInForeground(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
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

    /** Quita la última serie pendiente del ejercicio actual (se puso una de más). */
    fun removePendingSet(): Boolean {
        val we = currentExercise()
        val set = we?.sets?.lastOrNull { !it.completed } ?: return false
        edit { WorkoutEditor.removeSet(it, we.id, set.id) }
        // Si este ejercicio ya no tiene series pendientes, se pasa al siguiente que las tenga
        if (currentSet() == null) _workout.value?.let { w -> WorkoutEditor.nextPendingSet(w)?.let { _exerciseIndex.value = it.first } }
        return true
    }

    /**
     * Deshace la última serie marcada (un toque o un botón por error): vuelve a pendiente, se para
     * el descanso y se vuelve a ese ejercicio.
     */
    fun undoLastSet(): Boolean {
        val (index, set) = _workout.value?.exercises.orEmpty().withIndex()
            .flatMap { (i, we) -> we.sets.filter { it.completed }.map { i to it } }
            .maxByOrNull { (_, s) -> s.completedAtEpochMs ?: 0L } ?: return false
        edit { WorkoutEditor.uncompleteSet(it, it.exercises[index].id, set.id) }
        stopRest()
        _exerciseIndex.value = index
        return true
    }

    fun setRotaryTarget(target: RotaryTarget) {
        _rotaryTarget.value = target
    }

    private val _stemAction = MutableStateFlow(store.stemAction)
    val stemAction: StateFlow<StemAction> = _stemAction.asStateFlow()

    /** Cambia la función del botón rápido (completar serie → saltar descanso → siguiente ejercicio). */
    fun cycleStemAction() {
        val next = StemAction.entries[(_stemAction.value.ordinal + 1) % StemAction.entries.size]
        _stemAction.value = next
        store.stemAction = next
    }

    /** Botón rápido pulsado en la pantalla de la serie. Devuelve true si hay que ir al descanso. */
    fun onStemInWorkout(): Boolean = when (_stemAction.value) {
        StemAction.NEXT_EXERCISE -> {
            nextExercise()
            false
        }
        else -> currentSet() != null && completeSet()
    }

    /** Botón rápido pulsado durante el descanso. */
    fun onStemInRest() {
        when (_stemAction.value) {
            StemAction.NEXT_EXERCISE -> {
                stopRest()
                nextExercise()
            }
            else -> stopRest()
        }
    }

    fun setRepCounting(enabled: Boolean) {
        val on = enabled && repCounter.isAvailable && !_lowPower.value
        _repCounting.value = on
        store.repCounterEnabled = on
        if (on && _workout.value != null) repCounter.start() else repCounter.stop()
    }

    fun currentExercise() = _workout.value?.exercises?.getOrNull(_exerciseIndex.value)

    /** Serie que se está editando: la primera pendiente del ejercicio actual. */
    fun currentSet(): WorkoutSet? = currentExercise()?.sets?.firstOrNull { !it.completed }

    /** Bisel: un paso = el incremento del ejercicio (2,5 kg / 5 lb por defecto), o 1 rep / 5 s según el campo. */
    fun rotaryStep(direction: Int) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        val step = store.settings.value.stepKgFor(we.exercise.id)
        // El peso solo si se ve en pantalla: en dominadas a 0 kg se marca (y se cambia) las reps
        val target = effectiveTarget(we, s)
        // Peso y reps pasan también a las series pendientes que tenían lo mismo (como en el móvil): no hay que volver
        // a girar la corona en cada serie
        edit { w ->
            when {
                we.exercise.tracking == TrackingType.DURATION -> WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(durationSec = (it.durationSec + 5 * direction).coerceAtLeast(0)) }
                we.exercise.tracking == TrackingType.CARDIO -> WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(durationSec = (it.durationSec + 60 * direction).coerceAtLeast(0)) }
                target == RotaryTarget.WEIGHT -> WorkoutEditor.updateSetPropagating(w, we.id, s.id, weightKg = nextWeight(s.weightKg, step * direction))
                we.exercise.tracking == TrackingType.DISTANCE -> WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(distanceM = (it.distanceM + DISTANCE_STEP_M * direction).coerceAtLeast(0.0)) }
                else -> WorkoutEditor.updateSetPropagating(w, we.id, s.id, reps = (s.reps + direction).coerceAtLeast(0)).also { repCounter.reset() }
            }
        }
    }

    /** El campo que mueve el bisel: el peso solo si se enseña (ejercicio con peso o serie con peso apuntado). */
    fun effectiveTarget(we: com.bestrong.core.model.WorkoutExercise, set: com.bestrong.core.model.WorkoutSet): RotaryTarget =
        if (showsWeight(we, set)) _rotaryTarget.value else RotaryTarget.REPS

    /** Tocar «Anterior»: la serie actual con el peso, las reps, el tiempo y la distancia de la última vez. */
    fun copyPrevious(previous: com.bestrong.core.model.WorkoutSet) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        edit { w ->
            WorkoutEditor.updateSet(w, we.id, s.id) {
                it.copy(weightKg = previous.weightKg, reps = previous.reps, durationSec = previous.durationSec, distanceM = previous.distanceM, repsRight = previous.repsRight)
            }
        }
    }

    fun adjustReps(delta: Int) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        // Reps corregidas a mano: no las pisa el contador de repeticiones al marcar
        repCounter.reset()
        edit { w -> WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(reps = (it.reps + delta).coerceAtLeast(0)) } }
    }

    /**
     * Completa la serie actual. En superserie pasa al siguiente ejercicio del grupo sin descanso;
     * al cerrar la ronda arranca el descanso. Devuelve true si empieza un descanso.
     */
    private val _record = MutableStateFlow(false)

    /** Durante unos segundos tras marcar una serie que es récord (se ve en el descanso). */
    val record: StateFlow<Boolean> = _record.asStateFlow()
    private var recordJob: Job? = null

    /** Cambia cuando llegan marcas o historial del móvil (para recalcular la pista de récord). */
    val recordsVersion: kotlinx.coroutines.flow.Flow<Pair<Any, Any>> = kotlinx.coroutines.flow.combine(store.settings, store.history) { s, h -> s to h }

    /**
     * Peso orientativo (kg) para [reps] a [rpe] con el 1RM estimado reciente (historial del reloj o, si no,
     * el mejor que envía el móvil), ajustado al material del gimnasio activo; null sin reps o sin marca.
     */
    fun targetRpeLoadKg(exercise: Exercise, reps: Int, rpe: Double): Double? {
        val s = store.settings.value
        val e1rm = if (reps <= 0) {
            null
        } else {
            com.bestrong.core.stats.RpeLoad.recentOneRepMax(exercise.id, store.history.value, System.currentTimeMillis())
                ?: s.bestOneRepMaxKg[exercise.id]?.takeIf { it > 0 }
        }
        return e1rm?.let {
            val kg = com.bestrong.core.stats.RpeLoad.weightFor(it, reps, rpe)
            com.bestrong.core.stats.RpeLoad.roundToLoadable(kg, s.unit, s.stepKgFor(exercise.id), s.gymFor(exercise.id), exercise.equipment)
        }
    }

    /** Mejor 1RM estimado hasta ahora (historial, reloj y lo hecho hoy); null si no hay marca previa. */
    fun bestOneRepMax(exerciseId: String): Double? {
        val local = WorkoutStats.personalRecords(store.history.value)[exerciseId]
        val today = _workout.value?.exercises.orEmpty().filter { it.exercise.id == exerciseId }
            .flatMap { it.completedSets }.filter { it.countsForRecords }.map { it.estimatedOneRepMax }
        return (listOfNotNull(store.settings.value.bestOneRepMaxKg[exerciseId], local?.get(RecordType.BEST_ONE_REP_MAX)?.value) + today)
            .maxOrNull()?.takeIf { it > 0 }
    }

    /**
     * Supera tu mejor 1RM estimado o peso máximo en ese ejercicio: las marcas de todo el historial
     * (llegan del móvil), las de lo hecho solo en el reloj y las series anteriores de esta sesión.
     * Sin ninguna marca previa no hay récord que batir.
     */
    private fun isRecord(exerciseId: String, tracking: TrackingType, set: WorkoutSet): Boolean {
        val s = store.settings.value
        val local = WorkoutStats.personalRecords(store.history.value)[exerciseId]
        val earlier = _workout.value?.exercises.orEmpty().filter { it.exercise.id == exerciseId }
            .flatMap { it.completedSets }.filter { it.id != set.id && it.countsForRecords }
        val best1rm = listOfNotNull(s.bestOneRepMaxKg[exerciseId], local?.get(RecordType.BEST_ONE_REP_MAX)?.value) + earlier.map { it.estimatedOneRepMax }
        val heaviest = listOfNotNull(s.heaviestKg[exerciseId], local?.get(RecordType.HEAVIEST_WEIGHT)?.value) + earlier.map { it.weightKg }
        val eligible = tracking == TrackingType.WEIGHT_REPS && set.countsForRecords && best1rm.isNotEmpty()
        return eligible && (set.estimatedOneRepMax > best1rm.max() + 1e-6 || set.weightKg > heaviest.max() + 1e-6)
    }

    private fun celebrateRecord() {
        context.haptic(Haptics.RECORD)
        _record.value = true
        recordJob?.cancel()
        recordJob = scope.launch {
            delay(RECORD_BANNER_MS)
            _record.value = false
        }
    }

    fun completeSet(): Boolean {
        stopTempo()
        val w0 = _workout.value ?: return false
        val index = _exerciseIndex.value
        val we = currentExercise() ?: return false
        val s = currentSet() ?: return false
        // Cardio: el primer toque arranca el cronómetro; el segundo lo para y completa la serie
        if (we.exercise.tracking == TrackingType.CARDIO && !s.isRunning) {
            stopRest()
            edit { WorkoutEditor.startCardio(it, we.id, s.id) }
            context.haptic(Haptics.SET_DONE)
            return false
        }
        edit { w -> WorkoutEditor.toggleSetCompleted(withCountedReps(w, we, s), we.id, s.id) }
        repCounter.reset()
        val updated = _workout.value ?: w0
        val done = updated.exercises.firstOrNull { it.id == we.id }?.sets?.firstOrNull { it.id == s.id }
        if (done != null && isRecord(we.exercise.id, we.exercise.tracking, done)) celebrateRecord() else context.haptic(Haptics.SET_DONE)
        val (next, restSeconds) = WorkoutEditor.afterSetCompleted(updated, index, store.settings.value.warmupRestSeconds)
        next?.let { _exerciseIndex.value = it }
        return if (next != null && restSeconds > 0) {
            startRest(restSeconds)
            true
        } else {
            // Sin descanso (superserie, drop): un descanso anterior que siguiera corriendo no vibra a mitad de serie
            stopRest()
            false
        }
    }

    /** Repeticiones contadas por el acelerómetro (y RPE por la velocidad) en la serie que se completa. */
    private fun withCountedReps(w: Workout, we: WorkoutExercise, s: WorkoutSet): Workout {
        val counted = repCounter.reps.value
        val velocities = repCounter.velocities.value
        val rir = if (_repCounting.value) BarVelocity.estimatedRir(velocities) else null
        _velocityInfo.value = velocities.lastOrNull()?.let { VelocityInfo(it, rir) }
        val counts = _repCounting.value && counted > 0 && we.exercise.tracking in REP_TRACKING
        return if (counts) WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(reps = counted, rpe = it.rpe ?: rir?.let(BarVelocity::rpeFromRir)) } else w
    }

    // --- Descanso ----------------------------------------------------------------------------

    private fun startRest(seconds: Int) {
        _rest.value = Rest(System.currentTimeMillis() + seconds * 1000L, seconds)
        _recovered.value = false
        restPeakBpm = setPeakBpm
        setPeakBpm = 0
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
        store.activeRest = null
        releaseRestWakeLock()
        repCounter.reset()
    }

    /**
     * Con la pantalla apagada la CPU duerme y el delay() se retrasa: se mantiene despierta
     * (WakeLock parcial con tope) para que las vibraciones lleguen a su hora.
     */
    private fun scheduleRestEnd() {
        restJob?.cancel()
        val r = _rest.value ?: return
        store.activeRest = r
        val remaining = r.endsAtEpochMs - System.currentTimeMillis()
        acquireRestWakeLock(remaining.coerceAtLeast(0) + WAKE_LOCK_MARGIN_MS)
        restJob = scope.launch {
            if (remaining > 10_000) {
                delay(remaining - 10_000)
                context.haptic(Haptics.REST_WARNING) // aviso: quedan 10 s
                delay(10_000)
            } else {
                delay(remaining.coerceAtLeast(0))
            }
            _rest.value = null
            store.activeRest = null
            repCounter.reset()
            context.haptic(store.settings.value.restVibration.pattern)
            releaseRestWakeLock()
        }
    }

    private fun acquireRestWakeLock(timeoutMs: Long) {
        releaseRestWakeLock()
        restWakeLock = ContextCompat.getSystemService(context, PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeStrong:rest")
            ?.apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
    }

    private fun releaseRestWakeLock() {
        restWakeLock?.takeIf { it.isHeld }?.release()
        restWakeLock = null
    }

    // --- Calentamiento, progreso y tempo ------------------------------------------------------

    /** Series de aproximación para el ejercicio actual (barra, 40 %, 60 %, 80 %). */
    fun addWarmup() {
        val we = currentExercise() ?: return
        // Con la barra del ejercicio (o la del gimnasio), como en el móvil
        val s = store.settings.value
        val bar = s.unit.toKg(s.barInUnit(we.exercise.id))
        edit { WorkoutEditor.addWarmup(it, we.id, s.unit, bar) }
    }

    /** Drop set (−20 %); false si con ese peso no se puede bajar más (para avisar). */
    fun addDropSet(): Boolean = addLighter { w, id, step -> WorkoutEditor.addDropSet(w, id, step) }

    /** Dos series de descarga (−10 %) tras la más pesada; false si no se puede bajar más. */
    fun addBackOff(): Boolean = addLighter { w, id, step -> WorkoutEditor.addBackOff(w, id, step) }

    private fun addLighter(add: (Workout, String, Double) -> Workout): Boolean {
        val we = currentExercise()
        val w = _workout.value
        val step = we?.let { store.settings.value.stepKgFor(it.exercise.id) } ?: 0.0
        val changed = we != null && w != null && add(w, we.id, step) != w
        if (changed) edit { add(it, we!!.id, step) }
        return changed
    }

    /** Evolución del 1RM estimado y demás marcas del ejercicio (historial del móvil + del reloj). */
    fun progressFor(exerciseId: String): List<ExerciseProgressPoint> = WorkoutStats.exerciseProgress(exerciseId, store.history.value)

    private val _tempoActive = MutableStateFlow(false)
    val tempoActive: StateFlow<Boolean> = _tempoActive.asStateFlow()
    private var tempoJob: Job? = null

    /**
     * Guía de tempo con vibraciones para la serie actual: un toque largo al empezar a bajar,
     * uno corto en cada pausa y dos al subir. Se para al completar la serie o al volver a pulsar.
     */
    fun toggleTempo() {
        if (_tempoActive.value) {
            stopTempo()
            return
        }
        val we = currentExercise() ?: return
        val tempo = Tempo.parse(we.tempo) ?: return
        val reps = currentSet()?.reps?.takeIf { it > 0 } ?: 10
        _tempoActive.value = true
        tempoJob = scope.launch {
            context.haptic(longArrayOf(0, 60, 120, 60, 120, 60)) // preparados
            delay(1_500)
            repeat(reps) {
                tempo.guidePhases.forEach { (phase, seconds) ->
                    context.haptic(
                        when (phase) {
                            Tempo.Phase.ECCENTRIC -> longArrayOf(0, 180)
                            Tempo.Phase.CONCENTRIC -> longArrayOf(0, 60, 80, 60)
                            else -> longArrayOf(0, 30)
                        },
                    )
                    delay(seconds * 1_000L)
                }
            }
            context.haptic(longArrayOf(0, 400))
            _tempoActive.value = false
        }
    }

    private fun stopTempo() {
        tempoJob?.cancel()
        tempoJob = null
        _tempoActive.value = false
    }

    // --- Pulso: zonas y recuperación, bajo consumo --------------------------------------------

    private var setPeakBpm = 0
    private var restPeakBpm = 0
    private val _recovered = MutableStateFlow(false)

    /** El pulso ya bajó lo suficiente durante el descanso. */
    val recovered: StateFlow<Boolean> = _recovered.asStateFlow()

    fun heartRateZone(bpm: Int?): Int = bpm?.let { HeartRate.zone(it, store.maxHeartRate) } ?: 0

    val maxHeartRate: Int get() = store.maxHeartRate

    /** Ajusta el pulso máximo en pasos de 5 (170–210). */
    fun cycleMaxHeartRate(): Int {
        val next = store.maxHeartRate.let { if (it >= 210) 170 else it + 5 }
        store.maxHeartRate = next
        return next
    }

    fun setLowPower(on: Boolean) {
        _lowPower.value = on
        store.lowPower = on
        if (on) setRepCounting(false)
    }

    // --- Velocidad y calibración ------------------------------------------------------------

    private val _velocityInfo = MutableStateFlow<VelocityInfo?>(null)
    val velocityInfo: StateFlow<VelocityInfo?> = _velocityInfo.asStateFlow()

    fun startCalibration() = repCounter.startCalibration()

    fun finishCalibration(): Float {
        val t = repCounter.finishCalibration()
        store.repThreshold = t
        if (!_repCounting.value) repCounter.stop()
        return t
    }

    // --- Voz y pesos recientes ---------------------------------------------------------------

    /** Aplica una serie dictada («80 kilos 8 repeticiones») a la serie actual. Devuelve false si no entendió nada. */
    fun applySpoken(text: String): Boolean {
        val spoken = VoiceSetParser.parse(text)
        if (spoken.weight == null && spoken.reps == null) return false
        val we = currentExercise() ?: return false
        val s = currentSet() ?: return false
        val kg = spoken.weightKg(store.settings.value.unit)?.div(store.settings.value.weightFactor(we.exercise)) // dicho como total del par
        edit { w ->
            WorkoutEditor.updateSet(w, we.id, s.id) {
                it.copy(
                    weightKg = kg ?: it.weightKg,
                    reps = spoken.reps ?: it.reps,
                    rpe = spoken.rpe ?: it.rpe,
                )
            }
        }
        context.haptic(Haptics.VOICE_OK)
        return true
    }

    /** Últimos 3 pesos distintos usados en el ejercicio (accesos rápidos). */
    fun recentWeights(exerciseId: String): List<Double> =
        store.history.value.asSequence()
            .flatMap { w -> w.exercises.asSequence().filter { it.exercise.id == exerciseId } }
            .flatMap { it.completedSets.asSequence() }
            .filter { it.weightKg > 0 && it.type != com.bestrong.core.model.SetType.WARMUP }
            .map { it.weightKg }
            .distinct()
            .take(3)
            .toList()

    /**
     * RPE de la última serie normal completada del ejercicio actual: pasa al siguiente de 6…10 y luego a sin
     * RPE (corrige el que se estima por la velocidad o lo pone si no se contó). Se sincroniza con el móvil.
     */
    fun cycleLastSetRpe() {
        // La última hecha de todo el entreno: tras la última serie de un ejercicio el actual ya es el siguiente
        val (we, last) = _workout.value?.let(WorkoutEditor::lastCompletedNormal) ?: return
        val choices = listOf(6.0, 7.0, 8.0, 9.0, 10.0)
        val next = when (val r = last.rpe) {
            null -> choices.first()
            else -> choices.firstOrNull { it > r + 1e-9 }
        }
        edit { w -> WorkoutEditor.updateSet(w, we.id, last.id) { it.copy(rpe = next) } }
    }

    fun setCurrentWeight(kg: Double) {
        val we = currentExercise() ?: return
        val s = currentSet() ?: return
        edit { w -> WorkoutEditor.updateSet(w, we.id, s.id) { it.copy(weightKg = kg) } }
    }

    // --- Rutinas creadas en el reloj ---------------------------------------------------------

    /** Guarda una rutina hecha en el reloj (3 series por ejercicio) y la envía al móvil. */
    fun createRoutine(name: String, exercises: List<Exercise>): Routine {
        val routine = Routine(
            name = name,
            exercises = exercises.map { ex ->
                RoutineExercise(exercise = ex, restSeconds = com.bestrong.core.training.RestPolicy.defaultFor(ex))
            },
        )
        store.addRoutine(routine)
        scope.launch { phoneSync.sendWatchRoutine(routine) }
        return routine
    }

    // --- Métricas (llamado desde ExerciseService) --------------------------------------------

    /**
     * Una lectura de pulso con su hora real. Health Services las agrupa en lotes (sobre todo con
     * la pantalla apagada): se pasan todas, no solo la última, para no perder picos.
     */
    fun onHeartRate(bpm: Int, atEpochMs: Long = System.currentTimeMillis()) {
        if (bpm <= 0) return
        hr.add(bpm, atEpochMs)
        val rest = _rest.value
        if (rest == null) {
            if (bpm > setPeakBpm) setPeakBpm = bpm
        } else if (!_recovered.value && restPeakBpm > 0) {
            // Pulso de vuelta a la zona de recuperación: ya se puede hacer la siguiente serie
            val restedSec = ((System.currentTimeMillis() - (rest.endsAtEpochMs - rest.totalSeconds * 1000L)) / 1000).toInt()
            if (HeartRate.isRecovered(bpm, restPeakBpm, restedSec, maxHr = store.maxHeartRate)) {
                _recovered.value = true
                context.haptic(Haptics.RECOVERED)
            }
        }
        publishHeartRate()
    }

    /** Media y máximo de la sesión calculados por Health Services (desde que empezó el ejercicio). */
    fun onHeartRateStats(average: Int, max: Int) {
        // Con varios tramos (pesas, cinta…) las estadísticas de Health Services son solo del tramo actual
        if (average <= 0 || multiSegment) return
        platformHr = average to max
        publishHeartRate()
    }

    private fun publishHeartRate() {
        val platform = platformHr
        _metrics.update {
            it.copy(
                heartRate = hr.latest ?: it.heartRate,
                avgHeartRate = platform?.first ?: hr.average,
                maxHeartRate = listOfNotNull(platform?.second, hr.max).maxOrNull(),
            )
        }
        maybeSendMetrics()
    }

    /** Calorías del tramo actual de Health Services (empiezan de cero en cada tramo). */
    fun onCalories(kcal: Double) {
        segmentKcal = kcal.toInt()
        _metrics.update { it.copy(calories = store.closedSegmentsKcal + segmentKcal) }
    }

    /**
     * Health Services cambia de ejercicio (pesas → cinta o al revés): lo medido en el tramo que se
     * cierra se acumula y el nuevo empieza de cero.
     */
    fun onHealthSegmentChanged() {
        store.closedSegmentsKcal += segmentKcal
        segmentKcal = 0
        multiSegment = true
        platformHr = null
        publishHeartRate()
    }

    private fun resetHealthSegments() {
        store.closedSegmentsKcal = 0
        segmentKcal = 0
        multiSegment = false
    }

    private var segmentKcal = 0
    private var multiSegment = false

    /** Envía las métricas al móvil como mucho cada 5 s. */
    private fun maybeSendMetrics() {
        val w = _workout.value ?: return
        val now = System.currentTimeMillis()
        if (now - lastMetricsSentAt < if (_lowPower.value) 30_000 else 5_000) return
        lastMetricsSentAt = now
        val m = _metrics.value
        scope.launch { phoneSync.sendMetrics(LiveMetrics(w.id, m.heartRate, m.avgHeartRate, m.maxHeartRate, m.calories, now)) }
    }
}

/** Margen del WakeLock del descanso sobre su duración (vibración final incluida). */
private const val WAKE_LOCK_MARGIN_MS = 15_000L

/** Función del botón rápido del Galaxy Watch Ultra (y botones físicos asignables). */
enum class StemAction { COMPLETE_SET, SKIP_REST, NEXT_EXERCISE }

/** Ejercicios en los que el acelerómetro cuenta repeticiones (no los de tiempo ni el cardio). */
private val REP_TRACKING = setOf(TrackingType.WEIGHT_REPS, TrackingType.REPS_ONLY, TrackingType.DISTANCE)

/** Dónde queda el ejercicio que se estaba viendo tras una edición del móvil (o el siguiente pendiente). */
private fun keptExerciseIndex(local: Workout?, remote: Workout, index: Int): Int {
    val currentId = local?.exercises?.getOrNull(index)?.id
    val at = remote.exercises.indexOfFirst { it.id == currentId }
    return when {
        at >= 0 -> at
        else -> WorkoutEditor.nextPendingSet(remote)?.first ?: index.coerceIn(0, (remote.exercises.size - 1).coerceAtLeast(0))
    }
}

/** Sin rellenar desde el historial (sesiones de programa, con las cargas ya calculadas). */
private val EMPTY_LOOKUP: (String) -> List<WorkoutSet> = { emptyList() }
