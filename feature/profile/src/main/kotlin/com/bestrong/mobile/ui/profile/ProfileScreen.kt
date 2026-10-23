package com.bestrong.mobile.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.training.GymProfile
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.security.BiometricLock
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.help.HelpDialog
import com.bestrong.mobile.ui.help.TutorialDialog
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
    val hcGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    val hcComplete by vm.healthConnectComplete.collectAsStateWithLifecycle()
    // Lectura (peso, sueño, pulso en reposo): permiso aparte y luego se importa el peso
    val hcReadLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.importHealthWeights() }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        // Basta con la sesión; si no se conceden calorías o pulso, se exporta lo demás
        val ok = vm.healthConnectRequired in granted
        vm.updateSettings { it.copy(healthConnectExport = ok) }
        vm.refreshHealthConnect()
    }
    // Pedir los permisos que faltan no debe apagar la exportación si se cancela
    val hcMoreLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.refreshHealthConnect() }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreBackup) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupFolder) }
    val askNotifications = com.bestrong.mobile.ui.components.rememberNotificationPermission()
    var showReminders by rememberSaveable { mutableStateOf(false) }
    var editingGym by rememberSaveable(stateSaver = GymSaver) { mutableStateOf<GymProfile?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) AboutDialog { showAbout = false }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    if (showHelp) HelpDialog(onDismiss = { showHelp = false }, onReplayTutorial = { showHelp = false; showTutorial = true })
    if (showTutorial) {
        TutorialDialog(
            onDone = { showTutorial = false },
            unit = settings.unit,
            weeklyGoal = settings.weeklyGoal,
            onSetup = { unit, goal, home, training -> vm.updateSettings { it.copy(unit = unit, weeklyGoal = goal, trainingGoal = training).trainingAt(home) } },
            atHome = com.bestrong.core.model.Equipment.BARBELL !in settings.activeGym.equipment,
            trainingGoal = settings.trainingGoal,
            sex = settings.sex,
            onSex = { sex -> vm.updateSettings { it.copy(sex = sex) } },
        )
    }
    editingGym?.let { g ->
        GymEditorDialog(
            gym = g,
            canDelete = settings.gyms.size > 1 && settings.gyms.any { it.id == g.id },
            onSave = { updated ->
                // Con un nombre repetido se queda abierto (sale el aviso) para cambiarlo
                if (vm.saveGym(updated)) editingGym = null
            },
            onDelete = {
                vm.deleteGym(g.id)
                editingGym = null
            },
            onDismiss = { editingGym = null },
        )
    }
    if (showReminders) {
        RemindersDialog(
            settings = settings,
            onSave = { days, h, m ->
                if (days.isNotEmpty()) askNotifications()
                vm.updateSettings { it.copy(reminderDays = days, reminderHour = h, reminderMinute = m) }
                showReminders = false
            },
            onDismiss = { showReminders = false },
        )
    }
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
            // Dos pestañas: el progreso (lo que se mira a menudo) y los ajustes (lo que se toca de vez en cuando)
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
            } else {
                item { BodyDataCard(settings, vm::updateSettings) }
                item(span = { GridItemSpan(maxLineSpan) }) { AppearanceCard(settings, vm::updateSettings) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
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
                            WarmupRestSetting(settings.warmupRestSeconds) { v -> vm.updateSettings { it.copy(warmupRestSeconds = v) } }
                            SettingsSubheader(stringResource(R.string.settings_during))
                            SwitchItem(stringResource(R.string.rest_countdown), checked = settings.restCountdown) { v ->
                                vm.updateSettings { it.copy(restCountdown = v) }
                            }
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
                                // También al salir de la pantalla o al pasar la app a segundo plano (sin perder el foco)
                                DisposableEffect(Unit) { onDispose { vm.commitPartnerName() } }
                                androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.commitPartnerName() }
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.partner_name)) },
                                    supportingContent = {
                                        // El nombre se guarda con cada letra; su historial se renombra al terminar (Hecho o al
                                        // salir del campo)
                                        OutlinedTextField(
                                            partner,
                                            { partner = it.take(20); vm.setPartnerName(partner) },
                                            placeholder = { Text(stringResource(R.string.partner_name_body)) },
                                            singleLine = true,
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { vm.commitPartnerName() }),
                                            modifier = Modifier.onFocusChanged { if (!it.isFocused) vm.commitPartnerName() },
                                        )
                                    },
                                )
                            }
                            SwitchItem(
                                title = stringResource(R.string.media_controls),
                                body = stringResource(R.string.media_controls_body),
                                checked = settings.mediaControls,
                            ) { v -> vm.updateSettings { it.copy(mediaControls = v) } }
                            if (settings.mediaControls) MusicAppPicker(settings.musicApp) { pkg -> vm.updateSettings { it.copy(musicApp = pkg) } }
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
                            val unlockTitle = stringResource(R.string.progress_unlock_title)
                            SwitchItem(stringResource(R.string.progress_lock_setting), checked = settings.lockProgressPhotos) { v ->
                                // Quitar el bloqueo pide la huella/PIN: si no, cualquiera con el móvil desbloqueado lo desactivaría
                                if (v) {
                                    vm.updateSettings { it.copy(lockProgressPhotos = true) }
                                } else {
                                    BiometricLock.authenticate(context, unlockTitle, onSuccess = { vm.updateSettings { it.copy(lockProgressPhotos = false) } })
                                }
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
                            SettingsSubheader(stringResource(R.string.settings_health))
                            SwitchItem(
                                title = stringResource(R.string.hc_export),
                                body = when {
                                    !vm.healthConnectAvailable -> stringResource(R.string.hc_unavailable)
                                    settings.healthConnectExport && !hcGranted -> stringResource(R.string.hc_missing_perms)
                                    settings.healthConnectExport && !hcComplete -> stringResource(R.string.hc_more_perms)
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
                            if (settings.healthConnectExport && hcGranted && !hcComplete) {
                                TextButton(onClick = { hcMoreLauncher.launch(vm.healthConnectPermissions) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(stringResource(R.string.hc_grant_more))
                                }
                            }
                            if (vm.healthConnectAvailable) {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.hc_import_weight)) },
                                    supportingContent = { Text(stringResource(R.string.readiness_connect)) },
                                    // Los pesos de la báscula borrados aquí no vuelven al importar; con esto, sí
                                    trailingContent = { TextButton(onClick = vm::restoreIgnoredHealthWeights) { Text(stringResource(R.string.hc_restore_hidden)) } },
                                    modifier = Modifier.clickable { hcReadLauncher.launch(vm.healthReadPermissions) },
                                )
                            }
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.reminders)) },
                                supportingContent = { Text(reminderSummary(settings)) },
                                modifier = Modifier.clickable { showReminders = true },
                            )
                            SettingsSubheader(stringResource(R.string.settings_data))
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.auto_backup)) },
                                supportingContent = {
                                    Column {
                                        AutoBackupStatus(settings)
                                        if (settings.backupFolderUri != null) {
                                            TextButton(onClick = vm::backupNow, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.notif_backup_now)) }
                                        }
                                    }
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
                            // Con copia automática: la última de la carpeta, sin buscar el archivo
                            if (settings.backupFolderUri != null) {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.restore_latest)) },
                                    trailingContent = { TextButton(onClick = vm::listAutoBackups) { Text(stringResource(R.string.action_restore)) } },
                                )
                                AutoBackupPicker(vm)
                            }
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
                            SettingsSubheader(stringResource(R.string.settings_help))
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
                                modifier = Modifier.clickable { context.startActivity(AppIntents.privacy(context)) },
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

/** Qué app de música abre el botón del entreno (solo las instaladas). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MusicAppPicker(selected: String?, onSelect: (String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apps = remember { com.bestrong.mobile.share.MusicApps.installed(context) }
    if (apps.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.music_app), style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.music_app_auto)) })
            apps.forEach { a ->
                androidx.compose.material3.FilterChip(selected = selected == a.packageName, onClick = { onSelect(a.packageName) }, label = { Text(a.label) })
            }
        }
    }
}

/** Descanso tras una serie de calentamiento: basta con cambiar los discos. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WarmupRestSetting(seconds: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.warmup_rest_setting), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 45, 60, 90, 120).forEach { s ->
                FilterChip(selected = s == seconds, onClick = { onChange(s) }, label = { Text(com.bestrong.core.util.formatDuration(s.toLong())) })
            }
        }
    }
}

/** Más días que esto sin copia (es semanal) es que algo falla: se avisa en rojo. */
private const val BACKUP_STALE_DAYS = 14L

/** Estado de la copia automática: apagada, carpeta perdida, última copia (en rojo si es vieja) o pendiente. */
@Composable
private fun AutoBackupStatus(settings: com.bestrong.mobile.data.UserSettings) {
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
private fun AutoBackupPicker(vm: ProfileViewModel) {
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

/** Selector de las dos pestañas de Perfil, en píldora: la elegida, rellena con el degradado de marca. */
@Composable
private fun ProfileTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(stringResource(R.string.profile_progress), stringResource(R.string.settings))
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
                Text(
                    label,
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Subapartado dentro del panel de Ajustes: la lista larga se escanea por bloques en vez de leerla entera. */
@Composable
private fun SettingsSubheader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
