package com.bestrong.mobile.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserSettings(
    val defaultRestSeconds: Int = 120,
    val healthConnectExport: Boolean = false,
    val vibrateOnRestEnd: Boolean = true,
    val keepScreenOn: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UserSettings> = _settings

    private fun read() = UserSettings(
        defaultRestSeconds = prefs.getInt("rest", 120),
        healthConnectExport = prefs.getBoolean("hc", false),
        vibrateOnRestEnd = prefs.getBoolean("vibrate", true),
        keepScreenOn = prefs.getBoolean("screen_on", true),
    )

    fun update(block: (UserSettings) -> UserSettings) {
        val s = block(_settings.value)
        prefs.edit {
            putInt("rest", s.defaultRestSeconds)
            putBoolean("hc", s.healthConnectExport)
            putBoolean("vibrate", s.vibrateOnRestEnd)
            putBoolean("screen_on", s.keepScreenOn)
        }
        _settings.value = s
    }
}
