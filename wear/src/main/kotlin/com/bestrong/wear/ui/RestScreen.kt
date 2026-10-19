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
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.util.formatDuration
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
    val now = rememberVisibleNow(200)

    val unit = LocalWeightUnit.current
    val r = rest ?: return
    // Redondeado hacia arriba: 1:00 al empezar y 0:01 el último segundo (no 0:00 antes de acabar)
    val remaining = ((r.endsAtEpochMs - now + 999) / 1000).coerceAtLeast(0)
    val progress = (remaining.toFloat() / r.totalSeconds).coerceIn(0f, 1f)
    val next = workout?.let { w -> WorkoutEditor.nextPendingSet(w)?.let { (i, s) -> w.exercises[i] to s } }

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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.rest), style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(remaining), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                val zone = session.heartRateZone(metrics.heartRate)
                Text("♥ ${metrics.heartRate ?: "--"}" + if (zone > 0) " · Z$zone" else "", color = HeartRed, style = MaterialTheme.typography.labelSmall)
                // Una sola línea de aviso (el récord manda): con las dos, los botones se iban contra el anillo
                if (recovered && !record) Text(stringResource(R.string.recovered), color = Done, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                if (record) Text(stringResource(R.string.new_record_live), color = Done, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                next?.let { (we, s) ->
                    Text(
                        stringResource(R.string.next_any, we.exercise.displayName(), setText(s, we.exercise.tracking, unit)),
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
