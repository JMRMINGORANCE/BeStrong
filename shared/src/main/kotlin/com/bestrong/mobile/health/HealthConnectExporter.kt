package com.bestrong.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.Energy
import com.bestrong.core.model.Workout
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exporta entrenamientos a Health Connect. Samsung Health los lee desde ahí,
 * así que también aparecen en la app de Samsung.
 */
@Singleton
class HealthConnectExporter @Inject constructor(@ApplicationContext private val context: Context) {

    /** Imprescindible para exportar (las calorías y el pulso son opcionales). */
    val sessionPermission = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    private val caloriesPermission = HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    private val heartRatePermission = HealthPermission.getWritePermission(HeartRateRecord::class)

    /** Lo que se pide al activar la exportación (sesión, calorías y pulso). */
    val permissions: Set<String> = setOf(sessionPermission, caloriesPermission, heartRatePermission)

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun granted(): Set<String> =
        if (isAvailable()) client.permissionController.getGrantedPermissions() else emptySet()

    /** Basta con poder escribir la sesión; calorías y pulso se añaden si también están concedidos. */
    suspend fun hasPermissions(): Boolean = sessionPermission in granted()

    suspend fun export(workout: Workout) {
        val granted = granted()
        if (sessionPermission !in granted) return
        val start = Instant.ofEpochMilli(workout.startedAtEpochMs)
        val end = Instant.ofEpochMilli(workout.finishedAtEpochMs ?: return)
        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = null,
                endTime = end,
                endZoneOffset = null,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
                title = workout.name,
                notes = workout.notes.ifBlank { null },
            ),
        )
        // Calorías medidas por el reloj o estimadas (sin reloj); Samsung Health las suma al día
        if (caloriesPermission in granted) {
            workout.calories?.let {
                records += TotalCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = null,
                    endTime = end,
                    endZoneOffset = null,
                    energy = Energy.kilocalories(it.toDouble()),
                )
            }
        }
        // Gráfica de pulso del entreno (una muestra cada ~10 s medida por el reloj)
        if (heartRatePermission in granted && workout.heartRateSamples.isNotEmpty()) {
            val samples = workout.heartRateSamples
                .map { HeartRateRecord.Sample(start.plusSeconds(it.offsetSec.toLong()), it.bpm.toLong()) }
                .filter { it.time in start..end }
            if (samples.isNotEmpty()) {
                records += HeartRateRecord(
                    startTime = start,
                    startZoneOffset = null,
                    endTime = end,
                    endZoneOffset = null,
                    samples = samples,
                )
            }
        }
        client.insertRecords(records)
    }
}
