package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.SetType
import com.bestrong.core.stats.RecordType
import com.bestrong.core.util.formatVolume
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LineChart
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.reasonText
import com.bestrong.mobile.ui.workout.describeSet
import com.bestrong.mobile.ui.workout.setRemark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(vm: ExercisesViewModel, exerciseId: String, onBack: (() -> Unit)?) {
    val unit = LocalWeightUnit.current
    var includeVariants by rememberSaveable(exerciseId) { mutableStateOf(false) }
    val flow = remember(exerciseId, includeVariants) { vm.detail(exerciseId, includeVariants) }
    val state by flow.collectAsStateWithLifecycle()
    val s = state ?: return
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (editing) {
        ExerciseFormDialog(initial = s.exercise, onDismiss = { editing = false }, onSave = { vm.updateCustom(it); editing = false })
    }
    if (confirmDelete) {
        val used = remember { vm.routinesUsing(s.exercise.id) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_exercise_q, s.exercise.name)) },
            text = { Text(if (used > 0) stringResource(R.string.delete_exercise_used, used) else stringResource(R.string.delete_exercise_body)) },
            confirmButton = { Button(onClick = { confirmDelete = false; vm.deleteCustom(s.exercise.id); onBack?.invoke() }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.exercise.displayName()) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } } },
            actions = {
                if (s.exercise.isCustom) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_exercise)) }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete_exercise))
                    }
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ExerciseMediaSection(vm, s.exercise) }
            if (s.variants.size > 1) {
                item {
                    // Variantes enlazadas: historial y récords juntos o por separado
                    val names = s.variants.filter { it.id != s.exercise.id }.map { it.displayName() }.joinToString()
                    val desc = stringResource(R.string.include_variants_desc, names)
                    FilterChip(
                        selected = includeVariants,
                        onClick = { includeVariants = !includeVariants },
                        label = { Text(stringResource(R.string.include_variants, s.variants.size - 1)) },
                        modifier = Modifier.semantics { stateDescription = desc },
                    )
                }
            }
            if (s.progress.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.no_data_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_data_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@LazyColumn
            }
            item {
                Text(
                    "${s.exercise.primaryMuscle.label()} · ${s.exercise.equipment.label()}" +
                        (s.exercise.secondaryMuscles.takeIf { it.isNotEmpty() }?.map { it.label() }?.joinToString()?.let { stringResource(R.string.secondary_muscles, it) } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val labels = listOf(
                    stringResource(R.string.metric_1rm),
                    stringResource(R.string.metric_max_weight),
                    stringResource(R.string.metric_volume),
                    stringResource(R.string.metric_reps),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, l ->
                        SegmentedButton(selected = metric == i, onClick = { metric = i }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l) }
                    }
                }
            }
            item {
                val points = s.progress.takeLast(30).map { p ->
                    ChartPoint(
                        formatShortDate(p.dateEpochMs),
                        when (metric) { 0 -> p.bestOneRepMax; 1 -> p.heaviestWeight; 2 -> p.volume; else -> p.bestReps.toDouble() },
                    )
                }
                LineChart(points, Modifier.fillMaxWidth().padding(vertical = 8.dp), valueFormatter = { unit.format(it) })
            }
            item { SectionHeader(stringResource(R.string.personal_records)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecordType.entries.forEach { type ->
                            val pr = s.records[type] ?: return@forEach
                            Row(Modifier.fillMaxWidth()) {
                                Text(type.label(), Modifier.weight(1f))
                                Text(
                                    when (type) {
                                        RecordType.MOST_REPS -> stringResource(R.string.n_reps, pr.value.toInt())
                                        RecordType.BEST_SET_VOLUME -> unit.formatVolume(pr.value)
                                        else -> unit.format(pr.value, withUnit = true)
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            s.nextTarget?.let { t ->
                item {
                    val work = t.sets.firstOrNull { it.type != SetType.WARMUP } ?: t.sets.first()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionHeader(stringResource(R.string.next_target))
                            Text(
                                stringResource(R.string.next_target_line, t.sets.count { it.type != SetType.WARMUP }, describeSet(work, s.exercise.tracking, unit)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(t.reasonText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (s.strengthCurve.size >= 2) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(stringResource(R.string.strength_curve))
                            Text(stringResource(R.string.strength_curve_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BarChart(s.strengthCurve.map { (reps, kg) -> ChartPoint("$reps", unit.fromKg(kg)) })
                            // Mejores marcas por repeticiones (1RM, 3RM, 5RM…), las que se suelen buscar
                            val repMaxes = REP_MAXES.mapNotNull { r -> s.strengthCurve[r]?.let { r to it } }
                            if (repMaxes.isNotEmpty()) {
                                Text(
                                    repMaxes.joinToString("  ·  ") { (r, kg) -> stringResource(R.string.rep_max, r, unit.format(kg, withUnit = true)) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.nav_history)) }
            items(s.recentSessions, key = { it.id }) { w ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(w.name, fontWeight = FontWeight.SemiBold)
                        Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.bodySmall)
                        // Notas de ese día para el ejercicio («hombro cargado», «agarre más abierto»…)
                        w.exercises.filter { it.exercise.id == exerciseId && it.notes.isNotBlank() }.forEach {
                            Text("✎ " + it.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.forEachIndexed { i, set ->
                            Text("${i + 1}. ${describeSet(set, s.exercise.tracking, unit)}" + if (set.estimatedOneRepMax > 0) stringResource(R.string.set_line_1rm, unit.format(set.estimatedOneRepMax)) else "")
                            setRemark(set)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

private val REP_MAXES = listOf(1, 3, 5, 8, 10, 12)
