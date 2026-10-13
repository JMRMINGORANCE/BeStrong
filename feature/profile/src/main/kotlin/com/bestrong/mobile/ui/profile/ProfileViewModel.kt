package com.bestrong.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.io.Backup
import com.bestrong.core.io.EncryptedBackup
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.io.Xlsx
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.Achievement
import com.bestrong.core.stats.Balance
import com.bestrong.core.stats.PeriodSummary
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.StrengthStanding
import com.bestrong.core.stats.TrainingLoad
import com.bestrong.core.stats.WeekBucket
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.WatchPush
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.backup.FullBackup
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.ProgressPhoto
import com.bestrong.mobile.data.ProgressPhotoStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.health.HealthConnectReader
import com.bestrong.mobile.share.MonthlyReport
import com.bestrong.mobile.shared.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileStats(
    val totalWorkouts: Int = 0,
    val totalVolume: Double = 0.0,
    val totalDurationSec: Long = 0,
    val weeklyStreak: Int = 0,
    val weeks: List<WeekBucket> = emptyList(),
    val muscles30d: Map<MuscleGroup, Double> = emptyMap(),
    val topOneRepMax: List<PersonalRecord> = emptyList(),
    /** Series de esta semana por músculo (para el mapa con el objetivo de 10–20). */
    val weeklyMuscles: Map<MuscleGroup, Double> = emptyMap(),
)

/** Estadísticas de la fase 3 (niveles, equilibrio, logros, recuperación…). */
data class AdvancedStats(
    val bodyWeightKg: Double? = null,
    val standings: List<StrengthStanding> = emptyList(),
    val balance: Balance = Balance(0.0, 0.0, 0.0, 0.0),
    val week: PeriodSummary? = null,
    val weekEffectiveSets: Double = 0.0,
    val achievements: List<Achievement> = emptyList(),
    val timeline: List<PersonalRecord> = emptyList(),
    val daysSince: Map<MuscleGroup, Int?> = emptyMap(),
    val soreness: Map<MuscleGroup, Double> = emptyMap(),
    /** Reto con fecha estimada: (ejercicio, 1RM objetivo, fecha). */
    val challenge: Triple<String, Double, Long>? = null,
    /** Carga semanal (RPE × minutos) y relación aguda:crónica. */
    val load: TrainingLoad.Weekly? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BeStrongRepository,
    private val settingsStore: SettingsStore,
    private val healthConnect: HealthConnectExporter,
    private val watchPush: WatchPush,
    private val photoStore: ProgressPhotoStore,
    private val healthReader: HealthConnectReader,
    private val fullBackup: FullBackup,
) : ViewModel() {

    val healthReadPermissions: Set<String> = healthReader.permissions

    /** Trae los pesos de Health Connect (Samsung Health, básculas) a Medidas, sin duplicar días. */
    fun importHealthWeights() = viewModelScope.launch {
        val snap = runCatching { healthReader.read() }.getOrNull() ?: return@launch
        val zone = ZoneId.systemDefault()
        val have = repo.measurements.value.filter { it.weightKg != null }
            .map { java.time.Instant.ofEpochMilli(it.dateEpochMs).atZone(zone).toLocalDate() }.toSet()
        val fresh = snap.weights.groupBy { (t, _) -> java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate() }
            .filterKeys { it !in have }
            .map { (_, list) -> list.maxBy { it.first } }
        fresh.forEach { (t, kg) -> repo.saveMeasurement(BodyMeasurement(dateEpochMs = t, weightKg = kg)) }
        _messages.tryEmit(context.getString(R.string.hc_import_weight_done, fresh.size))
    }

    val stats: StateFlow<ProfileStats> = repo.history.map { history ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        ProfileStats(
            totalWorkouts = history.size,
            totalVolume = history.sumOf { it.totalVolume },
            totalDurationSec = history.sumOf { it.durationSeconds() },
            weeklyStreak = WorkoutStats.weeklyStreak(history, today, zone),
            weeks = WorkoutStats.weeklySummary(history, 12, today, zone),
            muscles30d = WorkoutStats.setsPerMuscle(history, System.currentTimeMillis() - 30L * 24 * 3600 * 1000),
            topOneRepMax = WorkoutStats.personalRecords(history).values
                .mapNotNull { it[RecordType.BEST_ONE_REP_MAX] }
                .sortedByDescending { it.value }
                .take(8),
            weeklyMuscles = WorkoutStats.weeklySetsPerMuscle(history, today, zone),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    val advanced: StateFlow<AdvancedStats> = combine(repo.history, repo.measurements, settingsStore.settings) { history, measures, s ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val bw = measures.sortedByDescending { it.dateEpochMs }.firstNotNullOfOrNull { it.weightKg }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        AdvancedStats(
            bodyWeightKg = bw,
            standings = bw?.let { Strength.standings(history, it, s.sex) }.orEmpty(),
            balance = Strength.balance(history, now),
            week = Strength.weekSummary(history, today, zone),
            weekEffectiveSets = history.filter { it.isFinished && it.startedAtEpochMs >= weekStart }.sumOf { Strength.effectiveSets(it) },
            achievements = Strength.achievements(history, today, zone, bw),
            timeline = Strength.recordTimeline(history, setOf(RecordType.BEST_ONE_REP_MAX)).takeLast(12).reversed(),
            daysSince = Strength.daysSinceTrained(history, today, zone),
            soreness = Strength.soreness(history, now),
            challenge = challenge(history),
            load = TrainingLoad.weekly(history, today, zone),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdvancedStats())

    /** El básico con más sesiones: próximo múltiplo de 10 kg de 1RM y cuándo se alcanzará al ritmo actual. */
    private fun challenge(history: List<com.bestrong.core.model.Workout>): Triple<String, Double, Long>? {
        val lift = Strength.STANDARD_LIFTS.maxByOrNull { id -> history.count { w -> w.exercises.any { it.exercise.id == id } } } ?: return null
        val points = WorkoutStats.exerciseProgress(lift, history).filter { it.bestOneRepMax > 0 }.map { it.dateEpochMs to it.bestOneRepMax }
        val best = points.maxOfOrNull { it.second } ?: return null
        val target = (kotlin.math.floor(best / 10.0) + 1) * 10.0
        val eta = Strength.predictDate(points, target) ?: return null
        return Triple(lift, target, eta)
    }

    val measurements: StateFlow<List<BodyMeasurement>> = repo.measurements

    // --- Fotos de progreso y fuerza relativa -------------------------------------------------

    val photos: StateFlow<List<ProgressPhoto>> = photoStore.photos
    fun addPhoto(uri: Uri) = viewModelScope.launch { runCatching { photoStore.add(uri) } }
    fun deletePhoto(photo: ProgressPhoto) = photoStore.delete(photo)

    /** Fuerza relativa (1RM / peso corporal) del básico con más datos: (ejercicio, [(fecha, peso, 1RM)]). */
    val relativeStrength: StateFlow<Pair<String, List<Triple<Long, Double, Double>>>?> = combine(repo.history, repo.measurements) { history, measures ->
        val weights = measures.mapNotNull { m -> m.weightKg?.takeIf { it > 0 }?.let { m.dateEpochMs to it } }
        if (weights.isEmpty()) return@combine null
        Strength.STANDARD_LIFTS.map { id -> id to Strength.bodyWeightVsStrength(WorkoutStats.exerciseProgress(id, history), weights) }
            .filter { it.second.size >= 2 }
            .maxByOrNull { it.second.size }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Informe del mes ([monthsAgo] = 0 el actual) como imagen o PDF. */
    fun shareMonthlyReport(monthsAgo: Int, pdf: Boolean) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now(zone).minusMonths(monthsAgo.toLong())
        val history = repo.historyOnce()
        val summary = withContext(Dispatchers.Default) { Strength.monthSummary(history, month, zone) }
        val days = WorkoutStats.trainingDays(history, zone).filter { YearMonth.from(it) == month }.toSet()
        runCatching { MonthlyReport.share(context, month, summary, days, settings.value.unit, pdf) }
            .onFailure { _messages.tryEmit(context.getString(R.string.export_error, it.message.orEmpty())) }
    }
    val settings: StateFlow<UserSettings> = settingsStore.settings

    val healthConnectAvailable = healthConnect.isAvailable()
    val healthConnectPermissions = healthConnect.permissions
    val healthConnectRequired = healthConnect.sessionPermission
    val healthConnectGranted = MutableStateFlow(false)

    fun refreshHealthConnect() = viewModelScope.launch {
        healthConnectGranted.value = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) = settingsStore.update(block)

    fun saveMeasurement(m: BodyMeasurement) = viewModelScope.launch {
        repo.saveMeasurement(m)
        // Con la exportación activada, el peso y la grasa también van a Health Connect
        if (settingsStore.settings.value.healthConnectExport) {
            runCatching { healthConnect.exportMeasurement(m) }.onFailure { android.util.Log.w("Profile", "Medida no exportada", it) }
        }
    }
    fun deleteMeasurement(id: String) = viewModelScope.launch { repo.deleteMeasurement(id) }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Periodo de las exportaciones a CSV y Excel. */
    val exportRange = MutableStateFlow(ExportRange.ALL)

    private fun inRange(history: List<com.bestrong.core.model.Workout>): List<com.bestrong.core.model.Workout> {
        val now = java.time.LocalDate.now()
        val from = when (exportRange.value) {
            ExportRange.ALL -> return history
            ExportRange.YEAR -> now.withDayOfYear(1)
            ExportRange.DAYS_90 -> now.minusDays(90)
            ExportRange.DAYS_30 -> now.minusDays(30)
        }.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return history.filter { it.startedAtEpochMs >= from }
    }

    fun exportXlsx(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val bytes = withContext(Dispatchers.Default) { Xlsx.workouts(inRange(repo.historyOnce())) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error(context.getString(R.string.file_open_error))
            }
        }
        _messages.tryEmit(result.fold({ context.getString(R.string.export_done) }, { context.getString(R.string.export_error, it.message.orEmpty()) }))
    }

    /** Copia completa cifrada con contraseña (AES-256-GCM). */
    fun exportEncrypted(uri: Uri, password: CharArray) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.Default) { EncryptedBackup.encrypt(Backup.encode(fullBackup.data()), password) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error(context.getString(R.string.file_open_error))
            }
        }
        password.fill(' ')
        _messages.tryEmit(result.fold({ context.getString(R.string.export_done) }, { context.getString(R.string.export_error, it.message.orEmpty()) }))
    }

    /** Copia cifrada pendiente de contraseña al restaurar. */
    val pendingEncrypted = MutableStateFlow<String?>(null)

    fun restoreEncrypted(password: CharArray) = viewModelScope.launch {
        val text = pendingEncrypted.value ?: return@launch
        val result = runCatching { withContext(Dispatchers.Default) { EncryptedBackup.decrypt(text, password) } }
        password.fill(' ')
        val plain = result.getOrElse { e ->
            // Con contraseña equivocada se puede reintentar; un archivo dañado no se arregla repitiéndola
            if (e is com.bestrong.core.io.DamagedBackupException) pendingEncrypted.value = null
            _messages.tryEmit(context.getString(if (e is com.bestrong.core.io.DamagedBackupException) R.string.backup_damaged else R.string.wrong_password))
            return@launch
        }
        pendingEncrypted.value = null
        restoreText(plain)
    }

    private suspend fun restoreText(text: String) {
        val result = runCatching {
            val data = withContext(Dispatchers.Default) { Backup.decode(text) }
            data to fullBackup.restore(data)
        }
        _messages.tryEmit(
            result.fold(
                { (data, added) -> context.getString(R.string.restore_done, data.history.size, data.routines.size, added) },
                { context.getString(R.string.restore_error, it.message.orEmpty()) },
            ),
        )
    }

    /** Lee un archivo elegido por el usuario, rechazando los enormes (un fichero equivocado de GB agotaría la memoria). */
    private fun readLimited(uri: android.net.Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= MAX_IMPORT_BYTES) { context.getString(R.string.file_too_big) }
            }
            out.toByteArray().decodeToString()
        } ?: error(context.getString(R.string.file_open_error))

    /** Borrado selectivo: entrenos antiguos, medidas, fotos o todo el historial. */
    fun deleteData(kind: DeleteKind) = viewModelScope.launch {
        val count = when (kind) {
            DeleteKind.OLD_HISTORY -> {
                val limit = System.currentTimeMillis() - 365L * 86_400_000L
                repo.historyOnce().filter { it.startedAtEpochMs < limit }.map { it.id }.also { repo.deleteWorkouts(it) }.size
            }
            DeleteKind.ALL_HISTORY -> repo.historyOnce(includePartner = true).map { it.id }.also { repo.deleteWorkouts(it) }.size
            DeleteKind.MEASUREMENTS -> repo.measurements.value.onEach { repo.deleteMeasurement(it.id) }.size
            DeleteKind.PHOTOS -> photoStore.photos.value.onEach { photoStore.delete(it) }.size
        }
        _messages.tryEmit(context.getString(R.string.delete_done, count))
    }

    /** Exporta todo el historial al archivo elegido (SAF). */
    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val history = repo.historyOnce()
            val csv = withContext(Dispatchers.Default) { WorkoutCsv.export(inRange(history)) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error(context.getString(R.string.file_open_error))
            }
        }
        _messages.tryEmit(result.fold({ context.getString(R.string.export_done) }, { context.getString(R.string.export_error, it.message.orEmpty()) }))
    }

    /** Importa un CSV de BeStrong, Hevy o Strong. */
    fun importCsv(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                readLimited(uri)
            }
            val parsed = withContext(Dispatchers.Default) {
                WorkoutCsv.import(text, repo.exercises.value, strongUnit = settings.value.unit)
            }
            parsed to repo.importWorkouts(parsed.workouts, parsed.newExercises)
        }
        _messages.tryEmit(
            result.fold(
                { (r, added) ->
                    context.getString(R.string.import_done, r.format.label, added) +
                        (if (r.workouts.size > added) context.getString(R.string.import_existing, r.workouts.size - added) else "") +
                        (if (r.newExercises.isNotEmpty()) context.getString(R.string.import_new_exercises, r.newExercises.size) else "") +
                        (if (r.skippedRows > 0) context.getString(R.string.import_skipped, r.skippedRows) else "")
                },
                { context.getString(R.string.import_error, it.message.orEmpty()) },
            ),
        )
    }

    /** Restaura una copia JSON de BeStrong (añade lo que falte, no borra nada). */
    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                readLimited(uri)
            }
            // Copia cifrada: se pide la contraseña y se sigue en restoreEncrypted
            if (EncryptedBackup.isEncrypted(text)) {
                pendingEncrypted.value = text
                return@launch
            }
            val data = withContext(Dispatchers.Default) { Backup.decode(text) }
            data to fullBackup.restore(data)
        }
        _messages.tryEmit(
            result.fold(
                { (data, added) -> context.getString(R.string.restore_done, data.history.size, data.routines.size, added) },
                { context.getString(R.string.restore_error, it.message.orEmpty()) },
            ),
        )
    }

    /** Carpeta elegida para la copia semanal: guardamos el permiso y hacemos una copia ya. */
    fun setBackupFolder(uri: Uri?) {
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } else {
            settings.value.backupFolderUri?.let { old ->
                runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
        }
        settingsStore.update { it.copy(backupFolderUri = uri?.toString()) }
        if (uri != null) {
            AutoBackupWorker.runNow(context)
            _messages.tryEmit(context.getString(R.string.backup_started))
        }
    }

    fun syncWatchNow() = viewModelScope.launch {
        watchPush.pushToWatchNow()
    }
}

enum class ExportRange { ALL, YEAR, DAYS_90, DAYS_30 }

enum class DeleteKind { OLD_HISTORY, MEASUREMENTS, PHOTOS, ALL_HISTORY }

private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
