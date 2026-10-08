package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.TrackingType
import com.bestrong.mobile.R
import com.bestrong.mobile.ui.components.EmptyState
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label
import kotlinx.coroutines.launch

/** Pestaña "Ejercicios": lista y detalle lado a lado en el Fold desplegado. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExerciseLibraryScreen(vm: ExercisesViewModel) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    // Material3 Adaptive 1.1: la navegación entre paneles es suspend (animada)
    val scope = rememberCoroutineScope()
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                ExerciseSearchList(
                    vm = vm,
                    title = stringResource(R.string.nav_exercises),
                    selected = setOfNotNull((navigator.currentDestination?.contentKey as? String)),
                    multiSelect = false,
                    onClick = { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it.id) } },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val id = (navigator.currentDestination?.contentKey as? String)
                if (id == null) {
                    EmptyState(Icons.Default.Search, stringResource(R.string.exercise_pick_title), stringResource(R.string.exercise_pick_body))
                } else {
                    ExerciseDetailScreen(vm, id, onBack = if (navigator.canNavigateBack()) ({ scope.launch { navigator.navigateBack() } }) else null)
                }
            }
        },
    )
}

/** Selector a pantalla completa (añadir ejercicios a un entrenamiento o rutina). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(vm: ExercisesViewModel, onBack: () -> Unit, onPicked: (List<String>) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_exercises)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            if (picked.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onPicked(picked.toList()) },
                    icon = { Icon(Icons.Default.Check, null) },
                    text = { Text(stringResource(R.string.add_n, picked.size)) },
                )
            }
        },
    ) { padding ->
        ExerciseSearchList(
            vm = vm,
            title = null,
            selected = picked.toSet(),
            multiSelect = true,
            onClick = { if (it.id in picked) picked.remove(it.id) else picked.add(it.id) },
            modifier = Modifier.padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSearchList(
    vm: ExercisesViewModel,
    title: String?,
    selected: Set<String>,
    multiSelect: Boolean,
    onClick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val usage by vm.usageCount.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        if (title != null) {
            TopAppBar(
                title = { Text(title) },
                actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, stringResource(R.string.create_exercise)) } },
            )
        }
        OutlinedTextField(
            value = filter.query,
            onValueChange = { q -> vm.filter.value = filter.copy(query = q) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(stringResource(R.string.search_exercise)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EnumFilter(stringResource(R.string.filter_muscle), MuscleGroup.entries, filter.muscle, { it.label() }) { vm.filter.value = filter.copy(muscle = it) }
            EnumFilter(stringResource(R.string.filter_equipment), Equipment.entries, filter.equipment, { it.label() }) { vm.filter.value = filter.copy(equipment = it) }
            if (title == null) FilterChip(selected = false, onClick = { creating = true }, label = { Text(stringResource(R.string.create_short)) })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { ex ->
                ListItem(
                    headlineContent = { Text(ex.displayName()) },
                    supportingContent = {
                        val times = usage[ex.id]
                        val parts = listOfNotNull(
                            ex.primaryMuscle.label(),
                            ex.equipment.label(),
                            if (ex.isCustom) stringResource(R.string.custom) else null,
                            times?.let { stringResource(R.string.n_times, it) },
                        )
                        Text(parts.joinToString(" · "))
                    },
                    trailingContent = if (multiSelect) ({ Checkbox(checked = ex.id in selected, onCheckedChange = null) }) else null,
                    tonalElevation = if (!multiSelect && ex.id in selected) 4.dp else 0.dp,
                    // En selección múltiple toda la fila es la casilla (TalkBack: «Press banca, casilla, marcada»)
                    modifier = if (multiSelect) {
                        Modifier.toggleable(value = ex.id in selected, role = Role.Checkbox) { onClick(ex) }
                    } else {
                        Modifier.clickable { onClick(ex) }
                    },
                )
            }
        }
    }

    if (creating) {
        CreateExerciseDialog(
            onDismiss = { creating = false },
            onCreate = { name, muscle, equipment, tracking ->
                val ex = vm.createCustom(name, muscle, equipment, tracking)
                creating = false
                if (multiSelect) onClick(ex)
            },
        )
    }
}

@Composable
private fun <T> EnumFilter(label: String, values: List<T>, selected: T?, name: @Composable (T) -> String, onSelect: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(if (selected != null) name(selected) else label) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.all)) }, onClick = { onSelect(null); open = false })
            values.forEach { v -> DropdownMenuItem(text = { Text(name(v)) }, onClick = { onSelect(v); open = false }) }
        }
    }
}

@Composable
private fun CreateExerciseDialog(onDismiss: () -> Unit, onCreate: (String, MuscleGroup, Equipment, TrackingType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }
    var tracking by remember { mutableStateOf(TrackingType.WEIGHT_REPS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnumFilter(stringResource(R.string.filter_muscle), MuscleGroup.entries, muscle, { it.label() }) { it?.let { m -> muscle = m } }
                    EnumFilter(stringResource(R.string.filter_equipment), Equipment.entries, equipment, { it.label() }) { it?.let { e -> equipment = e } }
                }
                Text(stringResource(R.string.track), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TrackingType.WEIGHT_REPS to stringResource(R.string.track_weight_reps),
                        TrackingType.REPS_ONLY to stringResource(R.string.track_reps),
                        TrackingType.DURATION to stringResource(R.string.track_time),
                    )
                        .forEach { (t, l) -> FilterChip(selected = tracking == t, onClick = { tracking = t }, label = { Text(l) }) }
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onCreate(name, muscle, equipment, tracking) }) { Text(stringResource(R.string.action_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
