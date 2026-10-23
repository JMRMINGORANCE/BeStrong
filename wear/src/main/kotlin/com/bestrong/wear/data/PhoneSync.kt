package com.bestrong.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.sync.WorkoutHealthData
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

    /**
     * Pulso y calorías medidos por el reloj de un entreno que terminó el móvil: el móvil los añade
     * al entreno guardado y recalcula el gasto con ellos.
     */
    suspend fun sendWorkoutHealth(data: WorkoutHealthData) {
        val request = PutDataMapRequest.create(SyncContract.PATH_WORKOUT_HEALTH_PREFIX + data.workoutId).apply {
            dataMap.putByteArray(SyncContract.KEY_PAYLOAD, SyncContract.encodeHealth(data))
            dataMap.putLong(SyncContract.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        runCatching { dataClient.putDataItem(request).await() }
            .onFailure { Log.w(TAG, "No se pudieron enviar el pulso y las calorías", it) }
    }

    /** Envía una rutina creada en el reloj (se guarda en el móvil al sincronizar). */
    suspend fun sendWatchRoutine(routine: Routine) {
        val request = PutDataMapRequest.create(SyncContract.PATH_WATCH_ROUTINE_PREFIX + routine.id).apply {
            dataMap.putByteArray(SyncContract.KEY_PAYLOAD, SyncContract.encodeRoutines(listOf(routine)))
            dataMap.putLong(SyncContract.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        runCatching { dataClient.putDataItem(request).await() }
            .onFailure { Log.w(TAG, "No se pudo enviar la rutina", it) }
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

    /**
     * Reenvía los entrenos terminados aquí que el móvil sigue sin tener (si se perdió el aviso, el DataItem se
     * quedaba sin procesar para siempre). El móvil los fusiona por id: un reenvío no duplica ni reexporta.
     */
    suspend fun resendPendingWorkouts() {
        val now = System.currentTimeMillis()
        // Solo los que aún tienen su aviso sin procesar: el móvil lo borra al guardarlos, así que si ya no está, lo
        // recibió (y quizá luego lo borró a propósito: reenviarlo lo resucitaría)
        val items = runCatching { dataClient.dataItems.await() }.getOrNull() ?: return
        val waiting = try {
            items.mapNotNull { it.uri.path?.takeIf { p -> p.startsWith(SyncContract.PATH_FINISHED_WORKOUT_PREFIX) } }
                .map { it.removePrefix(SyncContract.PATH_FINISHED_WORKOUT_PREFIX) }.toSet()
        } finally {
            items.release()
        }
        store.pendingForPhone
            .filter { w -> w.id in waiting && w.finishedAtEpochMs?.let { now - it in RESEND_AFTER_MS..RESEND_WINDOW_MS } == true }
            .forEach { sendFinishedWorkout(it) }
    }

    /** Lee el último estado publicado por el móvil (al abrir la app). */
    suspend fun pullFromPhone(onActive: (Workout) -> Unit) {
        val items = runCatching { dataClient.dataItems.await() }.getOrNull() ?: return
        val local = localNodeId()
        // Decodificar (rutinas e historial de hasta ~90 KB) fuera del hilo principal: al abrir la app no se traba;
        // el entreno en curso se entrega después, en el hilo de quien llama
        val active = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            var remote: Workout? = null
            try {
                items.forEach { item ->
                    val path = item.uri.path.orEmpty()
                    val payload = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                    if (path == SyncContract.PATH_ACTIVE_WORKOUT) {
                        if (item.uri.host != local) payload?.let { runCatching { SyncContract.decodeWorkout(it) }.getOrNull() }?.let { remote = it }
                    } else {
                        applyDataItem(store, path, payload)
                    }
                }
            } finally {
                items.release()
            }
            remote
        }
        active?.let(onActive)
    }

    companion object {
        private const val TAG = "PhoneSync"
        private const val RESEND_AFTER_MS = 10 * 60_000L
        private const val RESEND_WINDOW_MS = 14 * 24 * 60 * 60_000L

        fun applyDataItem(store: WearStore, path: String, payload: ByteArray?) {
            payload ?: return
            when (path) {
                SyncContract.PATH_ROUTINES -> runCatching { SyncContract.decodeRoutines(payload) }.onSuccess(store::setPhoneRoutines)
                SyncContract.PATH_RECENT_HISTORY -> runCatching { SyncContract.decodeWorkouts(payload) }.onSuccess(store::setPhoneHistory)
                SyncContract.PATH_SETTINGS -> runCatching { SyncContract.decodeSettings(payload) }.onSuccess(store::setSettings)
            }
        }
    }
}
