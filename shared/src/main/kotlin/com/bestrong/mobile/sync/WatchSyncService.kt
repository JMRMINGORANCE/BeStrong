package com.bestrong.mobile.sync

import android.util.Log
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SyncContract
import com.bestrong.mobile.WatchMetricsStore
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
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

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onDataChanged(events: DataEventBuffer) {
        // El servicio se destruye al volver de onDataChanged: procesamos de forma síncrona.
        runBlocking {
            events.forEach { event ->
                val item = event.dataItem
                val path = item.uri.path.orEmpty()
                when {
                    path.startsWith(SyncContract.PATH_FINISHED_WORKOUT_PREFIX) && event.type == DataEvent.TYPE_CHANGED -> {
                        val workout = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                            ?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() } ?: return@forEach
                        val isNew = repository.historyOnce().none { it.id == workout.id }
                        repository.saveWorkout(workout)
                        repository.clearRemoteActive(workout.id)
                        if (isNew) {
                            val records = WorkoutStats.newRecordsIn(workout, repository.historyOnce())
                            Notifier.records(this@WatchSyncService, workout, records, settings.settings.value.unit)
                        }
                        Log.i(TAG, "Entrenamiento del reloj guardado: ${workout.name}")
                        runCatching { Wearable.getDataClient(this@WatchSyncService).deleteDataItems(item.uri).await() }
                        appScope.launch { actions.exportToHealthConnectIfEnabled(workout) }
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
