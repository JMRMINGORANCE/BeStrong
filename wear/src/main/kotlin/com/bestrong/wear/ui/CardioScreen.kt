package com.bestrong.wear.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.R
import com.bestrong.wear.session.CardioSession
import com.bestrong.wear.session.CardioType
import kotlinx.coroutines.delay

/** Cardio libre con GPS: elegir tipo, ver distancia/ritmo/pulso y guardar. */
@Composable
fun CardioScreen(cardio: CardioSession, onDone: () -> Unit) {
    val state by cardio.state.collectAsStateWithLifecycle()
    val finished by cardio.finished.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<CardioType?>(null) }
    var noGps by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        noGps = granted[Manifest.permission.ACCESS_FINE_LOCATION] != true
        pending?.let(cardio::start)
        pending = null
    }
    val s = state
    val f = finished
    when {
        f != null -> {
            val set = f.exercises.firstOrNull()?.sets?.firstOrNull()
            val listState = rememberScalingLazyListState()
            ScreenScaffold(scrollState = listState, edgeButton = { EdgeButton(onClick = { cardio.dismissFinished(); onDone() }) { Text(stringResource(R.string.done)) } }) { padding ->
                ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
                    item { ListHeader { Text(f.name, color = Brand) } }
                    item {
                        Text(
                            stringResource(R.string.cardio_saved, "%.2f".format((set?.distanceM ?: 0.0) / 1000), formatDuration(f.durationSeconds())),
                            textAlign = TextAlign.Center,
                        )
                    }
                    f.avgHeartRate?.let { item { Text(stringResource(R.string.summary_hr, it, f.maxHeartRate?.toString() ?: "--"), color = HeartRed) } }
                    f.calories?.let { item { Text(stringResource(R.string.summary_kcal, it)) } }
                }
            }
        }
        s == null -> {
            val listState = rememberScalingLazyListState()
            ScreenScaffold(scrollState = listState) { padding ->
                ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
                    item { ListHeader { Text(stringResource(R.string.cardio)) } }
                    items(CardioType.entries) { type ->
                        Button(
                            onClick = {
                                pending = type
                                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            label = { Text(cardioLabel(type)) },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        else -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
            ScreenScaffold {
                Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(cardioLabel(s.type), color = Brand, style = MaterialTheme.typography.labelMedium)
                        Text(formatDuration((now - s.startedAtEpochMs) / 1000), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.cardio_distance, "%.2f".format(s.distanceM / 1000)), style = MaterialTheme.typography.titleMedium)
                        if (s.type == CardioType.CYCLING) {
                            Text(stringResource(R.string.cardio_speed, "%.1f".format(s.speedMps * 3.6)), style = MaterialTheme.typography.labelMedium)
                        } else {
                            val pace = s.paceSecPerKm(now)
                            Text(stringResource(R.string.cardio_pace, if (pace > 0) formatDuration(pace) else "--"), style = MaterialTheme.typography.labelMedium)
                        }
                        Text("♥ ${s.heartRate ?: "--"}", color = HeartRed, style = MaterialTheme.typography.labelMedium)
                        if (noGps) Text(stringResource(R.string.cardio_no_gps), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
                EdgeButton(onClick = cardio::finish, modifier = Modifier.align(Alignment.BottomCenter)) { Text(stringResource(R.string.finish)) }
            }
        }
    }
}

@Composable
fun cardioLabel(type: CardioType): String = stringResource(
    when (type) {
        CardioType.RUNNING -> R.string.cardio_running
        CardioType.WALKING -> R.string.cardio_walking
        CardioType.CYCLING -> R.string.cardio_cycling
    },
)
