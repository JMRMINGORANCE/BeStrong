package com.bestrong.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bestrong.core.util.formatDuration
import com.bestrong.wear.R
import com.bestrong.wear.session.Summary

/**
 * «Resumen en la muñeca»: tres anillos concéntricos —series hechas sobre las previstas y volumen frente a la última
 * vez, en verde (hecho), y los récords en ámbar (uno por ejercicio llena el anillo)— con la duración en el centro.
 */
@Composable
fun SummaryRings(s: Summary) {
    val w = s.workout
    val unit = LocalWeightUnit.current
    val sets = if (w.totalSetCount > 0) w.completedSetCount.toFloat() / w.totalSetCount else 1f
    // Volumen de los ejercicios repetidos frente al de la última vez (sin comparación, el anillo lleno)
    val delta = s.comparisons.sumOf { it.volumeDelta }
    val compared = w.exercises.filter { we -> s.comparisons.any { it.exerciseId == we.exercise.id } }
        .sumOf { we -> we.completedSets.sumOf { it.weightKg * it.reps } }
    val before = compared - delta
    val volume = if (before > 0) (compared / before).toFloat() else 1f
    val exercises = w.exercises.count { it.completedSets.isNotEmpty() }.coerceAtLeast(1)
    val records = (s.records.map { it.exerciseId }.distinct().size.toFloat() / exercises).coerceAtMost(1f)
    val colors = listOf(Done, Done.copy(alpha = 0.6f), Record)
    val description = stringResource(R.string.summary_rings_desc, w.completedSetCount, w.totalSetCount, unit.formatVolume(w.totalVolume), formatDuration(w.durationSeconds()))
    val track = MaterialTheme.colorScheme.surfaceContainer
    val extra = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Box(Modifier.size(120.dp).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            listOf(sets, volume, records).forEachIndexed { i, v ->
                val stroke = 9.dp.toPx()
                val inset = stroke / 2 + i * (stroke + 3.dp.toPx())
                val box = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(track, -90f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
                drawArc(colors[i], -90f, 360f * v.coerceIn(0f, 1f), false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
                // Más que la última vez: un trazo fino encima con lo que se ha superado
                if (i == 1 && v > 1f) drawArc(extra, -90f, 360f * (v - 1f).coerceAtMost(1f), false, Offset(inset, inset), box, style = Stroke(stroke / 3, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatDuration(w.durationSeconds()), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${w.completedSetCount}/${w.totalSetCount}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
