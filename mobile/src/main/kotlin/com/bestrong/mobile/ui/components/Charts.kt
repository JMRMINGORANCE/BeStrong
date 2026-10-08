package com.bestrong.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class ChartPoint(val label: String, val value: Double)

/** Gráfica de línea simple (progreso de un ejercicio, peso corporal...). */
@Composable
fun LineChart(points: List<ChartPoint>, modifier: Modifier = Modifier, height: Dp = 180.dp, valueFormatter: (Double) -> String = { "%.0f".format(it) }) {
    if (points.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = points.maxOf { it.value }
    val min = points.minOf { it.value }
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text("Máx ${valueFormatter(max)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Mín ${valueFormatter(min)}", style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
            fun y(v: Double) = (size.height - ((v - min) / range * size.height * 0.85) - size.height * 0.075).toFloat()
            listOf(0.075f, 0.5f, 0.925f).forEach { f ->
                drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = if (points.size == 1) size.width / 2 else i * stepX
                if (i == 0) path.moveTo(x, y(p.value)) else path.lineTo(x, y(p.value))
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { i, p ->
                val x = if (points.size == 1) size.width / 2 else i * stepX
                drawCircle(color, 4.dp.toPx(), Offset(x, y(p.value)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (points.size > 1) Text(points.last().label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Gráfica de barras (volumen semanal, entrenamientos por semana...). */
@Composable
fun BarChart(points: List<ChartPoint>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    if (points.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val max = points.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / points.size
            val barWidth = slot * 0.6f
            points.forEachIndexed { i, p ->
                val h = (p.value / max * size.height).toFloat().coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = if (p.value > 0) color else empty,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEachIndexed { i, p ->
                // Solo algunas etiquetas para que no se amontonen en el Fold plegado
                val show = points.size <= 6 || i % (points.size / 6 + 1) == 0 || i == points.lastIndex
                Text(
                    if (show) p.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
