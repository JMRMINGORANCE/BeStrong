package com.bestrong.mobile.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.mobile.shared.R

/** Periodo de exportación, Excel, copia cifrada y borrado selectivo. */
@Composable
fun DataToolsSection(vm: ProfileViewModel) {
    val range by vm.exportRange.collectAsStateWithLifecycle()
    val pending by vm.pendingEncrypted.collectAsStateWithLifecycle()
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let(vm::exportXlsx)
    }
    val encLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportEncrypted(uri, password.toCharArray())
        password = ""
    }

    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.data_range), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        listOf(ExportRange.ALL to R.string.range_all, ExportRange.YEAR to R.string.range_year, ExportRange.DAYS_90 to R.string.range_90, ExportRange.DAYS_30 to R.string.range_30).forEach { (r, l) ->
            FilterChip(selected = range == r, onClick = { vm.exportRange.value = r }, label = { Text(stringResource(l)) })
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.export_excel)) },
        supportingContent = { Text(stringResource(R.string.export_excel_body)) },
        trailingContent = { TextButton(onClick = { xlsxLauncher.launch("bestrong-${java.time.LocalDate.now()}.xlsx") }) { Text(stringResource(R.string.action_export)) } },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.export_encrypted)) },
        supportingContent = { Text(stringResource(R.string.export_encrypted_body)) },
        trailingContent = { TextButton(onClick = { askPassword = true }) { Text(stringResource(R.string.action_export)) } },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.delete_data), color = MaterialTheme.colorScheme.error) },
        supportingContent = { Text(stringResource(R.string.delete_data_body)) },
        modifier = Modifier.clickable { deleting = true },
    )

    if (askPassword) {
        AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text(stringResource(R.string.export_encrypted)) },
            text = {
                // Se escribe dos veces: con una errata la copia sería irrecuperable
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasswordField(password, stringResource(R.string.password_hint)) { password = it }
                    PasswordField(repeated, stringResource(R.string.password_repeat)) { repeated = it }
                    if (repeated.isNotEmpty() && repeated != password) {
                        Text(stringResource(R.string.password_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = password.length >= MIN_PASSWORD && repeated == password, onClick = {
                    repeated = ""
                    askPassword = false
                    encLauncher.launch("bestrong-${java.time.LocalDate.now()}.bestrong")
                }) { Text(stringResource(R.string.action_export)) }
            },
            dismissButton = { TextButton(onClick = { askPassword = false; password = ""; repeated = "" }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (pending != null) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.pendingEncrypted.value = null },
            title = { Text(stringResource(R.string.restore_backup)) },
            text = { PasswordField(pw, stringResource(R.string.password_restore)) { pw = it } },
            confirmButton = { TextButton(onClick = { vm.restoreEncrypted(pw.toCharArray()) }) { Text(stringResource(R.string.action_restore)) } },
            dismissButton = { TextButton(onClick = { vm.pendingEncrypted.value = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    RestorePreviewDialog(vm)
    TrashItem(vm)
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_data_body), style = MaterialTheme.typography.bodySmall)
                    listOf(
                        DeleteKind.OLD_HISTORY to R.string.delete_old_history,
                        DeleteKind.MEASUREMENTS to R.string.delete_measurements,
                        DeleteKind.PHOTOS to R.string.delete_photos,
                        DeleteKind.ALL_HISTORY to R.string.delete_all_history,
                    ).forEach { (k, l) ->
                        TextButton(onClick = { vm.deleteData(k); deleting = false }) { Text(stringResource(l), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PasswordField(value: String, hint: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(hint, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value,
            onChange,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }
}

/** Mínimo para la copia cifrada: el archivo puede atacarse sin conexión. */
private const val MIN_PASSWORD = 10

/** Qué trae la copia (entrenos, fechas, rutinas…) antes de restaurarla, y si se quieren también sus ajustes. */
@Composable
private fun RestorePreviewDialog(vm: ProfileViewModel) {
    val data by vm.pendingRestore.collectAsStateWithLifecycle()
    val summary = data?.let { com.bestrong.core.io.BackupSummary.of(it) } ?: return
    val withSettings by vm.restoreWithSettings.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = { vm.pendingRestore.value = null },
        title = { Text(stringResource(R.string.restore_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.restore_preview_counts, summary.workouts, summary.routines, summary.measurements))
                val first = summary.firstWorkoutEpochMs
                val last = summary.lastWorkoutEpochMs
                if (first != null && last != null) {
                    Text(
                        stringResource(R.string.restore_preview_range, com.bestrong.mobile.ui.components.formatDate(first), com.bestrong.mobile.ui.components.formatDate(last)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val info by vm.restoreInfo.collectAsStateWithLifecycle()
                Text(stringResource(R.string.restore_preview_new, info.newWorkouts), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                // Una copia vieja no borra nada, pero sus ajustes sí sustituirían a los de ahora
                if (info.olderThanLocal && summary.hasSettings && withSettings) Text(stringResource(R.string.restore_preview_older), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.restore_preview_merge), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.hasSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = withSettings, onCheckedChange = { vm.restoreWithSettings.value = it })
                        Text(stringResource(R.string.restore_preview_settings), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.confirmRestore(withSettings) }) { Text(stringResource(R.string.action_restore)) } },
        dismissButton = { TextButton(onClick = { vm.pendingRestore.value = null }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** «Papelera (3)»: los entrenos borrados de los últimos 30 días, para recuperar el que se borró sin querer. */
@Composable
private fun TrashItem(vm: ProfileViewModel) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    if (trash.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.trash_title, trash.size)) },
        supportingContent = { Text(stringResource(R.string.trash_body)) },
        trailingContent = { TextButton(onClick = { open = true }) { Text(stringResource(R.string.trash_open)) } },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.trash_title, trash.size)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    trash.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.workout.name + " · " + com.bestrong.mobile.ui.components.formatDate(t.workout.startedAtEpochMs), Modifier.weight(1f))
                            TextButton(onClick = { vm.restoreFromTrash(t.workout.id) }) { Text(stringResource(R.string.action_restore)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
