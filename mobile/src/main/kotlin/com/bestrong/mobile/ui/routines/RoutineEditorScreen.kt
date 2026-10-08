package com.bestrong.mobile.ui.routines

import com.bestrong.mobile.ui.components.LocalWeightUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.TrackingType
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(vm: RoutineEditorViewModel, onAddExercises: () -> Unit, onClose: () -> Unit) {
    val unit = LocalWeightUnit.current
    val routine by vm.routine.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Nueva rutina" else "Editar rutina") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar") } },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { vm.delete(onClose) }) { Icon(Icons.Default.Delete, "Eliminar rutina") }
                    TextButton(onClick = { vm.save(onClose) }) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // En el Fold desplegado, el editor no se estira de lado a lado
            val content = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
            item {
                Column(content, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(routine.name, vm::rename, label = { Text("Nombre de la rutina") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(routine.notes, vm::setNotes, label = { Text("Notas") }, modifier = Modifier.fillMaxWidth())
                }
            }
            itemsIndexed(routine.exercises, key = { _, re -> re.id }) { index, re ->
                Card(content) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(re.exercise.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text("Descanso ${formatDuration(re.restSeconds.toLong())}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.moveExercise(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
                            IconButton(onClick = { vm.moveExercise(index, index + 1) }, enabled = index < routine.exercises.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Bajar") }
                            IconButton(onClick = { vm.removeExercise(re.id) }) { Icon(Icons.Default.Close, "Quitar ${re.exercise.name}") }
                        }
                        if (index < routine.exercises.lastIndex) {
                            val linked = re.supersetId != null && re.supersetId == routine.exercises[index + 1].supersetId
                            FilterChip(
                                selected = linked,
                                onClick = { vm.toggleSupersetWithNext(re.id) },
                                label = { Text(if (linked) "En superserie con el siguiente" else "Superserie con el siguiente") },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(60, 90, 120, 180).forEach { s ->
                                TextButton(onClick = { vm.setRest(re.id, s) }) {
                                    Text(formatDuration(s.toLong()), fontWeight = if (s == re.restSeconds) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        re.sets.forEachIndexed { i, set ->
                            key(re.id, i, re.sets.size) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${i + 1}", Modifier.widthIn(min = 24.dp), fontWeight = FontWeight.Bold)
                                if (re.exercise.tracking == TrackingType.DURATION) {
                                    SmallNumberField("Seg", set.durationSec.toString(), Modifier.weight(1f)) { v -> vm.updateSet(re.id, i) { it.copy(durationSec = v.toIntOrNull() ?: 0) } }
                                } else {
                                    SmallNumberField(unit.label, unit.format(set.weightKg), Modifier.weight(1f), decimal = true) { v ->
                                        vm.updateSet(re.id, i) { it.copy(weightKg = unit.parseToKg(v) ?: 0.0) }
                                    }
                                    SmallNumberField("reps", set.reps.toString(), Modifier.weight(1f)) { v -> vm.updateSet(re.id, i) { it.copy(reps = v.toIntOrNull() ?: 0) } }
                                }
                                IconButton(onClick = { vm.removeSet(re.id, i) }) { Icon(Icons.Default.Close, "Quitar serie") }
                            } }
                        }
                        OutlinedButton(onClick = { vm.addSet(re.id) }, modifier = Modifier.fillMaxWidth()) { Text("+ Añadir serie") }
                    }
                }
            }
            item {
                Button(onClick = onAddExercises, modifier = content) {
                    Icon(Icons.Default.Add, null)
                    Text("Añadir ejercicios", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallNumberField(label: String, value: String, modifier: Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    // Texto local: así "82," no se convierte en "82" mientras se escribe
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)
            onChange(text)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
