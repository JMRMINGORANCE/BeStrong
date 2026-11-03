package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.shared.R

/**
 * Contenido de «Comunidad» (Perfil): BeStrong se hace con las ideas de quien la usa (nació en Forocoches).
 * Explica cómo aportar y lleva al buzón de propuestas.
 */
@Composable
fun CommunitySection(onPropose: () -> Unit, onMine: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        com.bestrong.mobile.ui.components.Panel {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.bestrong.mobile.ui.help.BrandIllustration(
                    Icons.Default.Groups,
                    listOf(Icons.Default.Lightbulb, Icons.Default.Forum, Icons.Default.AutoAwesome),
                    modifier = Modifier.size(150.dp),
                    animated = false,
                )
                Text(
                    stringResource(R.string.community_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.community_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onPropose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Lightbulb, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.proposals_open), Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onMine, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.proposals_tab_mine))
                }
            }
        }
        com.bestrong.mobile.ui.components.Panel {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.community_how),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Step(Icons.Default.Lightbulb, R.string.community_step1)
                Step(Icons.Default.Forum, R.string.community_step2)
                Step(Icons.Default.RocketLaunch, R.string.community_step3)
                Step(Icons.Default.TrackChanges, R.string.community_step4)
            }
        }
    }
}

@Composable
private fun Step(icon: ImageVector, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * Pantalla «Comunidad» (desde la fila de Perfil): cómo aportar, el buzón de ideas y los avisos del equipo.
 * Los avisos son los últimos que trajo [AnnouncementsWorker][com.bestrong.mobile.notify.AnnouncementsWorker]:
 * abrirla no pide nada a la red.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CommunityDialog(onPropose: () -> Unit, onMine: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val announcements = androidx.compose.runtime.remember { com.bestrong.mobile.notify.AnnouncementsWorker.cached(context) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        // Fondo opaco: el tema deja transparente el de Scaffold y se veía la pantalla de detrás
        com.bestrong.mobile.ui.theme.ScreenBackdrop {
            androidx.compose.material3.Scaffold(
                topBar = {
                    androidx.compose.material3.TopAppBar(
                        title = { Text(stringResource(R.string.community_tab)) },
                        navigationIcon = {
                            androidx.compose.material3.IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, stringResource(R.string.action_close))
                            }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CommunitySection(onPropose, onMine)
                        AnnouncementsPanel(announcements)
                    }
                }
            }
        }
    }
}

/** Avisos del equipo, el más reciente primero; sin ninguno, se explica dónde llegarán. */
@Composable
private fun AnnouncementsPanel(list: List<com.bestrong.mobile.notify.Announcement>) {
    com.bestrong.mobile.ui.components.Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.community_announcements),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (list.isEmpty()) {
                Text(stringResource(R.string.community_announcements_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list.forEach { a ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(a.title.ifEmpty { stringResource(R.string.notif_announcement_title) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(a.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
