package com.bestrong.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bestrong.core.model.Exercise
import com.bestrong.wear.R
import com.bestrong.wear.WearContainer

/**
 * Crear una rutina sin el móvil: nombre (por voz o automático) y ejercicios de la biblioteca.
 * Cada ejercicio lleva 3 series y el descanso según su tipo. Se envía al móvil al guardar.
 */
@Composable
fun RoutineCreateScreen(container: WearContainer, onSaved: () -> Unit) {
    val routines by container.store.routines.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.routine_name_default, routines.size + 1)
    var name by rememberSaveable { mutableStateOf("") }
    val exercises = remember { mutableStateListOf<Exercise>() }
    var picking by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput(stringResource(R.string.routine_rename_voice)) { name = it.trim().replaceFirstChar { c -> c.uppercase() } }
    if (picking) {
        // Atrás (botón) cierra solo el selector: no se pierde la rutina a medias
        androidx.activity.compose.BackHandler { picking = false }
        ExercisePickerScreen(container) { ex ->
            exercises += ex
            picking = false
        }
        return
    }
    val listState = rememberScalingLazyListState()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    if (exercises.isNotEmpty()) {
                        container.session.createRoutine(name.ifBlank { defaultName }, exercises.toList())
                        onSaved()
                    }
                },
                enabled = exercises.isNotEmpty(),
            ) { Text(stringResource(R.string.routine_save)) }
        },
    ) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(name.ifBlank { defaultName }, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) } }
            voice?.let { launch ->
                item {
                    Button(onClick = launch, label = { Text(stringResource(R.string.routine_rename_voice)) }, colors = ButtonDefaults.outlinedButtonColors(), modifier = Modifier.fillMaxWidth())
                }
            }
            if (exercises.isEmpty()) {
                item { Text(stringResource(R.string.routine_empty), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            }
            itemsIndexed(exercises) { i, ex ->
                Button(
                    onClick = { exercises.removeAt(i) },
                    label = { Text(ex.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    // Lo que se va a guardar: 3 × 10, 3 × 30 s o un bloque de cardio
                    secondaryLabel = {
                        val sets = com.bestrong.core.model.RoutineSet.defaultsFor(ex.tracking)
                        val first = sets.first()
                        Text("${sets.size} × " + (if (first.durationSec > 0) com.bestrong.core.util.formatDuration(first.durationSec.toLong()) else "${first.reps}") + " · ✕")
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(onClick = { picking = true }, label = { Text(stringResource(R.string.routine_add_exercise)) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
