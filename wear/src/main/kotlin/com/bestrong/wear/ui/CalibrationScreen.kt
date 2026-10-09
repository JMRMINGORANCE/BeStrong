package com.bestrong.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession

/** Calibra el contador de repeticiones con unas repeticiones de prueba. */
@Composable
fun CalibrationScreen(session: WorkoutSession, onDone: () -> Unit) {
    val reps by session.repCounter.reps.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        session.startCalibration()
        onDispose { if (result == null) session.finishCalibration() }
    }
    ScreenScaffold(
        edgeButton = {
            EdgeButton(onClick = { if (result == null) result = session.finishCalibration() else onDone() }) {
                Text(stringResource(if (result == null) R.string.calibrate_finish else R.string.done))
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.calibrate), color = Brand, style = MaterialTheme.typography.titleMedium)
                val r = result
                if (r == null) {
                    Text(stringResource(R.string.calibrate_hint), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Text("$reps", fontSize = 36.sp)
                } else {
                    Text(stringResource(R.string.calibrate_done, "%.2f".format(r)), textAlign = TextAlign.Center)
                }
            }
        }
    }
}
