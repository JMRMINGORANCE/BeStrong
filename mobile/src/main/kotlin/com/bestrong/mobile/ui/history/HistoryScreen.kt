package com.bestrong.mobile.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.mobile.R
import com.bestrong.mobile.share.WorkoutShareCard
import com.bestrong.mobile.ui.components.EmptyState
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.workout.describeSet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, onWorkoutStarted: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val days by vm.trainingDays.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val heatmap by vm.heatmap.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    // Material3 Adaptive 1.1: la navegación entre paneles es suspend (animada)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val deletedMsg = stringResource(R.string.undo_workout_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deleted) {
        if (deleted != null) {
            if (snackbar.showSnackbar(deletedMsg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted()
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    Column(Modifier.fillMaxSize()) {
                        TopAppBar(title = { Text(stringResource(R.string.nav_history)) })
                        if (history.isEmpty()) {
                            EmptyState(Icons.Default.History, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
                            return@Column
                        }
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { vm.query.value = it },
                                    placeholder = { Text(stringResource(R.string.history_search)) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (query.isBlank()) {
                                item { YearHeatmap(heatmap, Modifier.padding(vertical = 4.dp)) }
                                item { CalendarMonth(days, Modifier.padding(bottom = 8.dp)) }
                            }
                            items(filtered, key = { it.id }) { w ->
                                WorkoutCard(w, Modifier.clickable { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, w.id) } })
                            }
                        }
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    val id = (navigator.currentDestination?.contentKey as? String)
                    val w = history.firstOrNull { it.id == id }
                    if (w == null) {
                        EmptyState(Icons.Default.FitnessCenter, stringResource(R.string.history_pick_title), stringResource(R.string.history_pick_body))
                    } else {
                        WorkoutDetail(
                            w = w,
                            vm = vm,
                            onBack = if (navigator.canNavigateBack()) ({ scope.launch { navigator.navigateBack() } }) else null,
                            onWorkoutStarted = onWorkoutStarted,
                        )
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun WorkoutCard(w: Workout, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (w.source == WorkoutSource.WATCH) Icon(Icons.Default.Watch, stringResource(R.string.logged_on_watch), Modifier.padding(start = 4.dp))
            }
            Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.history_card_detail, formatDuration(w.durationSeconds()), unit.formatVolume(w.totalVolume), w.completedSetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            w.exercises.take(4).forEach { we ->
                Text(stringResource(R.string.sets_times_exercise, we.sets.size, we.exercise.displayName()), style = MaterialTheme.typography.bodySmall)
            }
            if (w.exercises.size > 4) Text(stringResource(R.string.and_n_more, w.exercises.size - 4), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDetail(w: Workout, vm: HistoryViewModel, onBack: (() -> Unit)?, onWorkoutStarted: () -> Unit) {
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    val records = remember(w.id) { vm.recordsIn(w) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(w.name) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } } },
            actions = {
                Box {
                    IconButton(onClick = { WorkoutShareCard.share(context, w, records, unit) }) { Icon(Icons.Default.Share, stringResource(R.string.share_workout)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.repeat_workout)) }, onClick = { menu = false; vm.repeat(w, onWorkoutStarted) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.save_as_routine)) }, onClick = { menu = false; vm.saveAsRoutine(w) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; vm.delete(w.id); onBack?.invoke() })
                    }
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(formatDate(w.startedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.stat_duration), formatDuration(w.durationSeconds()), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_volume), unit.formatVolume(w.totalVolume), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stat_sets), "${w.completedSetCount}", Modifier.weight(1f))
                }
            }
            if (w.avgHeartRate != null || w.calories != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        w.avgHeartRate?.let { StatTile(stringResource(R.string.stat_avg_hr), stringResource(R.string.bpm_value, it), Modifier.weight(1f)) }
                        w.maxHeartRate?.let { StatTile(stringResource(R.string.stat_max_hr), stringResource(R.string.bpm_value, it), Modifier.weight(1f)) }
                        w.calories?.let { StatTile(stringResource(R.string.stat_calories), stringResource(R.string.kcal_value, it), Modifier.weight(1f)) }
                    }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.n_records, records.size), fontWeight = FontWeight.Bold)
                            records.forEach { Text(stringResource(R.string.record_line, it.exerciseName, it.type.label(), unit.format(it.value)), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (w.notes.isNotBlank()) item { Text(w.notes) }
            items(w.exercises, key = { it.id }) { we ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(we.exercise.displayName(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        we.sets.forEachIndexed { i, set ->
                            Row {
                                Text(if (set.type.short.isEmpty()) "${i + 1}" else set.type.short, Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold)
                                Text(describeSet(set, we.exercise.tracking, unit), Modifier.weight(1f))
                                if (set.estimatedOneRepMax > 0) Text(stringResource(R.string.one_rm_value, unit.format(set.estimatedOneRepMax)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
