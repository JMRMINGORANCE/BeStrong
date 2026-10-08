package com.bestrong.mobile

import android.app.Application
import android.util.Log
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.training.Progression
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.db.BeStrongDatabase
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.sync.WatchSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.sync.SharedSettings
import com.bestrong.mobile.widget.BeStrongWidget
import com.bestrong.mobile.workout.RestTimerController
import com.bestrong.mobile.workout.WorkoutForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Contenedor de dependencias manual (sin Hilt para mantener el proyecto simple). */
class AppContainer(app: Application) {
    val context: android.content.Context = app
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = BeStrongDatabase.create(app)
    val repository = BeStrongRepository(database.dao(), appScope)
    val settings = SettingsStore(app)
    val watchSync = WatchSync(app)
    val healthConnect = HealthConnectExporter(app)
    val restTimer = RestTimerController(app, appScope) { settings.settings.value.vibrateOnRestEnd }
    /** Últimas métricas recibidas del reloj durante el entreno. */
    val watchMetrics = MutableStateFlow<LiveMetrics?>(null)

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

    @OptIn(FlowPreview::class)
    fun startWatchSync() {
        // Cada cambio en rutinas o historial se replica al reloj.
        appScope.launch {
            repository.routines.debounce(1_000).collect { watchSync.pushRoutines(it) }
        }
        appScope.launch {
            repository.history.debounce(2_000).collect {
                watchSync.pushHistory(it)
                BeStrongWidget.refresh(context)
            }
        }
        appScope.launch {
            settings.settings.map { SharedSettings(it.unit, it.progressionEnabled) }.distinctUntilChanged()
                .collect { watchSync.pushSettings(it) }
        }
        // Entreno en curso compartido en vivo con el reloj + servicio en primer plano
        appScope.launch {
            repository.activeWorkout.drop(1).debounce(300).collect { w ->
                watchSync.pushActive(w)
                if (w != null) WorkoutForegroundService.start(context)
                else {
                    restTimer.skip()
                    watchMetrics.value = null
                }
            }
        }
    }
}

class BeStrongApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startWatchSync()
    }
}
