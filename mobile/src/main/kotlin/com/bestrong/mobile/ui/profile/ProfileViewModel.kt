package com.bestrong.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WeekBucket
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.AppContainer
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
)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    val measurements: StateFlow<List<BodyMeasurement>> = repo.measurements
    val settings: StateFlow<UserSettings> = container.settings.settings

    val healthConnectAvailable = container.healthConnect.isAvailable()
    val healthConnectPermissions = container.healthConnect.permissions
    val healthConnectGranted = MutableStateFlow(false)

    fun refreshHealthConnect() = viewModelScope.launch {
        healthConnectGranted.value = runCatching { container.healthConnect.hasPermissions() }.getOrDefault(false)
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) = container.settings.update(block)

    fun saveMeasurement(m: BodyMeasurement) = viewModelScope.launch { repo.saveMeasurement(m) }
    fun deleteMeasurement(id: String) = viewModelScope.launch { repo.deleteMeasurement(id) }

    fun syncWatchNow() = viewModelScope.launch {
        container.watchSync.pushRoutines(repo.routines.value)
        container.watchSync.pushHistory(repo.history.value)
    }
}
