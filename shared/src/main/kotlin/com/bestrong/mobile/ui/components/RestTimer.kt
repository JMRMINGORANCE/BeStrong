package com.bestrong.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
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
    /** Lo que viene después («Press banca · 80 kg × 8»), como en el reloj. */
    next: String? = null,
) {
    val now = rememberNow(250)
    // Redondeado hacia arriba (como en el reloj): 1:30 al empezar y 0:01 el último segundo
    val remaining = ((rest.endsAtEpochMs - now + 999) / 1000).coerceAtLeast(0)
    val progress = if (rest.totalSeconds > 0) remaining.toFloat() / rest.totalSeconds else 0f
    val onBrand = MaterialTheme.colorScheme.onPrimary
    // Sobre el degradado de marca, con las esquinas de arriba redondeadas: se ve de lejos que toca descansar
    Box(
        modifier.fillMaxWidth()
            .clip(if (large) MaterialTheme.shapes.large else androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(brandGradient()),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.rest_title, formatDuration(remaining)),
                style = when {
                    giant -> MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp)
                    large -> MaterialTheme.typography.displayMedium
                    else -> MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Black,
                color = onBrand,
            )
            next?.let { Text(stringResource(R.string.rest_next, it), style = MaterialTheme.typography.labelLarge, color = onBrand, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.3f),
                drawStopIndicator = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dark = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                FilledTonalButton(onClick = { onAdjust(-15) }, colors = dark) { Text(stringResource(R.string.rest_minus_15), fontWeight = FontWeight.Bold) }
                FilledTonalButton(onClick = { onAdjust(15) }, colors = dark) { Text(stringResource(R.string.rest_plus_15), fontWeight = FontWeight.Bold) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.rest_skip), color = onBrand, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
