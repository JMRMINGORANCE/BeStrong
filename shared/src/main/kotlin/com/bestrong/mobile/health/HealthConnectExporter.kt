package com.bestrong.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.SetTimeline
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
    private val weightPermission = HealthPermission.getWritePermission(WeightRecord::class)
    private val bodyFatPermission = HealthPermission.getWritePermission(BodyFatRecord::class)

    /** Lo que se pide al activar la exportación (sesión, calorías, pulso y distancia). */
    val permissions: Set<String> =
        setOf(sessionPermission, caloriesPermission, activeCaloriesPermission, heartRatePermission, distancePermission, weightPermission, bodyFatPermission)

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private suspend fun granted(): Set<String> =
        if (isAvailable()) client.permissionController.getGrantedPermissions() else emptySet()

    /** Basta con poder escribir la sesión; calorías y pulso se añaden si también están concedidos. */
    suspend fun hasPermissions(): Boolean = sessionPermission in granted()

    /** Permisos de peso y grasa (nuevos: quien ya exportaba no los tenía). */
    suspend fun hasAllPermissions(): Boolean = granted().containsAll(setOf(weightPermission, bodyFatPermission))

    /** Borra de Health Connect el peso y la grasa de una medida borrada en la app. */
    suspend fun deleteMeasurement(id: String) {
        val granted = granted()
        if (weightPermission in granted) runCatching { client.deleteRecords(WeightRecord::class, emptyList(), listOf("$id-weight")) }
        if (bodyFatPermission in granted) runCatching { client.deleteRecords(BodyFatRecord::class, emptyList(), listOf("$id-fat")) }
    }

    /**
     * Cada registro lleva un id de cliente estable (id del entreno + tipo): si se exporta otra vez
     * (reenvío del reloj, reintento), Health Connect lo sustituye en lugar de duplicarlo.
     */
    private fun meta(workout: Workout, suffix: String) =
        Metadata(clientRecordId = "${workout.id}-$suffix", clientRecordVersion = System.currentTimeMillis())

    suspend fun export(workout: Workout) {
        val granted = granted()
        if (sessionPermission !in granted) return
        val start = Instant.ofEpochMilli(workout.startedAtEpochMs)
        val end = Instant.ofEpochMilli(workout.finishedAtEpochMs ?: return)
        val type = sessionType(workout)
        fun session(segments: List<ExerciseSegment>) = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = null,
            endTime = end,
            endZoneOffset = null,
            exerciseType = type,
            title = workout.name,
            notes = workout.notes.ifBlank { null },
            metadata = meta(workout, "session"),
            segments = segments,
        )
        // Cada serie como segmento con sus repeticiones (Samsung Health / Fit las muestran); si
        // Health Connect rechazara algún segmento, se exporta la sesión sin ellos
        val segments = if (type == ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING) segmentsOf(workout) else emptyList()
        val generic = segments.map { ExerciseSegment(it.startTime, it.endTime, ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING, it.repetitions) }
        val records = mutableListOf<Record>(
            runCatching { session(segments) }.recoverCatching { session(generic) }.getOrElse { session(emptyList()) },
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
                    metadata = meta(workout, "kcal"),
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
                    metadata = meta(workout, "active-kcal"),
                )
            }
        }
        if (heartRatePermission in granted) heartRateRecord(workout)?.let { records += it }
        if (distancePermission in granted) records += distanceRecords(workout, start, end)
        // Si Health Connect rechaza la sesión con segmentos (proveedor antiguo), se exporta sin ellos
        runCatching { client.insertRecords(records) }.getOrElse {
            if (segments.isEmpty()) throw it
            client.insertRecords(listOf(session(emptyList())) + records.drop(1))
        }
    }

    /**
     * Peso y % de grasa apuntados en Medidas (Samsung Health y las básculas los comparten por aquí).
     * Con id de cliente estable: editar o volver a guardar la medida la sustituye.
     */
    suspend fun exportMeasurement(m: BodyMeasurement) {
        val granted = granted()
        val time = Instant.ofEpochMilli(m.dateEpochMs)
        val records = listOfNotNull<Record>(
            m.weightKg?.takeIf { weightPermission in granted && it in 1.0..1000.0 }?.let {
                WeightRecord(time, null, Mass.kilograms(it), Metadata(clientRecordId = "${m.id}-weight", clientRecordVersion = System.currentTimeMillis()))
            },
            m.bodyFatPct?.takeIf { bodyFatPermission in granted && it in 1.0..100.0 }?.let {
                BodyFatRecord(time, null, Percentage(it), Metadata(clientRecordId = "${m.id}-fat", clientRecordVersion = System.currentTimeMillis()))
            },
        )
        if (records.isNotEmpty()) client.insertRecords(records)
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
        return if (samples.isEmpty()) null else HeartRateRecord(startTime = start, startZoneOffset = null, endTime = end, endZoneOffset = null, samples = samples, metadata = meta(workout, "hr"))
    }

    /**
     * Tipo de sesión: si todo el entreno es un único cardio (carrera, cinta, elíptica…), ese tipo;
     * si hay fuerza (aunque lleve cardio dentro), pesas.
     */
    private fun segmentsOf(workout: Workout): List<ExerciseSegment> = SetTimeline.windows(workout)
        .filter { CardioEnergy.kindOf(it.exercise.exercise.id) == null }
        .map { w ->
            ExerciseSegment(
                startTime = Instant.ofEpochMilli(w.startEpochMs),
                endTime = Instant.ofEpochMilli(w.endEpochMs),
                segmentType = segmentType(w.exercise.exercise.id),
                repetitions = w.set.reps + (w.set.repsRight ?: 0),
            )
        }

    /** Tipo de segmento de Health Connect para los básicos; el resto, «levantamiento de pesas». */
    private fun segmentType(exerciseId: String): Int = SEGMENT_TYPES[exerciseId] ?: ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING

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
            .mapIndexedNotNull { i, b ->
                val blockEnd = b.endOffsetSec?.let { start.plusSeconds(it.toLong()) }?.coerceAtMost(end) ?: end
                val blockStart = blockEnd.minusSeconds(b.durationSec.toLong()).coerceAtLeast(start)
                if (!blockStart.isBefore(blockEnd)) return@mapIndexedNotNull null
                DistanceRecord(
                    startTime = blockStart,
                    startZoneOffset = null,
                    endTime = blockEnd,
                    endZoneOffset = null,
                    distance = Length.meters(b.distanceM),
                    metadata = meta(workout, "dist-$i"),
                )
            }

    private companion object {
        val DISPLACEMENT = setOf(CardioKind.RUN, CardioKind.WALK, CardioKind.TREADMILL, CardioKind.BIKE)
    }
}

/** Tipos de segmento de Health Connect por ejercicio. */
private val SEGMENT_TYPES: Map<String, Int> = listOf(
    listOf("bench_press", "incline_bench_press", "decline_bench_press", "close_grip_bench", "db_bench_press") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
    listOf("squat", "front_squat", "smith_squat", "goblet_squat", "hack_squat", "air_squat") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT,
    listOf("deadlift", "romanian_deadlift") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
    listOf("overhead_press") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS,
    listOf("lat_pulldown") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN,
    listOf("pull_up", "chin_up", "chest_to_bar", "band_pull_up", "negative_pull_up") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
    listOf("leg_press") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS,
    listOf("leg_curl", "seated_leg_curl") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
    listOf("leg_extension") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION,
    listOf("lunge", "bulgarian_split_squat") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
    listOf("lateral_raise", "cable_lateral_raise") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
    listOf("db_row") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW,
    listOf("front_raise") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE,
    listOf("hanging_leg_raise") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
    listOf("plank", "side_plank") to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
).flatMap { (ids, type) -> ids.map { it to type } }.toMap()
