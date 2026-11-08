package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.mobile.community.CommunityIdeas
import com.bestrong.mobile.community.LocalOpenProposals
import com.bestrong.mobile.community.ProposalRequest
import com.bestrong.mobile.community.ProposalsInbox
import com.bestrong.mobile.notify.AnnouncementsWorker
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.Panel
import com.bestrong.mobile.ui.theme.enfoque

/**
 * Pestaña Ideas: proponer una mejora, respuestas nuevas del equipo, las ideas de usuarios que ya están en la app y los
 * avisos del equipo. Abrirla no pide nada a la red (las respuestas las trae el aviso de propuestas al abrir la app).
 */
@Composable
fun IdeasScreen(onOpenProfile: () -> Unit) {
    val context = LocalContext.current
    val open = LocalOpenProposals.current
    val unread by ProposalsInbox.unread(context).collectAsStateWithLifecycle()
    val announcements = remember { AnnouncementsWorker.cached(context) }
    val done = CommunityIdeas.all
    Box(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.bestrong.mobile.ui.components.EnfoqueHeader(
                pluralStringResource(R.plurals.ideas_counter, done.size, done.size),
                stringResource(R.string.nav_ideas),
                onOpenProfile,
            )
            if (unread) {
                Panel(
                    onClick = { open(ProposalRequest(tab = 1)) },
                    onClickLabel = stringResource(R.string.proposals_tab_mine),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.MarkChatUnread, null, tint = enfoque.action)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ideas_unread_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.ideas_unread_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            CommunitySection(onPropose = { open(ProposalRequest()) }, onMine = { open(ProposalRequest(tab = 1)) })
            CommunityIdeasPanel()
            AnnouncementsPanel(announcements)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** «Ya en la app gracias a vosotros»: lo que han traído las propuestas de usuarios, marcado como hecho. */
@Composable
fun CommunityIdeasPanel(modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(
            stringResource(R.string.ideas_done_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        CommunityIdeas.all.forEach { idea ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(22.dp), tint = enfoque.done)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(idea.title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.ideas_done_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
