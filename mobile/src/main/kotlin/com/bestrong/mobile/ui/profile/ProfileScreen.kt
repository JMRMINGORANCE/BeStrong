package com.bestrong.mobile.ui.profile

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.training.GymProfile
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.PrivacyPolicyActivity
import com.bestrong.mobile.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.help.HelpDialog
import com.bestrong.mobile.ui.help.TutorialDialog
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, onOpenMeasurements: () -> Unit, onOpenExercise: (String) -> Unit) {
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
    val hcGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    // Lectura (peso, sueño, pulso en reposo): permiso aparte y luego se importa el peso
    val hcReadLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.importHealthWeights() }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val ok = granted.containsAll(vm.healthConnectPermissions)
        vm.updateSettings { it.copy(healthConnectExport = ok) }
        vm.refreshHealthConnect()
    }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreBackup) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupFolder) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var showReminders by rememberSaveable { mutableStateOf(false) }
    var editingGym by remember { mutableStateOf<GymProfile?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) AboutDialog { showAbout = false }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    if (showHelp) HelpDialog(onDismiss = { showHelp = false }, onReplayTutorial = { showHelp = false; showTutorial = true })
    if (showTutorial) TutorialDialog { showTutorial = false }
    editingGym?.let { g ->
        GymEditorDialog(
            gym = g,
            canDelete = settings.gyms.size > 1 && settings.gyms.any { it.id == g.id },
            onSave = { updated ->
                vm.updateSettings { s ->
                    val exists = s.gyms.any { it.id == updated.id }
                    s.copy(gyms = if (exists) s.gyms.map { if (it.id == updated.id) updated else it } else s.gyms + updated)
                }
                editingGym = null
            },
            onDelete = {
                vm.updateSettings { s -> s.copy(gyms = s.gyms.filterNot { it.id == g.id }) }
                editingGym = null
            },
            onDismiss = { editingGym = null },
        )
    }
    if (showReminders) {
        RemindersDialog(
            settings = settings,
            onSave = { days, h, m ->
                if (days.isNotEmpty() && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.updateSettings { it.copy(reminderDays = days, reminderHour = h, reminderMinute = m) }
                showReminders = false
            },
            onDismiss = { showReminders = false },
        )
    }
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
            item { WeeklyMuscleMap(stats.weeklyMuscles) }
            item { WeekSummaryCard(advanced) }
            item { StrengthLevelsCard(advanced, settings.sex) { sex -> vm.updateSettings { it.copy(sex = sex) } } }
            item { BalanceCard(advanced) }
            item { MuscleRecoveryCard(advanced) }
            item { AchievementsCard(advanced) }
            item { RecordTimelineCard(advanced) }
            item { ToolsCard(onOneRm = { showOneRm = true }, onScores = { showScores = true }, onReport = { showReport = true }) }
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
            item(span = { GridItemSpan(maxLineSpan) }) { AppearanceCard(settings, vm::updateSettings) }
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
                        SwitchItem(
                            title = stringResource(R.string.progression_setting),
                            body = stringResource(R.string.progression_setting_body),
                            checked = settings.progressionEnabled,
                        ) { v -> vm.updateSettings { it.copy(progressionEnabled = v) } }
                        SwitchItem(stringResource(R.string.vibrate_rest), checked = settings.vibrateOnRestEnd) { v ->
                            vm.updateSettings { it.copy(vibrateOnRestEnd = v) }
                        }
                        SwitchItem(stringResource(R.string.keep_screen_on), checked = settings.keepScreenOn) { v ->
                            vm.updateSettings { it.copy(keepScreenOn = v) }
                        }
                        SwitchItem(
                            title = stringResource(R.string.one_handed),
                            body = stringResource(R.string.one_handed_body),
                            checked = settings.oneHanded,
                        ) { v -> vm.updateSettings { it.copy(oneHanded = v) } }
                        run {
                            var partner by remember(settings.partnerName) { mutableStateOf(settings.partnerName) }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.partner_name)) },
                                supportingContent = {
                                    OutlinedTextField(
                                        partner,
                                        { partner = it.take(20); vm.updateSettings { s -> s.copy(partnerName = partner.trim()) } },
                                        placeholder = { Text(stringResource(R.string.partner_name_body)) },
                                        singleLine = true,
                                    )
                                },
                            )
                        }
                        SwitchItem(
                            title = stringResource(R.string.media_controls),
                            body = stringResource(R.string.media_controls_body),
                            checked = settings.mediaControls,
                        ) { v -> vm.updateSettings { it.copy(mediaControls = v) } }
                        SwitchItem(
                            title = stringResource(R.string.voice_announcements),
                            body = stringResource(R.string.voice_announcements_body),
                            checked = settings.voiceAnnouncements,
                        ) { v -> vm.updateSettings { it.copy(voiceAnnouncements = v) } }
                        SwitchItem(
                            title = stringResource(R.string.volume_keys),
                            body = stringResource(R.string.volume_keys_body),
                            checked = settings.volumeKeys,
                        ) { v -> vm.updateSettings { it.copy(volumeKeys = v) } }
                        SwitchItem(stringResource(R.string.progress_lock_setting), checked = settings.lockProgressPhotos) { v ->
                            vm.updateSettings { it.copy(lockProgressPhotos = v) }
                        }
                        SwitchItem(
                            title = stringResource(R.string.dumbbell_total),
                            body = stringResource(R.string.dumbbell_total_body),
                            checked = settings.dumbbellTotal,
                        ) { v -> vm.updateSettings { it.copy(dumbbellTotal = v) } }
                        GymProfilesSection(
                            settings = settings,
                            onSelect = { id -> vm.updateSettings { s -> s.copy(gyms = s.gyms, activeGymId = id) } },
                            onEdit = { editingGym = it },
                            onAdd = { editingGym = GymProfile(name = "") },
                        )
                        SwitchItem(
                            title = stringResource(R.string.hc_export),
                            body = when {
                                !vm.healthConnectAvailable -> stringResource(R.string.hc_unavailable)
                                settings.healthConnectExport && !hcGranted -> stringResource(R.string.hc_missing_perms)
                                else -> stringResource(R.string.hc_body)
                            },
                            checked = settings.healthConnectExport && hcGranted,
                            enabled = vm.healthConnectAvailable,
                        ) { on ->
                            if (on && !hcGranted) {
                                hcLauncher.launch(vm.healthConnectPermissions)
                            } else {
                                vm.updateSettings { it.copy(healthConnectExport = on) }
                            }
                        }
                        if (vm.healthConnectAvailable) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.hc_import_weight)) },
                                supportingContent = { Text(stringResource(R.string.readiness_connect)) },
                                modifier = Modifier.clickable { hcReadLauncher.launch(vm.healthReadPermissions) },
                            )
                        }
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.reminders)) },
                            supportingContent = { Text(reminderSummary(settings)) },
                            modifier = Modifier.clickable { showReminders = true },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.auto_backup)) },
                            supportingContent = {
                                Text(
                                    when {
                                        settings.backupFolderUri == null -> stringResource(R.string.auto_backup_off)
                                        settings.lastBackupEpochMs > 0 -> stringResource(R.string.auto_backup_last, formatDate(settings.lastBackupEpochMs))
                                        else -> stringResource(R.string.auto_backup_on)
                                    },
                                )
                            },
                            trailingContent = {
                                if (settings.backupFolderUri == null) {
                                    TextButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.action_choose_folder)) }
                                } else {
                                    TextButton(onClick = { vm.setBackupFolder(null) }) { Text(stringResource(R.string.action_disable)) }
                                }
                            },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.restore_backup)) },
                            supportingContent = { Text(stringResource(R.string.restore_backup_body)) },
                            trailingContent = { TextButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }) { Text(stringResource(R.string.action_restore)) } },
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
                        DataToolsSection(vm)
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.help)) },
                            supportingContent = { Text(stringResource(R.string.help_body)) },
                            modifier = Modifier.clickable { showHelp = true },
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.about_title)) },
                            supportingContent = { Text(stringResource(R.string.about_subtitle)) },
                            modifier = Modifier.clickable { showAbout = true },
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

/**
 * Ajuste con interruptor: toda la fila es pulsable y TalkBack la lee como
 * «<título>, interruptor, activado» en lugar de un interruptor sin nombre.
 */
@Composable
private fun SwitchItem(title: String, body: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}
