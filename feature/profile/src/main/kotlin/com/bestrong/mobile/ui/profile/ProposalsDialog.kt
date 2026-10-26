package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bestrong.mobile.shared.R
import kotlinx.coroutines.launch

/** Buzón de ideas: proponer una mejora y ver las respuestas (se abre desde Perfil › Comunidad o el aviso de Ajustes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalsDialog(initialTab: Int = 0, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.proposals_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(R.string.proposals_tab_new, R.string.proposals_tab_mine).forEachIndexed { i, label ->
                        SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(label)) }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val content = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    if (tab == 0) NewProposal(content, onSent = { tab = 1 }) else MyProposals(content)
                }
            }
        }
    }
}

@Composable
private fun NewProposal(modifier: Modifier, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    var how by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.proposals_intro))
        OutlinedTextField(title, { title = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.proposals_field_title)) }, singleLine = true)
        OutlinedTextField(what, { what = it.take(3000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.proposals_field_what)) }, minLines = 4)
        OutlinedTextField(how, { how = it.take(3000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.proposals_field_how)) }, minLines = 3)
        Text(stringResource(R.string.proposals_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                sending = true
                error = null
                scope.launch {
                    val result = ProposalsApi.send(context, title, what, how)
                    sending = false
                    if (result.ok) {
                        title = ""
                        what = ""
                        how = ""
                        android.widget.Toast.makeText(context, R.string.proposals_sent, android.widget.Toast.LENGTH_LONG).show()
                        onSent()
                    } else {
                        error = result.error
                    }
                }
            },
            enabled = !sending && title.trim().length >= 4 && what.trim().length >= 10,
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.proposals_send)) }
    }
}

@Composable
private fun MyProposals(modifier: Modifier) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var proposals by remember { mutableStateOf<List<Proposal>?>(emptyList()) }
    LaunchedEffect(reload) {
        loading = true
        proposals = ProposalsApi.list(context)
        loading = false
    }
    val list = proposals
    when {
        loading -> CircularProgressIndicator(Modifier.padding(32.dp))
        list == null -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.proposals_load_error))
            TextButton(onClick = { reload++ }) { Text(stringResource(R.string.proposals_retry)) }
        }
        list.isEmpty() -> Text(stringResource(R.string.proposals_empty), Modifier.padding(32.dp))
        else -> LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.number }) { ProposalCard(it, onReplied = { reload++ }) }
        }
    }
}

@Composable
private fun ProposalCard(proposal: Proposal, onReplied: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reply by rememberSaveable(proposal.number) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(proposal.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text(statusText(proposal.status)) })
            }
            proposal.messages.forEach { m ->
                Text(
                    stringResource(if (m.fromUser) R.string.proposals_from_you else R.string.proposals_from_team),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (m.fromUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Text(m.text)
            }
            if (proposal.status == "necesita-info") {
                OutlinedTextField(reply, { reply = it.take(3000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.proposals_reply_hint)) }, minLines = 2)
                error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        sending = true
                        error = null
                        scope.launch {
                            val result = ProposalsApi.reply(context, proposal.number, reply)
                            sending = false
                            if (result.ok) {
                                reply = ""
                                onReplied()
                            } else {
                                error = result.error
                            }
                        }
                    },
                    enabled = !sending && reply.trim().length >= 2,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.proposals_reply_send)) }
            }
        }
    }
}

@Composable
private fun errorText(code: String): String = stringResource(
    when (code) {
        "limite" -> R.string.proposals_error_limit
        "corta" -> R.string.proposals_error_short
        else -> R.string.proposals_error_network
    },
)

@Composable
private fun statusText(status: String): String = stringResource(
    when (status) {
        "necesita-info" -> R.string.proposals_status_necesita_info
        "aceptada" -> R.string.proposals_status_aceptada
        "en-desarrollo" -> R.string.proposals_status_en_desarrollo
        "implementada" -> R.string.proposals_status_implementada
        "descartada" -> R.string.proposals_status_descartada
        "revision-humana" -> R.string.proposals_status_revision_humana
        else -> R.string.proposals_status_recibida
    },
)
