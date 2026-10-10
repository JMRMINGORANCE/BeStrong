package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.BuildConfig
import com.bestrong.mobile.R

/** Bibliotecas de código abierto que usa la app y su licencia. */
private val OPEN_SOURCE = listOf(
    "AndroidX, Jetpack Compose, Material 3, Room, WorkManager, Glance, Health Connect, Wear OS" to "Apache 2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0",
    "Dagger / Hilt" to "Apache 2.0",
    "Coil" to "Apache 2.0",
    "Google Play services Wearable" to "Android SDK License",
    "ZXing" to "Apache 2.0",
    "Atkinson Hyperlegible (Braille Institute)" to "SIL Open Font License 1.1",
    "OpenDyslexic (Abbie Gonzalez)" to "SIL Open Font License 1.1",
)

/** «Acerca de»: versión, créditos de contenido, licencias y apoyo opcional con un café. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.about_body))
                Text(stringResource(R.string.privacy_credits_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.privacy_credits), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleSmall)
                OPEN_SOURCE.forEach { (name, license) -> Text("• $name — $license", style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { uri.openUri(BuildConfig.REPO_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_source))
                }
                // Apoyo voluntario: la app es gratis y no tiene funciones de pago
                if (BuildConfig.DONATION_URL.isNotBlank()) {
                    Button(onClick = { uri.openUri(BuildConfig.DONATION_URL) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.about_coffee))
                    }
                    Text(stringResource(R.string.about_coffee_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
