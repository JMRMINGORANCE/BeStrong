package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.label
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, onOpenMeasurements: () -> Unit, onOpenExercise: (String) -> Unit, onGoTrain: () -> Unit = {}) {
    val unit = LocalWeightUnit.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val advanced by vm.advanced.collectAsStateWithLifecycle()
    var showOneRm by rememberSaveable { mutableStateOf(false) }
    var showScores by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    if (showReport) MonthlyReportDialog(onShare = { months, pdf -> vm.shareMonthlyReport(months, pdf) }) { showReport = false }
    if (showOneRm) OneRepMaxDialog { showOneRm = false }
    if (showScores) ScoresDialog(advanced.bodyWeightKg, settings.sex) { showScores = false }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // Buzón de ideas abierto en «Proponer» (0) o «Mis propuestas» (1); -1, cerrado
    var proposalsTab by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    if (proposalsTab >= 0) ProposalsDialog(initialTab = proposalsTab) { proposalsTab = -1 }
    // Cada vez que se vuelve a la pantalla (p. ej. tras cambiar permisos en Health Connect)
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshHealthConnect()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Tres pestañas: el progreso (lo que se mira a menudo), los ajustes (lo que se toca de vez en cuando) y la comunidad
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.bestrong.mobile.ui.components.ScreenTitle(
                        stringResource(R.string.nav_profile),
                        overline = if (tab == 0 && stats.totalWorkouts > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.workout_count, stats.totalWorkouts, stats.totalWorkouts) else null,
                    )
                    ProfileTabs(tab) { tab = it }
                }
            }
            if (tab == 0) {
                // Sin entrenos: un solo aviso con su botón (no cuatro ceros y varias gráficas vacías)
                // Mientras se calculan (un instante) no se enseña ni el aviso de vacío ni las gráficas
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        // 4 en fila en el Fold desplegado; 2 × 2 en la pantalla exterior
                        BoxWithConstraints {
                            val tiles: List<Pair<String, String>> = listOf(
                                stringResource(R.string.stat_workouts) to "${stats.totalWorkouts}",
                                stringResource(R.string.stat_streak) to "🔥 ${stats.weeklyStreak}",
                                stringResource(R.string.stat_total_volume) to unit.formatVolume(stats.totalVolume),
                                stringResource(R.string.stat_time) to formatDuration(stats.totalDurationSec),
                            )
                            val perRow = if (maxWidth < 600.dp) 2 else 4
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tiles.chunked(perRow).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                                    }
                                }
                                // Cómo es un entreno típico: duración, series y volumen medios
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
                    item {
                        com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                            Column(Modifier.padding(16.dp)) {
                                SectionHeader(stringResource(R.string.weekly_volume, unit.label))
                                WeekVsAverage(stats.weeks, { it.volume }) { unit.formatVolume(it) }
                                // «3 oct» y no «3/10» (que en EE. UU. es 10 de marzo)
                                val f = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
                                // TalkBack lee el volumen como se ve («27.216 lb»), no «27216,5»
                                BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), unit.fromKg(it.volume)) }, describe = { "${it.label}: ${unit.formatVolume(unit.toKg(it.value))}" })
                            }
                        }
                    }
                    item {
                        com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                            Column(Modifier.padding(16.dp)) {
                                SectionHeader(stringResource(R.string.workouts_per_week))
                                WeekVsAverage(stats.weeks, { it.workouts.toDouble() }) { formatWeight(it) }
                                // «3 oct» y no «3/10» (que en EE. UU. es 10 de marzo)
                                val f = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
                                BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), it.workouts.toDouble()) })
                            }
                        }
                    }
                    item {
                        com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SectionHeader(stringResource(R.string.sets_per_muscle))
                                if (stats.muscles30d.isEmpty()) Text(stringResource(R.string.no_data_yet))
                                val max = stats.muscles30d.values.maxOrNull() ?: 1.0
                                stats.muscles30d.forEach { (m, sets) ->
                                    Row {
                                        Text(m.label(), Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        LinearProgressIndicator(progress = { (sets / max).toFloat() }, modifier = Modifier.weight(0.45f).padding(top = 8.dp))
                                        Text(formatWeight(sets), Modifier.weight(0.15f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    item { WeeklyMuscleMap(stats.weeklyMuscles, settings.weeklySetsTarget) { n -> vm.updateSettings { it.copy(weeklySetsTarget = n) } } }
                    item { MuscleTrendCard(stats.muscleTrend, settings.weeklySetsTarget) }
                    stats.year?.let { y -> item { YearInReviewCard(y) } }
                    item { WeekSummaryCard(advanced) }
                    item { TrainingLoadCard(advanced) }
                    if (advanced.dayParts.size >= 2) item { DayPartCard(advanced.dayParts) }
                    if (advanced.weekdays.size >= 2) item { WeekdayCard(advanced.weekdays) }
                    item { StrengthLevelsCard(advanced, settings.sex) { sex -> vm.updateSettings { it.copy(sex = sex) } } }
                    item { BalanceCard(advanced) }
                    item { MuscleRecoveryCard(advanced) }
                    item { AchievementsCard(advanced) }
                    item { RecordTimelineCard(advanced) }
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
                if (stats.totalWorkouts > 0) {
                    item {
                        com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                            Column(Modifier.padding(16.dp)) {
                                SectionHeader(stringResource(R.string.best_1rm))
                                if (stats.topOneRepMax.isEmpty()) Text(stringResource(R.string.complete_sets_for_records))
                                stats.topOneRepMax.forEach { pr ->
                                    // Con mancuernas como total del par, el mismo número que en el detalle del ejercicio
                                    // (también los propios, que no están en la biblioteca)
                                    val equipment = (vm.exerciseOf(pr.exerciseId) ?: com.bestrong.core.library.ExerciseLibrary.byId(pr.exerciseId))?.equipment
                                    val pair = if (settings.dumbbellTotal && equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1
                                    ListItem(
                                        headlineContent = { Text(pr.exerciseLabel()) },
                                        trailingContent = { Text(unit.format(pr.value * pair, withUnit = true), fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.clickable { onOpenExercise(pr.exerciseId) },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Card(onClick = onOpenMeasurements) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.measurements)) },
                            supportingContent = {
                                // En la unidad del cuerpo (con unidades mixtas, la de las cargas puede ser otra) y el último peso apuntado,
                                // aunque la medida más reciente sea solo de cintura
                                val bodyUnit = com.bestrong.mobile.ui.components.LocalBodyUnit.current
                                Text(measurements.firstNotNullOfOrNull { it.weightKg }?.let { stringResource(R.string.last_weight, bodyUnit.format(it, withUnit = true)) } ?: stringResource(R.string.log_measurements))
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        )
                    }
                }
            } else if (tab == 2) {
                // Comunidad: la app se hace con las ideas de quien la usa
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CommunitySection(onPropose = { proposalsTab = 0 }, onMine = { proposalsTab = 1 }, modifier = Modifier.widthIn(max = 720.dp))
                }
            } else {
                // Ajustes: un menú por apartados; cada uno abre su pantalla
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsTab(vm, onOpenCommunity = { tab = 2 }, modifier = Modifier.widthIn(max = 720.dp))
                }
            }
        }
    }
}

/** Más días que esto sin copia (es semanal) es que algo falla: se avisa en rojo. */
private const val BACKUP_STALE_DAYS = 14L

/** Estado de la copia automática: apagada, carpeta perdida, última copia (en rojo si es vieja) o pendiente. */
@Composable
internal fun AutoBackupStatus(settings: com.bestrong.mobile.data.UserSettings) {
    val staleBefore = System.currentTimeMillis() - BACKUP_STALE_DAYS * 86_400_000L
    val error = MaterialTheme.colorScheme.error
    when {
        settings.backupFolderUri == null && settings.backupFolderLost -> Text(stringResource(R.string.auto_backup_lost), color = error)
        settings.backupFolderUri == null -> Text(stringResource(R.string.auto_backup_off))
        settings.lastBackupEpochMs in 1 until staleBefore -> Text(stringResource(R.string.auto_backup_stale, formatDate(settings.lastBackupEpochMs)), color = error)
        settings.lastBackupEpochMs > 0 -> Text(stringResource(R.string.auto_backup_last, formatDate(settings.lastBackupEpochMs)))
        else -> Text(stringResource(R.string.auto_backup_on))
    }
}

private val BACKUP_LIST_MAX_HEIGHT = 360.dp

/** Las copias automáticas de la carpeta (la más reciente primero): elegir una la lleva a la vista previa. */
@Composable
internal fun AutoBackupPicker(vm: ProfileViewModel) {
    val list by vm.autoBackups.collectAsStateWithLifecycle()
    val copies = list ?: return
    AlertDialog(
        onDismissRequest = { vm.autoBackups.value = null },
        title = { Text(stringResource(R.string.restore_latest)) },
        text = {
            Column(Modifier.heightIn(max = BACKUP_LIST_MAX_HEIGHT).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                copies.forEach { c ->
                    TextButton(onClick = { vm.autoBackups.value = null; vm.restoreBackup(c.uri) }, modifier = Modifier.fillMaxWidth()) {
                        // «2026-10-12 (1)»: copia repetida del mismo día, se enseña la fecha
                        val date = c.date.substringBefore(" (")
                        Text(c.workouts?.let { stringResource(R.string.auto_backup_item, date, it) } ?: date, Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.autoBackups.value = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Selector de las pestañas de Perfil (progreso, ajustes y comunidad), en píldora: la elegida, rellena con el degradado de marca. */
@Composable
private fun ProfileTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(stringResource(R.string.profile_progress), stringResource(R.string.settings), stringResource(R.string.community_tab))
    Row(
        Modifier.fillMaxWidth().then(Modifier.clip(androidx.compose.foundation.shape.CircleShape)).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (on) com.bestrong.mobile.ui.components.brandGradient() else androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Transparent))
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                // Con letra grande la etiqueta encoge antes de cortarse («Progr…», «Comu…»)
                val style = MaterialTheme.typography.bodyLarge
                androidx.compose.foundation.text.BasicText(
                    label,
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = style.copy(fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
                )
            }
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
