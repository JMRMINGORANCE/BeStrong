package com.bestrong.mobile.ui.workout

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import kotlinx.coroutines.delay

/**
 * Cuenta atrás de una serie por tiempo (plancha, isométricos…): 3 s para colocarse, la serie
 * y, al acabar, vibración y la serie se marca hecha (lo que arranca el descanso).
 * Se calcula con el reloj del sistema desde el arranque, así no se desvía aunque la pantalla parpadee.
 */
/** [onDone] recibe los segundos aguantados: el objetivo si se llegó al final, lo hecho si se para antes. */
@Composable
fun TimedSetDialog(seconds: Int, onDone: (heldSeconds: Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val total = seconds.coerceAtLeast(1) * 1000L
    // Momento de inicio de la serie (tras la preparación); sobrevive a girar o plegar el móvil
    val startAt by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime() + PREPARE_MS) }
    var now by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // La serie puede cambiar mientras corre (p. ej. marcada desde el reloj): se usa siempre la última acción
    val finish by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        var started = SystemClock.elapsedRealtime() >= startAt
        var lastSecond = Long.MAX_VALUE
        while (true) {
            now = SystemClock.elapsedRealtime()
            val left = startAt + total - now
            if (left <= 0) {
                buzz(context, long = true)
                finish(seconds)
                break
            }
            // Aviso corto al empezar la serie y en cada uno de los 3 últimos segundos
            if (!started && now >= startAt) {
                started = true
                buzz(context, long = false)
            }
            val secondsLeft = (left + 999) / 1000
            if (started && secondsLeft < lastSecond && secondsLeft <= 3) buzz(context, long = false)
            lastSecond = secondsLeft
            delay(100)
        }
    }
    val preparing = now < startAt
    val leftMs = if (preparing) startAt - now else startAt + total - now
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(if (preparing) R.string.timed_set_ready else R.string.timed_set_go)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    formatDuration(((leftMs + 999) / 1000).coerceAtLeast(0)),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (preparing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
                if (!preparing) LinearProgressIndicator(progress = { ((now - startAt).toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onDone(((SystemClock.elapsedRealtime() - startAt) / 1000).toInt().coerceIn(0, seconds)) }) {
                Text(stringResource(R.string.timed_set_finish_now))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val PREPARE_MS = 3_000L

private fun buzz(context: Context, long: Boolean) {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    } ?: return
    v.vibrate(VibrationEffect.createOneShot(if (long) 600 else 80, VibrationEffect.DEFAULT_AMPLITUDE))
}
