package com.bestrong.mobile.ui.profile

import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.mobile.security.BiometricLock
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.IconTile
import com.bestrong.mobile.ui.components.SettingsDivider
import com.bestrong.mobile.ui.components.SettingsGroup
import com.bestrong.mobile.ui.components.SettingsPage
import com.bestrong.mobile.ui.components.SettingsTint

/** Cada permiso o conexión que puede usar la app en el móvil. */
internal enum class PermissionKind { NOTIFICATIONS, HEALTH_WRITE, HEALTH_READ, BATTERY, WATCH, LOCK, BACKUP }

/** Estado de un permiso: activado, en parte o desactivado. */
internal enum class PermissionStatus { ON, PARTIAL, OFF }

internal class PermissionItem(
    val kind: PermissionKind,
    val icon: ImageVector,
    val tint: Color,
    val title: Int,
    val body: Int,
    val status: PermissionStatus,
    /** El texto del botón (Permitir, Conectar, Elegir…) y lo que hace. */
    val action: Int,
    val onAction: () -> Unit,
    /** Lo que «Activar todo» puede pedir de seguido (los que abren otra pantalla del sistema no). */
    val inBatch: Boolean,
) {
    /** Si falta, se avisa con un número rojo en el menú de Ajustes. */
    val essential get() = kind == PermissionKind.NOTIFICATIONS
}

internal class PermissionsState(
    val items: List<PermissionItem>,
    val notifications: Boolean,
    val requestNotifications: () -> Unit,
    val requestHealthWrite: () -> Unit,
    val requestHealthRead: () -> Unit,
    /** Pide uno tras otro los que se pueden conceder desde la app. */
    val requestAll: () -> Unit,
) {
    val total get() = items.size
    val ready get() = items.count { it.status == PermissionStatus.ON }
    val essentialMissing get() = items.count { it.essential && it.status != PermissionStatus.ON }
}

/**
 * Lee el estado de todos los permisos y conexiones (y lo vuelve a leer al volver a la app) y prepara cómo pedir cada
 * uno.
 */
@Composable
internal fun rememberPermissionsState(vm: ProfileViewModel): PermissionsState {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hcGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    val hcComplete by vm.healthConnectComplete.collectAsStateWithLifecycle()
    val hcRead by vm.healthReadState.collectAsStateWithLifecycle()
    val watch by vm.watchConnected.collectAsStateWithLifecycle()
    // «Activar todo»: la cola de lo que queda por pedir; cada respuesta pasa al siguiente
    var queue by remember { mutableStateOf<List<PermissionKind>>(emptyList()) }
    val next: () -> Unit = { queue = queue.drop(1) }
    val tick = rememberResumeTick {
        vm.refreshHealthConnect()
        vm.refreshWatch()
    }
    val requestNotifications = rememberNotificationRequest(onResult = next)
    val (requestWrite, requestRead) = rememberHealthLaunchers(vm, onResult = next)
    val chooseFolder = rememberBackupFolderPicker(vm)
    LaunchedEffect(queue) {
        when (queue.firstOrNull()) {
            PermissionKind.NOTIFICATIONS -> requestNotifications()
            PermissionKind.HEALTH_WRITE -> requestWrite()
            PermissionKind.HEALTH_READ -> requestRead()
            else -> Unit
        }
    }
    val notificationsOn = remember(tick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val batteryOn = remember(tick) { context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true }
    val lockOn = remember(tick) { BiometricLock.isAvailable(context) }
    val writeStatus = healthWriteStatus(hcGranted, hcComplete, settings.healthConnectExport)
    val readStatus = hcRead.toStatus()
    val connectWrite = {
        // Ya concedido pero con la exportación apagada: basta con encenderla
        if (hcGranted && hcComplete) vm.updateSettings { it.copy(healthConnectExport = true) } else requestWrite()
    }
    val items = buildList {
        add(
            PermissionItem(
                PermissionKind.NOTIFICATIONS, Icons.Default.NotificationsActive, SettingsTint.Amber, R.string.perm_notif_t, R.string.perm_notif_d,
                on(notificationsOn), R.string.perm_allow, requestNotifications, inBatch = true,
            ),
        )
        if (vm.healthConnectAvailable) {
            add(
                PermissionItem(
                    PermissionKind.HEALTH_WRITE, Icons.Default.HealthAndSafety, SettingsTint.Pink, R.string.perm_hc_write_t, R.string.perm_hc_write_d,
                    writeStatus, R.string.perm_connect, connectWrite,
                    inBatch = true,
                ),
            )
            add(
                PermissionItem(
                    PermissionKind.HEALTH_READ, Icons.Default.MonitorHeart, SettingsTint.Red, R.string.perm_hc_read_t, R.string.perm_hc_read_d,
                    readStatus,
                    R.string.perm_connect, requestRead, inBatch = true,
                ),
            )
        }
        add(
            PermissionItem(
                PermissionKind.BATTERY, Icons.Default.BatteryChargingFull, SettingsTint.Green, R.string.perm_battery_t, R.string.perm_battery_d,
                on(batteryOn), R.string.perm_open, { openBatterySettings(context) }, inBatch = false,
            ),
        )
        add(
            PermissionItem(
                PermissionKind.WATCH, Icons.Default.Watch, SettingsTint.Blue, R.string.perm_watch_t,
                if (watch == true) R.string.perm_watch_d_on else R.string.perm_watch_d_off,
                on(watch == true), R.string.perm_check, { vm.refreshWatch(); vm.syncWatchNow() }, inBatch = false,
            ),
        )
        add(
            PermissionItem(
                PermissionKind.LOCK, Icons.Default.Fingerprint, SettingsTint.Slate, R.string.perm_lock_t, R.string.perm_lock_d,
                on(lockOn), R.string.perm_open, { openSecuritySettings(context) }, inBatch = false,
            ),
        )
        add(
            PermissionItem(
                PermissionKind.BACKUP, Icons.Default.Folder, SettingsTint.Teal, R.string.perm_backup_t, R.string.perm_backup_d,
                on(settings.backupFolderUri != null), R.string.perm_choose, chooseFolder, inBatch = false,
            ),
        )
    }
    return PermissionsState(
        items = items,
        notifications = notificationsOn,
        requestNotifications = requestNotifications,
        requestHealthWrite = requestWrite,
        requestHealthRead = requestRead,
        requestAll = { queue = items.filter { it.inBatch && it.status != PermissionStatus.ON }.map { it.kind } },
    )
}

private fun on(b: Boolean) = if (b) PermissionStatus.ON else PermissionStatus.OFF

private fun healthWriteStatus(granted: Boolean, complete: Boolean, exporting: Boolean) = when {
    granted && complete && exporting -> PermissionStatus.ON
    granted -> PermissionStatus.PARTIAL
    else -> PermissionStatus.OFF
}

private fun GrantState.toStatus() = when (this) {
    GrantState.ALL -> PermissionStatus.ON
    GrantState.SOME -> PermissionStatus.PARTIAL
    GrantState.NONE -> PermissionStatus.OFF
}

/** «Permisos y conexiones»: el estado de todo de un vistazo y un botón para activarlo. */
@Composable
internal fun PermissionsPage(permissions: PermissionsState, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.perm_title), onBack, stringResource(R.string.perm_subtitle)) {
        item { PermissionsSummary(permissions) }
        item {
            SettingsGroup(null, footer = stringResource(R.string.perm_watch_note)) {
                permissions.items.forEachIndexed { i, item ->
                    if (i > 0) SettingsDivider()
                    PermissionRow(item)
                }
            }
        }
    }
}

/** Tarjeta de arriba: cuántos hay listos, una barra y «Activar todo». */
@Composable
private fun PermissionsSummary(state: PermissionsState) {
    val done = state.ready == state.total
    val pending = state.items.any { it.inBatch && it.status != PermissionStatus.ON }
    com.bestrong.mobile.ui.components.HeroCard {
        Text(
            if (done) stringResource(R.string.perm_all_ready) else stringResource(R.string.perm_ready, state.ready, state.total),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
        LinearProgressIndicator(
            progress = { state.ready / state.total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.onPrimary,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
            drawStopIndicator = {},
        )
        Text(stringResource(R.string.perm_enable_all_body), style = MaterialTheme.typography.bodyMedium)
        if (pending) {
            Button(
                onClick = state.requestAll,
                modifier = Modifier.padding(top = 6.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { Text(stringResource(R.string.perm_enable_all), fontWeight = FontWeight.Bold) }
        }
    }
}

/** Fila de un permiso: icono, qué es y para qué sirve, su estado y el botón para activarlo. */
@Composable
internal fun PermissionRow(item: PermissionItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(item.icon, item.tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(item.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(item.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill(item.status)
                if (item.status != PermissionStatus.ON) {
                    FilledTonalButton(onClick = item.onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                        Text(stringResource(item.action), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: PermissionStatus) {
    val status2 = com.bestrong.mobile.ui.theme.statusColors
    val (color, label) = when (status) {
        PermissionStatus.ON -> status2.good to stringResource(R.string.perm_granted)
        PermissionStatus.PARTIAL -> status2.warn to stringResource(R.string.perm_partial)
        PermissionStatus.OFF -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.perm_off)
    }
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status == PermissionStatus.ON) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = color)
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
    }
}
