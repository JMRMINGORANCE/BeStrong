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
import kotlinx.coroutines.flow.map

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
) {
    /**
     * Completa la siguiente serie pendiente y arranca su descanso (botón de la notificación,
     * botones de volumen). Devuelve false si no queda ninguna.
     */
    suspend fun completeNextSet(): Boolean {
        // La serie se elige dentro de updateActive, sobre el último estado guardado: dos toques
        // seguidos (o un cambio del reloj) completan series distintas y nunca desmarcan una hecha
        // (nextPendingSet solo devuelve series sin completar)
        val (before, updated) = repository.updateActive { w ->
            val (index, set) = WorkoutEditor.nextPendingSet(w) ?: return@updateActive w
            WorkoutEditor.toggleSetCompleted(w, w.exercises[index].id, set.id)
        } ?: return false
        val (index, _) = WorkoutEditor.nextPendingSet(before) ?: return false
        val (_, rest) = WorkoutEditor.afterSetCompleted(updated, index, settings.settings.value.warmupRestSeconds)
        if (rest > 0) restTimer.start(rest) else restTimer.skip()
        return true
    }

    /**
     * Series con las que rellenar un ejercicio al empezar: sugerencia de sobrecarga progresiva
     * si está activada; si no, lo que se hizo la última vez.
     */
    fun prefillLookup(): (String) -> List<WorkoutSet> {
        val history = repository.history.value
        val s = settings.settings.value
        return { id ->
            val ex = repository.exercise(id)
            val suggestion = if (s.progressionEnabled && ex != null) Progression.suggest(ex, history, s.stepKgFor(ex.id), repRange = s.repRangeFor(ex.id)) else null
            val previous = WorkoutStats.previousSets(id, history)
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
        val lastWeekBest = cap?.let { WorkoutStats.bestWeightBetween(ex.id, history, System.currentTimeMillis() - 7 * 86_400_000L, Long.MAX_VALUE) }
        fun capped(kg: Double) = if (cap == null) kg else ProgressionCap.limitIn(s.unit, kg, lastWeekBest, cap, s.stepKgFor(ex.id))
        if (s.unit != com.bestrong.core.util.WeightUnit.KG) return sets.map { it.copy(weightKg = capped(it.weightKg)) }
        val gym = s.gymFor(ex.id)
        val raise = kind == com.bestrong.core.training.SuggestionKind.INCREASE_WEIGHT
        val before = previous.filter { it.type != com.bestrong.core.model.SetType.WARMUP }
        return sets.mapIndexed { i, set ->
            val target = capped(set.weightKg)
            val rounded = if (target > 0) gym.nearestLoad(target, ex.equipment) else target
            val prev = before.getOrNull(i)?.weightKg
            // Subida perdida al redondear: el siguiente peso montable, si el tope semanal lo permite
            val lost = prev != null && prev > 0 && rounded <= prev + 1e-6
            val above = if (raise && lost) gym.loadAbove(prev!!, ex.equipment) else null
            set.copy(weightKg = above?.takeIf { it <= capped(it) + 1e-6 } ?: rounded)
        }
    }

    suspend fun exportHeartRateIfEnabled(workout: Workout) {
        if (!settings.settings.value.healthConnectExport) return
        runCatching { healthConnect.exportHeartRate(workout) }.onFailure { Log.w("HealthConnect", "Exportación del pulso fallida", it) }
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
    suspend fun saveMeasurement(m: com.bestrong.core.model.BodyMeasurement) {
        repository.saveMeasurement(m)
        if (settings.settings.value.healthConnectExport) {
            runCatching { healthConnect.exportMeasurement(m) }.onFailure { Log.w("HealthConnect", "Medida no exportada", it) }
        }
    }
}
