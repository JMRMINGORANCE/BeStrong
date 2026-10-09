package com.bestrong.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.R
import kotlinx.coroutines.delay

/** Reloj que avanza cada segundo mientras está en composición. */
@Composable
fun rememberNow(intervalMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}

data class RestState(val endsAtEpochMs: Long, val totalSeconds: Int)

@Composable
fun RestTimerBar(
    rest: RestState,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    /** Modo mesa del Fold: cuenta atrás gigante para verla desde el banco. */
    giant: Boolean = false,
) {
    val now = rememberNow(250)
    val remaining = ((rest.endsAtEpochMs - now) / 1000).coerceAtLeast(0)
    val progress = if (rest.totalSeconds > 0) remaining.toFloat() / rest.totalSeconds else 0f
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.rest_title, formatDuration(remaining)),
                style = when {
                    giant -> MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp)
                    large -> MaterialTheme.typography.displayMedium
                    else -> MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onAdjust(-15) }) { Text(stringResource(R.string.rest_minus_15)) }
                FilledTonalButton(onClick = { onAdjust(15) }) { Text(stringResource(R.string.rest_plus_15)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.rest_skip)) }
            }
        }
    }
}
