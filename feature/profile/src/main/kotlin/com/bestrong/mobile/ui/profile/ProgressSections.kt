package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LineChart
import com.bestrong.mobile.ui.components.LocalBodyUnit
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.SettingsDivider
import com.bestrong.mobile.ui.components.SettingsGroup
import com.bestrong.mobile.ui.components.SettingsNavRow
import com.bestrong.mobile.ui.components.SettingsRow
import com.bestrong.mobile.ui.components.formatShortDate
import java.time.format.DateTimeFormatter

/**
 * Progreso → Fuerza: récords e hitos primero (lo que más se mira), luego el volumen y los entrenos por semana, los
 * músculos, la carga, el equilibrio de patrones y las herramientas (calculadora de 1RM, puntuaciones, informe).
 */
@Composable
fun StrengthSection(vm: ProfileViewModel, onOpenExercise: (String) -> Unit, onGoTrain: () -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val advanced by vm.advanced.collectAsStateWithLifecycle()
    var showOneRm by rememberSaveable { mutableStateOf(false) }
    var showScores by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    if (showReport) MonthlyReportDialog(onShare = { months, pdf -> vm.shareMonthlyReport(months, pdf) }) { showReport = false }
    if (showOneRm) OneRepMaxDialog { showOneRm = false }
    if (showScores) ScoresDialog(advanced.bodyWeightKg, settings.sex) { showScores = false }
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Sin entrenos: un solo aviso con su botón (no cuatro ceros y varias gráficas vacías)
        if (stats.loaded && stats.totalWorkouts == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.bestrong.mobile.ui.components.EmptyState(
                    Icons.Default.FitnessCenter,
                    stringResource(R.string.profile_empty_title),
                    stringResource(R.string.profile_empty_body),
                    Modifier.heightIn(min = 320.dp),
                    action = stringResource(R.string.history_empty_action) to onGoTrain,
                )
            }
        } else if (stats.loaded) {
            item(span = { GridItemSpan(maxLineSpan) }) { RecordsCard(stats.topOneRepMax, advanced, vm::exerciseOf, onOpenExercise) }
            item(span = { GridItemSpan(maxLineSpan) }) { TotalsTiles(stats) }
            item { WeeklyVolumeCard(stats) }
            item { WorkoutsPerWeekCard(stats) }
            // Músculos: semana (silueta), 30 días, tendencia y recuperación en una sola tarjeta
            item { MusclesCard(stats, advanced, settings.weeklySetsTarget) { n -> vm.updateSettings { it.copy(weeklySetsTarget = n) } } }
            item { TrainingLoadCard(advanced) }
            item { PatternBalanceCard(advanced) }
        }
        item {
            ToolsCard(
                onOneRm = { showOneRm = true },
                onScores = { showScores = true },
                onReport = { showReport = true },
                onShareWeek = {
                    // Texto de los últimos 7 días para el entrenador (WhatsApp, correo…)
                    vm.coachWeekText()?.let { text ->
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                        context.startActivity(android.content.Intent.createChooser(send, null))
                    }
                },
            )
        }
    }
}

/** Entrenos, racha, volumen y tiempo totales en cuatro cifras, y cómo es un entreno típico. */
@Composable
private fun TotalsTiles(stats: ProfileStats) {
    val unit = LocalWeightUnit.current
    androidx.compose.foundation.layout.BoxWithConstraints {
        val tiles: List<Pair<String, String>> = listOf(
            stringResource(R.string.stat_workouts) to "${stats.totalWorkouts}",
            stringResource(R.string.stat_streak) to "${stats.weeklyStreak}",
            stringResource(R.string.stat_total_volume) to unit.formatVolume(stats.totalVolume),
            stringResource(R.string.stat_time) to formatDuration(stats.totalDurationSec),
        )
        // 4 en fila en el Fold desplegado; 2 × 2 en la pantalla exterior
        val perRow = if (maxWidth < 600.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) -> FigureTile(label, value, Modifier.weight(1f)) }
                }
            }
            if (stats.totalWorkouts > 0) {
                val n = stats.totalWorkouts
                Text(
                    stringResource(R.string.stat_average_workout, formatDuration(stats.totalDurationSec / n), stats.totalSets / n, unit.formatVolume(stats.totalVolume / n)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyVolumeCard(stats: ProfileStats) {
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(stringResource(R.string.weekly_volume, unit.label))
            WeekVsAverage(stats.weeks, { it.volume }) { unit.formatVolume(it) }
            // «3 oct» y no «3/10» (que en EE. UU. es 10 de marzo)
            val f = DateTimeFormatter.ofPattern("d MMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
            // TalkBack lee el volumen como se ve («27.216 lb»), no «27216,5»
            BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), unit.fromKg(it.volume)) }, describe = { "${it.label}: ${unit.formatVolume(unit.toKg(it.value))}" })
        }
    }
}

@Composable
private fun WorkoutsPerWeekCard(stats: ProfileStats) {
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(stringResource(R.string.workouts_per_week))
            WeekVsAverage(stats.weeks, { it.workouts.toDouble() }) { formatWeight(it) }
            val f = DateTimeFormatter.ofPattern("d MMM", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
            BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), it.workouts.toDouble()) })
        }
    }
}

/** «Esta semana: 3 · media: 2,5»: las barras no tienen escala, y esto responde a «¿voy por detrás?». */
@Composable
private fun WeekVsAverage(weeks: List<com.bestrong.core.stats.WeekBucket>, value: (com.bestrong.core.stats.WeekBucket) -> Double, format: (Double) -> String) {
    val now = weeks.lastOrNull() ?: return
    // Semanas ya cerradas y con entreno (las vacías bajarían la media de quien empezó hace poco)
    val past = weeks.dropLast(1).filter { value(it) > 0 }
    if (past.isEmpty()) return
    com.bestrong.mobile.ui.components.MetaPill(
        stringResource(R.string.stat_week_vs_avg, format(value(now)), format(past.sumOf(value) / past.size)),
        Modifier.padding(vertical = 6.dp),
    )
}

/** Peso, grasa y cintura de la medida más reciente que tenga cada uno (o «—»). */
@Composable
fun BodyTiles(list: List<BodyMeasurement>, modifier: Modifier = Modifier) {
    val bodyUnit = LocalBodyUnit.current
    val weight = list.firstNotNullOfOrNull { it.weightKg }?.let { bodyUnit.format(it, withUnit = true) } ?: "—"
    val fat = list.firstNotNullOfOrNull { it.bodyFatPct }?.let { formatWeight(it) + " %" } ?: "—"
    val waist = list.firstNotNullOfOrNull { it.waistCm }?.let { formatWeight(it) + " cm" } ?: "—"
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FigureTile(stringResource(R.string.enf_you_weight), weight, Modifier.weight(1f))
        FigureTile(stringResource(R.string.enf_you_fat), fat, Modifier.weight(1f))
        FigureTile(stringResource(R.string.enf_you_waist), waist, Modifier.weight(1f))
    }
}

/** Una cifra con su etiqueta encima, en mayúsculas pequeñas: el dato en la letra de las cifras, sin adornos. */
@Composable
internal fun FigureTile(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clearAndSetSemantics { contentDescription = "$label: $value" }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label.uppercase(), style = com.bestrong.mobile.ui.theme.KickerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        androidx.compose.foundation.text.BasicText(
            value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = com.bestrong.mobile.ui.theme.FigureFont,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = MaterialTheme.typography.titleMedium.fontSize),
        )
    }
}

/**
 * Progreso → Cuerpo: peso, grasa y cintura de un vistazo, la gráfica del peso con su tendencia y su objetivo, la
 * composición corporal y las últimas medidas. Apuntar, corregir, fotos e importar siguen en «Todas las medidas».
 */
@Composable
fun BodySection(vm: ProfileViewModel, onOpenMeasurements: () -> Unit) {
    val unit = LocalBodyUnit.current
    val list by vm.measurements.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BodyTiles(list) }
        val weights = list.filter { it.weightKg != null }.sortedBy { it.dateEpochMs }
        if (weights.size >= 2) {
            item {
                com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(stringResource(R.string.body_weight_unit, unit.label))
                        LineChart(weights.map { ChartPoint(formatShortDate(it.dateEpochMs), it.weightKg!!) }, valueFormatter = { unit.format(it) })
                        WeightTrendLine(weights.map { it.dateEpochMs to it.weightKg!! }, unit)
                        WeightGoalLine(vm, weights.map { it.dateEpochMs to it.weightKg!! }, unit)
                    }
                }
            }
        }
        if (list.isNotEmpty()) item { BodyCompositionCard(vm, list) }
        item { LatestMeasures(list) }
        item {
            SettingsGroup(null) {
                SettingsNavRow(
                    stringResource(R.string.enf_prog_all_measures),
                    Icons.Default.Straighten,
                    androidx.compose.ui.graphics.Color.Unspecified,
                    onOpenMeasurements,
                    summary = stringResource(R.string.measurements),
                )
            }
        }
    }
}

/** Pecho, brazo, muslo y cintura: el último dato de cada uno, con su fecha. */
@Composable
private fun LatestMeasures(list: List<BodyMeasurement>) {
    val rows: List<Pair<String, (BodyMeasurement) -> Double?>> = listOf(
        stringResource(R.string.f_chest) to { m: BodyMeasurement -> m.chestCm },
        stringResource(R.string.f_arm) to { m: BodyMeasurement -> m.armCm },
        stringResource(R.string.f_thigh) to { m: BodyMeasurement -> m.thighCm },
        stringResource(R.string.f_waist) to { m: BodyMeasurement -> m.waistCm },
    )
    val shown = rows.mapNotNull { (label, value) -> list.firstOrNull { value(it) != null }?.let { m -> Triple(label, value(m)!!, m.dateEpochMs) } }
    if (shown.isEmpty()) return
    SettingsGroup(stringResource(R.string.enf_prog_latest)) {
        shown.forEachIndexed { i, (label, value, date) ->
            if (i > 0) SettingsDivider(inset = false)
            SettingsRow(label, summary = formatShortDate(date)) {
                Text(formatWeight(value), style = MaterialTheme.typography.titleMedium, fontFamily = com.bestrong.mobile.ui.theme.FigureFont)
            }
        }
    }
}
