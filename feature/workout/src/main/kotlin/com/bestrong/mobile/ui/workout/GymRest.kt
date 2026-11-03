package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.RestState
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.rememberNow
import com.bestrong.mobile.ui.components.rememberSecondsLeft
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.KickerStyle
import com.bestrong.mobile.ui.theme.enfoque

/**
 * Barra de arriba del modo gimnasio: tiempo del entreno, pulso del reloj, un tramo por serie (verde las hechas,
 * blanco la actual) y «3/14».
 */
@Composable
internal fun GymTopBar(w: Workout, heartRate: Int?, currentSetId: String? = null) {
    val now = rememberNow()
    val sets = w.exercises.flatMap { it.sets }
    val current = currentSetId ?: WorkoutEditor.upcomingSet(w)?.second?.id
    val position = (sets.indexOfFirst { it.id == current }.takeIf { it >= 0 } ?: w.completedSetCount) + 1
    val elapsed = formatDuration(w.durationSeconds(now))
    val progress = stringResource(R.string.enf_wo_progress, position.coerceAtMost(sets.size), sets.size)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val elapsedDesc = stringResource(R.string.enf_wo_elapsed, elapsed)
        FigureText(elapsed, Modifier.clearAndSetSemantics { contentDescription = elapsedDesc })
        heartRate?.let { hr ->
            val desc = stringResource(R.string.enf_wo_hr, hr)
            Row(Modifier.clearAndSetSemantics { contentDescription = desc }, verticalAlignment = Alignment.CenterVertically) {
                Text("♥", color = BeStrongPalette.Heart, style = MaterialTheme.typography.labelLarge)
                FigureText(" $hr")
            }
        }
        Row(
            Modifier.weight(1f).clearAndSetSemantics { contentDescription = progress },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            sets.forEach { s ->
                val color = when {
                    s.completed -> enfoque.done
                    s.id == current -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
            }
        }
        FigureText("${position.coerceAtMost(sets.size)}/${sets.size}", Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun FigureText(text: String, modifier: Modifier = Modifier) {
    Text(text, fontFamily = FigureFont, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge, modifier = modifier, maxLines = 1)
}

/**
 * Descanso del modo gimnasio: el botón «Hecha» se convierte en un anillo con la cuenta atrás, ±15 s y Saltar; debajo,
 * la serie que viene con sus discos por lado.
 */
@Composable
internal fun GymRestView(w: Workout, rest: RestState, vm: ActiveWorkoutViewModel, inputs: GymInputs, modifier: Modifier = Modifier) {
    Column(modifier) {
        GymTopBar(w, inputs.heartRate)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LastDonePill(w, inputs.dumbbellTotal)
            RestRing(rest)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestButton("−15 s", stringResource(R.string.enf_wo_rest_less), Modifier.weight(1f)) { vm.adjustRest(-REST_STEP) }
                RestButton("+15 s", stringResource(R.string.enf_wo_rest_more), Modifier.weight(1f)) { vm.adjustRest(REST_STEP) }
                RestButton(stringResource(R.string.enf_wo_skip), null, Modifier.weight(1f)) { vm.skipRest() }
            }
            NextSetCard(w, inputs)
            Text(
                stringResource(R.string.enf_wo_auto_next).uppercase(),
                style = KickerStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
    }
}

/** «✓ Serie 5 hecha · 82,5 × 5» de la última serie marcada. */
@Composable
private fun LastDonePill(w: Workout, dumbbellTotal: Boolean) {
    val unit = LocalWeightUnit.current
    val last = w.exercises.flatMap { we -> we.sets.mapIndexed { i, s -> Triple(we, i, s) } }
        .filter { it.third.completed }
        .maxByOrNull { it.third.completedAtEpochMs ?: 0L } ?: return
    val (we, index, set) = last
    val factor = if (dumbbellTotal && we.exercise.equipment == Equipment.DUMBBELL) 2 else 1
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(enfoque.done.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Check, null, tint = enfoque.done, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.enf_wo_set_done, index + 1, describeSet(set, we.exercise, unit, factor)),
                fontFamily = FigureFont,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = enfoque.done,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            we.exercise.displayName().uppercase(),
            style = KickerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

/** El anillo grande que se vacía con la cuenta atrás. */
@Composable
private fun RestRing(rest: RestState) {
    val left = rememberSecondsLeft(rest.endsAtEpochMs)
    val total = rest.totalSeconds.coerceAtLeast(1)
    val fraction = (left.toFloat() / total).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    // Los últimos 10 s, del color de acción
    val arc = if (left <= WARN_SECONDS) enfoque.action else MaterialTheme.colorScheme.onSurface
    val leftText = formatDuration(left)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 240.dp).semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(236.dp).clearAndSetSemantics { }) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(arc, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.enf_wo_rest).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(leftText, fontSize = 62.sp, fontWeight = FontWeight.Black, style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"))
            Text(
                stringResource(R.string.enf_wo_rest_of, formatDuration(rest.totalSeconds.toLong())),
                fontFamily = FigureFont,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestButton(text: String, description: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = FigureFont, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** La serie que viene: ejercicio, carga × reps, «serie 1 de 3» y, con barra, los discos por lado. */
@Composable
private fun NextSetCard(w: Workout, inputs: GymInputs) {
    val unit = LocalWeightUnit.current
    val (i, set) = WorkoutEditor.upcomingSet(w) ?: return
    val we = w.exercises[i]
    val factor = if (inputs.dumbbellTotal && we.exercise.equipment == Equipment.DUMBBELL) 2 else 1
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.rd_focus_next).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(we.exercise.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.enf_wo_next_line, describeSet(set, we.exercise, unit, factor), we.sets.indexOf(set) + 1, we.sets.size),
            fontFamily = FigureFont,
            style = MaterialTheme.typography.labelLarge,
        )
        val barbell = we.exercise.equipment == Equipment.BARBELL || we.exercise.equipment == Equipment.SMITH
        if (barbell && we.exercise.tracking == TrackingType.WEIGHT_REPS && set.weightKg > 0) {
            val bar = inputs.barKgFor(we.exercise.id)?.let { unit.fromKg(it) } ?: PlateCalculator.defaultBar(unit)
            val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit, bar)
            val text = if (load.perSide.isEmpty()) stringResource(R.string.rd_plates_empty) else load.perSide.joinToString(" + ") { formatWeight(it) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BarbellDrawing(load.perSide, Modifier.width(96.dp).height(44.dp).clearAndSetSemantics { })
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.rd_plates_title), fontFamily = FigureFont, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text, fontFamily = FigureFont, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** El modo gimnasio ocupa la pantalla cuando no hay más hueco que una columna. */
@Composable
internal fun GymRestOrSet(
    w: Workout,
    rest: RestState?,
    vm: ActiveWorkoutViewModel,
    inputs: GymInputs,
    onFinish: () -> Unit,
    onShowList: () -> Unit,
) {
    val ownRest = rest?.takeIf { it.workoutId == null || it.workoutId == w.id }
    if (ownRest != null) {
        GymRestView(w, ownRest, vm, inputs, Modifier.fillMaxSize())
    } else {
        FocusSetView(w, vm, inputs, onFinish, onShowList, Modifier.fillMaxSize())
    }
}

private const val REST_STEP = 15
private const val WARN_SECONDS = 10
