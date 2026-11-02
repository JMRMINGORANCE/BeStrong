package com.bestrong.wear.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession

/** Descanso a pantalla completa con anillo de progreso por el borde de la esfera. */
@Composable
fun RestScreen(session: WorkoutSession) {
    val rest by session.rest.collectAsStateWithLifecycle()
    val metrics by session.metrics.collectAsStateWithLifecycle()
    val workout by session.workout.collectAsStateWithLifecycle()
    val recovered by session.recovered.collectAsStateWithLifecycle()
    val record by session.record.collectAsStateWithLifecycle()

    val unit = LocalWeightUnit.current
    val r = rest ?: return
    // Redondeado hacia arriba: 1:00 al empezar y 0:01 el último segundo (no 0:00 antes de acabar)
    // Una vez por segundo, justo cuando cambia el número (antes se redibujaba entera 5 veces por segundo)
    val remaining = rememberSecondsLeft(r.endsAtEpochMs)
    val progress = (remaining.toFloat() / r.totalSeconds).coerceIn(0f, 1f)
    val next = workout?.let { w -> WorkoutEditor.upcomingSet(w)?.let { (i, s) -> w.exercises[i] to s } }

    val view = androidx.compose.ui.platform.LocalView.current
    val focus = remember { FocusRequester() }
    var rotary by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    ScreenScaffold(timeText = {}) {
        Box(
            Modifier.fillMaxSize()
                // Botón rápido del Ultra durante el descanso: saltarlo (o pasar al siguiente ejercicio)
                .onPreviewKeyEvent { e ->
                    if (e.key.nativeKeyCode in STEM_KEYS) {
                        if (e.type == KeyEventType.KeyUp) session.onStemInRest()
                        true
                    } else {
                        false
                    }
                }
                // Corona o bisel: ±15 s por paso, como los botones
                .onRotaryScrollEvent { e ->
                    rotary += e.verticalScrollPixels
                    val steps = (rotary / ROTARY_STEP_PX).toInt()
                    if (steps != 0) {
                        rotary -= steps * ROTARY_STEP_PX
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        session.adjustRest(steps * REST_STEP_S)
                    }
                    true
                }
                .focusRequester(focus)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize().padding(4.dp),
                colors = ProgressIndicatorDefaults.colors(indicatorColor = Brand),
                strokeWidth = 8.dp,
            )
            val settings by session.settings.collectAsStateWithLifecycle()
            val transition = workout?.takeIf { settings.nextExerciseCard }?.let(::nextExerciseTransition)
            if (transition != null) {
                NextExerciseContent(transition.first, transition.second, remaining, settings, session)
                return@Box
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.rest), style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(remaining), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                val zone = session.heartRateZone(metrics.heartRate)
                Text("♥ ${metrics.heartRate ?: "--"}" + if (zone > 0) " · " + stringResource(R.string.hr_zone, zone) else "", color = HeartRed, style = MaterialTheme.typography.labelSmall)
                // Una sola línea de aviso (el récord manda): con las dos, los botones se iban contra el anillo
                // Si no hay otro aviso: la serie recién hecha frente a la de la última vez («▲ Última vez: 80×7»)
                when {
                    record -> Text(stringResource(R.string.new_record_live), color = Done, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    recovered -> Text(stringResource(R.string.recovered), color = Done, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    else -> LastSetLine(session, workout, unit)
                }
                next?.let { (we, s) ->
                    Text(
                        stringResource(R.string.next_any, we.exercise.displayName(), setText(s, we.exercise.tracking, unit, stringResource(R.string.unit_reps), session.settings.collectAsStateWithLifecycle().value.weightFactor(we.exercise))),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { session.adjustRest(-15) }, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_rest_minus))) { Text("-15") }
                    TextButton(onClick = session::stopRest, colors = TextButtonDefaults.filledTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_skip_rest))) { Text("⏭") }
                    TextButton(onClick = { session.adjustRest(15) }, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_rest_plus))) { Text("+15") }
                }
            }
        }
    }
}

private const val ROTARY_STEP_PX = 60f
private const val REST_STEP_S = 15

/**
 * El ejercicio que viene tras el descanso si es otro (no la siguiente serie del mismo ni el compañero de superserie),
 * con su primera serie pendiente.
 */
internal fun nextExerciseTransition(w: Workout): Pair<WorkoutExercise, WorkoutSet>? {
    val next = WorkoutEditor.upcomingSet(w) ?: return null
    val lastDone = w.exercises.withIndex()
        .mapNotNull { (i, we) -> we.sets.filter { it.completed }.maxOfOrNull { it.completedAtEpochMs ?: 0L }?.let { i to it } }
        .maxByOrNull { it.second }?.first ?: return null
    if (next.first == lastDone) return null
    val from = w.exercises[lastDone]
    val to = w.exercises[next.first]
    if (from.supersetId != null && from.supersetId == to.supersetId) return null
    return to to next.second
}

/**
 * «Siguiente ejercicio» en el reloj (propuesta 09): nombre, peso, discos por lado, nota fija y la cuenta atrás de
 * preparación, con «Empezar» (salta el descanso) y ±15 s.
 */
@Composable
private fun NextExerciseContent(we: WorkoutExercise, set: WorkoutSet, remaining: Long, settings: SharedSettings, session: WorkoutSession) {
    val unit = LocalWeightUnit.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.padding(horizontal = 22.dp),
    ) {
        Text(stringResource(R.string.next_exercise_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = Brand, fontWeight = FontWeight.Bold)
        Text(
            we.exercise.displayName(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            setText(set, we.exercise.tracking, unit, stringResource(R.string.unit_reps), settings.weightFactor(we.exercise)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (we.exercise.tracking == TrackingType.WEIGHT_REPS && set.weightKg > 0 &&
            (we.exercise.equipment == Equipment.BARBELL || we.exercise.equipment == Equipment.SMITH)
        ) {
            val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit, unit.fromKg(settings.barKgFor(we.exercise.id)))
            if (load.perSide.isNotEmpty()) {
                Text(
                    stringResource(R.string.next_exercise_plates, load.perSide.joinToString(" + ") { formatWeight(it) }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        settings.pinnedNotes[we.exercise.id]?.takeIf { it.isNotBlank() }?.let { note ->
            Text(note, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        Text(formatDuration(remaining), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { session.adjustRest(-15) }, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_rest_minus))) { Text("-15") }
            TextButton(onClick = session::stopRest, colors = TextButtonDefaults.filledTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.next_exercise_start))) { Text("▶") }
            TextButton(onClick = { session.adjustRest(15) }, colors = TextButtonDefaults.filledTonalTextButtonColors(), modifier = Modifier.describedAs(stringResource(R.string.a11y_rest_plus))) { Text("+15") }
        }
    }
}
