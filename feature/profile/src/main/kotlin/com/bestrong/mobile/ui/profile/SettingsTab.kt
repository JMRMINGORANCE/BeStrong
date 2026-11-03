package com.bestrong.mobile.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.training.GymProfile
import com.bestrong.core.util.VibrationStyle
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.data.AppFont
import com.bestrong.mobile.data.Appearance
import com.bestrong.mobile.security.BiometricLock
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SettingsChoice
import com.bestrong.mobile.ui.components.SettingsDivider
import com.bestrong.mobile.ui.components.SettingsGroup
import com.bestrong.mobile.ui.components.SettingsNavRow
import com.bestrong.mobile.ui.components.SettingsPage
import com.bestrong.mobile.ui.components.SettingsRow
import com.bestrong.mobile.ui.components.SettingsSwitchRow
import com.bestrong.mobile.ui.components.SettingsTint
import com.bestrong.mobile.ui.help.HelpDialog
import com.bestrong.mobile.ui.help.TutorialDialog

/**
 * Las pantallas en que se dividen los Ajustes: cuatro apartados (entreno, apariencia, datos y conexiones) y uno
 * aparte con lo que casi nadie toca. El nombre es el que usa la búsqueda universal para abrir cada una.
 */
private enum class SettingsPageId { TRAINING, APPEARANCE, DATA, CONNECTIONS, ADVANCED }

/**
 * Pestaña Ajustes de Perfil: un menú corto (como los ajustes del sistema) y cada apartado en su propia pantalla.
 * Lo raro va en «Avanzado» para que lo habitual se encuentre de un vistazo.
 */
@Composable
fun SettingsTab(vm: ProfileViewModel, modifier: Modifier = Modifier, openPage: String? = null, onPageOpened: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<SettingsPageId?>(null) }
    // Desde la búsqueda universal: directo a esa página
    androidx.compose.runtime.LaunchedEffect(openPage) {
        openPage?.let { name -> SettingsPageId.entries.firstOrNull { it.name == name }?.let { page = it } }
        if (openPage != null) onPageOpened()
    }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showTutorial by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberPermissionsState(vm)

    if (showAbout) AboutDialog { showAbout = false }
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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SettingsGroup(null) {
            SettingsNavRow(stringResource(R.string.s_training_title), Icons.Default.FitnessCenter, SettingsTint.Blue, { page = SettingsPageId.TRAINING }, stringResource(R.string.s_training_summary))
            SettingsDivider()
            SettingsNavRow(stringResource(R.string.appearance), Icons.Default.Palette, SettingsTint.Purple, { page = SettingsPageId.APPEARANCE }, stringResource(R.string.s_appearance_summary))
            SettingsDivider()
            SettingsNavRow(stringResource(R.string.s_data_title), Icons.Default.Backup, SettingsTint.Teal, { page = SettingsPageId.DATA }, stringResource(R.string.s_data_summary))
            SettingsDivider()
            SettingsNavRow(
                stringResource(R.string.perm_title),
                Icons.Default.Shield,
                SettingsTint.Red,
                { page = SettingsPageId.CONNECTIONS },
                summary = if (permissions.ready == permissions.total) stringResource(R.string.perm_all_ready) else stringResource(R.string.perm_ready, permissions.ready, permissions.total),
                badge = permissions.essentialMissing.takeIf { it > 0 }?.toString(),
            )
        }
        SettingsGroup(null) {
            SettingsNavRow(stringResource(R.string.s_advanced_title), Icons.Default.Tune, SettingsTint.Slate, { page = SettingsPageId.ADVANCED }, stringResource(R.string.s_advanced_summary))
        }
        SettingsGroup(stringResource(R.string.settings_help)) {
            SettingsNavRow(stringResource(R.string.help), Icons.AutoMirrored.Filled.HelpOutline, SettingsTint.Teal, { showHelp = true }, stringResource(R.string.help_body))
            SettingsDivider()
            SettingsNavRow(stringResource(R.string.about_title), Icons.Default.Info, SettingsTint.Slate, { showAbout = true }, stringResource(R.string.about_subtitle))
        }
    }

    page?.let { SettingsPageHost(it, vm, permissions) { page = null } }
}

/** La pantalla abierta de Ajustes. */
@Composable
private fun SettingsPageHost(page: SettingsPageId, vm: ProfileViewModel, permissions: PermissionsState, close: () -> Unit) {
    when (page) {
        SettingsPageId.TRAINING -> TrainingPage(vm, close)
        SettingsPageId.APPEARANCE -> AppearancePage(vm, close)
        SettingsPageId.DATA -> DataPage(vm, permissions, close)
        SettingsPageId.CONNECTIONS -> ConnectionsPage(vm, permissions, close)
        SettingsPageId.ADVANCED -> AdvancedPage(vm, close)
    }
}

/** Tu cuerpo, progresión, unidad de peso, gimnasios y lo que pasa durante el entreno. */
@Composable
private fun TrainingPage(vm: ProfileViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingGym by rememberSaveable(stateSaver = GymSaver) { mutableStateOf<GymProfile?>(null) }
    editingGym?.let { g ->
        GymEditorDialog(
            gym = g,
            canDelete = settings.gyms.size > 1 && settings.gyms.any { it.id == g.id },
            // Con un nombre repetido se queda abierto (sale el aviso) para cambiarlo
            onSave = { updated -> if (vm.saveGym(updated)) editingGym = null },
            onDelete = {
                vm.deleteGym(g.id)
                editingGym = null
            },
            onDismiss = { editingGym = null },
        )
    }
    SettingsPage(stringResource(R.string.s_training_title), onBack) {
        // Ya lleva su título y su explicación
        item { BodyDataCard(settings, vm::updateSettings) }
        item {
            SettingsGroup(stringResource(R.string.sg_progress)) {
                SettingsSwitchRow(
                    stringResource(R.string.progression_setting),
                    settings.progressionEnabled,
                    { v -> vm.updateSettings { it.copy(progressionEnabled = v) } },
                    summary = stringResource(R.string.progression_setting_body),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = SettingsTint.Green,
                )
                SettingsDivider()
                SettingsChoice(
                    stringResource(R.string.weight_unit),
                    WeightUnit.entries.map { it to it.label },
                    settings.unit,
                    { u -> vm.updateSettings { it.copy(unit = u) } },
                    icon = Icons.Default.FitnessCenter,
                    tint = SettingsTint.Orange,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    stringResource(R.string.dumbbell_total),
                    settings.dumbbellTotal,
                    { v -> vm.updateSettings { it.copy(dumbbellTotal = v) } },
                    summary = stringResource(R.string.dumbbell_total_body),
                    icon = Icons.Default.Calculate,
                    tint = SettingsTint.Purple,
                )
            }
        }
        item {
            SettingsGroup(null) {
                GymProfilesSection(
                    settings = settings,
                    onSelect = { id -> vm.updateSettings { s -> s.copy(gyms = s.gyms, activeGymId = id) } },
                    onEdit = { editingGym = it },
                    onAdd = { editingGym = GymProfile(name = "") },
                )
            }
        }
        item { DuringGroup(settings, vm::updateSettings) }
    }
}

/** Descanso, vibración, pantalla y voz durante el entreno (lo de siempre; lo fino, en Avanzado). */
@Composable
private fun DuringGroup(settings: com.bestrong.mobile.data.UserSettings, update: ((com.bestrong.mobile.data.UserSettings) -> com.bestrong.mobile.data.UserSettings) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_during)) {
        SettingsSwitchRow(stringResource(R.string.rest_countdown), settings.restCountdown, { v -> update { it.copy(restCountdown = v) } }, icon = Icons.Default.Timer, tint = SettingsTint.Green)
        SettingsDivider()
        SettingsSwitchRow(stringResource(R.string.vibrate_rest), settings.vibrateOnRestEnd, { v -> update { it.copy(vibrateOnRestEnd = v) } }, icon = Icons.Default.Vibration, tint = SettingsTint.Purple)
        SettingsDivider()
        SettingsSwitchRow(
            stringResource(R.string.next_exercise_setting),
            settings.nextExerciseCard,
            { v -> update { it.copy(nextExerciseCard = v) } },
            summary = stringResource(R.string.next_exercise_setting_body),
            icon = Icons.Default.SkipNext,
            tint = SettingsTint.Orange,
        )
        SettingsDivider()
        SettingsSwitchRow(stringResource(R.string.keep_screen_on), settings.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } }, icon = Icons.Default.ScreenLockPortrait, tint = SettingsTint.Amber)
        SettingsDivider()
        SettingsSwitchRow(
            stringResource(R.string.one_handed),
            settings.focusByDefault,
            { v -> update { it.copy(focusByDefault = v) } },
            summary = stringResource(R.string.one_handed_body),
            icon = Icons.Default.CenterFocusStrong,
            tint = SettingsTint.Orange,
        )
        SettingsDivider()
        SettingsSwitchRow(
            stringResource(R.string.voice_announcements),
            settings.voiceAnnouncements,
            { v -> update { it.copy(voiceAnnouncements = v) } },
            summary = stringResource(R.string.voice_announcements_body),
            icon = Icons.Default.RecordVoiceOver,
            tint = SettingsTint.Teal,
        )
    }
}

/** Lo que casi nadie toca: calentamiento, patrón de vibración, botones de volumen, tabla de series, unidad del cuerpo y más datos. */
@Composable
private fun AdvancedPage(vm: ProfileViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun update(block: (com.bestrong.mobile.data.UserSettings) -> com.bestrong.mobile.data.UserSettings) = vm.updateSettings(block)
    SettingsPage(stringResource(R.string.s_advanced_title), onBack, stringResource(R.string.s_advanced_summary)) {
        item {
            SettingsGroup(stringResource(R.string.settings_during)) {
                SettingsChoice(
                    stringResource(R.string.warmup_rest_setting),
                    listOf(30, 45, 60, 90, 120).map { it to com.bestrong.core.util.formatDuration(it.toLong()) },
                    settings.warmupRestSeconds,
                    { v -> update { it.copy(warmupRestSeconds = v) } },
                    icon = Icons.Default.Timer,
                    tint = SettingsTint.Amber,
                )
                SettingsDivider()
                SettingsChoice(
                    stringResource(R.string.rest_vibration),
                    VibrationStyle.entries.map { it to stringResource(it.labelRes()) },
                    settings.restVibration,
                    { v ->
                        update { it.copy(restVibration = v) }
                        vibratePattern(context, v.pattern)
                    },
                    summary = stringResource(R.string.rest_vibration_body),
                    extra = { TextButton(onClick = { vibratePattern(context, settings.restVibration.pattern) }) { Text(stringResource(R.string.vib_test)) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    stringResource(R.string.volume_keys),
                    settings.volumeKeys,
                    { v -> update { it.copy(volumeKeys = v) } },
                    summary = stringResource(R.string.volume_keys_body),
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    tint = SettingsTint.Blue,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.sg_table)) {
                SettingsSwitchRow(
                    stringResource(R.string.col_previous_setting),
                    settings.showPreviousColumn,
                    { v -> update { it.copy(showPreviousColumn = v) } },
                    summary = stringResource(R.string.col_previous_body),
                    icon = Icons.Default.TableRows,
                    tint = SettingsTint.Blue,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    stringResource(R.string.col_comparison_setting),
                    settings.showComparison,
                    { v -> update { it.copy(showComparison = v) } },
                    summary = stringResource(R.string.col_comparison_body),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = SettingsTint.Green,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.sg_weights)) {
                SettingsChoice(
                    stringResource(R.string.body_unit),
                    listOf<Pair<WeightUnit?, String>>(null to stringResource(R.string.body_unit_same)) + WeightUnit.entries.map { it to it.label },
                    settings.bodyUnit,
                    { u -> update { it.copy(bodyUnit = u) } },
                    icon = Icons.Default.MonitorWeight,
                    tint = SettingsTint.Teal,
                )
            }
        }
        item {
            SettingsGroup(stringResource(R.string.sg_more_data)) {
                Column(Modifier.padding(vertical = 8.dp)) { DataToolsSection(vm) }
            }
        }
    }
}

/** Tema, color de acento, AMOLED, contraste, daltonismo y letra. */
@Composable
private fun AppearancePage(vm: ProfileViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val a = settings.appearance
    fun setA(block: (Appearance) -> Appearance) = vm.updateSettings { it.copy(appearance = block(it.appearance)) }
    SettingsPage(stringResource(R.string.appearance), onBack) {
        // Estilo visual de toda la app y del reloj: miniaturas para comparar
        item {
            SettingsGroup(stringResource(R.string.style_title), footer = stringResource(R.string.style_footer)) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(12.dp)) {
                    val columns = (maxWidth / com.bestrong.mobile.ui.components.STYLE_CARD_MIN_WIDTH).toInt().coerceIn(2, 3)
                    com.bestrong.mobile.ui.components.StyleGrid(a.style, { st -> setA { it.copy(style = st) } }, columns = columns)
                }
            }
        }
        // Negro puro solo tiene sentido en el estilo oscuro (con el claro el grupo quedaría vacío)
        if (a.style.dark) {
            item {
                SettingsGroup(stringResource(R.string.sg_theme)) {
                    SettingsSwitchRow(
                        stringResource(R.string.amoled),
                        a.amoled,
                        { v -> setA { it.copy(amoled = v) } },
                        summary = stringResource(R.string.amoled_body),
                        icon = Icons.Default.Brightness2,
                        tint = SettingsTint.Slate,
                    )
                }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.sg_accessibility)) {
                SettingsSwitchRow(
                    stringResource(R.string.high_contrast),
                    a.highContrast,
                    { v -> setA { it.copy(highContrast = v) } },
                    summary = stringResource(R.string.high_contrast_body),
                    icon = Icons.Default.Contrast,
                    tint = SettingsTint.Slate,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    stringResource(R.string.colorblind),
                    a.colorblind,
                    { v -> setA { it.copy(colorblind = v) } },
                    summary = stringResource(R.string.colorblind_body),
                    icon = Icons.Default.Visibility,
                    tint = SettingsTint.Blue,
                )
                SettingsDivider()
                SettingsChoice(
                    stringResource(R.string.font),
                    AppFont.entries.map { it to stringResource(it.labelRes()) },
                    a.font,
                    { f -> setA { it.copy(font = f) } },
                    icon = Icons.Default.TextFields,
                    tint = SettingsTint.Teal,
                )
            }
        }
    }
}

/** Copias automáticas, restaurar, CSV, Health Connect, reloj y privacidad (Excel, copia cifrada y borrar, en Avanzado). */
@Composable
private fun DataPage(vm: ProfileViewModel, permissions: PermissionsState, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(vm::exportCsv) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreBackup) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupFolder) }
    AutoBackupPicker(vm)
    SettingsPage(stringResource(R.string.s_data_title), onBack) {
        item {
            SettingsGroup(stringResource(R.string.sg_backup)) {
                SettingsRow(
                    stringResource(R.string.auto_backup),
                    icon = Icons.Default.Backup,
                    tint = SettingsTint.Blue,
                    trailing = {
                        if (settings.backupFolderUri == null) {
                            TextButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.action_choose_folder)) }
                        } else {
                            TextButton(onClick = { vm.setBackupFolder(null) }) { Text(stringResource(R.string.action_disable)) }
                        }
                    },
                )
                Column(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp)) {
                    AutoBackupStatus(settings)
                    if (settings.backupFolderUri != null) {
                        TextButton(onClick = vm::backupNow, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(stringResource(R.string.notif_backup_now)) }
                    }
                }
                SettingsDivider()
                SettingsRow(
                    stringResource(R.string.restore_backup),
                    summary = stringResource(R.string.restore_backup_body),
                    icon = Icons.Default.Restore,
                    tint = SettingsTint.Green,
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
                )
                // Con copia automática: la última de la carpeta, sin buscar el archivo
                if (settings.backupFolderUri != null) {
                    SettingsDivider()
                    SettingsRow(stringResource(R.string.restore_latest), icon = Icons.Default.Restore, tint = SettingsTint.Teal, onClick = vm::listAutoBackups)
                }
            }
        }
        item {
            SettingsGroup(stringResource(R.string.sg_export)) {
                SettingsRow(
                    stringResource(R.string.csv_export),
                    summary = stringResource(R.string.csv_export_body),
                    icon = Icons.Default.FileUpload,
                    tint = SettingsTint.Orange,
                    onClick = { exportLauncher.launch("bestrong-${java.time.LocalDate.now()}.csv") },
                )
                SettingsDivider()
                SettingsRow(
                    stringResource(R.string.csv_import),
                    summary = stringResource(R.string.csv_import_body),
                    icon = Icons.Default.FileDownload,
                    tint = SettingsTint.Purple,
                    onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                )
            }
        }
        item { HealthConnectGroup(vm, settings, permissions) }
        item {
            SettingsGroup(stringResource(R.string.sg_watch)) {
                SettingsRow(
                    stringResource(R.string.watch_sync),
                    summary = stringResource(R.string.watch_sync_body),
                    icon = Icons.Default.Sync,
                    tint = SettingsTint.Blue,
                    onClick = vm::syncWatchNow,
                    trailing = { TextButton(onClick = vm::syncWatchNow) { Text(stringResource(R.string.action_sync)) } },
                )
            }
        }
        item { PrivacyGroup(vm, settings) }
    }
}

/** Exportar a Health Connect e importar de allí el peso. */
@Composable
private fun HealthConnectGroup(vm: ProfileViewModel, settings: com.bestrong.mobile.data.UserSettings, permissions: PermissionsState) {
    val hcGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    val hcComplete by vm.healthConnectComplete.collectAsStateWithLifecycle()
    SettingsGroup(stringResource(R.string.sg_health_connect)) {
        SettingsSwitchRow(
            stringResource(R.string.hc_export),
            settings.healthConnectExport && hcGranted,
            { on ->
                if (on && !hcGranted) {
                    permissions.requestHealthWrite()
                } else {
                    vm.updateSettings { it.copy(healthConnectExport = on) }
                }
            },
            summary = when {
                !vm.healthConnectAvailable -> stringResource(R.string.hc_unavailable)
                settings.healthConnectExport && !hcGranted -> stringResource(R.string.hc_missing_perms)
                settings.healthConnectExport && !hcComplete -> stringResource(R.string.hc_more_perms)
                else -> stringResource(R.string.hc_body)
            },
            icon = Icons.Default.HealthAndSafety,
            tint = SettingsTint.Pink,
            enabled = vm.healthConnectAvailable,
        )
        if (settings.healthConnectExport && hcGranted && !hcComplete) {
            TextButton(onClick = permissions.requestHealthWrite, modifier = Modifier.padding(start = 60.dp)) { Text(stringResource(R.string.hc_grant_more)) }
        }
        if (vm.healthConnectAvailable) {
            SettingsDivider()
            SettingsRow(
                stringResource(R.string.hc_import_weight),
                summary = stringResource(R.string.hc_import_weight_body),
                icon = Icons.Default.FileDownload,
                tint = SettingsTint.Teal,
                onClick = permissions.requestHealthRead,
                // Los pesos de la báscula borrados aquí no vuelven al importar; con esto, sí
                trailing = { TextButton(onClick = vm::restoreIgnoredHealthWeights) { Text(stringResource(R.string.hc_restore_hidden)) } },
            )
        }
    }
}

/** Bloqueo de las fotos de progreso y política de privacidad. */
@Composable
private fun PrivacyGroup(vm: ProfileViewModel, settings: com.bestrong.mobile.data.UserSettings) {
    val context = LocalContext.current
    val unlockTitle = stringResource(R.string.progress_unlock_title)
    SettingsGroup(stringResource(R.string.s_privacy_title)) {
        SettingsSwitchRow(
            stringResource(R.string.progress_lock_setting),
            settings.lockProgressPhotos,
            { v ->
                // Quitar el bloqueo pide la huella/PIN: si no, cualquiera con el móvil desbloqueado lo desactivaría
                if (v) {
                    vm.updateSettings { it.copy(lockProgressPhotos = true) }
                } else {
                    BiometricLock.authenticate(context, unlockTitle, onSuccess = { vm.updateSettings { it.copy(lockProgressPhotos = false) } })
                }
            },
            summary = stringResource(R.string.perm_lock_d),
            icon = Icons.Default.Lock,
            tint = SettingsTint.Slate,
        )
        SettingsDivider()
        SettingsNavRow(stringResource(R.string.privacy), Icons.Default.Policy, SettingsTint.Teal, { context.startActivity(AppIntents.privacy(context)) }, stringResource(R.string.privacy_body))
    }
}

/** Permisos y conexiones (con «Activar todo») y los recordatorios de entreno. */
@Composable
private fun ConnectionsPage(vm: ProfileViewModel, permissions: PermissionsState, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showReminders by rememberSaveable { mutableStateOf(false) }
    if (showReminders) {
        RemindersDialog(
            settings = settings,
            onSave = { days, h, m ->
                if (days.isNotEmpty() && !permissions.notifications) permissions.requestNotifications()
                vm.updateSettings { it.copy(reminderDays = days, reminderHour = h, reminderMinute = m) }
                showReminders = false
            },
            onDismiss = { showReminders = false },
        )
    }
    PermissionsPage(permissions, onBack) {
        item {
            SettingsGroup(stringResource(R.string.s_notif_title)) {
                SettingsNavRow(stringResource(R.string.reminders), Icons.Default.Alarm, SettingsTint.Amber, { showReminders = true }, reminderSummary(settings))
            }
        }
    }
}

/** Abre la lista de apps sin optimización de batería (o, si el móvil no la tiene, la ficha de la app). */
internal fun openBatterySettings(context: android.content.Context) {
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(list) }.onFailure { runCatching { context.startActivity(details) } }
}

/** Ajustes de seguridad del sistema (para poner un bloqueo de pantalla). */
internal fun openSecuritySettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Ajustes de notificaciones de la app (cuando ya se denegaron y el sistema no vuelve a preguntar). */
internal fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

internal fun vibratePattern(context: android.content.Context, pattern: LongArray) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.os.Vibrator::class.java)
    }
    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
}

/** Permiso de notificaciones: al denegarlo para siempre, el sistema ya no pregunta y se abren sus ajustes. */
@Composable
internal fun rememberNotificationRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotificationSettings(context)
        onResult()
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
    }
}

/**
 * Lanzadores de Health Connect (escritura y lectura), con lo que pasa al volver.
 *
 * Health Connect deja de mostrar su diálogo tras denegar dos veces (o si lo bloquea el sistema) y devuelve al
 * instante sin conceder nada: parecía que el botón no hacía nada. En ese caso se abren directamente los permisos de
 * BeStrong dentro de Health Connect, donde se pueden activar a mano.
 */
@Composable
internal fun rememberHealthLaunchers(vm: ProfileViewModel, onResult: () -> Unit): Pair<() -> Unit, () -> Unit> {
    val context = LocalContext.current
    var launchedAt by remember { mutableLongStateOf(0L) }
    fun silentlyRefused(granted: Set<String>, wanted: Set<String>) =
        granted.none { it in wanted } && SystemClock.elapsedRealtime() - launchedAt < SILENT_REFUSAL_MS
    val write = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        // Basta con la sesión; si no se conceden calorías o pulso, se exporta lo demás
        if (vm.healthConnectRequired in granted) vm.updateSettings { it.copy(healthConnectExport = true) }
        if (silentlyRefused(granted, vm.healthConnectPermissions)) openHealthConnectPermissions(context)
        vm.refreshHealthConnect()
        onResult()
    }
    val read = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (silentlyRefused(granted, vm.healthReadPermissions)) openHealthConnectPermissions(context)
        vm.importHealthWeights()
        vm.refreshHealthConnect()
        onResult()
    }
    fun launch(block: () -> Unit) {
        launchedAt = SystemClock.elapsedRealtime()
        // Si ni siquiera se puede abrir el diálogo, a los ajustes de Health Connect
        if (runCatching(block).isFailure) openHealthConnectPermissions(context)
    }
    return { launch { write.launch(vm.healthConnectPermissions) } } to { launch { read.launch(vm.healthReadPermissions) } }
}

/** Menos que esto entre pedir y recibir la respuesta: no llegó a verse ningún diálogo. */
private const val SILENT_REFUSAL_MS = 900L

/** Abre los permisos de BeStrong en Health Connect (o Health Connect sin más si el sistema no deja ir tan directo). */
internal fun openHealthConnectPermissions(context: Context) {
    val pkg = context.packageName
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, pkg))
        }
        add(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        add(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
    }
    val opened = intents.any { intent -> runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
    if (opened) Toast.makeText(context, R.string.hc_enable_in_settings, Toast.LENGTH_LONG).show()
}

@Composable
internal fun rememberBackupFolderPicker(vm: ProfileViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::setBackupFolder) }
    return { launcher.launch(null) }
}

/** Un contador que sube cada vez que se vuelve a la app (tras conceder algo en los ajustes del sistema). */
@Composable
internal fun rememberResumeTick(onResume: () -> Unit): Int {
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        tick++
        onResume()
        onPauseOrDispose {}
    }
    return tick
}

private fun VibrationStyle.labelRes() = when (this) {
    VibrationStyle.SHORT -> R.string.vib_short
    VibrationStyle.DOUBLE -> R.string.vib_double
    VibrationStyle.TRIPLE -> R.string.vib_triple
    VibrationStyle.LONG -> R.string.vib_long
    VibrationStyle.HEARTBEAT -> R.string.vib_heartbeat
    VibrationStyle.STRONG -> R.string.vib_strong
}

private fun AppFont.labelRes() = when (this) {
    AppFont.SYSTEM -> R.string.font_system
    AppFont.LEGIBLE -> R.string.font_legible
    AppFont.DYSLEXIA -> R.string.font_dyslexia
}
