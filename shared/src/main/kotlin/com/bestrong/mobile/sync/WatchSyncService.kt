package com.bestrong.mobile.sync

import android.util.Log
import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SyncContract
import com.bestrong.mobile.WatchMetricsStore
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
import com.bestrong.mobile.health.EnergyCalculator
import com.bestrong.mobile.notify.Notifier
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Recibe del Galaxy Watch:
 * - entrenamientos terminados (se guardan en el historial),
 * - el entreno en curso (sincronización en vivo),
 * - rutinas creadas en el reloj,
 * - métricas de pulso/calorías en directo.
 */
@AndroidEntryPoint
class WatchSyncService : WearableListenerService() {

    @Inject lateinit var repository: BeStrongRepository

    @Inject lateinit var actions: WorkoutActions

    @Inject lateinit var watchMetrics: WatchMetricsStore

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var energy: EnergyCalculator

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onDataChanged(events: DataEventBuffer) {
        // El servicio se destruye al volver de onDataChanged: procesamos de forma síncrona.
        runBlocking {
            events.forEach { event ->
                val item = event.dataItem
                val path = item.uri.path.orEmpty()
                when {
                    path.startsWith(SyncContract.PATH_FINISHED_WORKOUT_PREFIX) && event.type == DataEvent.TYPE_CHANGED -> {
                        val received = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                            ?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() } ?: return@forEach
                        // Calorías del reloj + activas (sin el gasto en reposo) con tus datos corporales
                        val workout = energy.complete(received, watchKcal = received.calories)
                        val isNew = repository.historyOnce().none { it.id == workout.id }
                        repository.saveWorkout(workout)
                        repository.clearRemoteActive(workout.id)
                        if (isNew) {
                            val records = WorkoutStats.newRecordsIn(workout, repository.historyOnce())
                            Notifier.records(this@WatchSyncService, workout, records, settings.settings.value.unit)
                        }
                        Log.i(TAG, "Entrenamiento del reloj guardado: ${workout.name}")
                        runCatching { Wearable.getDataClient(this@WatchSyncService).deleteDataItems(item.uri).await() }
                        // Un reenvío del mismo entreno no se vuelve a exportar
                        if (isNew) appScope.launch { actions.exportToHealthConnectIfEnabled(workout) }
                    }
                    // Entreno terminado en el móvil: el reloj manda el pulso y las calorías finales
                    path.startsWith(SyncContract.PATH_WORKOUT_HEALTH_PREFIX) && event.type == DataEvent.TYPE_CHANGED -> {
                        val health = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                            ?.let { runCatching { SyncContract.decodeHealth(it) }.getOrNull() }
                        val saved = health?.let { h -> repository.historyOnce().firstOrNull { it.id == h.workoutId } }
                        if (health != null && saved != null) {
                            val merged = energy.complete(
                                saved.copy(
                                    avgHeartRate = health.avgHeartRate ?: saved.avgHeartRate,
                                    maxHeartRate = health.maxHeartRate ?: saved.maxHeartRate,
                                    heartRateSamples = health.heartRateSamples.ifEmpty { saved.heartRateSamples },
                                ),
                                watchKcal = health.calories ?: saved.calories?.takeIf { saved.caloriesSource == CaloriesSource.WATCH },
                            )
                            repository.saveWorkout(merged)
                            // La sesión ya se exportó al terminar: ahora solo se añade la gráfica de pulso
                            if (saved.heartRateSamples.isEmpty()) appScope.launch { actions.exportHeartRateIfEnabled(merged) }
                        }
                        runCatching { Wearable.getDataClient(this@WatchSyncService).deleteDataItems(item.uri).await() }
                    }
                    // Rutina creada en el reloj: se guarda y se borra el DataItem
                    path.startsWith(SyncContract.PATH_WATCH_ROUTINE_PREFIX) && event.type == DataEvent.TYPE_CHANGED -> {
                        DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                            ?.let { runCatching { SyncContract.decodeRoutines(it) }.getOrNull() }
                            ?.forEach { routine -> repository.saveRoutine(routine) }
                        runCatching { Wearable.getDataClient(this@WatchSyncService).deleteDataItems(item.uri).await() }
                    }
                    path == SyncContract.PATH_ACTIVE_WORKOUT && isFromOtherNode(item.uri.host) -> {
                        if (event.type == DataEvent.TYPE_DELETED) {
                            repository.clearRemoteActive(null)
                        } else {
                            DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                                ?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() }
                                ?.let { repository.applyRemoteActive(it) }
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == SyncContract.PATH_METRICS) {
            runCatching { SyncContract.decodeMetrics(event.data) }.onSuccess { watchMetrics.metrics.value = it }
        }
    }

    private fun isFromOtherNode(host: String?): Boolean {
        val local = runCatching { runBlocking { Wearable.getNodeClient(this@WatchSyncService).localNode.await().id } }.getOrNull()
        return host != null && host != local
    }

    private companion object {
        const val TAG = "WatchSyncService"
    }
}
