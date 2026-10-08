package com.bestrong.mobile.ui.profile

import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.R
import androidx.compose.ui.res.stringResource
import com.bestrong.mobile.ui.components.LocalWeightUnit
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.PrivacyPolicyActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.StatTile
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, onOpenMeasurements: () -> Unit, onOpenExercise: (String) -> Unit) {
    val unit = LocalWeightUnit.current
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val hcGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val ok = granted.containsAll(vm.healthConnectPermissions)
        vm.updateSettings { it.copy(healthConnectExport = ok) }
        vm.refreshHealthConnect()
    }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    LaunchedEffect(Unit) { vm.refreshHealthConnect() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(stringResource(R.string.weekly_volume, unit.label))
                        val f = DateTimeFormatter.ofPattern("d/M")
                        BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), unit.fromKg(it.volume)) })
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(stringResource(R.string.workouts_per_week))
                        val f = DateTimeFormatter.ofPattern("d/M")
                        BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), it.workouts.toDouble()) })
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionHeader(stringResource(R.string.sets_per_muscle))
                        if (stats.muscles30d.isEmpty()) Text(stringResource(R.string.no_data_yet))
                        val max = stats.muscles30d.values.maxOrNull() ?: 1.0
                        stats.muscles30d.forEach { (m, sets) ->
                            Row {
                                Text(m.label(), Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(progress = { (sets / max).toFloat() }, modifier = Modifier.weight(0.45f).padding(top = 8.dp))
                                Text(formatWeight(sets), Modifier.weight(0.15f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(stringResource(R.string.best_1rm))
                        if (stats.topOneRepMax.isEmpty()) Text(stringResource(R.string.complete_sets_for_records))
                        stats.topOneRepMax.forEach { pr ->
                            ListItem(
                                headlineContent = { Text(pr.exerciseName) },
                                trailingContent = { Text(unit.format(pr.value, withUnit = true), fontWeight = FontWeight.Bold) },
                                modifier = Modifier.clickable { onOpenExercise(pr.exerciseId) },
                            )
                        }
                    }
                }
            }
            item {
                Card(onClick = onOpenMeasurements) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.measurements)) },
                        supportingContent = {
                            Text(measurements.firstOrNull()?.weightKg?.let { stringResource(R.string.last_weight, unit.format(it, withUnit = true)) } ?: stringResource(R.string.log_measurements))
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SectionHeader(stringResource(R.string.settings), Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.weight_unit)) },
                            trailingContent = {
                                SingleChoiceSegmentedButtonRow {
                                    WeightUnit.entries.forEachIndexed { i, u ->
                                        SegmentedButton(
                                            selected = settings.unit == u,
                                            onClick = { vm.updateSettings { it.copy(unit = u) } },
                                            shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
                                        ) { Text(u.label) }
                                    }
                                }
                            },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.progression_setting)) },
                            supportingContent = { Text(stringResource(R.string.progression_setting_body)) },
                            trailingContent = { Switch(settings.progressionEnabled, { v -> vm.updateSettings { it.copy(progressionEnabled = v) } }) },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.vibrate_rest)) },
                            trailingContent = { Switch(settings.vibrateOnRestEnd, { v -> vm.updateSettings { it.copy(vibrateOnRestEnd = v) } }) },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.keep_screen_on)) },
                            trailingContent = { Switch(settings.keepScreenOn, { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }) },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.hc_export)) },
                            supportingContent = {
                                Text(
                                    when {
                                        !vm.healthConnectAvailable -> stringResource(R.string.hc_unavailable)
                                        settings.healthConnectExport && !hcGranted -> stringResource(R.string.hc_missing_perms)
                                        else -> stringResource(R.string.hc_body)
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settings.healthConnectExport && hcGranted,
                                    enabled = vm.healthConnectAvailable,
                                    onCheckedChange = { on ->
                                        if (on && !hcGranted) hcLauncher.launch(vm.healthConnectPermissions)
                                        else vm.updateSettings { it.copy(healthConnectExport = on) }
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.csv_export)) },
                            supportingContent = { Text(stringResource(R.string.csv_export_body)) },
                            trailingContent = { TextButton(onClick = { exportLauncher.launch("bestrong-${java.time.LocalDate.now()}.csv") }) { Text(stringResource(R.string.action_export)) } },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.csv_import)) },
                            supportingContent = { Text(stringResource(R.string.csv_import_body)) },
                            trailingContent = { TextButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }) { Text(stringResource(R.string.action_import)) } },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.privacy)) },
                            supportingContent = { Text(stringResource(R.string.privacy_body)) },
                            modifier = Modifier.clickable { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.watch_sync)) },
                            supportingContent = { Text(stringResource(R.string.watch_sync_body)) },
                            trailingContent = { TextButton(onClick = vm::syncWatchNow) { Text(stringResource(R.string.action_sync)) } },
                        )
                    }
                }
            }
        }
    }
}
