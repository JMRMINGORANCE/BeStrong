package com.bestrong.mobile

import android.util.Log
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.training.Progression
import com.bestrong.core.training.ProgressionCap
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.workout.RestTimerController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** Últimas métricas recibidas del reloj durante el entreno. */
@Singleton
class WatchMetricsStore @Inject constructor() {
    val metrics = MutableStateFlow<LiveMetrics?>(null)
}

/** Lógica compartida al empezar y terminar entrenamientos. */
@Singleton
class WorkoutActions @Inject constructor(
    private val repository: BeStrongRepository,
    private val settings: SettingsStore,
    private val healthConnect: HealthConnectExporter,
    private val restTimer: RestTimerController,
    private val planning: PlanningStore,
    private val energy: com.bestrong.mobile.health.EnergyCalculator,
) {
    /**
     * Calorías de un entreno terminado (las del reloj si se midieron; si no, pulso o estimación), como al
     * terminarlo en el entreno. Para terminar desde fuera (olvidado) o tras corregir duración o series.
     */
    suspend fun withEnergy(workout: Workout): Workout =
        energy.complete(workout, watchKcal = workout.calories?.takeIf { workout.caloriesSource == com.bestrong.core.model.CaloriesSource.WATCH })

    /**
     * Termina el entreno de la pareja, si hay uno abierto, guardando lo que hizo (aparte: no cuenta para tus
     * récords). Al terminar o descartar el tuyo: sin esto quedaría huérfano, sin forma de volver a él.
     */
    suspend fun closePartner(withGym: Boolean = true) {
        val current = repository.takePartnerActive() ?: return
        // Su descanso se acaba con su entreno (el tuyo, si lo hay, sigue)
        restTimer.skipFor(current.id)
        val finished = WorkoutEditor.finish(current, System.currentTimeMillis()).let { if (withGym) withGym(it) else it }
        if (finished.exercises.isNotEmpty()) repository.saveWorkout(finished)
    }

    /** Serie marcada en el reloj: el descanso del móvil pasa a ser el suyo (si no, el de la serie anterior avisa a destiempo). */
    fun followRemoteRest(before: Workout?, after: Workout) {
        val left = com.bestrong.core.session.RestSync.remainingAfterRemote(before, after, System.currentTimeMillis(), settings.settings.value.warmupRestSeconds) ?: return
        if (left > 0) restTimer.start(left, after.id) else restTimer.skipFor(after.id)
    }

    /** Descarta el entreno de la pareja, con su descanso. */
    suspend fun discardPartner() {
        restTimer.skipFor(repository.partnerWorkout.value?.id)
        repository.setPartnerActive(null)
    }

    /** El entreno en texto (series y RPE), para mandarlo al entrenador o a un grupo. */
    fun shareText(workout: Workout, exerciseName: (com.bestrong.core.model.Exercise) -> String): String = settings.settings.value.let { prefs ->
        com.bestrong.core.io.CoachReport.text(
            listOf(workout.copy(finishedAtEpochMs = workout.finishedAtEpochMs ?: workout.startedAtEpochMs)),
            fromEpochMs = 0,
            unit = prefs.unit,
            exerciseName = exerciseName,
            dumbbellTotal = prefs.dumbbellTotal,
        )
    }

    /** Descanso en marcha (para mostrarlo fuera del entreno, p. ej. en Inicio). */
    val rest get() = restTimer.rest

    /**
     * Pulso y calorías finales del reloj sobre lo guardado, sin pisar la valoración que se pone a la vez. Si el
     * entreno aún no está guardado (el móvil lo está terminando), se dejan pendientes para [applyPendingWatchHealth].
     */
    suspend fun mergeWatchHealth(h: com.bestrong.core.sync.WorkoutHealthData) {
        var hadSamples = true
        var oldKcal: Pair<Int?, Int?>? = null
        val merged = repository.updateWorkout(h.workoutId) { saved ->
            hadSamples = saved.heartRateSamples.isNotEmpty()
            oldKcal = saved.calories to saved.activeCalories
            energy.complete(
                saved.copy(
                    avgHeartRate = h.avgHeartRate ?: saved.avgHeartRate,
                    maxHeartRate = h.maxHeartRate ?: saved.maxHeartRate,
                    heartRateSamples = h.heartRateSamples.ifEmpty { saved.heartRateSamples },
                ),
                watchKcal = h.calories ?: saved.calories?.takeIf { saved.caloriesSource == com.bestrong.core.model.CaloriesSource.WATCH },
            )
        }
        if (merged == null) {
            repository.savePendingHealth(h.workoutId, com.bestrong.core.sync.SyncContract.encodeHealth(h).decodeToString())
            // Si el móvil lo guardó justo entre medias, ya no lo recogerá: se aplica ahora
            if (repository.storedWorkout(h.workoutId) != null) applyPendingWatchHealth(h.workoutId)
            return
        }
        // La sesión ya se exportó al terminar. Si cambian las calorías (llegan las del reloj), se vuelve a exportar
        // entera (mismos ids, versión nueva: se actualiza, no se duplica); si no, solo la gráfica de pulso
        when {
            oldKcal != (merged.calories to merged.activeCalories) -> exportToHealthConnectIfEnabled(merged)
            !hadSamples -> exportHeartRateIfEnabled(merged)
        }
    }

    /** Tras guardar un entreno terminado en el móvil: lo del reloj que llegó antes, si es de ese entreno. */
    suspend fun applyPendingWatchHealth(workoutId: String) {
        val raw = repository.takePendingHealth(workoutId) ?: return
        runCatching { com.bestrong.core.sync.SyncContract.decodeHealth(raw.encodeToByteArray()) }.getOrNull()?.let { mergeWatchHealth(it) }
    }

    /** Se está viendo el entreno de la pareja (lo marca la pantalla del entreno; lo leen las teclas de volumen). */
    val partnerOnScreen = MutableStateFlow(false)

    /**
     * Completa la siguiente serie pendiente y arranca su descanso (botón de la notificación,
     * botones de volumen). Devuelve false si no queda ninguna.
     */
    suspend fun completeNextSet(visible: Boolean = false): Boolean {
        // La serie se elige dentro de updateActive, sobre el último estado guardado: dos toques
        // seguidos (o un cambio del reloj) completan series distintas y nunca desmarcan una hecha
        // (upcomingSet solo devuelve series sin completar). La que toca de verdad: en superseries o con ejercicios
        // hechos fuera de orden, no la primera pendiente de la lista
        val next: (Workout) -> Workout = { w ->
            WorkoutEditor.upcomingSet(w)?.let { (index, set) -> WorkoutEditor.toggleSetCompleted(w, w.exercises[index].id, set.id) } ?: w
        }
        // [visible]: la del que está en pantalla (teclas de volumen con el entreno de la pareja a la vista)
        val partner = visible && partnerOnScreen.value && repository.partnerWorkout.value != null
        val (before, updated) = (if (partner) repository.updatePartnerActive(next) else repository.updateActive(next)) ?: return false
        return WorkoutEditor.upcomingSet(before)?.let { (index, _) ->
            val (_, rest) = WorkoutEditor.afterSetCompleted(updated, index, settings.settings.value.warmupRestSeconds)
            if (rest > 0) restTimer.start(rest, updated.id) else restTimer.skipFor(updated.id)
        } != null
    }

    /**
     * Series con las que rellenar un ejercicio al empezar: sugerencia de sobrecarga progresiva
     * si está activada; si no, lo que se hizo la última vez. Con [athlete], con el historial de la pareja.
     */
    fun prefillLookup(athlete: String? = null): (String) -> List<WorkoutSet> {
        // La pareja con su propio historial (sus pesos, no los tuyos)
        val history = if (athlete != null) repository.partnerHistory.value.filter { it.athlete == athlete } else repository.history.value
        val s = settings.settings.value
        // Con varios gimnasios, lo último hecho en este (en el del hotel las mancuernas no son las de siempre)
        val gymName = s.activeGym.name.takeIf { s.gyms.size > 1 }
        return { id ->
            val ex = repository.exercise(id)
            val base = WorkoutStats.atGym(history, gymName, id)
            val suggestion = if (s.progressionEnabled && ex != null) Progression.suggest(ex, base, s.stepKgFor(ex.id), repRange = s.repRangeFor(ex.id)) else null
            val previous = WorkoutStats.previousSets(id, base)
            val sets = suggestion?.sets ?: previous
            if (ex == null) sets else loadable(ex, sets, suggestion?.kind, previous, history)
        }
    }

    /**
     * Ajusta unas series propuestas a lo que se puede montar en el gimnasio activo: tope de subida semanal
     * (Planificación), barra/discos/mancuernas (con la barra del ejercicio) y, si tocaba subir y el redondeo
     * deja el mismo peso, el siguiente que se pueda montar (si no, la subida se perdería). La asistencia
     * (peso negativo) se deja tal cual. Lo usan el relleno al empezar y la sugerencia del entreno.
     */
    fun loadable(
        ex: com.bestrong.core.model.Exercise,
        sets: List<WorkoutSet>,
        kind: com.bestrong.core.training.SuggestionKind?,
        previous: List<WorkoutSet> = WorkoutStats.previousSets(ex.id, repository.history.value),
        history: List<Workout> = repository.history.value,
    ): List<WorkoutSet> {
        val s = settings.settings.value
        val cap = planning.state.value.weeklyCap
        val lastWeekBest = cap?.let { ProgressionCap.lastWeekBest(ex.id, history, System.currentTimeMillis()) }
        fun capped(kg: Double) = if (cap == null) kg else ProgressionCap.limitIn(s.unit, kg, lastWeekBest, cap, s.stepKgFor(ex.id))
        // En libras se redondea con los discos de libras (como la calculadora de discos)
        val gym = s.gymFor(ex.id)
        val raise = kind == com.bestrong.core.training.SuggestionKind.INCREASE_WEIGHT
        val before = previous.filter { it.type != com.bestrong.core.model.SetType.WARMUP }
        return sets.mapIndexed { i, set ->
            val target = capped(set.weightKg)
            val rounded = if (target > 0) gym.nearestLoad(target, ex.equipment, s.unit) else target
            val prev = before.getOrNull(i)?.weightKg
            // Subida perdida al redondear: el siguiente peso montable, si el tope semanal lo permite
            val lost = prev != null && prev > 0 && rounded <= prev + 1e-6
            val above = if (raise && lost) gym.loadAbove(prev!!, ex.equipment, s.unit) else null
            set.copy(weightKg = above?.takeIf { it <= capped(it) + 1e-6 } ?: rounded)
        }
    }

    /** Si el tope semanal de Planificación recorta alguna de estas series (para decirlo junto a la sugerencia). */
    fun cappedByWeek(ex: com.bestrong.core.model.Exercise, sets: List<WorkoutSet>, history: List<Workout> = repository.history.value): Boolean {
        val cap = planning.state.value.weeklyCap ?: return false
        val lastWeekBest = ProgressionCap.lastWeekBest(ex.id, history, System.currentTimeMillis())
        val step = settings.settings.value.stepKgFor(ex.id)
        return sets.any { it.weightKg > ProgressionCap.limit(it.weightKg, lastWeekBest, cap, step) + 1e-6 }
    }

    /**
     * Si la rutina tiene gimnasio (y sigue existiendo), pasa a ser el activo: antes de rellenar, para que los
     * pesos salgan con su material. Para empezarla, repetirla o repetir un entreno que salió de ella
     * ([doneAtGymName]: dónde se hizo ese entreno, que manda sobre el de la rutina).
     */
    fun useRoutineGym(routine: com.bestrong.core.model.Routine?, doneAtGymName: String? = null) {
        val gyms = settings.settings.value.gyms
        // Al repetir un entreno: el gimnasio donde se hizo (si sigue con ese nombre); si no, el de la rutina
        val id = gyms.firstOrNull { it.name == doneAtGymName }?.id ?: routine?.gymId?.takeIf { id -> gyms.any { it.id == id } } ?: return
        settings.update { it.copy(activeGymId = id) }
    }

    /** Nombre del gimnasio activo para guardarlo en el entreno (solo con varios perfiles: con uno no aporta). */
    fun currentGymName(): String? = settings.settings.value.takeIf { it.gyms.size > 1 }?.activeGym?.name

    /** 2 con «suma de las dos mancuernas» en un ejercicio con mancuernas (el peso del par, como en pantalla); si no, 1. */
    fun weightFactor(exercise: com.bestrong.core.model.Exercise): Int =
        if (settings.settings.value.dumbbellTotal && exercise.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1

    /** El entreno con el gimnasio donde se ha hecho, si aún no lo tiene (al terminarlo en el móvil o en el reloj). */
    fun withGym(workout: Workout): Workout = if (workout.gymName != null) workout else workout.copy(gymName = currentGymName())

    suspend fun exportHeartRateIfEnabled(workout: Workout) {
        if (!settings.settings.value.healthConnectExport) return
        runCatching { healthConnect.exportHeartRate(workout) }.onFailure { Log.w("HealthConnect", "Exportación del pulso fallida", it) }
    }

    /** Al borrar un entreno, también su sesión en Health Connect (si se exportaba). */
    suspend fun deleteFromHealthConnectIfEnabled(workout: Workout) {
        if (!settings.settings.value.healthConnectExport) return
        runCatching { healthConnect.deleteWorkout(workout) }.onFailure { Log.w("HealthConnect", "Borrado fallido", it) }
    }

    suspend fun exportToHealthConnectIfEnabled(workout: Workout) {
        if (!settings.settings.value.healthConnectExport) return
        runCatching { healthConnect.export(workout) }.onFailure { Log.w("HealthConnect", "Exportación fallida", it) }
    }

    /** Recupera el entreno descartado y, si se descartó en mitad de un descanso, lo sigue donde iba. */
    suspend fun restoreDiscarded(): Boolean {
        if (!repository.restoreDiscarded()) return false
        repository.restoredRestEnd?.let { (end, total) -> restTimer.restore(end, total) }
        return true
    }

    /** Guarda una medida y, con la exportación activada, también en Health Connect. */
    /**
     * Peso contestado desde el aviso: si hoy ya hay una medida, se le pone (o cambia) el peso en vez de crear una
     * segunda del mismo día.
     */
    suspend fun saveWeighIn(kg: Double) {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        // De la base de datos: con la app cerrada (respuesta desde el aviso) la lista en memoria aún está vacía
        val existing = repository.measurementsOnce().firstOrNull { java.time.Instant.ofEpochMilli(it.dateEpochMs).atZone(zone).toLocalDate() == today }
        // Si el de hoy vino de la báscula, se corrige aquí pero no se exporta (Health Connect ya tiene el suyo)
        saveMeasurement(existing?.copy(weightKg = kg) ?: com.bestrong.core.model.BodyMeasurement(dateEpochMs = System.currentTimeMillis(), weightKg = kg))
    }

    suspend fun saveMeasurement(m: com.bestrong.core.model.BodyMeasurement) {
        repository.saveMeasurement(m)
        if (settings.settings.value.healthConnectExport) {
            runCatching { healthConnect.exportMeasurement(m) }.onFailure { Log.w("HealthConnect", "Medida no exportada", it) }
        }
    }
}
