package com.bestrong.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.MuscleTargets
import com.bestrong.core.stats.VolumeStatus
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.Panel
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.theme.statusColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Panel de la semana de Inicio (propuesta 2): racha y volumen lado a lado y los músculos de la semana con su
 * estado. Va debajo de «Hoy toca» y de la tira de días.
 */
@Composable
fun WeekDashboard(week: WeekProgress, history: List<Workout>, setsTarget: Int, onOpenMuscle: (MuscleGroup) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val (thisWeek, lastWeek) = remember(history, today) { weekVolumes(history, today, zone) }
    val muscles = remember(history, today) { WorkoutStats.weeklySetsPerMuscle(history.filter { it.isFinished }, today, zone) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val streakText = pluralStringResource(R.plurals.rd_home_streak_weeks, week.streak, week.streak)
            Panel(Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = streakText }, contentPadding = 16.dp) {
                Text(stringResource(R.string.rd_home_streak), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("🔥 ${week.streak}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(streakText.substringAfter(" ", streakText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val unit = LocalWeightUnit.current
            val volume = unit.formatVolume(thisWeek)
            val delta = if (lastWeek > 0) (thisWeek - lastWeek) / lastWeek else null
            val deltaText = delta?.let { d ->
                (if (d >= 0) statusColors.goodMark.ifEmpty { "▲ " } else statusColors.badMark.ifEmpty { "▼ " }) +
                    stringResource(R.string.rd_home_vs_last_week, kotlin.math.abs(d * 100).toInt())
            }
            val volumeLabel = stringResource(R.string.rd_home_week_volume)
            Panel(Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = listOfNotNull(volumeLabel, volume, deltaText).joinToString(", ") }, contentPadding = 16.dp) {
                Text(volumeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(volume, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, maxLines = 1)
                deltaText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if ((delta ?: 0.0) >= 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MusclesThisWeek(muscles, setsTarget, onOpenMuscle)
    }
}

/** Los músculos con más series de la semana y, si quedan, los que aún van cortos. */
@Composable
private fun MusclesThisWeek(muscles: Map<MuscleGroup, Double>, target: Int, onOpenMuscle: (MuscleGroup) -> Unit) {
    if (muscles.isEmpty()) return
    val shown = muscles.filterKeys { it != MuscleGroup.FULL_BODY && it != MuscleGroup.CARDIO }
        .entries.sortedByDescending { it.value }.take(MUSCLES_SHOWN)
    val max = maxOf(MuscleTargets.MAX_WEEKLY_SETS, shown.maxOfOrNull { it.value } ?: 0.0)
    Panel(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rd_home_muscles_title), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.rd_home_muscles_target, target), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        shown.forEach { (m, sets) ->
            val status = MuscleTargets.status(sets, target.toDouble())
            val color = statusColor(status)
            val name = m.label()
            val mark = when (status) {
                VolumeStatus.LOW -> statusColors.warnMark
                VolumeStatus.OPTIMAL -> statusColors.goodMark
                VolumeStatus.HIGH -> statusColors.badMark
                VolumeStatus.NONE -> ""
            }
            val setsText = stringResource(R.string.n_sets_value, formatWeight(sets))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.rd_home_muscle_open)) { onOpenMuscle(m) }
                    .clearAndSetSemantics { contentDescription = "$name: $setsText" }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth((sets / max).toFloat().coerceIn(0.03f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
                }
                Text(mark + formatWeight(sets), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun statusColor(status: VolumeStatus): Color = when (status) {
    VolumeStatus.NONE -> MaterialTheme.colorScheme.outlineVariant
    VolumeStatus.LOW -> statusColors.warn
    VolumeStatus.OPTIMAL -> statusColors.good
    VolumeStatus.HIGH -> statusColors.bad
}

/** Volumen de esta semana (lunes a hoy) y el de la semana pasada entera. */
private fun weekVolumes(history: List<Workout>, today: LocalDate, zone: ZoneId): Pair<Double, Double> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastMonday = monday.minusWeeks(1)
    var current = 0.0
    var last = 0.0
    history.filter { it.isFinished }.forEach { w ->
        val d = Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone).toLocalDate()
        when {
            !d.isBefore(monday) -> current += w.totalVolume
            !d.isBefore(lastMonday) -> last += w.totalVolume
        }
    }
    return current to last
}

private const val MUSCLES_SHOWN = 4
