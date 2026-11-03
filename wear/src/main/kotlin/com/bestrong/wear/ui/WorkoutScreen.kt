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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    val repCounting by session.repCounting.collectAsStateWithLifecycle()
    // Marcas del móvil: si llegan nuevas, la pista «N reps = récord» se recalcula
    val records by session.recordsVersion.collectAsStateWithLifecycle(initialValue = Unit to Unit)
    val countedReps by session.repCounter.reps.collectAsStateWithLifecycle()
    val repConfidence by session.repCounter.confidence.collectAsStateWithLifecycle()
    val sharedSettings = session.settings.collectAsStateWithLifecycle().value
    val pinnedNotes = sharedSettings.pinnedNotes
    val velocity by session.velocityInfo.collectAsStateWithLifecycle()
    val voice = rememberVoiceInput(stringResource(R.string.voice_prompt)) { session.applySpoken(it) }
    val unit = LocalWeightUnit.current
    val focus = remember { FocusRequester() }
    var accumulated by remember { mutableFloatStateOf(0f) }
    val view = androidx.compose.ui.platform.LocalView.current
    // Sin entreno (notificación o tile antiguos) el foco no llega a engancharse: no debe cerrar la app
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val w = workout
    if (w == null) {
        LaunchedEffect(Unit) { onNoWorkout() }
        return
    }
    val we = w.exercises.getOrNull(index)
    val factor = we?.let { sharedSettings.weightFactor(it.exercise) } ?: 1
    val set = we?.sets?.firstOrNull { !it.completed }
    val setNumber = we?.sets?.indexOf(set)?.plus(1) ?: 0
    val previous = we?.let { com.bestrong.core.session.WorkoutEditor.previousMatch(it.sets, setNumber - 1, session.previousFor(it.exercise.id)) }

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
                        // Un toque por paso: se ajusta sin mirar
                        repeat(kotlin.math.abs(steps)) {
                            session.rotaryStep(if (steps > 0) 1 else -1)
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
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
                    ElapsedText(w)
                    Text("♥ ${metrics.heartRate ?: "--"}", color = HeartRed, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    // Una sola línea en pantallas redondas pequeñas: con zona no caben también las kcal (siguen en el menú)
                    val zone = session.heartRateZone(metrics.heartRate)
                    if (zone > 0) {
                        Text(stringResource(R.string.hr_zone, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, softWrap = false)
                    } else {
                        metrics.calories?.let { Text(stringResource(R.string.kcal, it), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
                    }
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
                // «Enfoque»: el ejercicio y la serie en una etiqueta pequeña; lo grande es el peso
                val position = set?.let { " · $setNumber/${we.sets.size}" }.orEmpty()
                Text(
                    we.exercise.displayName().uppercase() + position,
                    style = KickerStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (typeLabel.isNotBlank()) Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                we.targetRpe?.takeIf { we.exercise.tracking == TrackingType.WEIGHT_REPS && set.type == SetType.NORMAL }?.let { rpe ->
                    // Con historial, el peso orientativo para las reps de esta serie a ese RPE
                    val load = remember(we.exercise.id, set.reps, rpe, records) { session.targetRpeLoadKg(we.exercise, set.reps, rpe) }
                    Text(
                        stringResource(R.string.target_rpe_short, com.bestrong.core.util.formatRpe(rpe)) + load?.let { " · ≈" + unit.format(it * factor) }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        // Tocar pone ese peso en la serie actual
                        modifier = if (load != null) Modifier.clickable(onClickLabel = stringResource(R.string.use_this_weight)) { session.setCurrentWeight(load) } else Modifier,
                    )
                }
                // Rango de la doble progresión (lo elige el móvil)
                if (we.exercise.tracking == TrackingType.WEIGHT_REPS) {
                    session.settings.collectAsStateWithLifecycle().value.repRangeFor(we.exercise.id)?.let { r ->
                        Text(stringResource(R.string.rep_range_short, r.first, r.last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                when (we.exercise.tracking) {
                    TrackingType.DURATION -> BigValue("${set.durationSec}", "s", selected = true, onClick = null)
                    TrackingType.CARDIO -> if (set.isRunning) {
                        CardioStopwatch(set.startedAtEpochMs ?: 0L, w.pausedAtEpochMs)
                    } else {
                        BigValue("${set.durationSec / 60}", "min", selected = true, onClick = null)
                    }
                    else -> LoadValues(we, set, unit, factor, session)
                }
                if (repCounting) {
                    CountedRepsLine(countedReps, repConfidence, onAdjust = session::adjustCountedReps)
                    velocity?.let { v ->
                        Text(
                            stringResource(R.string.velocity_info, "%.2f".format(v.lastVelocity), v.rir?.toString() ?: "?"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                // Avisos en un bloque que cede sitio: con muchos, se recortan por abajo (los menos importantes) y el
                // menú ⋯ (Terminar, Deshacer…) nunca se queda sin hueco
                Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                    previous?.let {
                        Text(
                            stringResource(R.string.previous_any, setText(it, we.exercise.tracking, unit, stringResource(R.string.unit_reps), factor)),
                            // «La última vez» bien visible: es lo que hay que batir; tocarla la copia en esta serie
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.copy_previous)) { session.copyPrevious(it) },
                        )
                    }
                    // Reps que batirían tu récord con este peso (solo series que cuentan)
                    if (we.exercise.tracking == TrackingType.WEIGHT_REPS && set.countsForRecords) {
                        // Solo cambia con las series hechas (no a cada giro de la corona)
                        val doneSignature = w.exercises.sumOf { e -> e.completedSets.sumOf { it.estimatedOneRepMax } }
                        val best = remember(we.exercise.id, doneSignature, records) { session.bestOneRepMax(we.exercise.id) }
                        best?.let { com.bestrong.core.stats.OneRepMax.repsToBeat(it, set.weightKg) }?.let { reps ->
                            Text(pluralStringResource(R.plurals.record_hint, reps, reps), style = MaterialTheme.typography.labelSmall, color = Record)
                        }
                    }
                    // Las reps que planea la rutina para esta serie, si difieren de lo que pone
                    set?.targetReps?.takeIf { it != set.reps && we.exercise.tracking == TrackingType.WEIGHT_REPS }?.let { planned ->
                        Text(stringResource(R.string.planned_reps, planned), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                    // «Enfoque»: HECHA en el borde inferior, en el color de acción
                    colors = ButtonDefaults.buttonColors(containerColor = Brand, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.align(Alignment.BottomCenter).semantics { contentDescription = edgeDescription },
                ) {
                    // Cardio: ▶ arranca el cronómetro (y el reloj pasa al modo de esa máquina); HECHA lo para
                    if (startsTimer) {
                        Text("▶", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.enf_done), fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String, selected: Boolean, onClick: (() -> Unit)?) {
    // Zona táctil de 48 dp: es lo que elige qué ajusta la corona. Como opción seleccionable (TalkBack dice cuál
    // está marcada); sin [onClick] (duración, cronómetro) no se anuncia como un botón que no hace nada
    val choose = onClick?.let { Modifier.selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = it) } ?: Modifier
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).then(choose).padding(horizontal = 4.dp),
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

/**
 * Peso enorme y «× 5 reps» debajo (o solo las reps, o los metros, si no hay peso). Lo marcado es lo que mueve el
 * bisel; tocar el otro valor le pasa el bisel.
 */
@Composable
private fun LoadValues(
    we: com.bestrong.core.model.WorkoutExercise,
    set: com.bestrong.core.model.WorkoutSet,
    unit: com.bestrong.core.util.WeightUnit,
    factor: Int,
    session: WorkoutSession,
) {
    val effective = session.effectiveTarget(we, set)
    val distance = we.exercise.tracking == TrackingType.DISTANCE
    val value = if (distance) "${set.distanceM.toInt()}" else "${set.reps}"
    val label = if (distance) "m" else stringResource(R.string.unit_reps)
    if (com.bestrong.wear.session.showsWeight(we, set)) {
        HeroValue(unit.format(set.weightKg * factor), unit.label, selected = effective == RotaryTarget.WEIGHT) { session.setRotaryTarget(RotaryTarget.WEIGHT) }
        CountValue("× $value $label", selected = effective == RotaryTarget.REPS) { session.setRotaryTarget(RotaryTarget.REPS) }
    } else {
        HeroValue(value, label, selected = true) { session.setRotaryTarget(RotaryTarget.REPS) }
    }
}

/**
 * La cifra grande de la serie. Tocarla hace que el bisel la ajuste; la raya de acento debajo dice cuál manda.
 */
@Composable
private fun HeroValue(value: String, unit: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$value $unit" },
    ) {
        Text(value, style = FigureStyle, fontSize = 46.sp, lineHeight = 46.sp, maxLines = 1)
        Box(Modifier.size(width = 28.dp, height = 2.dp).background(if (selected) Brand else Color.Transparent))
    }
}

/** «× 5 reps» bajo el peso: tocarlo pasa el bisel a las reps (en el color de acción mientras manda). */
@Composable
private fun CountValue(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = if (selected) Brand else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 32.dp)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(top = 4.dp),
        textAlign = TextAlign.Center,
    )
}

private const val ROTARY_STEP_PX = 48f

/** Botones físicos: en el Watch Ultra el botón rápido se puede asignar a la app en Ajustes › Funciones avanzadas. */
internal val STEM_KEYS = setOf(
    android.view.KeyEvent.KEYCODE_STEM_PRIMARY,
    android.view.KeyEvent.KEYCODE_STEM_1,
    android.view.KeyEvent.KEYCODE_STEM_2,
    android.view.KeyEvent.KEYCODE_STEM_3,
)

/** Cronómetro del bloque de cardio en marcha (mm:ss), actualizado cada segundo; parado con el entreno en pausa. */
@Composable
private fun CardioStopwatch(startedAtEpochMs: Long, pausedAt: Long?) {
    val elapsed by androidx.compose.runtime.produceState(0L, startedAtEpochMs, pausedAt) {
        while (true) {
            value = (((pausedAt ?: System.currentTimeMillis()) - startedAtEpochMs) / 1000).coerceAtLeast(0)
            kotlinx.coroutines.delay(1000)
        }
    }
    BigValue(com.bestrong.core.util.formatDuration(elapsed), "", selected = true, onClick = null)
}

/** Tiempo del entreno: el reloj de cada segundo solo redibuja esta etiqueta, no toda la pantalla del entreno. */
@Composable
private fun ElapsedText(w: com.bestrong.core.model.Workout) {
    val now = rememberVisibleNow(1_000)
    Text(formatDuration(w.durationSeconds(now)), style = MaterialTheme.typography.labelSmall)
}

/**
 * «Contadas: 8 · fiable» con «−» y «+» para corregir de un toque antes de marcar la serie (lo que se guarda es este
 * número). La confianza dice si conviene revisarlo.
 */
@Composable
internal fun CountedRepsLine(reps: Int, confidence: com.bestrong.core.sensors.RepConfidence, onAdjust: (Int) -> Unit) {
    val conf = stringResource(
        when (confidence) {
            com.bestrong.core.sensors.RepConfidence.HIGH -> R.string.rep_conf_high
            com.bestrong.core.sensors.RepConfidence.MEDIUM -> R.string.rep_conf_medium
            com.bestrong.core.sensors.RepConfidence.LOW -> R.string.rep_conf_low
        },
    )
    val color = when (confidence) {
        com.bestrong.core.sensors.RepConfidence.HIGH -> Done
        com.bestrong.core.sensors.RepConfidence.MEDIUM -> MaterialTheme.colorScheme.secondary
        com.bestrong.core.sensors.RepConfidence.LOW -> MaterialTheme.colorScheme.error
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        CountStep("−", stringResource(R.string.count_minus)) { onAdjust(-1) }
        Text(stringResource(R.string.counted_reps_conf, reps, conf), style = MaterialTheme.typography.labelSmall, color = color)
        CountStep("+", stringResource(R.string.count_plus)) { onAdjust(1) }
    }
}

/** Botón pequeño de texto (48 dp de zona táctil) para corregir el recuento. */
@Composable
private fun CountStep(symbol: String, description: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(36.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = Brand)
    }
}
