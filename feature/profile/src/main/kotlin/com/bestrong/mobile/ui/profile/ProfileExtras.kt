package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.MuscleTargets
import com.bestrong.core.stats.VolumeStatus
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.theme.statusColors
import java.time.DayOfWeek
import java.time.format.TextStyle

private val MAP_MUSCLES = MuscleGroup.entries - MuscleGroup.FULL_BODY - MuscleGroup.CARDIO

@Composable
private fun VolumeStatus.color(): Color = when (this) {
    VolumeStatus.NONE -> MaterialTheme.colorScheme.outlineVariant
    VolumeStatus.LOW -> statusColors.warn
    VolumeStatus.OPTIMAL -> statusColors.good
    VolumeStatus.HIGH -> statusColors.bad
}

/** Con modo daltónico, cada estado lleva además su símbolo (▲ ● ▼). */
@Composable
private fun VolumeStatus.label(): String = when (this) {
    VolumeStatus.NONE -> ""
    VolumeStatus.LOW -> statusColors.warnMark
    VolumeStatus.OPTIMAL -> statusColors.goodMark
    VolumeStatus.HIGH -> statusColors.badMark
} + stringResource(
    when (this) {
        VolumeStatus.NONE -> R.string.volume_none
        VolumeStatus.LOW -> R.string.volume_low
        VolumeStatus.OPTIMAL -> R.string.volume_optimal
        VolumeStatus.HIGH -> R.string.volume_high
    },
)

/**
 * Mapa muscular de la semana: series efectivas por músculo frente a la recomendación
 * de 10–20 series semanales. El estado se indica con color **y** texto (accesible).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyMuscleMap(weekly: Map<MuscleGroup, Double>, target: Int = MuscleTargets.MIN_WEEKLY_SETS.toInt(), onTarget: (Int) -> Unit = {}) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.muscle_map_title))
            Text(
                stringResource(R.string.muscle_map_body, target, MuscleTargets.MAX_WEEKLY_SETS.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Tu mínimo semanal (también lo usan Inicio y el widget)
            // FlowRow: con letra grande los chips bajan de línea en vez de salirse
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.weekly_sets_target), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                for (n in WEEKLY_TARGET_CHOICES) {
                    val described = stringResource(R.string.n_sets_value, "$n")
                    FilterChip(selected = n == target, onClick = { onTarget(n) }, label = { Text("$n") }, modifier = Modifier.semantics { contentDescription = described })
                }
            }
            MAP_MUSCLES.forEach { m ->
                val sets = weekly[m] ?: 0.0
                val status = MuscleTargets.status(sets, target.toDouble())
                val name = m.label()
                val statusText = status.label()
                val setsText = stringResource(R.string.n_sets_value, formatWeight(sets))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$name: $setsText, $statusText" },
                ) {
                    Text(name, Modifier.weight(0.3f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Barra hasta 25 series con marcas en 10 y 20
                    Box(Modifier.weight(0.36f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                        val fraction = (sets / 25.0).coerceIn(0.0, 1.0).toFloat()
                        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(12.dp).background(status.color()))
                    }
                    Text(formatWeight(sets), Modifier.weight(0.1f).padding(start = 8.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(10.dp).clip(CircleShape).background(status.color()))
                    Text(statusText, Modifier.weight(0.24f).padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun reminderSummary(s: UserSettings): String {
    if (s.reminderDays.isEmpty()) return stringResource(R.string.reminders_off)
    val locale = LocalConfiguration.current.locales[0]
    val days = s.reminderDays.sorted().joinToString { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
    return stringResource(R.string.reminders_summary, days, "%02d:%02d".format(s.reminderHour, s.reminderMinute))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemindersDialog(settings: UserSettings, onSave: (Set<Int>, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(settings.reminderDays) }
    val time = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, is24Hour = true)
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reminders_body), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = d.value in days,
                            onClick = { days = if (d.value in days) days - d.value else days + d.value },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
                        )
                    }
                }
                TimeInput(time)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(days, time.hour, time.minute) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Mínimos semanales que se pueden elegir en el mapa muscular. */
private val WEEKLY_TARGET_CHOICES = listOf(6, 8, 10, 12, 15)
