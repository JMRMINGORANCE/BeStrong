package com.bestrong.mobile

import android.app.Application
import android.util.Log
import com.bestrong.core.model.Workout
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.db.BeStrongDatabase
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.sync.WatchSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
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
            repository.history.debounce(2_000).collect { watchSync.pushHistory(it) }
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
