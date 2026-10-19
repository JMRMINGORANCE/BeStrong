package com.bestrong.mobile.sync

import android.util.Log
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.sync.WorkoutMerge
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

    /**
     * Entreno terminado en el reloj, listo para guardar: ejercicios con los datos completos del móvil (el reloj
     * puede traerlos recortados), calorías del reloj + activas con tus datos corporales y el gimnasio: el ya
     * guardado si es un reenvío ([stored]; aunque luego se cambiara de gimnasio), si no el que trae o el activo.
     */
    private suspend fun completeFromWatch(received: com.bestrong.core.model.Workout, stored: com.bestrong.core.model.Workout?): com.bestrong.core.model.Workout {
        val full = received.copy(exercises = received.exercises.map { we -> we.copy(exercise = repository.exercise(we.exercise.id) ?: we.exercise) })
        val completed = energy.complete(full, watchKcal = received.calories)
        // Ya estaba guardado (terminado también en el móvil o reenvío): se fusionan las series y se conserva lo puesto
        // aquí después (valoración, notas, gimnasio corregido a mano, datos de pulso)
        if (stored != null) {
            return WorkoutMerge.merge(stored, completed).copy(
                finishedAtEpochMs = stored.finishedAtEpochMs ?: completed.finishedAtEpochMs,
                gymName = stored.gymName,
                rating = stored.rating ?: completed.rating,
                notes = stored.notes.ifBlank { completed.notes },
                avgHeartRate = stored.avgHeartRate ?: completed.avgHeartRate,
                maxHeartRate = stored.maxHeartRate ?: completed.maxHeartRate,
                heartRateSamples = stored.heartRateSamples.ifEmpty { completed.heartRateSamples },
            )
        }
        // Uno que ya no existe (renombrado mientras se entrenaba en el reloj) cuenta como el activo
        val known = completed.gymName?.takeIf { n -> settings.settings.value.gyms.any { it.name == n } }
        return actions.withGym(completed.copy(gymName = known))
    }

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
                        val stored = repository.storedWorkout(received.id)
                        // Series hechas en el móvil que el reloj no llegó a recibir: se fusionan con su terminado
                        val withLocal = repository.localCopyOf(received.id)
                            ?.let { WorkoutMerge.merge(it, received).copy(finishedAtEpochMs = received.finishedAtEpochMs) } ?: received
                        val workout = completeFromWatch(withLocal, stored)
                        val isNew = stored == null
                        repository.saveWorkout(workout)
                        repository.clearRemoteActive(workout.id)
                        if (isNew) {
                            val records = WorkoutStats.newRecordsIn(workout, repository.historyOnce())
                            Notifier.records(this@WatchSyncService, workout, records, settings.settings.value.unit)
                        }
                        Log.i(TAG, "Entrenamiento del reloj guardado: ${workout.id}")
                        runCatching { Wearable.getDataClient(this@WatchSyncService).deleteDataItems(item.uri).await() }
                        // Un reenvío del mismo entreno no se vuelve a exportar
                        if (isNew) appScope.launch { actions.exportToHealthConnectIfEnabled(workout) }
                    }
                    // Entreno terminado en el móvil: el reloj manda el pulso y las calorías finales
                    path.startsWith(SyncContract.PATH_WORKOUT_HEALTH_PREFIX) && event.type == DataEvent.TYPE_CHANGED -> {
                        val health = DataMapItem.fromDataItem(item).dataMap.getByteArray(SyncContract.KEY_PAYLOAD)
                            ?.let { runCatching { SyncContract.decodeHealth(it) }.getOrNull() }
                        health?.let { actions.mergeWatchHealth(it) }
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
