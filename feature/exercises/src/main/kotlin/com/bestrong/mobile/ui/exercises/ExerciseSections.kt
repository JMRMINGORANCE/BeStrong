package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Exercise
import com.bestrong.core.stats.ExerciseProgressPoint
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.theme.FigureFont
import com.bestrong.mobile.ui.theme.KickerStyle

/** Secciones de la ficha del ejercicio (Historia es la 0). */
internal const val SECTION_TECHNIQUE = 1
internal const val SECTION_RECORDS = 2

/** «PECHO · BARRA» encima y los segmentos Historia · Técnica · Récords. */
@Composable
internal fun ExerciseHeaderSegments(exercise: Exercise, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${exercise.primaryMuscle.label()} · ${exercise.equipment.label()}".uppercase(),
            style = KickerStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        com.bestrong.mobile.ui.components.EnfoqueSegments(
            listOf(stringResource(R.string.enf_lib_history), stringResource(R.string.enf_lib_technique), stringResource(R.string.enf_lib_records)),
            selected,
            onSelect,
        )
    }
}

/** Días que se miran para «+6 kg en 3 meses». */
private const val TREND_DAYS = 90L

/** El 1RM estimado de la última sesión, en grande, y cuánto ha cambiado en los últimos tres meses. */
@Composable
internal fun EstimatedOneRepMax(progress: List<ExerciseProgressPoint>, factor: Int) {
    val unit = LocalWeightUnit.current
    val withRm = progress.filter { it.bestOneRepMax > 0 }
    val last = withRm.lastOrNull() ?: return
    val since = last.dateEpochMs - TREND_DAYS * 86_400_000L
    val first = withRm.firstOrNull { it.dateEpochMs >= since }
    val change = first?.takeIf { it !== last }?.let { (last.bestOneRepMax - it.bestOneRepMax) * factor }
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.enf_lib_e1rm).uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(unit.format(last.bestOneRepMax * factor), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, fontFamily = FigureFont)
            Text(unit.label, style = MaterialTheme.typography.titleMedium, fontFamily = FigureFont, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            change?.takeIf { kotlin.math.abs(it) >= 0.1 }?.let { c ->
                val up = c > 0
                Text(
                    (if (up) "▲ +" else "▼ ") + unit.format(c, withUnit = true),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FigureFont,
                    color = if (up) com.bestrong.mobile.ui.theme.enfoque.done else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}
