package com.bestrong.mobile

import android.app.Application
import android.content.Context
import android.util.Log
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.training.Progression
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.sync.WatchSync
import com.bestrong.mobile.widget.BeStrongWidget
import com.bestrong.mobile.workout.RestTimerController
import com.bestrong.mobile.workout.WorkoutForegroundService
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
) {
    /**
     * Series con las que rellenar un ejercicio al empezar: sugerencia de sobrecarga progresiva
     * si está activada; si no, lo que se hizo la última vez.
     */
    fun prefillLookup(): (String) -> List<WorkoutSet> {
        val history = repository.history.value
        val s = settings.settings.value
        return { id ->
            val ex = repository.exercise(id)
            (if (s.progressionEnabled && ex != null) Progression.suggest(ex, history, s.unit.stepKg())?.sets else null)
                ?: WorkoutStats.previousSets(id, history)
        }
    }

    suspend fun exportToHealthConnectIfEnabled(workout: Workout) {
        if (!settings.settings.value.healthConnectExport) return
        runCatching { healthConnect.export(workout) }.onFailure { Log.w("HealthConnect", "Exportación fallida", it) }
    }
}

/** Replica rutinas, historial, ajustes y el entreno en curso al reloj; mantiene el widget al día. */
@Singleton
class AppSync @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val repository: BeStrongRepository,
    private val settings: SettingsStore,
    private val watchSync: WatchSync,
    private val restTimer: RestTimerController,
    private val watchMetrics: WatchMetricsStore,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            repository.routines.debounce(1_000).collect { watchSync.pushRoutines(it) }
        }
        scope.launch {
            repository.history.debounce(2_000).collect {
                watchSync.pushHistory(it)
                BeStrongWidget.refresh(context)
            }
        }
        scope.launch {
            settings.settings.map { SharedSettings(it.unit, it.progressionEnabled) }.distinctUntilChanged()
                .collect { watchSync.pushSettings(it) }
        }
        // Entreno en curso compartido en vivo con el reloj + servicio en primer plano
        scope.launch {
            repository.activeWorkout.drop(1).debounce(300).collect { w ->
                watchSync.pushActive(w)
                if (w != null) WorkoutForegroundService.start(context)
                else {
                    restTimer.skip()
                    watchMetrics.metrics.value = null
                }
            }
        }
    }
}

@HiltAndroidApp
class BeStrongApplication : Application() {
    @Inject lateinit var appSync: AppSync

    override fun onCreate() {
        super.onCreate()
        appSync.start()
    }
}
