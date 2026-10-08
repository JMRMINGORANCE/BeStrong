package com.bestrong.mobile.sync

import android.util.Log
import com.bestrong.core.sync.SyncContract
import com.bestrong.mobile.BeStrongApplication
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Recibe los entrenamientos terminados en el Galaxy Watch y los guarda en el historial. */
class WatchSyncService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val container = (application as BeStrongApplication).container
        val received = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path.orEmpty().startsWith(SyncContract.PATH_FINISHED_WORKOUT_PREFIX) }
            .mapNotNull { event ->
                val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                bytes?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() }?.let { it to event.dataItem.uri }
            }
        if (received.isEmpty()) return

        // El servicio se destruye al volver de onDataChanged: guardamos de forma síncrona.
        runBlocking {
            received.forEach { (workout, uri) ->
                container.repository.saveWorkout(workout)
                Log.i(TAG, "Entrenamiento del reloj guardado: ${workout.name}")
                runCatching { Wearable.getDataClient(this@WatchSyncService).deleteDataItems(uri).await() }
            }
        }
        container.appScope.launch {
            received.forEach { (workout, _) -> container.exportToHealthConnectIfEnabled(workout) }
        }
    }

    private companion object {
        const val TAG = "WatchSyncService"
    }
}
