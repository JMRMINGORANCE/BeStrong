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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.R
import com.bestrong.wear.session.RotaryTarget
import com.bestrong.wear.session.WorkoutSession
import kotlinx.coroutines.delay

/**
 * Pantalla principal del entreno en el reloj (pensada para la esfera de 47 mm del Watch Ultra).
 * - Bisel/corona: ajusta el campo seleccionado (peso o reps). Toca el número para cambiar de campo.
 * - Botón inferior (EdgeButton) o botón rápido del Ultra: completar serie.
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
    // Marcas del móvil: si llegan nuevas, la pista «N reps = récord» se recalcula
    val records by session.recordsVersion.collectAsStateWithLifecycle(initialValue = Unit to Unit)
    val countedReps by session.repCounter.reps.collectAsStateWithLifecycle()
    val pinnedNotes = session.settings.collectAsStateWithLifecycle().value.pinnedNotes
    val velocity by session.velocityInfo.collectAsStateWithLifecycle()
    val voice = rememberVoiceInput(stringResource(R.string.voice_prompt)) { session.applySpoken(it) }
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

    val completeDescription = stringResource(R.string.complete_set)
    ScreenScaffold {
        Box(
            Modifier
                .fillMaxSize()
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
                        if (e.type == KeyEventType.KeyUp && session.onStemInWorkout()) onRest()
                        true
                    } else {
                        false
                    }
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            Column(
                Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 62.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(w.durationSeconds(now)), style = MaterialTheme.typography.labelSmall)
                    Text("♥ ${metrics.heartRate ?: "--"}", color = HeartRed, style = MaterialTheme.typography.labelSmall)
                    session.heartRateZone(metrics.heartRate).takeIf { it > 0 }?.let { z ->
                        Text(stringResource(R.string.hr_zone, z), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    metrics.calories?.let { Text(stringResource(R.string.kcal, it), style = MaterialTheme.typography.labelSmall) }
                }
                if (we == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.add_first_exercise), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onMenu, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_menu))) { Text("⋯") }
                        TextButton(onClick = onAddExercise, colors = TextButtonDefaults.filledTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_add_exercise))) { Text("＋") }
                    }
                    return@Column
                }
                if (we.supersetId != null) Text(stringResource(R.string.superset), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(
                    we.exercise.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                // Nota fija del móvil (asiento, agarre…), en una línea
                pinnedNotes[we.exercise.id]?.let {
                    Text("📌 $it", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
                if (set == null) {
                    Text(stringResource(R.string.exercise_done), color = Done, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = session::addSet, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_add_one_set))) { Text("+1") }
                        TextButton(onClick = onMenu, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_menu))) { Text("⋯") }
                        TextButton(onClick = session::nextExercise, enabled = index < w.exercises.lastIndex, modifier = Modifier.describedAs(stringResource(R.string.a11y_next_exercise))) { Text("▶") }
                    }
                    return@Column
                }
                val typeLabel = when (set.type) {
                    SetType.WARMUP -> stringResource(R.string.type_warmup)
                    SetType.DROP -> stringResource(R.string.type_drop)
                    SetType.FAILURE -> stringResource(R.string.type_failure)
                    else -> ""
                }
                Text(stringResource(R.string.set_of, setNumber, we.sets.size, typeLabel), style = MaterialTheme.typography.labelSmall)
                we.targetRpe?.takeIf { we.exercise.tracking == TrackingType.WEIGHT_REPS && set.type == SetType.NORMAL }?.let { rpe ->
                    // Con historial, el peso orientativo para las reps de esta serie a ese RPE
                    val load = remember(we.exercise.id, set.reps, rpe, records) { session.targetRpeLoadKg(we.exercise, set.reps, rpe) }
                    Text(
                        stringResource(R.string.target_rpe_short, rpe.toString().removeSuffix(".0")) + load?.let { " · ≈" + unit.format(it) }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                // Rango de la doble progresión (lo elige el móvil)
                if (we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                    session.settings.collectAsStateWithLifecycle().value.repRangeFor(we.exercise.id)?.let { r ->
                        Text(stringResource(R.string.rep_range_short, r.first, r.last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                when (we.exercise.tracking) {
                    TrackingType.DURATION -> BigValue("${set.durationSec}", "s", selected = true) {}
                    TrackingType.CARDIO -> if (set.isRunning) {
                        CardioStopwatch(set.startedAtEpochMs ?: 0L)
                    } else {
                        BigValue("${set.durationSec / 60}", "min", selected = true) {}
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (we.exercise.tracking == TrackingType.WEIGHT_REPS || set.weightKg > 0) {
                            BigValue(unit.format(set.weightKg), unit.label, selected = target == RotaryTarget.WEIGHT) {
                                session.setRotaryTarget(RotaryTarget.WEIGHT)
                            }
                            Text("×", style = MaterialTheme.typography.titleLarge)
                        }
                        BigValue(
                            "${set.reps}", "reps",
                            selected = target == RotaryTarget.REPS || we.exercise.tracking == TrackingType.REPS_ONLY && set.weightKg == 0.0,
                        ) { session.setRotaryTarget(RotaryTarget.REPS) }
                    }
                }
                if (repCounting) {
                    Text(stringResource(R.string.counted_reps, countedReps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    velocity?.let { v ->
                        Text(
                            stringResource(R.string.velocity_info, "%.2f".format(v.lastVelocity), v.rir?.toString() ?: "?"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                // Reps que batirían tu récord con este peso (solo series que cuentan)
                if (we.exercise.tracking == TrackingType.WEIGHT_REPS && set.countsForRecords) {
                    // Solo cambia con las series hechas (no a cada giro de la corona)
                    val doneSignature = w.exercises.sumOf { e -> e.completedSets.sumOf { it.estimatedOneRepMax } }
                    val best = remember(we.exercise.id, doneSignature, records) { session.bestOneRepMax(we.exercise.id) }
                    best?.let { com.bestrong.core.stats.OneRepMax.repsToBeat(it, set.weightKg) }?.let { reps ->
                        Text(pluralStringResource(R.plurals.record_hint, reps, reps), style = MaterialTheme.typography.labelSmall, color = Done)
                    }
                }
                previous?.takeIf { we.exercise.tracking != TrackingType.CARDIO }?.let {
                    Text(
                        stringResource(R.string.previous, unit.format(it.weightKg), it.reps),
                        // «La última vez» bien visible: es lo que hay que batir
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onMenu, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_menu))) { Text("⋯") }
                    voice?.let { launch ->
                        TextButton(onClick = launch, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_voice))) { Text("🎤") }
                    }
                    TextButton(
                        onClick = session::nextExercise,
                        enabled = index < w.exercises.lastIndex,
                        colors = TextButtonDefaults.filledTonalTextButtonColors(),
                        modifier = Modifier.describedAs(stringResource(R.string.a11y_next_exercise)),
                    ) { Text("▶") }
                }
            }
            if (set != null) {
                val startsTimer = we?.exercise?.tracking == TrackingType.CARDIO && !set.isRunning
                val edgeDescription = if (startsTimer) stringResource(R.string.cardio_start) else completeDescription
                EdgeButton(
                    onClick = { if (session.completeSet()) onRest() },
                    buttonSize = EdgeButtonSize.Small,
                    colors = ButtonDefaults.buttonColors(containerColor = Done, contentColor = Color.Black),
                    modifier = Modifier.align(Alignment.BottomCenter).semantics { contentDescription = edgeDescription },
                ) {
                    // Cardio: ▶ arranca el cronómetro (y el reloj pasa al modo de esa máquina); ✓ lo para
                    Text(if (startsTimer) "▶" else "✓", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
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
            color = if (selected) Brand else MaterialTheme.colorScheme.onSurface,
        )
        Text(unit, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.size(width = 24.dp, height = 2.dp).background(if (selected) Brand else Color.Transparent))
    }
}

private const val ROTARY_STEP_PX = 48f

/** Botones físicos: en el Watch Ultra el botón rápido se puede asignar a la app en Ajustes › Funciones avanzadas. */
internal val STEM_KEYS = setOf(
    android.view.KeyEvent.KEYCODE_STEM_PRIMARY,
    android.view.KeyEvent.KEYCODE_STEM_1,
    android.view.KeyEvent.KEYCODE_STEM_2,
    android.view.KeyEvent.KEYCODE_STEM_3,
)

/** Cronómetro del bloque de cardio en marcha (mm:ss), actualizado cada segundo. */
@Composable
private fun CardioStopwatch(startedAtEpochMs: Long) {
    val elapsed by androidx.compose.runtime.produceState(0L, startedAtEpochMs) {
        while (true) {
            value = ((System.currentTimeMillis() - startedAtEpochMs) / 1000).coerceAtLeast(0)
            kotlinx.coroutines.delay(1000)
        }
    }
    BigValue(com.bestrong.core.util.formatDuration(elapsed), "", selected = true) {}
}
