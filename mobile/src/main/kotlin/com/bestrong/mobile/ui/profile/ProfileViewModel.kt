package com.bestrong.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.io.Backup
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.Achievement
import com.bestrong.core.stats.Balance
import com.bestrong.core.stats.PeriodSummary
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.StrengthStanding
import com.bestrong.core.stats.WeekBucket
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.AppSync
import com.bestrong.mobile.R
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.health.HealthConnectExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
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
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BeStrongRepository,
    private val settingsStore: SettingsStore,
    private val healthConnect: HealthConnectExporter,
    private val appSync: AppSync,
) : ViewModel() {

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

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
    val settings: StateFlow<UserSettings> = settingsStore.settings

    val healthConnectAvailable = healthConnect.isAvailable()
    val healthConnectPermissions = healthConnect.permissions
    val healthConnectGranted = MutableStateFlow(false)

    fun refreshHealthConnect() = viewModelScope.launch {
        healthConnectGranted.value = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) = settingsStore.update(block)

    fun saveMeasurement(m: BodyMeasurement) = viewModelScope.launch { repo.saveMeasurement(m) }
    fun deleteMeasurement(id: String) = viewModelScope.launch { repo.deleteMeasurement(id) }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Exporta todo el historial al archivo elegido (SAF). */
    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val csv = WorkoutCsv.export(repo.historyOnce())
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
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error(context.getString(R.string.file_open_error))
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
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error(context.getString(R.string.file_open_error))
            }
            val data = withContext(Dispatchers.Default) { Backup.decode(text) }
            data to repo.restore(data)
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
        appSync.pushToWatchNow()
    }
}
