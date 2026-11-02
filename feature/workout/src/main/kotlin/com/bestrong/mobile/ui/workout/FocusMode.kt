package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.brandGradient
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.theme.statusColors

/**
 * Modo foco: una sola serie a pantalla completa, con el peso y las reps en grande y un botón enorme para marcarla.
 * Pensado para el banco y la pantalla exterior del Fold (una mano). Deslizar a los lados cambia de serie.
 */
@Composable
internal fun FocusSetView(
    w: Workout,
    previous: (String) -> List<WorkoutSet>,
    vm: ActiveWorkoutViewModel,
    dumbbellTotal: Boolean,
    barKgFor: (String) -> Double?,
    pinnedNote: (String) -> String?,
    onFinish: () -> Unit,
    onShowList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val haptics = LocalHapticFeedback.current
    // La serie elegida a mano (tocando un chip o deslizando); si no, la que toca
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val flat = w.exercises.flatMapIndexed { i, we -> we.sets.map { i to it.id } }
    val target: Pair<Int, WorkoutSet>? = picked?.let { id ->
        w.exercises.withIndex().firstNotNullOfOrNull { (i, we) -> we.sets.firstOrNull { it.id == id }?.let { i to it } }
    } ?: WorkoutEditor.upcomingSet(w)

    if (target == null) {
        AllDone(onFinish, onShowList, modifier)
        return
    }
    val (exIndex, set) = target
    val we = w.exercises[exIndex]
    val setIndex = we.sets.indexOfFirst { it.id == set.id }
    val factor = if (dumbbellTotal && we.exercise.equipment == Equipment.DUMBBELL) 2 else 1
    val flatIndex = flat.indexOfFirst { it.second == set.id }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .pointerInput(flatIndex, flat.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val step = when {
                            total < -SWIPE_PX -> 1
                            total > SWIPE_PX -> -1
                            else -> 0
                        }
                        flat.getOrNull(flatIndex + step)?.takeIf { step != 0 }?.let { picked = it.second }
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                ) { _, dx -> total += dx }
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ExerciseProgress(w, exIndex)
        Column {
            Text(
                stringResource(R.string.rd_focus_position, exIndex + 1, w.exercises.size, setIndex + 1, we.sets.size).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                we.exercise.displayName(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val details = listOfNotNull(
                stringResource(R.string.rd_focus_rest, formatDuration(we.restSeconds.toLong())),
                we.tempo?.takeIf { it.isNotBlank() }?.let { "Tempo $it" },
                pinnedNote(we.exercise.id)?.takeIf { it.isNotBlank() },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (we.exercise.tracking) {
            TrackingType.WEIGHT_REPS, TrackingType.REPS_ONLY -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                        BigValue(
                            value = unit.format(set.weightKg * factor),
                            label = unit.label,
                            onMinus = { vm.stepWeight(we.id, set.id, -1) },
                            onPlus = { vm.stepWeight(we.id, set.id, 1) },
                            modifier = Modifier.weight(1.15f),
                        )
                    }
                    BigValue(
                        value = "${set.reps}",
                        label = stringResource(R.string.rd_reps),
                        onMinus = { vm.stepReps(we.id, set.id, -1) },
                        onPlus = { vm.stepReps(we.id, set.id, 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                val prev = WorkoutEditor.previousMatch(we.sets, setIndex, previous(we.exercise.id))
                prev?.let { p -> PreviousRow(p, set, we.exercise, factor) { vm.updateSet(we.id, set.id, weightKg = p.weightKg, reps = p.reps) } }
            }
            else -> Text(stringResource(R.string.rd_focus_use_list), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SetChips(we.sets, set.id, we.exercise, factor) { picked = it }

        // El botón grande: lo que se pulsa con el pulgar entre serie y serie
        val doneLabel = stringResource(if (set.completed) R.string.rd_focus_undo else R.string.rd_focus_done)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(28.dp))
                .background(if (set.completed) androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest) else brandGradient())
                .clickable(role = Role.Button, onClickLabel = doneLabel) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val wasDone = set.completed
                    vm.toggleSet(we.id, set.id)
                    // Hecha: se pasa sola a la siguiente que toca
                    if (!wasDone) picked = null
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (set.completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                if (!set.completed) Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(28.dp))
                Text(doneLabel, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp))
            }
        }

        // Discos por lado (barra) y la pista para cambiar de serie
        val hints = buildList {
            if (we.exercise.equipment == Equipment.BARBELL && we.exercise.tracking == TrackingType.WEIGHT_REPS && set.weightKg > 0) {
                val bar = barKgFor(we.exercise.id)?.let { unit.fromKg(it) } ?: PlateCalculator.defaultBar(unit)
                val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit, bar)
                if (load.perSide.isNotEmpty()) add(stringResource(R.string.rd_focus_plates, load.perSide.joinToString(" · ") { formatWeight(it) }))
            }
            add(stringResource(R.string.rd_focus_swipe))
        }
        Text(
            hints.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        NextExerciseCard(w, exIndex, dumbbellTotal)
    }
}

/** Un tramo por ejercicio: verde hecho, naranja el actual, gris pendiente. */
@Composable
private fun ExerciseProgress(w: Workout, current: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        w.exercises.forEachIndexed { i, we ->
            val color = when {
                we.sets.isNotEmpty() && we.sets.all { it.completed } -> statusColors.good
                i == current -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
            Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
        }
    }
}

/** Número enorme con − y + debajo. */
@Composable
private fun BigValue(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            value,
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, color = LocalContentColor.current, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 64.sp),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$value $label" },
        )
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Remove, stringResource(R.string.rd_focus_less, label)) }
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Add, stringResource(R.string.rd_focus_more, label)) }
        }
    }
}

/** «Anterior 80 kg × 8 · ▲ +2,5 kg»: tocarla la copia en la serie. */
@Composable
private fun PreviousRow(prev: WorkoutSet, set: WorkoutSet, exercise: com.bestrong.core.model.Exercise, factor: Int, onCopy: () -> Unit) {
    val unit = LocalWeightUnit.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.rd_focus_copy_previous), onClick = onCopy)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.rd_focus_previous), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(describeSet(prev, exercise, unit, factor), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val delta = (set.weightKg - prev.weightKg) * factor
        if (exercise.tracking == TrackingType.WEIGHT_REPS && kotlin.math.abs(delta) > 1e-6) {
            Text(
                (if (delta > 0) statusColors.goodMark.ifEmpty { "▲ " } + "+" else statusColors.badMark.ifEmpty { "▼ " } + "−") + unit.format(kotlin.math.abs(delta), withUnit = true),
                color = if (delta > 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Las series del ejercicio: hechas en verde, la elegida con borde; tocar una la abre. */
@Composable
private fun SetChips(sets: List<WorkoutSet>, selectedId: String, exercise: com.bestrong.core.model.Exercise, factor: Int, onPick: (String) -> Unit) {
    val unit = LocalWeightUnit.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sets.forEachIndexed { i, s ->
            val text = when (exercise.tracking) {
                TrackingType.WEIGHT_REPS -> "${unit.format(s.weightKg * factor)}×${s.reps}"
                TrackingType.REPS_ONLY -> "${s.reps}"
                else -> "${i + 1}"
            }
            val selected = s.id == selectedId
            Box(
                Modifier.clip(MaterialTheme.shapes.medium)
                    .background(if (s.completed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .clickable(role = Role.Tab, onClickLabel = stringResource(R.string.rd_focus_open_set, i + 1)) { onPick(s.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    (if (s.completed) "✓ " else "") + text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (s.completed) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** El siguiente ejercicio con algo pendiente, para ir preparando la máquina. */
@Composable
private fun NextExerciseCard(w: Workout, current: Int, dumbbellTotal: Boolean) {
    val unit = LocalWeightUnit.current
    val next = w.exercises.withIndex().firstOrNull { (i, we) -> i > current && we.sets.any { !it.completed } }?.value ?: return
    val first = next.sets.first { !it.completed }
    val factor = if (dumbbellTotal && next.exercise.equipment == Equipment.DUMBBELL) 2 else 1
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.bestrong.mobile.ui.components.ExerciseThumb(next.exercise, 44.dp, enlargeOnTap = false)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.rd_focus_next).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(next.exercise.displayName(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${next.sets.count { !it.completed }} × " + describeSet(first, next.exercise, unit, factor), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AllDone(onFinish: () -> Unit, onShowList: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("🏁", style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.rd_focus_all_done), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.rd_focus_all_done_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.workout_finish), fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onShowList, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rd_focus_show_list)) }
    }
}

/** Lo que hay que arrastrar para pasar de serie. */
private const val SWIPE_PX = 120f
