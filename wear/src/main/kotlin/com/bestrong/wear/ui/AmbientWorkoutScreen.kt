package com.bestrong.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla siempre activa (ambient): fondo negro, texto gris/blanco sin rellenos, sin animaciones.
 * Se actualiza cuando el sistema lo pide (≈ 1 vez por minuto), por eso el descanso muestra la hora de fin.
 */
@Composable
fun AmbientWorkoutScreen(session: WorkoutSession) {
    val workout by session.workout.collectAsStateWithLifecycle()
    val index by session.exerciseIndex.collectAsStateWithLifecycle()
    val rest by session.rest.collectAsStateWithLifecycle()
    val metrics by session.metrics.collectAsStateWithLifecycle()
    val lowPower by session.lowPower.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val w = workout ?: return
    val we = w.exercises.getOrNull(index)
    val set = we?.sets?.firstOrNull { !it.completed }
    val tracking = we?.exercise?.tracking ?: com.bestrong.core.model.TrackingType.WEIGHT_REPS
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp).burnInShift(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(w.name, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            we?.let { Text(it.exercise.displayName(), color = Color.White, fontSize = 16.sp, maxLines = 2, textAlign = TextAlign.Center) }
            set?.let {
                Text(
                    // Como en la pantalla normal: segundos, minutos o metros según el ejercicio (no «0 kg × 0»)
                    if (tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) {
                        "${unit.format(it.weightKg)} ${unit.label} × ${it.reps}"
                    } else {
                        setText(it, tracking, unit, stringResource(R.string.unit_reps))
                    },
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            // AOD más completo: pulso y series hechas (en bajo consumo, solo lo esencial)
            if (!lowPower) {
                Text(
                    "♥ ${metrics.heartRate ?: "--"} · ${w.completedSetCount}/${w.totalSetCount}",
                    color = Color.Gray,
                    fontSize = 13.sp,
                )
            }
            rest?.let { r ->
                // En el formato del reloj (12/24 h) y sin segundos: en AOD se redibuja una vez por minuto
                val context = androidx.compose.ui.platform.LocalContext.current
                val pattern = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), if (android.text.format.DateFormat.is24HourFormat(context)) "Hm" else "hm")
                val end = DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(r.endsAtEpochMs).atZone(ZoneId.systemDefault()))
                Text(stringResource(R.string.rest_until, end), color = Color.Gray, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/**
 * AOD fuera de un entreno (resumen, inicio, editor…): negro con el nombre en gris y desplazado, en vez de la interfaz a
 * todo color (botones naranjas encendidos minutos en una pantalla OLED).
 */
@Composable
fun AmbientIdleScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp).burnInShift(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.app_name), color = Color.Gray, fontSize = 16.sp)
    }
}

/** AOD de la sesión de cardio: tiempo de inicio, distancia y pulso. */
@Composable
fun AmbientCardioScreen(cardio: com.bestrong.wear.session.CardioSession) {
    val state by cardio.state.collectAsStateWithLifecycle()
    val s = state ?: return
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp).burnInShift(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cardioLabel(s.type), color = Color.Gray, fontSize = 13.sp)
            Text("%.2f km".format(s.distanceM / 1000), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Text("♥ ${s.heartRate ?: "--"}", color = Color.Gray, fontSize = 13.sp)
        }
    }
}

/**
 * Protección contra quemado de pantallas OLED: si el reloj la pide, el contenido se desplaza unos píxeles en
 * cada actualización del modo siempre activo (≈ 1 vez por minuto) para no dejar marcado el texto fijo.
 */
object AmbientBurnIn {
    val tick = kotlinx.coroutines.flow.MutableStateFlow(0)

    @Volatile var required = false

    fun onUpdate() {
        if (required) tick.value = (tick.value + 1) % SHIFT_POSITIONS
    }
}

private const val SHIFT_POSITIONS = 9
private const val SHIFT_DP = 4

@Composable
private fun Modifier.burnInShift(): Modifier {
    val t by AmbientBurnIn.tick.collectAsStateWithLifecycle()
    if (!AmbientBurnIn.required) return this
    return offset(((t % 3) - 1).times(SHIFT_DP).dp, ((t / 3) - 1).times(SHIFT_DP).dp)
}
