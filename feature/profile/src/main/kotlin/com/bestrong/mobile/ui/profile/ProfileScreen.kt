package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SettingsDivider
import com.bestrong.mobile.ui.components.SettingsGroup
import com.bestrong.mobile.ui.components.SettingsNavRow
import com.bestrong.mobile.ui.components.formatDate

/** Lo que se abre dentro de Tú, encima de la lista. */
private enum class YouPage { RECORDS, SETTINGS }

/**
 * Tú (se abre desde el avatar de cualquier pestaña): quién eres en dos líneas, tus cifras del cuerpo y una lista
 * corta con Récords e hitos, Medidas, Ajustes y Comunidad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onOpenMeasurements: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    /** Página de Ajustes a abrir al llegar (nombre de la página, desde la búsqueda universal). */
    openSettingsPage: String? = null,
    onSettingsPageOpened: () -> Unit = {},
) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Comunidad (ideas y avisos) y, encima, el buzón abierto en «Proponer» (0) o «Mis propuestas» (1); -1, cerrado
    var showCommunity by rememberSaveable { mutableStateOf(false) }
    var proposalsTab by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    if (showCommunity) CommunityDialog(onPropose = { proposalsTab = 0 }, onMine = { proposalsTab = 1 }) { showCommunity = false }
    if (proposalsTab >= 0) ProposalsDialog(initialTab = proposalsTab) { proposalsTab = -1 }
    // Cada vez que se vuelve a la pantalla (p. ej. tras cambiar permisos en Health Connect)
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshHealthConnect()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var page by rememberSaveable { mutableStateOf<YouPage?>(null) }
    // Desde la búsqueda: a Ajustes (la página la abre [SettingsTab])
    LaunchedEffect(openSettingsPage) { if (openSettingsPage != null) page = YouPage.SETTINGS }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {},
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { if (page != null) page = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                YouPage.RECORDS -> item {
                    val advanced by vm.advanced.collectAsStateWithLifecycle()
                    RecordsCard(stats.topOneRepMax, advanced, vm::exerciseOf, onOpenExercise)
                }
                YouPage.SETTINGS -> item {
                    Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        SettingsTab(vm, openPage = openSettingsPage, onPageOpened = onSettingsPageOpened)
                    }
                }
                null -> {
                    item { YouHeader(stats) }
                    item { BodyTiles(measurements) }
                    item {
                        YouMenu(
                            onRecords = { page = YouPage.RECORDS },
                            onMeasurements = onOpenMeasurements,
                            onSettings = { page = YouPage.SETTINGS },
                            onCommunity = { showCommunity = true },
                        )
                    }
                }
            }
        }
    }
}

/** Avatar, «Tú» y la racha con el total de entrenos. */
@Composable
private fun YouHeader(stats: ProfileStats) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, Modifier.size(28.dp))
        }
        Column {
            Text(stringResource(R.string.nav_you), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            if (stats.loaded) {
                Text(
                    stringResource(R.string.enf_you_streak, stats.weeklyStreak, stats.totalWorkouts),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = com.bestrong.mobile.ui.theme.FigureFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** La lista de Tú: cada fila abre lo suyo. */
@Composable
private fun YouMenu(onRecords: () -> Unit, onMeasurements: () -> Unit, onSettings: () -> Unit, onCommunity: () -> Unit) {
    val none = androidx.compose.ui.graphics.Color.Unspecified
    SettingsGroup(null) {
        SettingsNavRow(stringResource(R.string.records_milestones), Icons.Default.EmojiEvents, none, onRecords, stringResource(R.string.enf_you_records_sub))
        SettingsDivider()
        SettingsNavRow(stringResource(R.string.measurements), Icons.Default.Straighten, none, onMeasurements, stringResource(R.string.enf_you_measures_sub))
        SettingsDivider()
        SettingsNavRow(stringResource(R.string.settings), Icons.Default.Settings, none, onSettings, stringResource(R.string.enf_you_settings_sub))
        SettingsDivider()
        SettingsNavRow(stringResource(R.string.community_tab), Icons.Default.Groups, none, onCommunity, stringResource(R.string.community_row_body))
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
