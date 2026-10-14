package com.bestrong.mobile.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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

private val GLOSSARY = listOf(
    R.string.g_rpe to R.string.g_rpe_d,
    R.string.g_rir to R.string.g_rir_d,
    R.string.g_1rm to R.string.g_1rm_d,
    R.string.g_tm to R.string.g_tm_d,
    R.string.g_amrap to R.string.g_amrap_d,
    R.string.g_emom to R.string.g_emom_d,
    R.string.g_superset to R.string.g_superset_d,
    R.string.g_drop to R.string.g_drop_d,
    R.string.g_deload to R.string.g_deload_d,
    R.string.g_volume to R.string.g_volume_d,
    R.string.g_overload to R.string.g_overload_d,
    R.string.g_tempo to R.string.g_tempo_d,
    R.string.g_dots to R.string.g_dots_d,
)

private val FAQ = listOf(
    R.string.faq_watch_q to R.string.faq_watch_a,
    R.string.faq_backup_q to R.string.faq_backup_a,
    R.string.faq_rest_q to R.string.faq_rest_a,
    R.string.faq_hc_q to R.string.faq_hc_a,
    R.string.faq_units_q to R.string.faq_units_a,
    R.string.faq_progress_q to R.string.faq_progress_a,
)

/** Ayuda a pantalla completa: novedades, glosario de términos y preguntas frecuentes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDialog(onDismiss: () -> Unit, onReplayTutorial: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.help)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) } },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val content = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
                item {
                    OutlinedButton(onClick = onReplayTutorial, modifier = content) { Text(stringResource(R.string.tutorial_replay)) }
                }
                item { Heading(R.string.whats_new, content) }
                item { Text(stringResource(R.string.whats_new_body), content) }
                item { Heading(R.string.glossary, content) }
                items(GLOSSARY) { (term, def) -> Entry(term, def, content) }
                item { Heading(R.string.faq, content) }
                items(FAQ) { (q, a) -> Entry(q, a, content) }
                item { Spacer(Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun Heading(@StringRes text: Int, modifier: Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Entry(@StringRes title: Int, @StringRes body: Int, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.Bold)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val TUTORIAL = listOf(
    R.string.tut1_t to R.string.tut1_d,
    R.string.tut2_t to R.string.tut2_d,
    R.string.tut3_t to R.string.tut3_d,
    R.string.tut4_t to R.string.tut4_d,
)

/** Tutorial de bienvenida (primer arranque o desde Ayuda). */
@Composable
fun TutorialDialog(
    onDone: () -> Unit,
    unit: com.bestrong.core.util.WeightUnit = com.bestrong.core.util.WeightUnit.KG,
    weeklyGoal: Int = 3,
    /** Última página: lo básico para empezar (unidad, días por semana y si se entrena en casa). */
    onSetup: (com.bestrong.core.util.WeightUnit, Int, Boolean) -> Unit = { _, _, _ -> },
    atHome: Boolean = false,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var chosenUnit by rememberSaveable { mutableStateOf(unit) }
    var chosenGoal by rememberSaveable { mutableIntStateOf(weeklyGoal) }
    var chosenHome by rememberSaveable { mutableStateOf(atHome) }
    val pages = TUTORIAL.size + 1
    val setup = page == TUTORIAL.size
    val finish = { onSetup(chosenUnit, chosenGoal, chosenHome); onDone() }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(if (setup) R.string.setup_title else TUTORIAL[page].first)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (setup) {
                    SetupPage(chosenUnit, chosenGoal, { chosenUnit = it }, { chosenGoal = it })
                    PlaceChoice(chosenHome) { chosenHome = it }
                } else {
                    Text(stringResource(TUTORIAL[page].second))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        (0 until pages).joinToString(" ") { if (it == page) "●" else "○" },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (setup) finish() else page++ }) { Text(stringResource(if (setup) R.string.start else R.string.next)) }
        },
        // «Saltar» lleva a la configuración (lo único que conviene no saltarse)
        dismissButton = { if (!setup) TextButton(onClick = { page = TUTORIAL.size }) { Text(stringResource(R.string.skip)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SetupPage(
    unit: com.bestrong.core.util.WeightUnit,
    goal: Int,
    onUnit: (com.bestrong.core.util.WeightUnit) -> Unit,
    onGoal: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_unit), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            com.bestrong.core.util.WeightUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = u == unit,
                    onClick = { onUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(i, com.bestrong.core.util.WeightUnit.entries.size),
                ) { Text(u.label) }
            }
        }
        Text(stringResource(R.string.setup_goal), style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 1..7) FilterChip(selected = d == goal, onClick = { onGoal(d) }, label = { Text("$d") })
        }
        Text(stringResource(R.string.setup_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** «¿Dónde entrenas?»: elige el perfil de material (discos y barra o mancuernas de casa) para los pesos sugeridos. */
@Composable
private fun PlaceChoice(home: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_place), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !home, onClick = { onChange(false) }, label = { Text(stringResource(R.string.setup_place_gym)) })
            FilterChip(selected = home, onClick = { onChange(true) }, label = { Text(stringResource(R.string.setup_place_home)) })
        }
    }
}

/** Novedades de la versión, una vez tras actualizar. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new)) },
        text = { Text(stringResource(R.string.whats_new_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it)) } },
    )
}

/** Consejos ya descartados y cómo descartar uno nuevo (se guardan en Ajustes). */
class Tips(val dismissed: Set<String>, val onDismiss: (String) -> Unit)

val LocalTips = staticCompositionLocalOf { Tips(emptySet()) {} }

/** Consejo contextual que aparece una sola vez hasta que se descarta. */
@Composable
fun TipCard(id: String, @StringRes text: Int, modifier: Modifier = Modifier) {
    val tips = LocalTips.current
    if (id in tips.dismissed) return
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            TextButton(onClick = { tips.onDismiss(id) }) { Text(stringResource(R.string.got_it)) }
        }
    }
}
