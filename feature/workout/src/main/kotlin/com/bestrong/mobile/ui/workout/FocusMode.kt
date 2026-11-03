package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.OneRepMax
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.KickerStyle
import com.bestrong.mobile.ui.theme.enfoque
import com.bestrong.mobile.ui.theme.statusColors

/**
 * Modo gimnasio («Enfoque»): una sola serie a pantalla completa, con el peso y las reps enormes, − y + a los lados y
 * el botón «Hecha» abajo, al alcance del pulgar. Deslizar a los lados cambia de serie; «Todas las series» abre la
 * lista completa.
 */
@Composable
internal fun FocusSetView(
    w: Workout,
    vm: ActiveWorkoutViewModel,
    inputs: GymInputs,
    onFinish: () -> Unit,
    onShowList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La serie elegida a mano (tocando un chip o deslizando); si no, la que toca
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val flat = w.exercises.flatMapIndexed { i, we -> we.sets.map { i to it.id } }
    val target: Pair<Int, WorkoutSet>? = picked?.let { id ->
        w.exercises.withIndex().firstNotNullOfOrNull { (i, we) -> we.sets.firstOrNull { it.id == id }?.let { i to it } }
    } ?: WorkoutEditor.upcomingSet(w)

    if (target == null) {
        Column(modifier) {
            GymTopBar(w, inputs.heartRate)
            AllDone(onFinish, onShowList, Modifier.weight(1f))
        }
        return
    }
    val (exIndex, set) = target
    val we = w.exercises[exIndex]
    val flatIndex = flat.indexOfFirst { it.second == set.id }

    Column(modifier) {
        GymTopBar(w, inputs.heartRate, currentSetId = set.id)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SetHeader(we, set, inputs.pinnedNote(we.exercise.id))
            SetValues(we, set, vm, inputs)
            SetChips(we.sets, set.id, we.exercise, pairFactor(we.exercise, inputs.dumbbellTotal)) { picked = it }
        }
        DoneFooter(we, set, vm, onShowList) { picked = null }
    }
}

/** Factor del peso: con «suma de las dos mancuernas» se enseña el par. */
private fun pairFactor(exercise: Exercise, dumbbellTotal: Boolean): Int = if (dumbbellTotal && exercise.equipment == Equipment.DUMBBELL) 2 else 1

/** «SERIE 3 DE 5», el ejercicio en grande y sus notas (descanso, tempo, nota fija) como chips. */
@Composable
private fun SetHeader(we: WorkoutExercise, set: WorkoutSet, pinnedNote: String?) {
    val setIndex = we.sets.indexOfFirst { it.id == set.id }
    Text(
        stringResource(R.string.enf_wo_set_of, setIndex + 1, we.sets.size).uppercase(),
        style = KickerStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        we.exercise.displayName(),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
    )
    val chips = listOfNotNull(
        stringResource(R.string.rd_focus_rest, formatDuration(we.restSeconds.toLong())),
        we.tempo?.takeIf { it.isNotBlank() }?.let { "Tempo $it" },
        pinnedNote?.takeIf { it.isNotBlank() },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { GymChip(it) }
    }
}

@Composable
internal fun GymChip(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        fontFamily = FigureFont,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

/** Peso y reps enormes con − y +, la serie anterior y el chip de récord. */
@Composable
private fun SetValues(we: WorkoutExercise, set: WorkoutSet, vm: ActiveWorkoutViewModel, inputs: GymInputs) {
    val unit = LocalWeightUnit.current
    val factor = pairFactor(we.exercise, inputs.dumbbellTotal)
    when (we.exercise.tracking) {
        TrackingType.WEIGHT_REPS, TrackingType.REPS_ONLY -> {
            if (we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                BigValue(unit.format(set.weightKg * factor), unit.label, { vm.stepWeight(we.id, set.id, -1) }, { vm.stepWeight(we.id, set.id, 1) })
                GymDivider()
            }
            BigValue("${set.reps}", stringResource(R.string.rd_reps), { vm.stepReps(we.id, set.id, -1) }, { vm.stepReps(we.id, set.id, 1) })
            val setIndex = we.sets.indexOfFirst { it.id == set.id }
            val prev = WorkoutEditor.previousMatch(we.sets, setIndex, inputs.previous(we.exercise.id))
            PreviousLine(prev, set, we, factor, inputs.bestOneRepMax(we.exercise.id)) { p -> vm.updateSet(we.id, set.id, weightKg = p.weightKg, reps = p.reps) }
        }
        else -> Text(stringResource(R.string.rd_focus_use_list), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
    }
}

@Composable
private fun GymDivider() {
    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Número enorme en el centro con − y + circulares a los lados. */
@Composable
private fun BigValue(value: String, label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Default.Remove, stringResource(R.string.rd_focus_less, label), onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                value,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = LocalContentColor.current,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 36.sp, maxFontSize = 76.sp),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$value $label" },
            )
            Text(label, fontFamily = FigureFont, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StepButton(Icons.Default.Add, stringResource(R.string.rd_focus_more, label), onPlus)
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(26.dp)) }
}

/** «Anterior 80 × 5 · ▲ +2,5» (tocarla la copia) y, a la derecha, «récord con 6» o «★ récord». */
@Composable
private fun PreviousLine(prev: WorkoutSet?, set: WorkoutSet, we: WorkoutExercise, factor: Int, bestOneRepMax: Double?, onCopy: (WorkoutSet) -> Unit) {
    val unit = LocalWeightUnit.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val text = prev?.let { p ->
            val delta = (set.weightKg - p.weightKg) * factor
            val deltaText = when {
                we.exercise.tracking != TrackingType.WEIGHT_REPS || kotlin.math.abs(delta) < 1e-6 -> ""
                delta > 0 -> " · ▲ +" + unit.format(delta)
                else -> " · ▼ −" + unit.format(-delta)
            }
            stringResource(R.string.rd_focus_previous) + " " + describeSet(p, we.exercise, unit, factor) + deltaText
        }.orEmpty()
        Text(
            text,
            fontFamily = FigureFont,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.weight(1f).then(
                if (prev != null) Modifier.clickable(onClickLabel = stringResource(R.string.rd_focus_copy_previous)) { onCopy(prev) } else Modifier,
            ),
        )
        if (we.exercise.tracking == TrackingType.WEIGHT_REPS && bestOneRepMax != null) RecordChip(set, bestOneRepMax)
    }
}

/** Ámbar: «récord con N» si con N reps se bate el 1RM estimado; relleno («★ récord») si ya se alcanza. */
@Composable
private fun RecordChip(set: WorkoutSet, bestOneRepMax: Double) {
    val reps = OneRepMax.repsToBeat(bestOneRepMax, set.weightKg) ?: return
    val hit = set.reps >= reps
    val record = enfoque.record
    Text(
        if (hit) stringResource(R.string.enf_wo_record_hit) else "★ " + stringResource(R.string.enf_wo_record_with, reps),
        fontFamily = FigureFont,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (hit) enfoque.onRecord else record,
        modifier = Modifier.clip(CircleShape)
            .then(if (hit) Modifier.background(record) else Modifier.border(1.dp, record.copy(alpha = 0.5f), CircleShape))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** El botón enorme «Hecha» y, debajo, el acceso a todas las series. */
@Composable
private fun DoneFooter(we: WorkoutExercise, set: WorkoutSet, vm: ActiveWorkoutViewModel, onShowList: () -> Unit, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val doneLabel = stringResource(if (set.completed) R.string.enf_wo_undo else R.string.enf_wo_done)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val bg = if (set.completed) MaterialTheme.colorScheme.surfaceContainerHighest else enfoque.action
        val fg = if (set.completed) MaterialTheme.colorScheme.onSurface else enfoque.onAction
        Row(
            Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(22.dp)).background(bg)
                .clickable(role = Role.Button, onClickLabel = doneLabel) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val wasDone = set.completed
                    vm.toggleSet(we.id, set.id)
                    // Hecha: se pasa sola a la siguiente que toca
                    if (!wasDone) onDone()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!set.completed) Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(30.dp))
            Text(doneLabel, color = fg, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
        TextButton(onClick = onShowList) {
            Text("↔ " + stringResource(R.string.enf_wo_all_sets).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Las series del ejercicio: hechas en verde, la elegida con borde; tocar una la abre. */
@Composable
private fun SetChips(sets: List<WorkoutSet>, selectedId: String, exercise: Exercise, factor: Int, onPick: (String) -> Unit) {
    val unit = LocalWeightUnit.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sets.forEachIndexed { i, s ->
            val text = when (exercise.tracking) {
                TrackingType.WEIGHT_REPS -> "${unit.format(s.weightKg * factor)}×${s.reps}"
                TrackingType.REPS_ONLY -> "${s.reps}"
                else -> "${i + 1}"
            }
            val selected = s.id == selectedId
            Box(
                Modifier.clip(MaterialTheme.shapes.medium)
                    .background(if (s.completed) enfoque.done.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.medium) else Modifier)
                    .clickable(role = Role.Tab, onClickLabel = stringResource(R.string.rd_focus_open_set, i + 1)) { onPick(s.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    (if (s.completed) "✓ " else "") + text,
                    fontFamily = FigureFont,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (s.completed) enfoque.done else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AllDone(onFinish: () -> Unit, onShowList: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("✓", style = MaterialTheme.typography.displayLarge, color = statusColors.good)
        Text(stringResource(R.string.rd_focus_all_done), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.rd_focus_all_done_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.workout_finish), fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onShowList, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.enf_wo_all_sets)) }
    }
}

/** Lo que hay que arrastrar para pasar de serie. */
private const val SWIPE_PX = 120f
