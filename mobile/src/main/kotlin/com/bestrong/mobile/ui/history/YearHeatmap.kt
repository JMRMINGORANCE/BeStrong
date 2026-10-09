package com.bestrong.mobile.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Mapa de calor del año (estilo GitHub): una casilla por día, columnas por semana. */
@Composable
fun YearHeatmap(counts: Map<LocalDate, Int>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val start = LocalDate.of(today.year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = ((today.toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary
    val total = counts.values.sum()
    val description = stringResource(R.string.heatmap_desc, total, today.year)
    Column(modifier) {
        Text(stringResource(R.string.heatmap_title, today.year, total), style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
            val cell = minOf(size.width / weeks, size.height / 7)
            val gap = cell * 0.18f
            for (w in 0 until weeks) {
                for (d in 0 until 7) {
                    val day = start.plusDays((w * 7 + d).toLong())
                    if (day.isAfter(today) || day.year != today.year) continue
                    val n = counts[day] ?: 0
                    drawRoundRect(
                        color = if (n > 0) full.copy(alpha = (0.45f + 0.3f * n).coerceAtMost(1f)) else empty,
                        topLeft = Offset(w * cell, d * cell),
                        size = Size(cell - gap, cell - gap),
                        cornerRadius = CornerRadius(cell * 0.2f),
                    )
                }
            }
        }
    }
}
