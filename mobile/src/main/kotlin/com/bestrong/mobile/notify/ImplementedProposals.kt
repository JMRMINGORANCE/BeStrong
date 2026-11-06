package com.bestrong.mobile.notify

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bestrong.mobile.net.AppsScript
import com.bestrong.mobile.shared.R
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Una propuesta del usuario que ya se ha hecho (etiqueta «implementada» en el buzón). */
data class ImplementedProposal(val number: Int, val title: String)

/**
 * Aviso dentro de la app cuando se hace una propuesta que envió este usuario: un banner arriba, una sola vez por
 * propuesta. Solo pregunta al buzón si este móvil ha enviado alguna propuesta (si no, no hay identificador y no se
 * hace nada) y como mucho cada [MIN_GAP_MS]. Usa la misma petición que Perfil › Comunidad: no envía nada nuevo.
 */
class ImplementedProposals(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    private val prefs by lazy { activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val _banner = MutableStateFlow<ImplementedProposal?>(null)

    /** La propuesta que toca enseñar ahora (la más antigua sin enseñar), o null. */
    val banner: StateFlow<ImplementedProposal?> = _banner
    private var pending: List<ImplementedProposal> = emptyList()
    private var job: Job? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        val device = prefs.getString(KEY_DEVICE, null) ?: return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (job?.isActive == true || System.currentTimeMillis() - last < MIN_GAP_MS) return
        job = activity.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { fetch(device) }.getOrNull() } ?: return@launch
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            pending = toShow(list, shown())
            _banner.value = pending.firstOrNull()
        }
    }

    /** «Genial» o cerrar: no se vuelve a enseñar esa propuesta; si hay otra, sale después. */
    fun dismiss(number: Int) {
        prefs.edit().putStringSet(KEY_SHOWN, shown().map { it.toString() }.toSet() + number.toString()).apply()
        pending = pending.filter { it.number != number }
        _banner.value = pending.firstOrNull()
    }

    private fun shown(): Set<Int> = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun fetch(device: String): List<Pair<ImplementedProposal, String>>? {
        val id = URLEncoder.encode(device, "UTF-8")
        val json = JSONObject(AppsScript.request(URL("${AppsScript.ENDPOINT}?key=${AppsScript.APP_KEY}&device=$id"), null))
        return if (json.optBoolean("ok")) parse(json) else null
    }

    companion object {
        /** Las mismas preferencias que el buzón de propuestas (ahí vive el identificador de esta instalación). */
        private const val PREFS = "proposals"
        private const val KEY_DEVICE = "device"
        private const val KEY_SHOWN = "implemented_shown"
        private const val KEY_LAST_CHECK = "implemented_last_check"
        private const val MIN_GAP_MS = 3 * 60 * 60 * 1000L
        private const val MAX_TITLE = 80
        const val IMPLEMENTED = "implementada"

        /** Respuesta del script: {ok, proposals:[{number, title, status}]} → (propuesta, estado). */
        fun parse(json: JSONObject): List<Pair<ImplementedProposal, String>> {
            val array = json.optJSONArray("proposals") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val p = array.optJSONObject(i) ?: return@mapNotNull null
                val number = p.optInt("number")
                if (number <= 0) null else ImplementedProposal(number, p.optString("title").trim().take(MAX_TITLE)) to p.optString("status")
            }
        }

        /** Las implementadas que aún no se han enseñado, de la más antigua a la más reciente. */
        fun toShow(list: List<Pair<ImplementedProposal, String>>, shown: Set<Int>): List<ImplementedProposal> =
            list.filter { (p, status) -> status == IMPLEMENTED && p.number !in shown }.map { it.first }.sortedBy { it.number }
    }
}

/** Banner arriba de la pantalla: «¡Tu propuesta ya es realidad!» con el título de la propuesta. */
@Composable
fun ImplementedProposalBanner(proposal: ImplementedProposal?, onDismiss: (Int) -> Unit) {
    if (proposal == null) return
    Popup(alignment = Alignment.TopCenter, onDismissRequest = { onDismiss(proposal.number) }) {
        Card(
            Modifier.statusBarsPadding().padding(12.dp).widthIn(max = 560.dp).fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Celebration, null, Modifier.size(28.dp).padding(top = 2.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.implemented_banner_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.implemented_banner_text, proposal.title), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onDismiss(proposal.number) }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.implemented_banner_ok))
                    }
                }
            }
        }
    }
}
