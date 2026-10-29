package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SettingsDivider
import com.bestrong.mobile.ui.help.BrandIllustration

/**
 * Tras la bienvenida (solo el primer arranque): los permisos que se conceden desde la app (notificaciones y Health
 * Connect) en una pantalla, con «Activar todo» para pedirlos de seguido. Todo es opcional: «Ahora no» (o Atrás) cierra
 * sin pedir nada, y luego se pueden activar en Ajustes → Permisos y conexiones.
 */
@Composable
fun OnboardingPermissionsDialog(vm: ProfileViewModel, onDone: () -> Unit) {
    val state = rememberPermissionsState(vm)
    // Solo los que se piden con el diálogo del sistema: batería, reloj, bloqueo y copias se quedan en Ajustes
    val items = state.items.filter { it.inBatch }
    val pending = items.any { it.status != PermissionStatus.ON }
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = com.bestrong.mobile.ui.theme.LocalScreenColor.current,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrandIllustration(
                        Icons.Default.HealthAndSafety,
                        listOf(Icons.Default.NotificationsActive, Icons.Default.MonitorHeart, Icons.Default.Favorite),
                        modifier = Modifier.size(ONBOARDING_ART),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.onb_perm_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.onb_perm_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    ) {
                        items.forEachIndexed { i, item ->
                            if (i > 0) SettingsDivider()
                            PermissionRow(item)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.onb_perm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = if (pending) state.requestAll else onDone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        stringResource(if (pending) R.string.perm_enable_all else R.string.onb_perm_continue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // Siempre se puede seguir sin conceder nada: no es obligatorio
                if (pending) {
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(stringResource(R.string.onb_perm_later))
                    }
                }
            }
        }
    }
}

private val ONBOARDING_ART = 120.dp
