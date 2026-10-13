package com.bestrong.mobile.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bestrong.core.model.HeartRateSample
import com.bestrong.core.training.HeartRateTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Datos de salud que BeStrong lee de Health Connect (Samsung Health los publica ahí). */
data class HealthSnapshot(
    /** (fecha, kg) de los últimos 90 días. */
    val weights: List<Pair<Long, Double>> = emptyList(),
    /** Horas dormidas en las últimas 24 h. */
    val sleepHours: Double? = null,
    /** Pulso en reposo más reciente y media de 30 días. */
    val restingHr: Int? = null,
    val baselineRestingHr: Int? = null,
    /** Altura más reciente (cm). */
    val heightCm: Double? = null,
)

@Singleton
class HealthConnectReader @Inject constructor(@ApplicationContext private val context: Context) {

    private fun available() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions: Set<String> by lazy {
        setOfNotNull(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            // Para calcular calorías más precisas: altura (metabolismo basal) y pulso durante el entreno
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            // Sin este permiso Health Connect solo deja leer 30 días antes de la concesión (peso de 90 días, altura)
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY.takeIf { historyAvailable() },
        )
    }

    @OptIn(ExperimentalFeatureAvailabilityApi::class)
    private fun historyAvailable(): Boolean = runCatching {
        available() && client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    suspend fun granted(): Set<String> =
        if (!available()) emptySet() else runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** Lee lo que esté permitido; lo que no, queda a null o vacío. */
    suspend fun read(now: Instant = Instant.now()): HealthSnapshot {
        val ok = granted()
        if (ok.isEmpty()) return HealthSnapshot()
        val weights = if (HealthPermission.getReadPermission(WeightRecord::class) in ok) {
            runCatching {
                client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now)))
                    .records.map { it.time.toEpochMilli() to it.weight.inKilograms }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in ok) {
            runCatching {
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)))
                    .records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() } / 60.0
            }.getOrNull()?.takeIf { it > 0 }
        } else {
            null
        }
        val resting = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in ok) {
            runCatching {
                client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now)))
                    .records.sortedBy { it.time }.map { it.beatsPerMinute.toInt() }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val height = if (HealthPermission.getReadPermission(HeightRecord::class) in ok) {
            runCatching {
                client.readRecords(ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(3650)), now)))
                    .records.maxByOrNull { it.time }?.height?.inMeters?.times(100)
            }.getOrNull()
        } else {
            null
        }
        return HealthSnapshot(
            heightCm = height,
            weights = weights,
            sleepHours = sleep,
            restingHr = resting.lastOrNull(),
            baselineRestingHr = resting.takeIf { it.size >= 3 }?.average()?.toInt(),
        )
    }

    /**
     * Pulso registrado en Health Connect durante un entreno (por ejemplo, de un reloj que no usa
     * BeStrong), agrupado en tramos de 10 s. Vacío si no hay permiso o lecturas.
     */
    suspend fun heartRateDuring(startEpochMs: Long, endEpochMs: Long): List<HeartRateSample> {
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted()) return emptyList()
        val range = TimeRangeFilter.between(Instant.ofEpochMilli(startEpochMs), Instant.ofEpochMilli(endEpochMs))
        val records = runCatching { client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records }.getOrDefault(emptyList())
        val tracker = HeartRateTracker(startEpochMs)
        records.flatMap { it.samples }.sortedBy { it.time }
            .filter { it.time.toEpochMilli() in startEpochMs..endEpochMs }
            .forEach { tracker.add(it.beatsPerMinute.toInt(), it.time.toEpochMilli()) }
        return tracker.samples()
    }
}
