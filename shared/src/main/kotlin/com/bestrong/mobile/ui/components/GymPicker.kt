package com.bestrong.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.bestrong.core.training.GymProfile
import com.bestrong.mobile.shared.R

/**
 * Elegir el gimnasio activo (casa, hotel, el del trabajo…) sin ir a Perfil: cambia los discos, la barra y las
 * mancuernas con que se redondean las sugerencias y el peso por RPE. Se usa en Inicio y durante el entreno.
 */
@Composable
fun GymPickerDialog(gyms: List<GymProfile>, activeId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_switch_title)) },
        text = {
            Column {
                gyms.forEach { g ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().selectable(selected = g.id == activeId, role = Role.RadioButton) {
                            onPick(g.id)
                            onDismiss()
                        },
                    ) {
                        RadioButton(selected = g.id == activeId, onClick = null)
                        Text(g.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
