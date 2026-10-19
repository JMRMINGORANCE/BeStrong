package com.bestrong.wear.data

import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.WearContainer
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Recibe del móvil: rutinas, historial, ajustes y el entreno en curso (en vivo). */
@AndroidEntryPoint
class PhoneSyncService : WearableListenerService() {
    @Inject lateinit var container: WearContainer

    override fun onDataChanged(events: DataEventBuffer) {
        val localNode = runBlocking { container.phoneSync.localNodeId() }
        // Se leen los eventos aquí (el búfer solo vale durante esta llamada) y los cambios se
        // aplican en el hilo principal, como el resto de la sesión (este es un hilo de binder)
        val changes: List<() -> Unit> = events.mapNotNull { event ->
            val item = event.dataItem
            val path = item.uri.path.orEmpty()
            if (path == SyncContract.PATH_ACTIVE_WORKOUT) {
                // Ignoramos los cambios hechos por el propio reloj
                if (item.uri.host == localNode) return@mapNotNull null
                if (event.type == DataEvent.TYPE_DELETED) {
                    { container.session.clearFromRemote() }
                } else {
                    DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                        ?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() }
                        ?.let { w -> { container.session.applyRemote(w) } }
                }
            } else if (event.type == DataEvent.TYPE_DELETED && path.startsWith(SyncContract.PATH_WATCH_ROUTINE_PREFIX)) {
                // El móvil borra el aviso de la rutina al guardarla: ya no hace falta conservarla aparte
                val id = path.removePrefix(SyncContract.PATH_WATCH_ROUTINE_PREFIX)
                return@mapNotNull { container.store.routineDelivered(id) }
            } else if (event.type == DataEvent.TYPE_CHANGED) {
                val payload = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                return@mapNotNull { PhoneSync.applyDataItem(container.store, path, payload) }
            } else {
                null
            }
        }
        runBlocking(Dispatchers.Main.immediate) { changes.forEach { it() } }
        container.refreshSurfaces()
    }
}
