package com.bestrong.mobile.ui.profile

import com.bestrong.mobile.backup.AutoBackupWorker
import android.content.Intent
import com.bestrong.mobile.R
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.bestrong.core.io.WorkoutCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WeekBucket
import com.bestrong.core.stats.WorkoutStats
import android.content.Context
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.sync.WatchSync
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.bestrong.mobile.data.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

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

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BeStrongRepository,
    private val settingsStore: SettingsStore,
    private val healthConnect: HealthConnectExporter,
    private val watchSync: WatchSync,
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
        watchSync.pushRoutines(repo.routines.value)
        watchSync.pushHistory(repo.history.value)
    }
}
