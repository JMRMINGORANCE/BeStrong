package com.bestrong.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.session.RotaryTarget
import com.bestrong.wear.session.WorkoutSession
import kotlinx.coroutines.delay

/**
 * Pantalla principal del entreno en el reloj (diseñada para la esfera de 47 mm del Watch Ultra).
 * Bisel/corona: ajusta el campo seleccionado (peso o reps). Toca el número para cambiar de campo.
 */
@Composable
fun WorkoutScreen(
    session: WorkoutSession,
    onRest: () -> Unit,
    onMenu: () -> Unit,
    onAddExercise: () -> Unit,
    onNoWorkout: () -> Unit,
) {
    val workout by session.workout.collectAsStateWithLifecycle()
    val index by session.exerciseIndex.collectAsStateWithLifecycle()
    val metrics by session.metrics.collectAsStateWithLifecycle()
    val target by session.rotaryTarget.collectAsStateWithLifecycle()
    val repCounting by session.repCounting.collectAsStateWithLifecycle()
    val countedReps by session.repCounter.reps.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val focus = remember { FocusRequester() }
    var accumulated by remember { mutableFloatStateOf(0f) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val w = workout
    if (w == null) {
        LaunchedEffect(Unit) { onNoWorkout() }
        return
    }
    val we = w.exercises.getOrNull(index)
    val set = we?.sets?.firstOrNull { !it.completed }
    val setNumber = we?.sets?.indexOf(set)?.plus(1) ?: 0
    val previous = we?.let { session.previousFor(it.exercise.id).getOrNull(setNumber - 1) }

    Scaffold(
        modifier = Modifier
            .onRotaryScrollEvent { event ->
                // Acumula el giro: un "clic" del bisel ≈ un paso
                accumulated += event.verticalScrollPixels
                val steps = (accumulated / ROTARY_STEP_PX).toInt()
                if (steps != 0) {
                    accumulated -= steps * ROTARY_STEP_PX
                    repeat(kotlin.math.abs(steps)) { session.rotaryStep(if (steps > 0) 1 else -1) }
                }
                true
            }
            // Botón rápido / botones físicos del Galaxy Watch Ultra: completar serie
            .onPreviewKeyEvent { e ->
                if (e.key.nativeKeyCode in STEM_KEYS) {
                    if (e.type == KeyEventType.KeyUp && session.currentSet() != null && session.completeSet()) onRest()
                    true
                } else false
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(w.durationSeconds(now)), style = MaterialTheme.typography.caption2)
                Text("♥ ${metrics.heartRate ?: "--"}", color = HeartRed, style = MaterialTheme.typography.caption2)
                metrics.calories?.let { Text("$it kcal", style = MaterialTheme.typography.caption2) }
            }
            if (we == null) {
                Spacer(Modifier.height(8.dp))
                Text("Añade tu primer ejercicio", textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onAddExercise, colors = ButtonDefaults.primaryButtonColors()) { Text("＋") }
                Spacer(Modifier.height(4.dp))
                CompactButton(onClick = onMenu, colors = ButtonDefaults.secondaryButtonColors()) { Text("⋯") }
                return@Column
            }
            if (we.supersetId != null) Text("Superserie", style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.secondary)
            Text(
                we.exercise.name,
                style = MaterialTheme.typography.title3,
                color = Brand,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (set == null) {
                Text("Ejercicio completado ✓", color = Done, style = MaterialTheme.typography.body2)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactButton(onClick = session::addSet, colors = ButtonDefaults.secondaryButtonColors()) { Text("+1") }
                    CompactButton(onClick = onMenu, colors = ButtonDefaults.secondaryButtonColors()) { Text("⋯") }
                    CompactButton(onClick = session::nextExercise, enabled = index < w.exercises.lastIndex) { Text("▶") }
                }
                return@Column
            }
            val typeLabel = when (set.type) {
                SetType.WARMUP -> " · Calentamiento"
                SetType.DROP -> " · Drop set"
                SetType.FAILURE -> " · Al fallo"
                SetType.NORMAL -> ""
            }
            Text("Serie $setNumber/${we.sets.size}$typeLabel", style = MaterialTheme.typography.caption2)

            when (we.exercise.tracking) {
                TrackingType.DURATION -> BigValue("${set.durationSec}", "s", selected = true) {}
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (we.exercise.tracking == TrackingType.WEIGHT_REPS || set.weightKg > 0) {
                        BigValue(unit.format(set.weightKg), unit.label, selected = target == RotaryTarget.WEIGHT) {
                            session.setRotaryTarget(RotaryTarget.WEIGHT)
                        }
                        Text("×", style = MaterialTheme.typography.title2)
                    }
                    BigValue("${set.reps}", "reps", selected = target == RotaryTarget.REPS || we.exercise.tracking == TrackingType.REPS_ONLY && set.weightKg == 0.0) {
                        session.setRotaryTarget(RotaryTarget.REPS)
                    }
                }
            }
            if (repCounting) {
                Text("Contadas: $countedReps reps (beta)", style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.secondary)
            }
            previous?.let {
                Text(
                    "Anterior: ${unit.format(it.weightKg)}×${it.reps}",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactButton(onClick = onMenu, colors = ButtonDefaults.secondaryButtonColors()) { Text("⋯") }
                Button(
                    onClick = { if (session.completeSet()) onRest() },
                    colors = ButtonDefaults.primaryButtonColors(backgroundColor = Done),
                    modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
                ) { Text("✓", fontSize = 26.sp, color = Color.Black) }
                CompactButton(
                    onClick = session::nextExercise,
                    enabled = index < w.exercises.lastIndex,
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("▶") }
            }
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
    ) {
        Text(
            value,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Brand else MaterialTheme.colors.onSurface,
        )
        Text(unit, style = MaterialTheme.typography.caption3)
        Box(Modifier.size(width = 24.dp, height = 2.dp).background(if (selected) Brand else Color.Transparent))
    }
}

private const val ROTARY_STEP_PX = 48f

/** Botones físicos: en el Watch Ultra el botón rápido se puede asignar a la app en Ajustes › Funciones avanzadas. */
private val STEM_KEYS = setOf(
    android.view.KeyEvent.KEYCODE_STEM_PRIMARY,
    android.view.KeyEvent.KEYCODE_STEM_1,
    android.view.KeyEvent.KEYCODE_STEM_2,
    android.view.KeyEvent.KEYCODE_STEM_3,
)
