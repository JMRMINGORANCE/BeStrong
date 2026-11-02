package com.bestrong.mobile.ui.planning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.training.CyclePhase
import com.bestrong.core.training.CycleTimeline
import com.bestrong.core.training.CycleWeek
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.theme.statusColors
import java.time.ZoneId

/** Color de cada bloque, del tema: acumulación (secundario), intensificación (acento) y descarga (terciario). */
@Composable
private fun CyclePhase.color(): Color = when (this) {
    CyclePhase.ACCUMULATION -> MaterialTheme.colorScheme.secondary
    CyclePhase.INTENSIFICATION -> MaterialTheme.colorScheme.primary
    CyclePhase.DELOAD -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun CyclePhase.label(): String = stringResource(
    when (this) {
        CyclePhase.ACCUMULATION -> R.string.cycle_phase_accumulation
        CyclePhase.INTENSIFICATION -> R.string.cycle_phase_intensification
        CyclePhase.DELOAD -> R.string.cycle_phase_deload
    },
)

private fun CycleWeek.mondayMs(): Long = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Periodización visual: el ciclo en curso como línea temporal por bloques con la semana actual marcada, la carga
 * prevista frente a la hecha por semana y cuándo toca la próxima descarga. Con sitio (Fold desplegado), además la
 * rejilla de % del training max por ejercicio y semana.
 */
@Composable
fun CycleTimelineCard(t: CycleTimeline, programName: String?, exerciseName: (String) -> String) {
    val current = t.current ?: t.weeks.first()
    var selectedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = t.weeks.firstOrNull { it.index == selectedIndex } ?: current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        // Cabecera: «Programa · ciclo 3» y el nombre
        Text(
            stringResource(if (t.kind == CycleTimeline.Kind.PROGRAM) R.string.cycle_overline_program else R.string.cycle_overline_meso, t.cycle).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(programName ?: stringResource(R.string.cycle_meso_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        WeekStrip(t, current, selected) { selectedIndex = it }
        PhaseLegend(t)
        LoadChart(t, current, selected)
        DeloadNotice(t)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= WIDE && t.intensity.isNotEmpty()) IntensityGrid(t, current, exerciseName)
        }
    }
}

/** Una caja por semana con el color de su bloque; la actual con borde y «HOY» encima. Tocar una la elige. */
@Composable
private fun WeekStrip(t: CycleTimeline, current: CycleWeek, selected: CycleWeek, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        t.weeks.forEach { w ->
            val future = w.index > current.index
            val desc = stringResource(R.string.cycle_week_desc, w.index, w.phase.label(), w.doneSets, w.plannedSets)
            Column(
                Modifier.weight(1f)
                    .selectable(selected = w.index == selected.index, role = Role.Tab, onClick = { onPick(w.index) })
                    .clearAndSetSemantics { contentDescription = desc + if (w.current) ", " else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (w.current) stringResource(R.string.cycle_today).uppercase() else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                val color = w.phase.color()
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(MaterialTheme.shapes.small)
                        .background(if (future) color.copy(alpha = 0.22f) else color)
                        .then(
                            when {
                                w.current -> Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                                w.index == selected.index -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        stringResource(R.string.cycle_week_short, w.index),
                        Modifier.padding(6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (future) MaterialTheme.colorScheme.onSurfaceVariant else contentOn(w.phase),
                    )
                }
            }
        }
    }
}

@Composable
private fun contentOn(phase: CyclePhase): Color = when (phase) {
    CyclePhase.ACCUMULATION -> MaterialTheme.colorScheme.onSecondary
    CyclePhase.INTENSIFICATION -> MaterialTheme.colorScheme.onPrimary
    CyclePhase.DELOAD -> MaterialTheme.colorScheme.onTertiary
}

/** Los bloques que hay en el ciclo, con su color. */
@Composable
private fun PhaseLegend(t: CycleTimeline) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        t.weeks.map { it.phase }.distinct().forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(p.color()))
                Text(p.label(), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Series previstas (contorno discontinuo) frente a hechas (relleno) por semana. */
@Composable
private fun LoadChart(t: CycleTimeline, current: CycleWeek, selected: CycleWeek) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cycle_week_of, selected.index, t.weeks.size).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.cycle_load_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.cycle_sets_of, selected.doneSets, selected.plannedSets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // Solo semanas cerradas: la actual va a medias
                if (selected.plannedSets > 0 && selected.index < current.index) {
                    val pct = ((selected.doneSets - selected.plannedSets) * 100.0 / selected.plannedSets).toInt()
                    Text(
                        (if (pct >= 0) statusColors.goodMark.ifEmpty { "▲ " } + "+" else statusColors.badMark.ifEmpty { "▼ " } + "−") +
                            stringResource(R.string.cycle_vs_plan, kotlin.math.abs(pct)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pct >= 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        val colors = t.weeks.map { it.phase.color() }
        val parts = mutableListOf<String>()
        for (w in t.weeks) parts += stringResource(R.string.cycle_week_desc, w.index, w.phase.label(), w.doneSets, w.plannedSets)
        val desc = parts.joinToString()
        Canvas(Modifier.fillMaxWidth().height(110.dp).clearAndSetSemantics { contentDescription = desc }) {
            val max = t.weeks.maxOf { maxOf(it.plannedSets, it.doneSets) }.coerceAtLeast(1).toFloat()
            val slot = size.width / t.weeks.size
            val barW = slot * 0.5f
            t.weeks.forEachIndexed { i, w ->
                val x = i * slot + (slot - barW) / 2
                val hp = size.height * w.plannedSets / max
                val hd = size.height * w.doneSets / max
                if (hd > 0f) {
                    drawRoundRect(colors[i].copy(alpha = if (w.current) 1f else 0.7f), Offset(x, size.height - hd), Size(barW, hd), CornerRadius(4.dp.toPx()))
                }
                if (hp > 0f) {
                    drawRoundRect(
                        outline,
                        Offset(x, size.height - hp),
                        Size(barW, hp),
                        CornerRadius(4.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            t.weeks.forEach { w ->
                Text(
                    stringResource(R.string.cycle_week_short, w.index),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (w.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (t.weeks.all { it.plannedSets == 0 && it.doneSets == 0 }) {
            Text(stringResource(R.string.cycle_no_plan), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).border(1.dp, outline, MaterialTheme.shapes.extraSmall))
                Text(stringResource(R.string.cycle_planned), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primary))
                Text(stringResource(R.string.cycle_done), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** «Descarga en 2 semanas · S4 · desde el 23 nov» o «Esta semana toca descarga». */
@Composable
private fun DeloadNotice(t: CycleTimeline) {
    val deload = t.nextDeload ?: return
    val weeks = t.weeksToDeload ?: return
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                if (weeks == 0) stringResource(R.string.cycle_deload_now) else pluralStringResource(R.plurals.cycle_deload_in, weeks, weeks),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                stringResource(R.string.cycle_deload_detail, deload.index, formatShortDate(deload.mondayMs())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** Rejilla de % del training max por ejercicio y semana (solo con sitio: Fold desplegado). */
@Composable
private fun IntensityGrid(t: CycleTimeline, current: CycleWeek, exerciseName: (String) -> String) {
    val maxPct = t.intensity.flatMap { it.percents }.filterNotNull().maxOrNull() ?: 1.0
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.cycle_intensity_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Box(Modifier.width(NAME_COL))
                    t.weeks.forEach { w ->
                        Text(
                            stringResource(R.string.cycle_week_short, w.index),
                            Modifier.width(CELL).padding(horizontal = 3.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (w.current) FontWeight.Bold else FontWeight.Normal,
                            color = if (w.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                t.intensity.forEach { row ->
                    val fixed = row.percents.filterNotNull().distinct().size <= 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(NAME_COL).padding(end = 8.dp)) {
                            Text(exerciseName(row.exerciseId), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                row.tier ?: stringResource(if (fixed) R.string.cycle_row_fixed else R.string.cycle_row_main),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        row.percents.forEachIndexed { i, pct ->
                            val w = t.weeks[i]
                            val base = if (w.phase == CyclePhase.DELOAD) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            val strength = ((pct ?: 0.0) / maxPct).toFloat().coerceIn(0f, 1f)
                            val bg = base.copy(alpha = 0.15f + 0.75f * strength * strength)
                            Box(
                                Modifier.width(CELL).padding(horizontal = 3.dp).height(40.dp).clip(MaterialTheme.shapes.small).background(bg)
                                    .then(if (w.index == current.index) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    pct?.let { "${(it * 100).toInt()} %" } ?: "–",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (strength > 0.75f) (if (w.phase == CyclePhase.DELOAD) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary) else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A partir de este ancho de tarjeta se enseña la rejilla de intensidad. */
private val WIDE = 600.dp
private val NAME_COL = 150.dp
private val CELL = 78.dp
