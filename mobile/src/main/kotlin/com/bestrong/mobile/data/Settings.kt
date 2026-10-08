package com.bestrong.mobile.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.bestrong.core.util.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserSettings(
    val defaultRestSeconds: Int = 120,
    val healthConnectExport: Boolean = false,
    val vibrateOnRestEnd: Boolean = true,
    val keepScreenOn: Boolean = true,
    val unit: WeightUnit = WeightUnit.KG,
    /** Rellenar pesos con la sugerencia de sobrecarga progresiva al empezar. */
    val progressionEnabled: Boolean = true,
    /** Días de la semana con recordatorio (1 = lunes … 7 = domingo). */
    val reminderDays: Set<Int> = emptySet(),
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    /** Carpeta (SAF) para la copia de seguridad semanal; null = desactivada. */
    val backupFolderUri: String? = null,
    val lastBackupEpochMs: Long = 0,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UserSettings> = _settings

    private fun read() = UserSettings(
        defaultRestSeconds = prefs.getInt("rest", 120),
        healthConnectExport = prefs.getBoolean("hc", false),
        vibrateOnRestEnd = prefs.getBoolean("vibrate", true),
        keepScreenOn = prefs.getBoolean("screen_on", true),
        unit = runCatching { WeightUnit.valueOf(prefs.getString("unit", null) ?: "KG") }.getOrDefault(WeightUnit.KG),
        progressionEnabled = prefs.getBoolean("progression", true),
        reminderDays = prefs.getStringSet("reminder_days", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        reminderHour = prefs.getInt("reminder_hour", 18),
        reminderMinute = prefs.getInt("reminder_minute", 0),
        backupFolderUri = prefs.getString("backup_folder", null),
        lastBackupEpochMs = prefs.getLong("backup_last", 0),
    )

    @Synchronized
    fun update(block: (UserSettings) -> UserSettings) {
        val s = block(_settings.value)
        prefs.edit {
            putInt("rest", s.defaultRestSeconds)
            putBoolean("hc", s.healthConnectExport)
            putBoolean("vibrate", s.vibrateOnRestEnd)
            putBoolean("screen_on", s.keepScreenOn)
            putString("unit", s.unit.name)
            putBoolean("progression", s.progressionEnabled)
            putStringSet("reminder_days", s.reminderDays.map { it.toString() }.toSet())
            putInt("reminder_hour", s.reminderHour)
            putInt("reminder_minute", s.reminderMinute)
            putString("backup_folder", s.backupFolderUri)
            putLong("backup_last", s.lastBackupEpochMs)
        }
        _settings.value = s
    }
}
