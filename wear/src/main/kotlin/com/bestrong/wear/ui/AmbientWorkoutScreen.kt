package com.bestrong.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Text
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
    val unit = LocalWeightUnit.current
    val w = workout ?: return
    val we = w.exercises.getOrNull(index)
    val set = we?.sets?.firstOrNull { !it.completed }
    Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(w.name, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            we?.let { Text(it.exercise.name, color = Color.White, fontSize = 16.sp, maxLines = 2, textAlign = TextAlign.Center) }
            set?.let {
                Text(
                    "${unit.format(it.weightKg)} ${unit.label} × ${it.reps}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            rest?.let { r ->
                val end = DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(r.endsAtEpochMs).atZone(ZoneId.systemDefault()))
                Text("Descanso hasta $end", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}
