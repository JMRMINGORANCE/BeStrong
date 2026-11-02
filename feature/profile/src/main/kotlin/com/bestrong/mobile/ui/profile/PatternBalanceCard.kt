package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.stats.MovementPattern
import com.bestrong.core.stats.PatternBalance
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.theme.statusColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
private fun MovementPattern.label(): String = stringResource(
    when (this) {
        MovementPattern.PUSH_HORIZONTAL -> R.string.pt_push_h
        MovementPattern.PUSH_VERTICAL -> R.string.pt_push_v
        MovementPattern.PULL_HORIZONTAL -> R.string.pt_pull_h
        MovementPattern.PULL_VERTICAL -> R.string.pt_pull_v
        MovementPattern.SQUAT -> R.string.pt_squat
        MovementPattern.HINGE -> R.string.pt_hinge
        MovementPattern.CORE -> R.string.pt_core
    },
)

@Composable
private fun MovementPattern.short(): String = stringResource(
    when (this) {
        MovementPattern.PUSH_HORIZONTAL -> R.string.pt_push_h_short
        MovementPattern.PUSH_VERTICAL -> R.string.pt_push_v_short
        MovementPattern.PULL_HORIZONTAL -> R.string.pt_pull_h_short
        MovementPattern.PULL_VERTICAL -> R.string.pt_pull_v_short
        MovementPattern.SQUAT -> R.string.pt_squat
        MovementPattern.HINGE -> R.string.pt_hinge_short
        MovementPattern.CORE -> R.string.pt_core
    },
)

/**
 * Equilibrio de patrones (propuesta 05): radar con las series de trabajo de cada patrón de movimiento en 4 semanas,
 * las proporciones empuje:tirón y bisagra:sentadilla y ejercicios para lo que va corto.
 */
@Composable
fun PatternBalanceCard(stats: AdvancedStats) {
    val b = stats.patterns
    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        SectionHeader(stringResource(R.string.pt_title))
        if (b.total < PatternBalance.MIN_TOTAL_SETS) {
            Text(stringResource(R.string.pt_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Panel
        }
        Text(stringResource(R.string.pt_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val description = MovementPattern.entries.map { it.label() + " " + formatWeight(b.sets[it] ?: 0.0) }.joinToString(", ")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Radar(b, Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1.15f).semantics { contentDescription = description })
        }
        RatioLine(stringResource(R.string.pt_push_pull), b.pushPullRatio, okRange = 0.67..1.5)
        RatioLine(stringResource(R.string.pt_hinge_squat), b.hingeSquatRatio, okRange = 0.5..1.5)
        if (stats.patternSuggestions.isNotEmpty()) {
            Text(stringResource(R.string.pt_suggest_title), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            stats.patternSuggestions.forEach { (p, exercises) ->
                Column {
                    Text(p.label() + " · " + stringResource(R.string.n_sets_value, formatWeight(b.sets[p] ?: 0.0)), style = MaterialTheme.typography.bodyMedium, color = statusColors.warn)
                    if (exercises.isNotEmpty()) {
                        Text(stringResource(R.string.pt_try, exercises.map { it.displayName() }.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** «Empuje : tirón  1,2 : 1» con su estado (bien / desequilibrado). */
@Composable
private fun RatioLine(label: String, ratio: Double?, okRange: ClosedFloatingPointRange<Double>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        val ok = ratio != null && ratio in okRange
        Text(
            if (ratio == null) "—" else (if (ok) statusColors.goodMark else statusColors.warnMark) + "%.1f : 1".format(ratio),
            fontWeight = FontWeight.Bold,
            color = when {
                ratio == null -> MaterialTheme.colorScheme.onSurfaceVariant
                ok -> statusColors.good
                else -> statusColors.warn
            },
        )
    }
}

/** Radar de 7 ejes con anillos de referencia y el polígono de tus series. */
@Composable
private fun Radar(b: PatternBalance, modifier: Modifier) {
    val patterns = MovementPattern.entries
    val labels = patterns.map { it.short() }
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val lag = statusColors.warn
    val lagging = b.lagging().toSet()
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (b.sets.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2 * 0.68f
        fun point(i: Int, f: Float): Offset {
            val a = -Math.PI / 2 + 2 * Math.PI * i / patterns.size
            return Offset(c.x + (r * f * cos(a)).toFloat(), c.y + (r * f * sin(a)).toFloat())
        }
        listOf(0.33f, 0.66f, 1f).forEach { f ->
            val ring = Path().apply { patterns.indices.forEach { i -> point(i, f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }; close() }
            drawPath(ring, grid, style = Stroke(1.dp.toPx()))
        }
        patterns.indices.forEach { i -> drawLine(grid, c, point(i, 1f), 1.dp.toPx()) }
        val shape = Path().apply {
            patterns.forEachIndexed { i, p ->
                val f = ((b.sets[p] ?: 0.0) / max).toFloat().coerceIn(0.04f, 1f)
                point(i, f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
            }
            close()
        }
        drawPath(shape, fill.copy(alpha = 0.25f))
        drawPath(shape, fill, style = Stroke(2.dp.toPx()))
        patterns.forEachIndexed { i, p ->
            val f = ((b.sets[p] ?: 0.0) / max).toFloat().coerceIn(0.04f, 1f)
            drawCircle(if (p in lagging) lag else fill, 4.dp.toPx(), point(i, f))
            val layout = measurer.measure(labels[i], TextStyle(fontSize = 11.sp, color = if (p in lagging) lag else text, fontWeight = FontWeight.SemiBold))
            val at = point(i, 1.22f)
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }
    }
}
