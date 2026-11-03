package com.bestrong.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bestrong.core.sensors.RepCalibration
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession
import kotlinx.coroutines.delay

/** Pasos de la calibración guiada. */
private enum class CalibrationStep { READY, COUNTING, RESULT }

/**
 * Calibración guiada del contador de repeticiones:
 * 1. «Coge la barra»: cuenta atrás de 3 s (lo de colocarse no cuenta).
 * 2. «Haz 5 repeticiones normales»: se ven en directo; al llegar a 5 termina sola.
 * 3. Resultado: la sensibilidad que queda o, si no se vieron bien, «Repetir» (el umbral de antes se mantiene).
 */
@Composable
fun CalibrationScreen(session: WorkoutSession, onDone: () -> Unit) {
    val reps by session.repCounter.reps.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(CalibrationStep.READY) }
    var countdown by remember { mutableIntStateOf(READY_SECONDS) }
    var result by remember { mutableStateOf<RepCalibration?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    fun finish() {
        if (step == CalibrationStep.COUNTING) {
            result = session.finishCalibration()
            step = CalibrationStep.RESULT
        }
    }
    // Cada intento: cuenta atrás y empezar a escuchar
    LaunchedEffect(attempt) {
        step = CalibrationStep.READY
        result = null
        for (s in READY_SECONDS downTo 1) {
            countdown = s
            delay(1_000)
        }
        session.startCalibration()
        step = CalibrationStep.COUNTING
    }
    // Con las 5 reps hechas termina sola (un respiro para la última)
    LaunchedEffect(reps, step) {
        if (step == CalibrationStep.COUNTING && reps >= TARGET_REPS) {
            delay(1_200)
            finish()
        }
    }
    // Salir a medias no deja el contador en modo calibración (ni cambia el umbral)
    DisposableEffect(Unit) { onDispose { if (step == CalibrationStep.COUNTING) session.finishCalibration() } }

    ScreenScaffold {
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.calibrate), color = Brand, style = MaterialTheme.typography.titleMedium)
                CalibrationContent(step, countdown, reps, result)
            }
        }
        val ok = result?.ok == true
        EdgeButton(
            onClick = {
                when (step) {
                    CalibrationStep.READY -> Unit
                    CalibrationStep.COUNTING -> finish()
                    CalibrationStep.RESULT -> if (ok) onDone() else attempt++
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(stringResource(edgeLabel(step, ok)))
        }
    }
}

/** Lo que se ve en cada paso: la cuenta atrás, las reps en directo o el resultado. */
@Composable
private fun CalibrationContent(step: CalibrationStep, countdown: Int, reps: Int, result: RepCalibration?) {
    when (step) {
        CalibrationStep.READY -> {
            Text(stringResource(R.string.calibrate_ready), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            Text("$countdown", fontSize = 40.sp)
        }
        CalibrationStep.COUNTING -> {
            Text(stringResource(R.string.calibrate_go, TARGET_REPS), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            Text("$reps", fontSize = 40.sp)
            RepDots(reps)
        }
        CalibrationStep.RESULT -> {
            val r = result
            if (r != null && r.ok) {
                Text(
                    stringResource(R.string.calibrate_ok, stringResource(sensitivityLabel(r.threshold)), r.repsSeen),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(stringResource(R.string.calibrate_retry_hint), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Texto del botón de abajo: terminar, repetir si no salió bien o hecho. */
private fun edgeLabel(step: CalibrationStep, ok: Boolean): Int = when {
    step != CalibrationStep.RESULT -> R.string.calibrate_finish
    ok -> R.string.done
    else -> R.string.calibrate_retry
}

/** Cinco puntos que se rellenan con cada repetición vista. */
@Composable
private fun RepDots(reps: Int) {
    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(TARGET_REPS) { i ->
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (i < reps) Brand else MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

/** Sensibilidad que entiende cualquiera en vez del umbral en m/s². */
fun sensitivityLabel(threshold: Float): Int = when {
    threshold < SENSITIVE_BELOW -> R.string.sensitivity_high
    threshold < NORMAL_BELOW -> R.string.sensitivity_medium
    else -> R.string.sensitivity_low
}

private const val READY_SECONDS = 3
private const val TARGET_REPS = 5
private const val SENSITIVE_BELOW = 0.9f
private const val NORMAL_BELOW = 1.6f
