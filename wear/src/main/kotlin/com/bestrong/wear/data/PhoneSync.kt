package com.bestrong.wear.data

import android.content.Context
import android.util.Log
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SyncContract
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/** Comunicación reloj → móvil por la Data Layer. */
class PhoneSync(private val context: Context, private val store: WearStore) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

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
            .onFailure { Log.w("PhoneSync", "No se pudo enviar el entrenamiento", it) }
    }

    /** Lee el último estado publicado por el móvil (al abrir la app). */
    suspend fun pullFromPhone() {
        val items = runCatching { dataClient.dataItems.await() }.getOrNull() ?: return
        try {
            items.forEach { item -> applyDataItem(store, item.uri.path.orEmpty(), DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)) }
        } finally {
            items.release()
        }
    }

    companion object {
        fun applyDataItem(store: WearStore, path: String, payload: ByteArray?) {
            payload ?: return
            when (path) {
                SyncContract.PATH_ROUTINES -> runCatching { SyncContract.decodeRoutines(payload) }.onSuccess(store::setRoutines)
                SyncContract.PATH_RECENT_HISTORY -> runCatching { SyncContract.decodeWorkouts(payload) }.onSuccess(store::setPhoneHistory)
            }
        }
    }
}
