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
        var done: Pair<Workout, Int>? = null
        repository.updateActive { w ->
            val (index, set) = WorkoutEditor.nextPendingSet(w) ?: return@updateActive w
            if (set.completed) return@updateActive w
            WorkoutEditor.toggleSetCompleted(w, w.exercises[index].id, set.id).also { done = it to index }
        }
        val (updated, index) = done ?: return false
        val (_, rest) = WorkoutEditor.afterSetCompleted(updated, index)
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
        val gym = s.activeGym
        val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
        return { id ->
            val ex = repository.exercise(id)
            val sets = (if (s.progressionEnabled && ex != null) Progression.suggest(ex, history, s.unit.stepKg())?.sets else null)
                ?: WorkoutStats.previousSets(id, history)
            // Límite de subida semanal (Planificación): no más de un x % sobre lo mejor de la semana pasada
            val cap = planning.state.value.weeklyCap
            val capped = if (cap == null) {
                sets
            } else {
                val lastWeekBest = WorkoutStats.bestWeightBetween(id, history, weekAgo, Long.MAX_VALUE)
                sets.map { it.copy(weightKg = ProgressionCap.limit(it.weightKg, lastWeekBest, cap)) }
            }
            // Solo pesos que se pueden montar con la barra, discos y mancuernas del gimnasio activo
            if (ex == null || s.unit != com.bestrong.core.util.WeightUnit.KG) capped else capped.map { it.copy(weightKg = if (it.weightKg > 0) gym.nearestLoad(it.weightKg, ex.equipment) else 0.0) }
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
}
