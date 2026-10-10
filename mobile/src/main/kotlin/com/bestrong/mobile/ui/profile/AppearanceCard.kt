package com.bestrong.mobile.ui.profile

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bestrong.core.util.VibrationStyle
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.R
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.theme.Accent
import com.bestrong.mobile.ui.theme.AppFont
import com.bestrong.mobile.ui.theme.Appearance
import com.bestrong.mobile.ui.theme.ThemeMode

/** Tema, acento, AMOLED, contraste, daltonismo, letra, vibración, unidades mixtas y columnas. */
@Composable
fun AppearanceCard(settings: UserSettings, onUpdate: ((UserSettings) -> UserSettings) -> Unit) {
    val a = settings.appearance
    fun setA(block: (Appearance) -> Appearance) = onUpdate { it.copy(appearance = block(it.appearance)) }
    val context = LocalContext.current
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionHeader(stringResource(R.string.appearance), Modifier.padding(horizontal = 16.dp))
            Chips(
                stringResource(R.string.theme_mode),
                ThemeMode.entries.map {
                    it to stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    )
                },
                a.mode,
            ) { m -> setA { it.copy(mode = m) } }
            val accents = Accent.entries.filter { it != Accent.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            Chips(
                stringResource(R.string.accent),
                accents.map {
                    it to stringResource(
                        when (it) {
                            Accent.ORANGE -> R.string.accent_orange
                            Accent.BLUE -> R.string.accent_blue
                            Accent.GREEN -> R.string.accent_green
                            Accent.PURPLE -> R.string.accent_purple
                            Accent.PINK -> R.string.accent_pink
                            Accent.DYNAMIC -> R.string.accent_dynamic
                        },
                    )
                },
                a.accent,
            ) { c -> setA { it.copy(accent = c) } }
            Toggle(stringResource(R.string.amoled), stringResource(R.string.amoled_body), a.amoled) { v -> setA { it.copy(amoled = v) } }
            Toggle(stringResource(R.string.high_contrast), stringResource(R.string.high_contrast_body), a.highContrast) { v -> setA { it.copy(highContrast = v) } }
            Toggle(stringResource(R.string.colorblind), stringResource(R.string.colorblind_body), a.colorblind) { v -> setA { it.copy(colorblind = v) } }
            Chips(
                stringResource(R.string.font),
                AppFont.entries.map {
                    it to stringResource(
                        when (it) {
                            AppFont.SYSTEM -> R.string.font_system
                            AppFont.LEGIBLE -> R.string.font_legible
                            AppFont.DYSLEXIA -> R.string.font_dyslexia
                        },
                    )
                },
                a.font,
            ) { f -> setA { it.copy(font = f) } }
            Chips(
                stringResource(R.string.rest_vibration),
                VibrationStyle.entries.map {
                    it to stringResource(
                        when (it) {
                            VibrationStyle.SHORT -> R.string.vib_short
                            VibrationStyle.DOUBLE -> R.string.vib_double
                            VibrationStyle.TRIPLE -> R.string.vib_triple
                            VibrationStyle.LONG -> R.string.vib_long
                            VibrationStyle.HEARTBEAT -> R.string.vib_heartbeat
                            VibrationStyle.STRONG -> R.string.vib_strong
                        },
                    )
                },
                settings.restVibration,
                body = stringResource(R.string.rest_vibration_body),
            ) { v ->
                onUpdate { it.copy(restVibration = v) }
                vibrate(context, v.pattern)
            }
            TextButton(onClick = { vibrate(context, settings.restVibration.pattern) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.vib_test))
            }
            Chips(
                stringResource(R.string.body_unit),
                listOf<Pair<WeightUnit?, String>>(null to stringResource(R.string.body_unit_same)) + WeightUnit.entries.map { it to it.label },
                settings.bodyUnit,
            ) { u -> onUpdate { it.copy(bodyUnit = u) } }
            Toggle(stringResource(R.string.col_previous_setting), stringResource(R.string.col_previous_body), settings.showPreviousColumn) { v ->
                onUpdate { it.copy(showPreviousColumn = v) }
            }
            Toggle(stringResource(R.string.col_comparison_setting), stringResource(R.string.col_comparison_body), settings.showComparison) { v ->
                onUpdate { it.copy(showComparison = v) }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(title: String, options: List<Pair<T, String>>, selected: T, body: String? = null, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

private fun vibrate(context: android.content.Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
}
