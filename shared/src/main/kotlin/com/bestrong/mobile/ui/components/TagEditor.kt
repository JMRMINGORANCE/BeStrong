package com.bestrong.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bestrong.core.stats.Journal
import com.bestrong.mobile.shared.R

/** Etiquetas sugeridas de serie, en el idioma de la app («dormí-mal», «nuevo-gimnasio»…). */
@Composable
fun defaultTagSuggestions(): List<String> = stringResource(R.string.diary_default_tags).split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Etiquetas de solo lectura («#dormí-mal»); tocar una llama a [onClick] si se pasa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(tags: List<String>, modifier: Modifier = Modifier, onClick: ((String) -> Unit)? = null) {
    if (tags.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { t ->
            if (onClick != null) {
                SuggestionChip(onClick = { onClick(t) }, label = { Text("#$t") })
            } else {
                MetaPill("#$t")
            }
        }
    }
}

/**
 * Editor de etiquetas: las puestas (con su X para quitarlas), un campo para escribir una nueva y sugerencias
 * (las más usadas en el historial y unas de serie) que se añaden con un toque.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(tags: List<String>, suggestions: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    fun add() {
        val next = Journal.withTag(tags, text)
        text = ""
        if (next != tags) onChange(next)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { t ->
                    val remove = stringResource(R.string.diary_remove_tag, t)
                    InputChip(
                        selected = true,
                        onClick = { onChange(tags - t) },
                        label = { Text("#$t") },
                        trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                        modifier = Modifier.semantics { contentDescription = remove },
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.diary_add_tag)) },
            placeholder = { Text(stringResource(R.string.diary_tag_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = {
                IconButton(onClick = { add() }, enabled = Journal.normalizeTag(text).isNotEmpty()) {
                    Icon(Icons.Default.Add, stringResource(R.string.diary_add_tag))
                }
            },
        )
        val shown = suggestions.map(Journal::normalizeTag).filter { it.isNotEmpty() && it !in tags }.distinct().take(MAX_SUGGESTIONS)
        if (shown.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEach { s ->
                    SuggestionChip(onClick = { onChange(tags + s) }, label = { Text("#$s", style = MaterialTheme.typography.labelMedium) })
                }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 8
