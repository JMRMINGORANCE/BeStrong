package com.bestrong.wear.data

import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.BeStrongWearApp
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/** Recibe del móvil: rutinas, historial, ajustes y el entreno en curso (en vivo). */
class PhoneSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val container = (application as BeStrongWearApp).container
        val localNode = runBlocking { container.phoneSync.localNodeId() }
        events.forEach { event ->
            val item = event.dataItem
            val path = item.uri.path.orEmpty()
            if (path == SyncContract.PATH_ACTIVE_WORKOUT) {
                // Ignoramos los cambios hechos por el propio reloj
                if (item.uri.host == localNode) return@forEach
                if (event.type == DataEvent.TYPE_DELETED) {
                    container.session.clearFromRemote()
                } else {
                    DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                        ?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() }
                        ?.let { container.session.applyRemote(it) }
                }
            } else if (event.type == DataEvent.TYPE_CHANGED) {
                PhoneSync.applyDataItem(container.store, path, DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD))
            }
        }
        container.refreshSurfaces()
    }
}
