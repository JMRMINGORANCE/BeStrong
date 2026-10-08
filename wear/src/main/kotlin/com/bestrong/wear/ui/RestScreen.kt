package com.bestrong.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight
import com.bestrong.wear.session.WorkoutSession
import kotlinx.coroutines.delay

/** Descanso a pantalla completa con anillo de progreso por el borde de la esfera. */
@Composable
fun RestScreen(session: WorkoutSession) {
    val rest by session.rest.collectAsStateWithLifecycle()
    val metrics by session.metrics.collectAsStateWithLifecycle()
    val workout by session.workout.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(200) } }

    val r = rest ?: return
    val remaining = ((r.endsAtEpochMs - now) / 1000).coerceAtLeast(0)
    val progress = (remaining.toFloat() / r.totalSeconds).coerceIn(0f, 1f)
    val next = workout?.let { w -> com.bestrong.core.session.WorkoutEditor.nextPendingSet(w)?.let { (i, s) -> w.exercises[i] to s } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            indicatorColor = Brand,
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Descanso", style = MaterialTheme.typography.caption1)
            Text(formatDuration(remaining), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("♥ ${metrics.heartRate ?: "--"}", color = HeartRed, style = MaterialTheme.typography.caption2)
            next?.let { (we, s) ->
                Text(
                    "Sig.: ${we.exercise.name} ${formatWeight(s.weightKg)}×${s.reps}",
                    style = MaterialTheme.typography.caption3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactButton(onClick = { session.adjustRest(-15) }, colors = ButtonDefaults.secondaryButtonColors()) { Text("-15") }
                CompactButton(onClick = session::stopRest) { Text("⏭") }
                CompactButton(onClick = { session.adjustRest(15) }, colors = ButtonDefaults.secondaryButtonColors()) { Text("+15") }
            }
        }
    }
}
