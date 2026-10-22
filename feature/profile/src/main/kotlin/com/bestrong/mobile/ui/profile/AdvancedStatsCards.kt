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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.stats.OneRepMax
import com.bestrong.core.stats.Scores
import com.bestrong.core.stats.Sex
import com.bestrong.core.stats.StrengthLevel
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.theme.statusColors
import kotlin.math.abs

@Composable
private fun levelLabel(level: StrengthLevel?): String = stringResource(
    when (level) {
        null -> R.string.level_none
        StrengthLevel.BEGINNER -> R.string.level_beginner
        StrengthLevel.NOVICE -> R.string.level_novice
        StrengthLevel.INTERMEDIATE -> R.string.level_intermediate
        StrengthLevel.ADVANCED -> R.string.level_advanced
        StrengthLevel.ELITE -> R.string.level_elite
    },
)

@Composable
private fun liftName(id: String): String = ExerciseLibrary.byId(id)?.displayName() ?: id

@Composable
fun StrengthLevelsCard(stats: AdvancedStats, sex: Sex, onSex: (Sex) -> Unit) {
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.strength_levels))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sex == Sex.MALE, onClick = { onSex(Sex.MALE) }, label = { Text(stringResource(R.string.sex_male)) })
                FilterChip(selected = sex == Sex.FEMALE, onClick = { onSex(Sex.FEMALE) }, label = { Text(stringResource(R.string.sex_female)) })
            }
            when {
                stats.bodyWeightKg == null -> Text(stringResource(R.string.strength_levels_need_weight), style = MaterialTheme.typography.bodySmall)
                stats.standings.isEmpty() -> Text(stringResource(R.string.strength_levels_empty), style = MaterialTheme.typography.bodySmall)
            }
            stats.standings.forEach { s ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(liftName(s.exerciseId), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(levelLabel(s.level), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { s.progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                    Text(
                        stringResource(R.string.strength_ratio, "%.2f".format(s.ratio)) +
                            (s.nextLevelKg?.let { " · " + stringResource(R.string.strength_next, unit.format(it, withUnit = true)) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun BalanceCard(stats: AdvancedStats) {
    val b = stats.balance
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.balance_title))
            Text(stringResource(R.string.balance_push_pull, formatWeight(b.push), formatWeight(b.pull)))
            Text(stringResource(R.string.balance_upper_lower, formatWeight(b.upper), formatWeight(b.lower)))
            if (b.pushPullImbalanced) Text(stringResource(R.string.balance_push_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (b.upperLowerImbalanced) Text(stringResource(R.string.balance_leg_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (!b.pushPullImbalanced && !b.upperLowerImbalanced && b.upper + b.lower > 0) {
                Text(stringResource(R.string.balance_ok), color = statusColors.good, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun WeekSummaryCard(stats: AdvancedStats) {
    val unit = LocalWeightUnit.current
    val w = stats.week ?: return
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.week_summary))
            // Cada cifra con su plural («1 entreno», «1 serie»): con una sola frase salía «1 entrenos · … · 1 series»
            Text(
                listOf(
                    pluralStringResource(R.plurals.n_workouts_count, w.workouts, w.workouts),
                    unit.formatVolume(w.volume),
                    pluralStringResource(R.plurals.n_sets_count, w.sets, w.sets) + " " + stringResource(R.string.week_effective_suffix, formatWeight(stats.weekEffectiveSets)),
                ).joinToString(" · "),
            )
            w.volumeChange?.let { c ->
                Text(
                    stringResource(R.string.week_vs_last, (if (c >= 0) "+" else "−") + "${(abs(c) * 100).toInt()} %"),
                    color = if (c >= 0) statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (w.records.isNotEmpty()) Text(pluralStringResource(R.plurals.week_records, w.records.size, w.records.size), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val ACHIEVEMENT_NAMES = mapOf(
    "first_workout" to R.string.ach_first_workout,
    "workouts_10" to R.string.ach_workouts_10,
    "workouts_50" to R.string.ach_workouts_50,
    "workouts_100" to R.string.ach_workouts_100,
    "workouts_250" to R.string.ach_workouts_250,
    "streak_4" to R.string.ach_streak_4,
    "streak_12" to R.string.ach_streak_12,
    "streak_26" to R.string.ach_streak_26,
    "volume_10t" to R.string.ach_volume_10t,
    "volume_100t" to R.string.ach_volume_100t,
    "volume_1000t" to R.string.ach_volume_1000t,
    "sets_1000" to R.string.ach_sets_1000,
    "bench_bw" to R.string.ach_bench_bw,
    "squat_1_5bw" to R.string.ach_squat_1_5bw,
    "deadlift_2bw" to R.string.ach_deadlift_2bw,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsCard(stats: AdvancedStats) {
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val unlocked = stats.achievements.count { it.unlocked }
            SectionHeader(stringResource(R.string.achievements) + " · " + stringResource(R.string.achievements_count, unlocked, stats.achievements.size))
            stats.challenge?.let { (lift, target, eta) ->
                Text(stringResource(R.string.challenge_eta, unit.format(target, withUnit = true), liftName(lift), formatShortDate(eta)), fontWeight = FontWeight.SemiBold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Primero los conseguidos; luego los más cercanos
                stats.achievements.sortedWith(compareByDescending<com.bestrong.core.stats.Achievement> { it.unlocked }.thenByDescending { it.progress }).forEach { a ->
                    val name = ACHIEVEMENT_NAMES[a.id]?.let { stringResource(it) } ?: a.id
                    AssistChip(
                        onClick = {},
                        label = { Text(if (a.unlocked) "🏆 $name" else "$name · ${(a.progress * 100).toInt()} %") },
                    )
                }
            }
        }
    }
}

@Composable
fun RecordTimelineCard(stats: AdvancedStats) {
    if (stats.timeline.isEmpty()) return
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(stringResource(R.string.record_timeline))
            stats.timeline.forEach { r ->
                Text(
                    stringResource(R.string.record_timeline_line, formatShortDate(r.dateEpochMs), liftName(r.exerciseId), unit.format(r.value * com.bestrong.mobile.ui.components.pairFactor(r.exerciseId), withUnit = true)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleRecoveryCard(stats: AdvancedStats) {
    if (stats.daysSince.values.all { it == null }) return
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.muscle_recovery))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.daysSince.forEach { (m, days) ->
                    val sore = stats.soreness[m] ?: 0.0
                    val whenText = when (days) {
                        null -> stringResource(R.string.muscle_never)
                        0 -> stringResource(R.string.muscle_today)
                        else -> stringResource(R.string.muscle_days_ago, days)
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("${m.label()} · $whenText" + if (sore >= 0.5) " · " + stringResource(R.string.muscle_sore) else "") },
                    )
                }
            }
        }
    }
}

@Composable
fun ToolsCard(onOneRm: () -> Unit, onScores: () -> Unit, onReport: () -> Unit = {}, onShareWeek: () -> Unit = {}) {
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.tools))
            OutlinedButton(onClick = onOneRm, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_1rm)) }
            OutlinedButton(onClick = onScores, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_scores)) }
            OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_monthly_report)) }
            OutlinedButton(onClick = onShareWeek, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tool_share_week)) }
        }
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
                    stringResource(if (load.tooHigh) R.string.training_load_high else R.string.training_load_ratio, String.format(java.util.Locale.getDefault(), "%.1f", r)),
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
fun MuscleTrendCard(trend: Map<com.bestrong.core.model.MuscleGroup, List<Pair<java.time.LocalDate, Double>>>, target: Int = com.bestrong.core.stats.MuscleTargets.MIN_WEEKLY_SETS.toInt()) {
    if (trend.isEmpty()) return
    var selected by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(trend.keys.first()) }
    val weeks = trend[selected] ?: trend.values.first()
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.muscle_trend))
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

/** «Rindes mejor por la tarde»: % medio de tu mejor 1RM estimado según la hora a la que entrenas. */
@Composable
fun DayPartCard(parts: List<com.bestrong.core.stats.DayPartStat>) {
    val best = parts.first()
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.day_part_title))
            // Solo hay «mejor hora» si la diferencia se nota (3 puntos); si no, da igual cuándo entrenes
            val clear = best.relativeStrength - parts[1].relativeStrength >= CLEAR_DIFFERENCE
            Text(if (clear) stringResource(R.string.day_part_best, best.part.label()) else stringResource(R.string.day_part_no_difference), fontWeight = FontWeight.SemiBold)
            parts.forEach { p ->
                Text(
                    stringResource(R.string.day_part_line, p.part.label().replaceFirstChar { it.uppercase() }, (p.relativeStrength * 100).toInt(), p.sessions),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(stringResource(R.string.day_part_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun com.bestrong.core.stats.DayPart.label(): String = stringResource(
    when (this) {
        com.bestrong.core.stats.DayPart.MORNING -> R.string.day_part_morning
        com.bestrong.core.stats.DayPart.AFTERNOON -> R.string.day_part_afternoon
        com.bestrong.core.stats.DayPart.EVENING -> R.string.day_part_evening
    },
)

/** Diferencia mínima (fracción de tu mejor marca) para decir que una franja es mejor. */
private const val CLEAR_DIFFERENCE = 0.03

/** «Tus mejores días»: % medio de tu mejor marca según el día de la semana (útil para colocar las sesiones duras). */
@Composable
fun WeekdayCard(days: List<Triple<java.time.DayOfWeek, Int, Double>>) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val best = days.first()
    val clear = best.third - days[1].third >= CLEAR_DIFFERENCE
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.weekday_title))
            val bestName = best.first.getDisplayName(java.time.format.TextStyle.FULL, locale)
            Text(if (clear) stringResource(R.string.weekday_best, bestName) else stringResource(R.string.weekday_no_difference), fontWeight = FontWeight.SemiBold)
            days.forEach { (day, n, rel) ->
                Text(
                    stringResource(R.string.day_part_line, day.getDisplayName(java.time.format.TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }, (rel * 100).toInt(), n),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
