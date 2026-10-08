package com.bestrong.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.sync.SyncContract
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Comunicación reloj ↔ móvil por la Data Layer. */
@Singleton
class PhoneSync @Inject constructor(@ApplicationContext private val context: Context, private val store: WearStore) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    /**
     * Envía un entrenamiento terminado. Los DataItems se entregan aunque el móvil
     * esté desconectado en ese momento (se sincronizan al reconectar).
     */
    suspend fun sendFinishedWorkout(workout: Workout) {
        val request = PutDataMapRequest.create(SyncContract.PATH_FINISHED_WORKOUT_PREFIX + workout.id).apply {
            dataMap.putByteArray(SyncContract.KEY_PAYLOAD, SyncContract.encodeWorkout(workout))
            dataMap.putLong(SyncContract.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        runCatching { dataClient.putDataItem(request).await() }
            .onFailure { Log.w(TAG, "No se pudo enviar el entrenamiento", it) }
    }

    /** Publica el entreno en curso para verlo y editarlo en vivo en el móvil (null = borrar). */
    suspend fun pushActive(workout: Workout?) {
        runCatching {
            if (workout == null) {
                dataClient.deleteDataItems(Uri.Builder().scheme("wear").path(SyncContract.PATH_ACTIVE_WORKOUT).build()).await()
            } else {
                val request = PutDataMapRequest.create(SyncContract.PATH_ACTIVE_WORKOUT).apply {
                    dataMap.putByteArray(SyncContract.KEY_PAYLOAD, SyncContract.encodeWorkout(workout))
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
            }
        }.onFailure { Log.w(TAG, "No se pudo sincronizar el entreno activo", it) }
    }

    /** Pulso y calorías en directo al móvil (mensaje, no se guarda). */
    suspend fun sendMetrics(metrics: LiveMetrics) {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        val bytes = SyncContract.encodeMetrics(metrics)
        nodes.forEach { node -> runCatching { messageClient.sendMessage(node.id, SyncContract.PATH_METRICS, bytes).await() } }
    }

    suspend fun localNodeId(): String? = runCatching { nodeClient.localNode.await().id }.getOrNull()

    /** Lee el último estado publicado por el móvil (al abrir la app). */
    suspend fun pullFromPhone(onActive: (Workout) -> Unit) {
        val items = runCatching { dataClient.dataItems.await() }.getOrNull() ?: return
        val local = localNodeId()
        try {
            items.forEach { item ->
                val path = item.uri.path.orEmpty()
                val payload = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                if (path == SyncContract.PATH_ACTIVE_WORKOUT) {
                    if (item.uri.host != local) payload?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() }?.let(onActive)
                } else {
                    applyDataItem(store, path, payload)
                }
            }
        } finally {
            items.release()
        }
    }

    companion object {
        private const val TAG = "PhoneSync"

        fun applyDataItem(store: WearStore, path: String, payload: ByteArray?) {
            payload ?: return
            when (path) {
                SyncContract.PATH_ROUTINES -> runCatching { SyncContract.decodeRoutines(payload) }.onSuccess(store::setRoutines)
                SyncContract.PATH_RECENT_HISTORY -> runCatching { SyncContract.decodeWorkouts(payload) }.onSuccess(store::setPhoneHistory)
                SyncContract.PATH_SETTINGS -> runCatching { SyncContract.decodeSettings(payload) }.onSuccess(store::setSettings)
            }
        }
    }
}
