package com.bestrong.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.bestrong.core.model.Workout
import com.bestrong.core.training.CardioEnergy
import com.bestrong.core.training.CardioKind
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
    private val activeCaloriesPermission = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    private val distancePermission = HealthPermission.getWritePermission(DistanceRecord::class)

    /** Lo que se pide al activar la exportación (sesión, calorías, pulso y distancia). */
    val permissions: Set<String> =
        setOf(sessionPermission, caloriesPermission, activeCaloriesPermission, heartRatePermission, distancePermission)

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
                exerciseType = sessionType(workout),
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
        // Activas (sin el gasto en reposo): lo que suma Samsung Health sin contar dos veces el basal
        if (activeCaloriesPermission in granted) {
            workout.activeCalories?.takeIf { it > 0 }?.let {
                records += ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = null,
                    endTime = end,
                    endZoneOffset = null,
                    energy = Energy.kilocalories(it.toDouble()),
                )
            }
        }
        if (heartRatePermission in granted) heartRateRecord(workout)?.let { records += it }
        if (distancePermission in granted) records += distanceRecords(workout, start, end)
        client.insertRecords(records)
    }

    /** Solo la gráfica de pulso (llega del reloj después de exportar la sesión). */
    suspend fun exportHeartRate(workout: Workout) {
        if (heartRatePermission !in granted()) return
        heartRateRecord(workout)?.let { client.insertRecords(listOf(it)) }
    }

    /** Gráfica de pulso del entreno (una muestra cada ~10 s medida por el reloj). */
    private fun heartRateRecord(workout: Workout): HeartRateRecord? {
        val start = Instant.ofEpochMilli(workout.startedAtEpochMs)
        val end = workout.finishedAtEpochMs?.let(Instant::ofEpochMilli) ?: start
        val samples = workout.heartRateSamples
            .map { HeartRateRecord.Sample(start.plusSeconds(it.offsetSec.toLong()), it.bpm.toLong()) }
            .filter { it.time in start..end }
        return if (samples.isEmpty()) null else HeartRateRecord(startTime = start, startZoneOffset = null, endTime = end, endZoneOffset = null, samples = samples)
    }

    /**
     * Tipo de sesión: si todo el entreno es un único cardio (carrera, cinta, elíptica…), ese tipo;
     * si hay fuerza (aunque lleve cardio dentro), pesas.
     */
    private fun sessionType(workout: Workout): Int {
        val done = workout.exercises.filter { it.completedSets.isNotEmpty() }
        val kinds = done.mapNotNull { CardioEnergy.kindOf(it.exercise.id) }.toSet()
        val onlyCardio = kinds.size == 1 && done.all { CardioEnergy.kindOf(it.exercise.id) != null }
        return if (onlyCardio) cardioType(kinds.single()) else ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
    }

    private fun cardioType(kind: CardioKind): Int = when (kind) {
        CardioKind.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        CardioKind.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        CardioKind.TREADMILL -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
        CardioKind.BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        CardioKind.STATIONARY_BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
        CardioKind.ELLIPTICAL -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        CardioKind.ROWER -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        CardioKind.STAIRS -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        CardioKind.JUMP_ROPE -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    /**
     * Distancia recorrida de verdad (carrera, caminata, cinta y bici), un registro por bloque en su
     * tramo de tiempo. La de la elíptica o el remo no se exporta: no es desplazamiento.
     */
    private fun distanceRecords(workout: Workout, start: Instant, end: Instant): List<Record> =
        com.bestrong.core.training.Energy.cardioBlocks(workout)
            .filter { it.distanceM > 0 && it.kind in DISPLACEMENT }
            .mapNotNull { b ->
                val blockEnd = b.endOffsetSec?.let { start.plusSeconds(it.toLong()) }?.coerceAtMost(end) ?: end
                val blockStart = blockEnd.minusSeconds(b.durationSec.toLong()).coerceAtLeast(start)
                if (!blockStart.isBefore(blockEnd)) return@mapNotNull null
                DistanceRecord(
                    startTime = blockStart,
                    startZoneOffset = null,
                    endTime = blockEnd,
                    endZoneOffset = null,
                    distance = Length.meters(b.distanceM),
                )
            }

    private companion object {
        val DISPLACEMENT = setOf(CardioKind.RUN, CardioKind.WALK, CardioKind.TREADMILL, CardioKind.BIKE)
    }
}
