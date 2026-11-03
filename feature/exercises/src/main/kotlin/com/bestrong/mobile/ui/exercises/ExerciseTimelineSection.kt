package com.bestrong.mobile.ui.exercises

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.TrackingType
import com.bestrong.core.stats.ExerciseTimeline
import com.bestrong.core.stats.Milestone
import com.bestrong.core.stats.MilestoneKind
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.workout.describeSet

/**
 * «Historia» del ejercicio (bajo la gráfica de progreso de la ficha): una línea de tiempo con los hitos (primera vez, récords,
 * pesos redondos, cambios de gimnasio, vueltas tras un parón y descargas). Tocar un hito enseña las series de ese día.
 * Arriba, la nota fija.
 */
@Composable
internal fun ExerciseTimelineSection(vm: ExercisesViewModel, exercise: Exercise, weightFactor: Int) {
    val history by vm.history.collectAsStateWithLifecycle()
    val milestones = remember(history, exercise.id) { ExerciseTimeline.milestones(history, exercise.id) }
    if (milestones.isEmpty()) return
    val unit = LocalWeightUnit.current
    var showAll by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var open by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    val note = remember(exercise.id, vm.mediaVersion.collectAsStateWithLifecycle().value) { vm.note(exercise.id) }

    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        SectionHeader(stringResource(R.string.tl_title))
        if (note.isNotBlank()) Banner(Icons.Default.PushPin, stringResource(R.string.tl_pinned, note), MaterialTheme.colorScheme.onSurfaceVariant)
        val shown = if (showAll) milestones else milestones.take(SHOWN)
        Column {
            shown.forEachIndexed { i, m ->
                MilestoneRow(m, exercise, weightFactor, last = i == shown.lastIndex, expanded = open == m.workoutId + m.kind, onToggle = {
                    open = if (open == m.workoutId + m.kind) null else m.workoutId + m.kind
                }) {
                    // Las series de ese día
                    val w = history.firstOrNull { it.id == m.workoutId }
                    w?.exercises?.filter { it.exercise.id == exercise.id }?.flatMap { it.completedSets }?.forEachIndexed { k, set ->
                        Text("${k + 1}. " + describeSet(set, exercise, unit, weightFactor), style = MaterialTheme.typography.bodySmall)
                    }
                    w?.let { Text(it.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (milestones.size > SHOWN) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) stringResource(R.string.tl_show_less) else stringResource(R.string.tl_show_all, milestones.size))
            }
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Un hito: punto y línea a la izquierda, texto y fecha a la derecha; tocarlo despliega el día. */
@Composable
private fun MilestoneRow(
    m: Milestone,
    exercise: Exercise,
    factor: Int,
    last: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    details: @Composable () -> Unit,
) {
    val (icon, color) = style(m.kind)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tl_open_day), onClick = onToggle),
    ) {
        // Raíl: punto con el icono y la línea hacia el siguiente
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 6.dp).size(26.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(15.dp), tint = color)
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title(m), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatShortDate(m.dateEpochMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            value(m, exercise, factor)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
            if (expanded) details()
        }
    }
}

@Composable
private fun style(kind: MilestoneKind): Pair<ImageVector, Color> = when (kind) {
    // Récords en ámbar y logros en verde; el resto, en gris (el color significa algo)
    MilestoneKind.RECORD_ONE_REP_MAX, MilestoneKind.RECORD_WEIGHT, MilestoneKind.RECORD_REPS -> Icons.Default.EmojiEvents to com.bestrong.mobile.ui.theme.enfoque.record
    MilestoneKind.ROUND_WEIGHT -> Icons.Default.FitnessCenter to com.bestrong.mobile.ui.theme.enfoque.done
    MilestoneKind.FIRST_TIME -> Icons.Default.Flag to com.bestrong.mobile.ui.theme.enfoque.done
    MilestoneKind.GYM_CHANGE -> Icons.Default.Place to MaterialTheme.colorScheme.onSurfaceVariant
    MilestoneKind.COMEBACK -> Icons.Default.Replay to MaterialTheme.colorScheme.onSurfaceVariant
    MilestoneKind.DELOAD -> Icons.Default.TrendingDown to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun title(m: Milestone): String {
    val unit = LocalWeightUnit.current
    return when (m.kind) {
        MilestoneKind.FIRST_TIME -> stringResource(R.string.tl_first)
        MilestoneKind.RECORD_ONE_REP_MAX -> stringResource(R.string.tl_record_1rm)
        MilestoneKind.RECORD_WEIGHT -> stringResource(R.string.tl_record_weight)
        MilestoneKind.RECORD_REPS -> stringResource(R.string.tl_record_reps)
        MilestoneKind.ROUND_WEIGHT -> stringResource(R.string.tl_round, unit.format(m.value, withUnit = true))
        MilestoneKind.GYM_CHANGE -> stringResource(R.string.tl_gym, m.gym.orEmpty())
        MilestoneKind.COMEBACK -> stringResource(R.string.tl_comeback, m.value.toInt())
        MilestoneKind.DELOAD -> stringResource(R.string.tl_deload)
    }
}

/** «104,5 kg · antes 101,5 kg» o «15 reps · antes 13». */
@Composable
private fun value(m: Milestone, exercise: Exercise, factor: Int): String? {
    val unit = LocalWeightUnit.current
    fun kg(v: Double) = unit.format(v * factor, withUnit = true)
    val repsTemplate = stringResource(R.string.tl_reps)
    fun reps(v: Double) = repsTemplate.format(v.toInt())
    return when (m.kind) {
        MilestoneKind.RECORD_ONE_REP_MAX, MilestoneKind.RECORD_WEIGHT ->
            kg(m.value) + (m.previous?.takeIf { it > 0 }?.let { " · " + stringResource(R.string.tl_prev, kg(it)) } ?: "")
        MilestoneKind.RECORD_REPS -> reps(m.value) + (m.previous?.takeIf { it > 0 }?.let { " · " + stringResource(R.string.tl_prev, reps(it)) } ?: "")
        MilestoneKind.FIRST_TIME -> if (exercise.tracking == TrackingType.WEIGHT_REPS && m.value > 0) {
            kg(m.value)
        } else if (m.value > 0) {
            reps(m.value)
        } else {
            null
        }
        MilestoneKind.DELOAD -> kg(m.value)
        else -> null
    }
}

/** Hitos a la vista antes de «Ver todos». */
private const val SHOWN = 6
