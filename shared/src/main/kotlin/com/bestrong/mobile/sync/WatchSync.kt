package com.bestrong.mobile.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.sync.SyncContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await

/** Envía datos al Galaxy Watch por la Wearable Data Layer. */
@Singleton
class WatchSync @Inject constructor(@ApplicationContext private val context: Context) {
    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun pushRoutines(routines: List<Routine>) =
        put(SyncContract.PATH_ROUTINES, SyncContract.encodeRoutines(routines))

    suspend fun pushHistory(history: List<Workout>) =
        put(SyncContract.PATH_RECENT_HISTORY, SyncContract.encodeRecentHistory(history))

    suspend fun pushSettings(settings: SharedSettings) =
        put(SyncContract.PATH_SETTINGS, SyncContract.encodeSettings(settings))

    /** Publica el entreno en curso (o lo borra) para que el reloj lo muestre en vivo. */
    suspend fun pushActive(workout: Workout?) {
        if (workout == null) {
            runCatching {
                dataClient.deleteDataItems(Uri.Builder().scheme("wear").path(SyncContract.PATH_ACTIVE_WORKOUT).build()).await()
            }
        } else {
            put(SyncContract.PATH_ACTIVE_WORKOUT, SyncContract.encodeWorkout(workout), urgent = true)
        }
    }

    private suspend fun put(path: String, payload: ByteArray, urgent: Boolean = false) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putByteArray(SyncContract.KEY_PAYLOAD, payload)
        }.asPutDataRequest().apply { if (urgent) setUrgent() }
        runCatching { dataClient.putDataItem(request).await() }
            .onFailure { Log.w(TAG, "No se pudo sincronizar $path (¿sin Wear OS / Play Services?)", it) }
    }

    suspend fun hasWatch(): Boolean = runCatching { nodeClient.connectedNodes.await().isNotEmpty() }.getOrDefault(false)

    /** Abre BeStrong en el reloj y, si se indica, empieza una rutina. Devuelve false si no hay reloj. */
    suspend fun startOnWatch(routineId: String?): Boolean {
        val nodes = runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())
        if (nodes.isEmpty()) return false
        val uri = Uri.Builder()
            .scheme(SyncContract.DEEP_LINK_SCHEME)
            .authority(SyncContract.DEEP_LINK_HOST)
            .apply { routineId?.let { appendQueryParameter(SyncContract.DEEP_LINK_PARAM_ROUTINE, it) } }
            .build()
        val intent = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(uri)
        val helper = RemoteActivityHelper(context)
        return nodes.any { node ->
            runCatching { helper.startRemoteActivity(intent, node.id).await() }.isSuccess
        }
    }

    private companion object {
        const val TAG = "WatchSync"
    }
}
