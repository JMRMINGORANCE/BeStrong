package com.bestrong.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.Energy
import com.bestrong.core.model.Workout
import java.time.Instant

/**
 * Exporta entrenamientos a Health Connect. Samsung Health los lee desde ahí,
 * así que también aparecen en la app de Samsung.
 */
class HealthConnectExporter(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        isAvailable() && client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun export(workout: Workout) {
        if (!hasPermissions()) return
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
        workout.calories?.let {
            records += TotalCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = null,
                endTime = end,
                endZoneOffset = null,
                energy = Energy.kilocalories(it.toDouble()),
            )
        }
        client.insertRecords(records)
    }
}
