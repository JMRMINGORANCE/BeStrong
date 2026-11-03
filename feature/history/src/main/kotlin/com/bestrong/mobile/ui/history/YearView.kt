package com.bestrong.mobile.ui.history

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.stats.YearSummary
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** El año que se enseña en Historial: entrenos por día (mapa de calor) y su resumen (null sin entrenos acabados). */
data class YearView(val year: Int = LocalDate.now().year, val counts: Map<LocalDate, Int> = emptyMap(), val summary: YearSummary? = null)

/**
 * «Tu año»: el mapa de calor y el resumen (entrenos, días, volumen, récords, mejor racha, músculo estrella
 * y mayores subidas) en un solo bloque, que se puede compartir como texto.
 */
@Composable
fun YearSection(view: YearView) {
    val title = stringResource(R.string.year_title, view.year)
    val lines = view.summary?.let { yearLines(it) }.orEmpty()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(title)
        YearHeatmap(view.counts, view.year)
        lines.forEachIndexed { i, line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
        }
        if (lines.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, (listOf(title) + lines).joinToString("\n"))
                    context.startActivity(Intent.createChooser(send, null))
                },
            ) {
                Icon(Icons.Default.Share, null)
                Text(stringResource(R.string.share_as_text), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun yearLines(y: YearSummary): List<String> {
    val unit = LocalWeightUnit.current
    // Horas redondeadas; por debajo de una hora, en minutos
    val time = if (y.period.durationSec >= SECONDS_PER_HOUR) "${Math.round(y.period.durationSec / SECONDS_PER_HOUR.toDouble())} h" else "${y.period.durationSec / 60} min"
    val first = pluralStringResource(R.plurals.year_workouts_n, y.period.workouts, y.period.workouts) + " · " +
        pluralStringResource(R.plurals.year_days, y.trainingDays, y.trainingDays) + " · " + time
    val records = pluralStringResource(R.plurals.year_records_n, y.period.records.size, y.period.records.size) + " · " +
        pluralStringResource(R.plurals.year_streak, y.longestWeeklyStreak, y.longestWeeklyStreak)
    val gains = y.biggestGains.map { (ex, from, to) ->
        val f = com.bestrong.mobile.ui.components.pairFactor(ex)
        stringResource(R.string.year_gain, ex.displayName(), unit.format(from * f), unit.format(to * f, withUnit = true))
    }
    return listOfNotNull(
        first,
        stringResource(R.string.year_volume, unit.formatVolume(y.period.volume)),
        records,
        y.topMuscle?.let { stringResource(R.string.year_top_muscle, it.label()) },
    ) + gains
}

private const val SECONDS_PER_HOUR = 3600L

/** Mapa de calor del año (estilo GitHub): una casilla por día, columnas por semana; el año en curso, hasta hoy. */
@Composable
private fun YearHeatmap(counts: Map<LocalDate, Int>, year: Int) {
    val today = LocalDate.now()
    val last = if (year == today.year) today else LocalDate.of(year, 12, 31)
    val start = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = ((last.toEpochDay() - start.toEpochDay()) / 7 + 1).toInt()
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.heatmap_desc, counts.values.sum(), year)
    // Alto según el ancho: casillas cuadradas, sin hueco debajo (con tope en pantallas anchas)
    Canvas(Modifier.fillMaxWidth().aspectRatio(weeks / 7f).heightIn(max = 140.dp).semantics { contentDescription = description }) {
        val cell = minOf(size.width / weeks, size.height / 7)
        val gap = cell * 0.18f
        for (w in 0 until weeks) {
            for (d in 0 until 7) {
                val day = start.plusDays((w * 7 + d).toLong())
                if (day.isAfter(last) || day.year != year) continue
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
