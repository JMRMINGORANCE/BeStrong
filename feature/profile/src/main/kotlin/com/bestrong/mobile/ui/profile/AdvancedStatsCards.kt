package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.stats.OneRepMax
import com.bestrong.core.stats.Scores
import com.bestrong.core.stats.Sex
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label

@Composable
internal fun liftName(id: String): String = ExerciseLibrary.byId(id)?.displayName() ?: id

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleRecoveryContent(stats: AdvancedStats) {
    if (stats.daysSince.values.all { it == null }) {
        Text(stringResource(R.string.no_data_yet), style = MaterialTheme.typography.bodySmall)
        return
    }
    run {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.daysSince.forEach { (m, days) ->
                    val sore = stats.soreness[m] ?: 0.0
                    val whenText = when (days) {
                        null -> stringResource(R.string.muscle_never)
                        0 -> stringResource(R.string.muscle_today)
                        else -> stringResource(R.string.muscle_days_ago, days)
                    }
                    com.bestrong.mobile.ui.components.MetaPill("${m.label()} · $whenText" + if (sore >= 0.5) " · " + stringResource(R.string.muscle_sore) else "")
                }
            }
        }
    }
}

@Composable
fun ToolsCard(onOneRm: () -> Unit, onScores: () -> Unit, onReport: () -> Unit = {}, onShareWeek: () -> Unit = {}) {
    // Como los Ajustes: una fila por herramienta con su icono de color (antes, cuatro botones iguales)
    com.bestrong.mobile.ui.components.SettingsGroup(stringResource(R.string.tools)) {
        val tint = com.bestrong.mobile.ui.components.SettingsTint
        com.bestrong.mobile.ui.components.SettingsNavRow(stringResource(R.string.tool_1rm), Icons.Default.Calculate, tint.Orange, onOneRm)
        com.bestrong.mobile.ui.components.SettingsDivider()
        com.bestrong.mobile.ui.components.SettingsNavRow(stringResource(R.string.tool_scores), Icons.Default.EmojiEvents, tint.Amber, onScores)
        com.bestrong.mobile.ui.components.SettingsDivider()
        com.bestrong.mobile.ui.components.SettingsNavRow(stringResource(R.string.tool_monthly_report), Icons.Default.Description, tint.Blue, onReport)
        com.bestrong.mobile.ui.components.SettingsDivider()
        com.bestrong.mobile.ui.components.SettingsNavRow(stringResource(R.string.tool_share_week), Icons.Default.Share, tint.Green, onShareWeek)
    }
}

@Composable
private fun DecimalField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) = OutlinedTextField(
    value,
    { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7)) },
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = modifier,
)

/** Calculadora de 1RM con todas las fórmulas y tabla de porcentajes. */
@Composable
fun OneRepMaxDialog(onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var weight by remember { mutableStateOf("100") }
    var reps by remember { mutableStateOf("5") }
    val kg = unit.parseToKg(weight) ?: 0.0
    val r = reps.toIntOrNull() ?: 0
    val avg = OneRepMax.average(kg, r)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_1rm)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(weight, stringResource(R.string.calc_weight) + " (${unit.label})", { weight = it }, Modifier.weight(1f))
                    OutlinedTextField(
                        reps,
                        { reps = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.calc_reps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (avg > 0) {
                    Text(stringResource(R.string.calc_average, unit.format(avg, withUnit = true)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OneRepMax.all(kg, r).forEach { (f, v) ->
                        Row {
                            Text(f.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(unit.format(v, withUnit = true), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OneRepMax.table(avg).forEach { (pct, rr, w) ->
                        Row {
                            Text(stringResource(R.string.calc_percent_row, pct, rr), Modifier.weight(1f))
                            Text(unit.format(w, withUnit = true), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** DOTS, Wilks, IPF GL e intentos de competición. */
@Composable
fun ScoresDialog(initialBodyWeightKg: Double?, sex: Sex, onDismiss: () -> Unit) {
    val unit = LocalWeightUnit.current
    var total by remember { mutableStateOf("") }
    var bw by remember { mutableStateOf(initialBodyWeightKg?.let { unit.format(it) } ?: "") }
    var benchOnly by remember { mutableStateOf(false) }
    val totalKg = unit.parseToKg(total) ?: 0.0
    val bwKg = unit.parseToKg(bw) ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_scores)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DecimalField(total, stringResource(if (benchOnly) R.string.calc_weight else R.string.calc_total) + " (${unit.label})", { total = it }, Modifier.fillMaxWidth())
                DecimalField(bw, stringResource(R.string.calc_body_weight) + " (${unit.label})", { bw = it }, Modifier.fillMaxWidth())
                FilterChip(selected = benchOnly, onClick = { benchOnly = !benchOnly }, label = { Text(stringResource(R.string.calc_bench_only)) })
                if (totalKg > 0 && bwKg > 0) {
                    if (!benchOnly) {
                        Text("DOTS: %.1f".format(Scores.dots(totalKg, bwKg, sex)), fontWeight = FontWeight.Bold)
                        Text("Wilks: %.1f".format(Scores.wilks(totalKg, bwKg, sex)))
                    }
                    Text("IPF GL: %.1f".format(Scores.ipfGl(totalKg, bwKg, sex, benchOnly)))
                    if (benchOnly) {
                        val (a, b, c) = Scores.attempts(totalKg)
                        Text(
                            stringResource(R.string.calc_attempts, unit.format(totalKg, withUnit = true), unit.format(a), unit.format(b), unit.format(c)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Informe mensual: este mes o los dos anteriores, como imagen o PDF. */
@Composable
fun MonthlyReportDialog(onShare: (monthsAgo: Int, pdf: Boolean) -> Unit, onDismiss: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var monthsAgo by remember { mutableIntStateOf(0) }
    val now = java.time.YearMonth.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_monthly_report)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..2).forEach { i ->
                    val m = now.minusMonths(i.toLong())
                    FilterChip(
                        selected = monthsAgo == i,
                        onClick = { monthsAgo = i },
                        label = { Text(m.month.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)) },
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onShare(monthsAgo, false); onDismiss() }) { Text(stringResource(R.string.report_image)) }
                TextButton(onClick = { onShare(monthsAgo, true); onDismiss() }) { Text(stringResource(R.string.report_pdf)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Carga semanal (esfuerzo × minutos) de las últimas 8 semanas y aviso si sube demasiado de golpe. */
@Composable
fun TrainingLoadCard(stats: AdvancedStats) {
    val load = stats.load ?: return
    if (load.weeks.all { it.second == 0.0 }) return
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.training_load))
            BarChart(load.weeks.map { (monday, value) -> ChartPoint(formatShortDate(monday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()), value) })
            load.acuteChronicRatio?.let { r ->
                Text(
                    stringResource(if (load.tooHigh) R.string.training_load_high else R.string.training_load_ratio, String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "%.1f", r)),
                    color = if (load.tooHigh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(stringResource(R.string.training_load_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Series por semana de un músculo (8 semanas) frente a la recomendación de 10–20. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MuscleTrendContent(trend: Map<com.bestrong.core.model.MuscleGroup, List<Pair<java.time.LocalDate, Double>>>, target: Int = com.bestrong.core.stats.MuscleTargets.MIN_WEEKLY_SETS.toInt()) {
    if (trend.isEmpty()) {
        Text(stringResource(R.string.no_data_yet), style = MaterialTheme.typography.bodySmall)
        return
    }
    var selected by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(trend.keys.first()) }
    val weeks = trend[selected] ?: trend.values.first()
    run {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                trend.keys.forEach { m ->
                    androidx.compose.material3.FilterChip(selected = m == selected, onClick = { selected = m }, label = { Text(m.label()) })
                }
            }
            BarChart(weeks.map { (monday, sets) -> ChartPoint(formatShortDate(monday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()), sets) })
            MuscleZoneLine(weeks, target)
            Text(stringResource(R.string.muscle_trend_hint, target, com.bestrong.core.stats.MuscleTargets.MAX_WEEKLY_SETS.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Media de las semanas cerradas frente al rango habitual de 10–20 series. */
@Composable
private fun MuscleZoneLine(weeks: List<Pair<java.time.LocalDate, Double>>, target: Int) {
    // Tu mínimo semanal (Perfil › mapa muscular), el mismo que usan el mapa, Inicio y el widget
    val (avg, zone) = com.bestrong.core.stats.WorkoutStats.weeklySetsZone(weeks, minSets = target.toDouble()) ?: return
    val max = com.bestrong.core.stats.MuscleTargets.MAX_WEEKLY_SETS.toInt()
    val value = "%.1f".format(avg).removeSuffix(",0").removeSuffix(".0")
    val (text, color) = when (zone) {
        com.bestrong.core.stats.VolumeStatus.NONE, com.bestrong.core.stats.VolumeStatus.LOW -> stringResource(R.string.muscle_zone_low, value, target, max) to MaterialTheme.colorScheme.onSurfaceVariant
        com.bestrong.core.stats.VolumeStatus.OPTIMAL -> stringResource(R.string.muscle_zone_ok, value, target, max) to MaterialTheme.colorScheme.primary
        com.bestrong.core.stats.VolumeStatus.HIGH -> stringResource(R.string.muscle_zone_high, value, target, max) to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
