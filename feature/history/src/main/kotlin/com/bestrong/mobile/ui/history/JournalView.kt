package com.bestrong.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Workout
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.TagChips
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.theme.statusColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** «Lista» o «Diario»: cómo se ven los entrenos del historial. */
@Composable
internal fun HistoryViewToggle(journal: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !journal, onClick = { onChange(false) }, label = { Text(stringResource(R.string.diary_list)) })
        FilterChip(selected = journal, onClick = { onChange(true) }, label = { Text(stringResource(R.string.diary_journal)) })
    }
}

/** Etiquetas usadas (con cuántas veces); tocar una filtra el historial por ella y enseña cómo rindes con ella. */
@Composable
internal fun TagFilterRow(vm: HistoryViewModel) {
    val tags by vm.tagCounts.collectAsStateWithLifecycle()
    val selected by vm.tagFilter.collectAsStateWithLifecycle()
    if (tags.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { (t, n) ->
            FilterChip(
                selected = selected == t,
                onClick = { vm.tagFilter.value = if (selected == t) null else t },
                label = { Text("#$t · $n") },
            )
        }
    }
    selected?.let { TagEffectCard(vm, it) }
}

/** «Con #dormí-mal rindes un 7 % menos» (volumen frente a la media de la misma rutina). */
@Composable
private fun TagEffectCard(vm: HistoryViewModel, tag: String) {
    val history by vm.history.collectAsStateWithLifecycle()
    val effect by produceState<Double?>(null, tag, history) { value = vm.tagEffect(tag) }
    val e = effect
    val pct = e?.let { (abs(it) * 100).roundToInt() }
    val text = when {
        e == null || pct == null -> stringResource(R.string.diary_effect_none, tag)
        pct == 0 -> stringResource(R.string.diary_effect_same, tag)
        e < 0 -> stringResource(R.string.diary_effect_less, tag, pct)
        else -> stringResource(R.string.diary_effect_more, tag, pct)
    }
    com.bestrong.mobile.ui.components.Panel(contentPadding = 14.dp) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            color = when {
                e == null || pct == 0 -> MaterialTheme.colorScheme.onSurface
                e < 0 -> statusColors.bad
                else -> statusColors.good
            },
        )
        if (e != null) Text(stringResource(R.string.diary_effect_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Una entrada del diario: fecha y nombre, la nota, la valoración en barras y las etiquetas. */
@Composable
internal fun DiaryEntry(w: Workout, onOpen: () -> Unit, onTag: (String) -> Unit) {
    com.bestrong.mobile.ui.components.Panel(onClick = onOpen, onClickLabel = stringResource(R.string.action_open), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatShortDate(w.startedAtEpochMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(" · " + w.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (w.notes.isNotBlank()) {
            Text(w.notes, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(R.string.diary_no_note), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        w.rating?.let { r ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RatingBar(stringResource(R.string.rating_energy), r.energy, invert = false)
                RatingBar(stringResource(R.string.rating_pain), r.pain, invert = true)
                RatingBar(stringResource(R.string.rating_motivation), r.motivation, invert = false)
            }
        }
        TagChips(w.tags, onClick = onTag)
    }
}

/** Valoración de 1 a 5 en cinco segmentos (las molestias en el color de aviso: más es peor). */
@Composable
private fun RatingBar(label: String, value: Int, invert: Boolean) {
    val color = if (invert) statusColors.warn else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = "$label: $value/5" }) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { i ->
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (i < value) color else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}
