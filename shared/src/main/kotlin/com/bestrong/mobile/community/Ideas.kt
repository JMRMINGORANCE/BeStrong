package com.bestrong.mobile.community

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bestrong.core.community.ProposalPrompt
import com.bestrong.mobile.shared.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** De qué trata una propuesta: se elige con un toque y se envía como primera línea del texto (en español, para el equipo). */
enum class ProposalCategory(val tag: String, @StringRes val label: Int) {
    EXERCISE("Ejercicio que falta", R.string.ideas_cat_exercise),
    PROGRAM("Programa o rutina", R.string.ideas_cat_program),
    DESIGN("Diseño", R.string.ideas_cat_design),
    BUG("Algo que falla", R.string.ideas_cat_bug),
    OTHER("Otra cosa", R.string.ideas_cat_other),
}

/** Abrir el buzón: en «Proponer» (0) o «Mis propuestas» (1), con la categoría y el título ya puestos si se sabe. */
data class ProposalRequest(val tab: Int = 0, val category: ProposalCategory? = null, val title: String = "")

/** Abre el buzón de ideas desde cualquier pantalla (lo da la raíz de la app; en vistas previas no hace nada). */
val LocalOpenProposals = staticCompositionLocalOf<(ProposalRequest) -> Unit> { {} }

/** Ideas de usuarios que ya están en la app (número de la propuesta y qué trajo). El mantenedor añade una por cada una. */
data class CommunityIdea(val number: Int, @StringRes val title: Int)

object CommunityIdeas {
    val all = listOf(
        CommunityIdea(3, R.string.ideas_done_3),
        CommunityIdea(2, R.string.ideas_done_2),
    )
}

/**
 * Lo que el móvil recuerda del buzón, solo en este dispositivo (las mismas preferencias que el buzón): cuántos
 * mensajes del equipo hay y cuántos se han visto (para el punto en la pestaña Ideas) y cuándo se pidió una idea.
 */
object ProposalsInbox {
    private const val PREFS = "proposals"
    private const val KEY_TEAM_LATEST = "team_latest"
    private const val KEY_TEAM_SEEN = "team_seen"
    private const val KEY_FINISHED = "prompt_finished"
    private const val KEY_LAST_WORKOUT = "prompt_last_workout"
    private const val KEY_LAST_ASKED = "prompt_last_asked"
    private const val KEY_OPTED_OUT = "prompt_opted_out"

    private val state = MutableStateFlow(false)

    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Hay respuestas del equipo sin ver en «Mis propuestas». */
    fun unread(context: Context): StateFlow<Boolean> {
        if (!loaded) {
            loaded = true
            refresh(context)
        }
        return state
    }

    private fun refresh(context: Context) {
        val p = prefs(context)
        state.value = ProposalPrompt.hasUnread(ProposalPrompt.decode(p.getString(KEY_TEAM_LATEST, null)), ProposalPrompt.decode(p.getString(KEY_TEAM_SEEN, null)))
    }

    /** Mensajes del equipo por propuesta, según la última consulta al buzón. */
    fun updateLatest(context: Context, counts: Map<Int, Int>) {
        prefs(context).edit().putString(KEY_TEAM_LATEST, ProposalPrompt.encode(counts)).apply()
        refresh(context)
    }

    /** Se ha abierto «Mis propuestas» con esta lista: todo visto. */
    fun markSeen(context: Context, counts: Map<Int, Int>) {
        val encoded = ProposalPrompt.encode(counts)
        prefs(context).edit().putString(KEY_TEAM_LATEST, encoded).putString(KEY_TEAM_SEEN, encoded).apply()
        refresh(context)
    }

    /**
     * Al enseñar el resumen de un entreno: lo cuenta (una vez por entreno) y dice si toca pedir una idea; si toca, lo
     * apunta para no volver a pedirla en un tiempo.
     */
    fun onSummary(context: Context, workoutId: String, hasRecords: Boolean): Boolean {
        val p = prefs(context)
        val finished = if (p.getString(KEY_LAST_WORKOUT, null) == workoutId) {
            p.getInt(KEY_FINISHED, 0)
        } else {
            (p.getInt(KEY_FINISHED, 0) + 1).also { p.edit().putInt(KEY_FINISHED, it).putString(KEY_LAST_WORKOUT, workoutId).apply() }
        }
        val now = System.currentTimeMillis()
        val ask = ProposalPrompt.shouldAsk(finished, hasRecords, p.getLong(KEY_LAST_ASKED, 0), now, p.getBoolean(KEY_OPTED_OUT, false))
        if (ask) p.edit().putLong(KEY_LAST_ASKED, now).apply()
        return ask
    }

    fun optOut(context: Context) {
        prefs(context).edit().putBoolean(KEY_OPTED_OUT, true).apply()
    }
}

/** Línea discreta con la bombilla («¿Echas algo en falta? Propón una mejora») que abre el buzón. */
@Composable
fun ProposalNudge(@StringRes text: Int, modifier: Modifier = Modifier, request: ProposalRequest = ProposalRequest()) {
    val open = LocalOpenProposals.current
    TextButton(onClick = { open(request) }, modifier = modifier) {
        Icon(Icons.Default.Lightbulb, null, Modifier.size(18.dp))
        Text(stringResource(text), Modifier.padding(start = 8.dp), textAlign = TextAlign.Center)
    }
}

/**
 * Fin del entreno: en un buen momento (hito o récord) una tarjeta que pide una idea, con «Ahora no» y «No volver a
 * preguntar»; el resto de veces, solo la línea discreta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryProposalPrompt(workoutId: String, hasRecords: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val open = LocalOpenProposals.current
    // Saveable: al girar la pantalla no se cuenta otra vez ni cambia lo que se enseña
    var ask by rememberSaveable(workoutId) { mutableStateOf(ProposalsInbox.onSummary(context, workoutId, hasRecords)) }
    if (!ask) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { ProposalNudge(R.string.ideas_nudge) }
        return
    }
    com.bestrong.mobile.ui.components.Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.bestrong.mobile.ui.components.IconTile(Icons.Default.Lightbulb, com.bestrong.mobile.ui.components.SettingsTint.Amber, size = 36)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (hasRecords) R.string.ideas_ask_title_record else R.string.ideas_ask_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.ideas_ask_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Con letra grande o en otros idiomas no caben los tres en una línea: bajan a la siguiente
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                ProposalsInbox.optOut(context)
                ask = false
            }) { Text(stringResource(R.string.ideas_ask_never)) }
            TextButton(onClick = { ask = false }) { Text(stringResource(R.string.ideas_ask_later)) }
            Button(onClick = {
                ask = false
                open(ProposalRequest())
            }) { Text(stringResource(R.string.ideas_ask_propose)) }
        }
    }
}
