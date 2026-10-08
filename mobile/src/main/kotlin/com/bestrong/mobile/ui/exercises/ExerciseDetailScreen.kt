package com.bestrong.mobile.ui.exercises

import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.R
import androidx.compose.ui.res.stringResource
import com.bestrong.mobile.ui.components.LocalWeightUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.stats.RecordType
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.EmptyState
import com.bestrong.mobile.ui.components.LineChart
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.workout.describeSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(vm: ExercisesViewModel, exerciseId: String, onBack: (() -> Unit)?) {
    val unit = LocalWeightUnit.current
    val flow = remember(exerciseId) { vm.detail(exerciseId) }
    val state by flow.collectAsStateWithLifecycle()
    val s = state ?: return
    var metric by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.exercise.displayName()) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } } },
            actions = {
                if (s.exercise.isCustom) IconButton(onClick = { vm.deleteCustom(s.exercise.id); onBack?.invoke() }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete_exercise))
                }
            },
        )
        if (s.progress.isEmpty()) {
            EmptyState(Icons.Default.ShowChart, stringResource(R.string.no_data_title), stringResource(R.string.no_data_body))
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item { SectionHeader(stringResource(R.string.nav_history)) }
            items(s.recentSessions, key = { it.id }) { w ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(w.name, fontWeight = FontWeight.SemiBold)
                        Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.bodySmall)
                        w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.forEachIndexed { i, set ->
                            Text("${i + 1}. ${describeSet(set, s.exercise.tracking, unit)}" + if (set.estimatedOneRepMax > 0) stringResource(R.string.set_line_1rm, unit.format(set.estimatedOneRepMax)) else "")
                        }
                    }
                }
            }
        }
    }
}
