package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.training.SetComparison
import com.bestrong.core.util.WeightUnit
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.R
import com.bestrong.mobile.ui.components.LocalWeightUnit

/** Cabecera de la tabla de series: SERIE | ANTERIOR | KG | REPS | ✓ */
@Composable
fun SetHeader(tracking: TrackingType) {
    val unit = LocalWeightUnit.current.label.uppercase()
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val style = MaterialTheme.typography.labelSmall
        Text(stringResource(R.string.set_col_set), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
        Text(stringResource(R.string.set_col_previous), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.4f), textAlign = TextAlign.Center)
        when (tracking) {
            TrackingType.WEIGHT_REPS -> {
                Text(unit, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(stringResource(R.string.set_col_reps), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            TrackingType.REPS_ONLY -> {
                Text("+$unit", style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(stringResource(R.string.set_col_reps), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            TrackingType.DURATION -> Text(stringResource(R.string.set_col_seconds), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f), textAlign = TextAlign.Center)
            TrackingType.DISTANCE -> {
                Text(unit, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(stringResource(R.string.set_col_meters), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.size(48.dp))
    }
}

fun describeSet(set: WorkoutSet, tracking: TrackingType, unit: WeightUnit): String = when (tracking) {
    TrackingType.WEIGHT_REPS -> "${unit.format(set.weightKg)} ${unit.label} × ${set.reps}"
    TrackingType.REPS_ONLY -> if (set.weightKg > 0) "+${unit.format(set.weightKg)} ${unit.label} × ${set.reps}" else "${set.reps} reps"
    TrackingType.DURATION -> "${set.durationSec} s"
    TrackingType.DISTANCE -> (if (set.weightKg > 0) "${unit.format(set.weightKg)} ${unit.label} · " else "") + "${set.distanceM.toInt()} m"
} + (set.repsRight?.let { " | $it" } ?: "") + (set.rpe?.let { " @${it.toString().removeSuffix(".0")}" } ?: "")

private val RPE_VALUES = listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

@Composable
fun SetRow(
    index: Int,
    set: WorkoutSet,
    previous: WorkoutSet?,
    tracking: TrackingType,
    onWeightKg: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onToggle: () -> Unit,
    unilateral: Boolean = false,
    onRepsRight: (Int) -> Unit = {},
    onDistance: (Double) -> Unit = {},
    onNote: (String) -> Unit = {},
    onAssisted: (Boolean) -> Unit = {},
    onType: (SetType) -> Unit,
    onRpe: (Double?) -> Unit,
    onDelete: () -> Unit,
) {
    val unit = LocalWeightUnit.current
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
        false
    })
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, stringResource(R.string.set_delete), tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) {
        val bg = if (set.completed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        val label = if (set.type == SetType.NORMAL) "${index + 1}" else set.type.short
        Row(
            Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 2.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var menu by remember { mutableStateOf(false) }
            var editingNote by remember { mutableStateOf(false) }
            if (editingNote) {
                var text by remember { mutableStateOf(set.note) }
                AlertDialog(
                    onDismissRequest = { editingNote = false },
                    title = { Text(stringResource(R.string.set_note)) },
                    text = { OutlinedTextField(text, { text = it }, placeholder = { Text(stringResource(R.string.set_note_hint)) }, minLines = 2) },
                    confirmButton = { TextButton(onClick = { onNote(text.trim()); editingNote = false }) { Text(stringResource(R.string.action_save)) } },
                    dismissButton = { TextButton(onClick = { editingNote = false }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
            val typeDescription = stringResource(R.string.set_type_and_rpe, label)
            val stateText = stringResource(if (set.completed) R.string.set_done else R.string.set_pending)
            val labelColor = when (set.type) {
                SetType.WARMUP -> MaterialTheme.colorScheme.tertiary
                SetType.DROP -> MaterialTheme.colorScheme.secondary
                SetType.FAILURE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(Modifier.weight(0.6f)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { menu = true }.padding(vertical = 6.dp)
                        .semantics { contentDescription = typeDescription },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label + (if (set.note.isNotBlank()) " ✎" else "") + (if (set.assisted) " ⚑" else ""), fontWeight = FontWeight.Bold, color = labelColor, textAlign = TextAlign.Center)
                    set.rpe?.let { Text("@${it.toString().removeSuffix(".0")}", style = MaterialTheme.typography.labelSmall) }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        SetType.NORMAL to stringResource(R.string.set_type_normal),
                        SetType.WARMUP to stringResource(R.string.set_type_warmup),
                        SetType.DROP to stringResource(R.string.set_type_drop),
                        SetType.FAILURE to stringResource(R.string.set_type_failure),
                        SetType.CLUSTER to stringResource(R.string.set_type_cluster),
                        SetType.PARTIAL to stringResource(R.string.set_type_partial),
                        SetType.NEGATIVE to stringResource(R.string.set_type_negative),
                    )
                        .forEach { (t, l) -> DropdownMenuItem(text = { Text(if (t == set.type) "✓ $l" else l) }, onClick = { onType(t); menu = false }) }
                    HorizontalDivider()
                    Text(stringResource(R.string.rpe), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    RPE_VALUES.forEach { v ->
                        val t = v.toString().removeSuffix(".0")
                        DropdownMenuItem(text = { Text((if (set.rpe == v) "✓ " else "") + stringResource(R.string.rpe_value, t)) }, onClick = { onRpe(v); menu = false })
                    }
                    if (set.rpe != null) DropdownMenuItem(text = { Text(stringResource(R.string.rpe_remove)) }, onClick = { onRpe(null); menu = false })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text((if (set.assisted) "✓ " else "") + stringResource(R.string.set_assisted)) },
                        onClick = { onAssisted(!set.assisted); menu = false },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.set_note)) }, onClick = { editingNote = true; menu = false })
                }
            }
            Column(
                Modifier.weight(1.4f).clickable(enabled = previous != null, onClickLabel = stringResource(R.string.set_copy_previous)) {
                    previous?.let { onWeightKg(it.weightKg); onReps(it.reps); onDuration(it.durationSec) }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    previous?.let { describeSet(it, tracking, unit) } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                // Comparación en vivo con la misma serie de la vez anterior (+2,5 kg · +1)
                val hasValues = set.weightKg > 0 || set.reps > 0 || set.durationSec > 0
                val delta = if (hasValues) SetComparison.delta(set, previous)?.takeIf { !it.isZero } else null
                if (delta != null) {
                    val beats = SetComparison.beats(set, previous) == true
                    val text = buildList {
                        if (delta.weightKg != 0.0) add((if (delta.weightKg > 0) "+" else "−") + unit.format(kotlin.math.abs(delta.weightKg), withUnit = true))
                        if (delta.reps != 0) add((if (delta.reps > 0) "+" else "−") + kotlin.math.abs(delta.reps))
                        if (delta.durationSec != 0) add((if (delta.durationSec > 0) "+" else "−") + kotlin.math.abs(delta.durationSec) + " s")
                    }.joinToString(" · ")
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (beats) BeStrongPalette.VolumeOptimal else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        modifier = Modifier.semantics { contentDescription = text },
                    )
                }
            }
            when (tracking) {
                TrackingType.WEIGHT_REPS, TrackingType.REPS_ONLY -> {
                    // Peso corporal: + lastre, − asistencia (dominadas con banda o máquina asistida)
                    val signed = tracking == TrackingType.REPS_ONLY
                    NumberField(
                        (if (set.weightKg < 0) "-" else "") + (unit.format(kotlin.math.abs(set.weightKg)).takeIf { set.weightKg != 0.0 } ?: ""),
                        stringResource(if (signed) R.string.set_weight_signed else R.string.set_weight_in, unit.label),
                        Modifier.weight(1f),
                        decimal = true,
                        signed = signed,
                    ) {
                        when {
                            it.isEmpty() || it == "-" -> onWeightKg(0.0)
                            it.startsWith("-") -> unit.parseToKg(it.drop(1))?.let { kg -> onWeightKg(-kg) }
                            else -> unit.parseToKg(it)?.let(onWeightKg)
                        }
                    }
                    if (unilateral) {
                        // Unilateral: izquierdo | derecho
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            NumberField(set.reps.takeIf { it > 0 }?.toString() ?: "", stringResource(R.string.set_reps_left), Modifier.weight(1f)) {
                                if (it.isEmpty()) onReps(0) else it.toIntOrNull()?.let(onReps)
                            }
                            NumberField(set.repsRight?.takeIf { it > 0 }?.toString() ?: "", stringResource(R.string.set_reps_right), Modifier.weight(1f)) {
                                onRepsRight(it.toIntOrNull() ?: 0)
                            }
                        }
                    } else {
                        NumberField(set.reps.takeIf { it > 0 }?.toString() ?: "", stringResource(R.string.set_reps), Modifier.weight(1f)) {
                            if (it.isEmpty()) onReps(0) else it.toIntOrNull()?.let(onReps)
                        }
                    }
                }
                TrackingType.DURATION -> NumberField(set.durationSec.takeIf { it > 0 }?.toString() ?: "", stringResource(R.string.set_seconds), Modifier.weight(2f)) {
                    if (it.isEmpty()) onDuration(0) else it.toIntOrNull()?.let(onDuration)
                }
                TrackingType.DISTANCE -> {
                    NumberField(unit.format(set.weightKg).takeIf { set.weightKg > 0 } ?: "", stringResource(R.string.set_weight_in, unit.label), Modifier.weight(1f), decimal = true) {
                        if (it.isEmpty()) onWeightKg(0.0) else unit.parseToKg(it)?.let(onWeightKg)
                    }
                    NumberField(set.distanceM.takeIf { it > 0 }?.toInt()?.toString() ?: "", stringResource(R.string.set_meters), Modifier.weight(1f)) {
                        onDistance(it.toDoubleOrNull() ?: 0.0)
                    }
                }
            }
            FilledIconToggleButton(
                checked = set.completed,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { stateDescription = stateText },
            ) {
                Icon(Icons.Default.Check, stringResource(R.string.set_complete, label))
            }
        }
    }
}

/**
 * Campo numérico compacto. Mantiene su propio texto para no perder el cursor mientras
 * el valor viaja a la base de datos y vuelve.
 */
@Composable
private fun NumberField(value: String, description: String, modifier: Modifier, decimal: Boolean = false, signed: Boolean = false, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value }
    BasicTextField(
        value = text,
        onValueChange = { new ->
            val filtered = new.filterIndexed { i, c -> c.isDigit() || (decimal && (c == '.' || c == ',')) || (signed && c == '-' && i == 0) }.take(6)
            text = filtered
            // "82," aún no es un número completo: se guarda al escribir el decimal
            if (!filtered.endsWith(',') && !filtered.endsWith('.')) onChange(filtered)
        },
        singleLine = true,
        textStyle = TextStyle(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 10.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text("0", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                inner()
            }
        },
    )
}
