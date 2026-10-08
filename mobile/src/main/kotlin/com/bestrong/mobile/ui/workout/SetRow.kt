package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.util.formatWeight

/** Cabecera de la tabla de series: SERIE | ANTERIOR | KG | REPS | ✓ */
@Composable
fun SetHeader(tracking: TrackingType) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val style = MaterialTheme.typography.labelSmall
        Text("SERIE", style = style, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
        Text("ANTERIOR", style = style, modifier = Modifier.weight(1.4f), textAlign = TextAlign.Center)
        when (tracking) {
            TrackingType.WEIGHT_REPS -> {
                Text("KG", style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("REPS", style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            TrackingType.REPS_ONLY -> {
                Text("+KG", style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("REPS", style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            TrackingType.DURATION -> Text("SEG", style = style, modifier = Modifier.weight(2f), textAlign = TextAlign.Center)
        }
        Box(Modifier.size(48.dp))
    }
}

fun describeSet(set: WorkoutSet, tracking: TrackingType): String = when (tracking) {
    TrackingType.WEIGHT_REPS -> "${formatWeight(set.weightKg)} kg × ${set.reps}"
    TrackingType.REPS_ONLY -> if (set.weightKg > 0) "+${formatWeight(set.weightKg)} kg × ${set.reps}" else "${set.reps} reps"
    TrackingType.DURATION -> "${set.durationSec} s"
}

@Composable
fun SetRow(
    index: Int,
    set: WorkoutSet,
    previous: WorkoutSet?,
    tracking: TrackingType,
    onWeight: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onToggle: () -> Unit,
    onCycleType: () -> Unit,
    onDelete: () -> Unit,
) {
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
            ) { Icon(Icons.Default.Delete, "Eliminar serie", tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) {
        val bg = if (set.completed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        Row(
            Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = if (set.type == SetType.NORMAL) "${index + 1}" else set.type.short
            val labelColor = when (set.type) {
                SetType.WARMUP -> MaterialTheme.colorScheme.tertiary
                SetType.DROP -> MaterialTheme.colorScheme.secondary
                SetType.FAILURE -> MaterialTheme.colorScheme.error
                SetType.NORMAL -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                label,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.6f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onCycleType).padding(vertical = 10.dp),
            )
            Text(
                previous?.let { describeSet(it, tracking) } ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1.4f).clickable(enabled = previous != null) {
                    previous?.let { onWeight(it.weightKg); onReps(it.reps); onDuration(it.durationSec) }
                },
            )
            when (tracking) {
                TrackingType.WEIGHT_REPS, TrackingType.REPS_ONLY -> {
                    NumberField(formatWeight(set.weightKg).takeIf { set.weightKg > 0 } ?: "", Modifier.weight(1f), decimal = true) {
                        it.replace(',', '.').toDoubleOrNull()?.let(onWeight) ?: if (it.isEmpty()) onWeight(0.0) else Unit
                    }
                    NumberField(set.reps.takeIf { it > 0 }?.toString() ?: "", Modifier.weight(1f)) {
                        it.toIntOrNull()?.let(onReps) ?: if (it.isEmpty()) onReps(0) else Unit
                    }
                }
                TrackingType.DURATION -> NumberField(set.durationSec.takeIf { it > 0 }?.toString() ?: "", Modifier.weight(2f)) {
                    it.toIntOrNull()?.let(onDuration) ?: if (it.isEmpty()) onDuration(0) else Unit
                }
            }
            FilledIconToggleButton(checked = set.completed, onCheckedChange = { onToggle() }) {
                Icon(Icons.Default.Check, "Completar serie")
            }
        }
    }
}

/**
 * Campo numérico compacto. Mantiene su propio texto para no perder el cursor mientras
 * el valor viaja a la base de datos y vuelve.
 */
@Composable
private fun NumberField(value: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value }
    BasicTextField(
        value = text,
        onValueChange = { new ->
            val filtered = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)
            text = filtered
            onChange(filtered)
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
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text("0", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                inner()
            }
        },
    )
}
