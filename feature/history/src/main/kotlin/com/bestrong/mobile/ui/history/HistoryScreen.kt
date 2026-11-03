package com.bestrong.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RestStats
import com.bestrong.core.stats.RestSummary
import com.bestrong.core.stats.formatValue
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.mobile.share.DragShare
import com.bestrong.mobile.share.WorkoutShareCard
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.EmptyState
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.StatTile
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.spokenAs
import com.bestrong.mobile.ui.workout.describeSet
import com.bestrong.mobile.ui.workout.setRemark
import kotlinx.coroutines.launch

/** Otra vista de Progreso (Cuerpo, Fuerza) que se elige en los segmentos de la cabecera, junto al historial. */
class ProgressSegment(val label: String, val content: @Composable () -> Unit)

/**
 * Progreso: cabecera con el mes y el avatar, segmentos (Historial y los que lleguen en [otherSegments]) y, en
 * Historial, el calendario, «Tu año» y los entrenos con su detalle al lado en el Fold desplegado.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    onWorkoutStarted: () -> Unit,
    onGoTrain: () -> Unit = {},
    /** Entreno a abrir al llegar (desde la búsqueda universal); [onWorkoutOpened] avisa de que ya se abrió. */
    openWorkoutId: String? = null,
    onWorkoutOpened: () -> Unit = {},
    /** Avatar de la cabecera: abre Tú. */
    onOpenProfile: () -> Unit = {},
    otherSegments: List<ProgressSegment> = emptyList(),
) {
    val history by vm.history.collectAsStateWithLifecycle()
    val recordCounts by vm.recordCounts.collectAsStateWithLifecycle()
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
    EditUndoSnackbars(vm, snackbar, undoLabel)

    CopiedSnackbar(vm, snackbar) { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } }
    LaunchedEffect(openWorkoutId) {
        openWorkoutId?.let { id ->
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
            onWorkoutOpened()
        }
    }
    // Lista de tarjetas o diario (nota, valoración y etiquetas de cada sesión)
    var journal by rememberSaveable { mutableStateOf(false) }
    // Segmento de Progreso: 0 = Historial; los demás, [otherSegments]
    var segment by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }

    var logging by remember { mutableStateOf(false) }
    // Día tocado en el calendario (sin entreno): el diálogo empieza en él
    var logStart by remember { mutableStateOf<Long?>(null) }
    if (logging) {
        val routines by vm.routines.collectAsStateWithLifecycle()
        LogPastWorkoutDialog(
            routines = routines,
            onLog = { r, start, end ->
                logging = false
                vm.logPast(r, start, end) { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } }
            },
            onDismiss = { logging = false },
            startEpochMs = logStart,
        )
    }

    Box(Modifier.fillMaxSize()) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    // Sin barra superior: el título no puede quedar bajo la barra de estado
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                        val monthYear = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale))
                        com.bestrong.mobile.ui.components.EnfoqueHeader(monthYear, stringResource(R.string.nav_progress), onOpenProfile, Modifier.padding(horizontal = 20.dp)) {
                            if (segment == 0) {
                                IconButton(onClick = {
                                    logStart = null
                                    logging = true
                                }) { Icon(Icons.Default.EditCalendar, stringResource(R.string.log_past_title)) }
                            }
                        }
                        if (otherSegments.isNotEmpty()) {
                            com.bestrong.mobile.ui.components.EnfoqueSegments(
                                listOf(stringResource(R.string.enf_prog_history)) + otherSegments.map { it.label },
                                segment,
                                { segment = it },
                                Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        if (segment > 0) {
                            otherSegments.getOrNull(segment - 1)?.content?.invoke()
                            return@Column
                        }
                        if (history.isEmpty()) {
                            EmptyState(
                                Icons.Default.History,
                                stringResource(R.string.history_empty_title),
                                stringResource(R.string.history_empty_body),
                                action = stringResource(R.string.history_empty_action) to onGoTrain,
                            )
                            return@Column
                        }
                        HistoryList(
                            vm = vm,
                            journal = journal,
                            onJournal = { journal = it },
                            recordCounts = recordCounts,
                            onOpen = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
                            onLogDay = { day ->
                                logStart = pastWorkoutStart(day)
                                logging = true
                            },
                        )
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

/** Historial: calendario, «Tu año» y los entrenos por meses con su buscador y filtros. */
@Composable
private fun HistoryList(
    vm: HistoryViewModel,
    journal: Boolean,
    onJournal: (Boolean) -> Unit,
    recordCounts: Map<String, Int>,
    onOpen: (String) -> Unit,
    onLogDay: (java.time.LocalDate) -> Unit,
) {
    val history by vm.history.collectAsStateWithLifecycle()
    val days by vm.trainingDays.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filtered by vm.filtered.collectAsStateWithLifecycle()
    val year by vm.year.collectAsStateWithLifecycle()
    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val view = LocalView.current
    // Días con récord: en ámbar en el calendario
    val recordDays = remember(history, recordCounts) {
        history.filter { recordCounts.getOrDefault(it.id, 0) > 0 }
            .map { java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toSet()
    }
    // Primero el calendario (lo que más se mira), luego el año y la lista con su buscador
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (query.isBlank()) {
            item {
                com.bestrong.mobile.ui.components.Panel {
                    CalendarMonth(days, Modifier, selectedDay, onEmptyDay = onLogDay, onSelect = { vm.selectedDay.value = it }, recordDays = recordDays)
                }
            }
            item { com.bestrong.mobile.ui.components.Panel { YearSection(year) } }
        }
        item { com.bestrong.mobile.ui.components.SectionHeader(stringResource(R.string.history_workouts), large = true) }
        item { HistoryViewToggle(journal, onJournal) }
        item { com.bestrong.mobile.ui.components.SearchPill(query, { vm.query.value = it }, stringResource(R.string.history_search)) }
        item { GymFilterRow(vm) }
        item { RoutineFilterRow(vm) }
        item { TagFilterRow(vm) }
        if (journal && filtered.isEmpty()) item { Text(stringResource(R.string.diary_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        selectedDay?.let { day ->
            item {
                // Filtro por el día tocado en el calendario; tocar el chip lo quita
                FilterChip(
                    selected = true,
                    onClick = { vm.selectedDay.value = null },
                    label = { Text(stringResource(R.string.history_day_filter, formatShortDate(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))) },
                )
            }
        }
        item { FilteredSummary(vm, filtered) }
        // Separada por meses («Octubre 2026 · 12 entrenos»): con cientos de entrenos se sabe dónde se está
        val byMonth = filtered.groupBy { java.time.YearMonth.from(java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(java.time.ZoneId.systemDefault())) }
        byMonth.forEach { (month, monthWorkouts) ->
            item(key = "m-$month") { MonthHeader(month, monthWorkouts.size) }
            items(monthWorkouts, key = { it.id }) { w ->
                if (journal) {
                    DiaryEntry(w, onOpen = { onOpen(w.id) }, onTag = { vm.tagFilter.value = it })
                    return@items
                }
                // Pulsación larga: arrastrar el entreno como texto a otra app (pantalla dividida)
                val dragText = workoutText(w)
                WorkoutCard(
                    w,
                    recordCounts.getOrDefault(w.id, 0),
                    Modifier.clip(MaterialTheme.shapes.large).combinedClickable(
                        // Con etiquetas, TalkBack ofrece también la pulsación larga (si no, no se descubre)
                        onClickLabel = stringResource(R.string.action_open),
                        onLongClickLabel = stringResource(R.string.action_drag_share),
                        onLongClick = { DragShare.start(view, w.name, dragText) },
                        onClick = { onOpen(w.id) },
                    ),
                )
            }
        }
    }
}

/** Un entreno de la lista: la fecha en un sello a la izquierda, el nombre, sus datos en píldoras y sus ejercicios. */
@Composable
private fun WorkoutCard(w: Workout, records: Int, modifier: Modifier = Modifier) {
    val unit = LocalWeightUnit.current
    val date = java.time.Instant.ofEpochMilli(w.startedAtEpochMs).atZone(java.time.ZoneId.systemDefault())
    com.bestrong.mobile.ui.components.Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Sello con el día y el mes
            Column(
                // Sello neutro: el color se reserva para acción, hecho y récord
                Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontFamily = com.bestrong.mobile.ui.theme.FigureFont, fontWeight = FontWeight.Bold)
                Text(
                    date.month.getDisplayName(java.time.format.TextStyle.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).uppercase(),
                    style = com.bestrong.mobile.ui.theme.KickerStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(w.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(formatDate(w.startedAtEpochMs), style = MaterialTheme.typography.labelMedium, fontFamily = com.bestrong.mobile.ui.theme.FigureFont, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (w.source == WorkoutSource.WATCH) Icon(Icons.Default.Watch, stringResource(R.string.logged_on_watch), Modifier.padding(start = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bestrong.mobile.ui.components.MetaPill("⏱ " + formatDuration(w.durationSeconds()), Modifier.spokenAs(formatDuration(w.durationSeconds())))
            if (w.totalVolume > 0) com.bestrong.mobile.ui.components.MetaPill(unit.formatVolume(w.totalVolume))
            com.bestrong.mobile.ui.components.MetaPill(androidx.compose.ui.res.pluralStringResource(R.plurals.history_sets_done, w.completedSetCount, w.completedSetCount))
            // Día de récords a la vista sin abrir el entreno
            if (records > 0) RecordChip(androidx.compose.ui.res.pluralStringResource(R.plurals.n_records, records, records))
        }
        // Las imágenes de sus ejercicios y, debajo, cuáles fueron
        com.bestrong.mobile.ui.components.ExerciseThumbStrip(w.exercises.map { it.exercise }, 40.dp)
        val res = androidx.compose.ui.platform.LocalContext.current.resources
        val names = w.exercises.map { it.sets.size to it.exercise.displayName() }
        Text(
            names.joinToString { (n, name) -> res.getString(R.string.sets_times_exercise, n, name) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Chip ámbar de récord («★ 2 récords»): el único color de la tarjeta. */
@Composable
private fun RecordChip(text: String) {
    val record = com.bestrong.mobile.ui.theme.enfoque.record
    Text(
        "★ $text",
        Modifier.clip(androidx.compose.foundation.shape.CircleShape)
            .border(1.dp, record.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = com.bestrong.mobile.ui.theme.FigureFont,
        fontWeight = FontWeight.SemiBold,
        color = record,
        maxLines = 1,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDetail(w: Workout, vm: HistoryViewModel, onBack: (() -> Unit)?, onWorkoutStarted: () -> Unit) {
    val unit = LocalWeightUnit.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember(w.id) { mutableStateOf(false) }
    var editInfo by rememberSaveable { mutableStateOf(false) }
    var editSet by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<Pair<String, String>?, List<String>>({ it?.toList().orEmpty() }, { l -> l.takeIf { it.size == 2 }?.let { it[0] to it[1] } })) {
        mutableStateOf(null)
    }
    var copying by rememberSaveable { mutableStateOf(false) }
    // Comparar con otra sesión (propuesta 02) y editar las etiquetas del diario (propuesta 07)
    var comparing by rememberSaveable { mutableStateOf(false) }
    if (comparing) CompareSessionsDialog(w, vm) { comparing = false }
    var editingTags by rememberSaveable { mutableStateOf(false) }
    if (editingTags) {
        val counts by vm.tagCounts.collectAsStateWithLifecycle()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingTags = false },
            title = { Text(stringResource(R.string.diary_edit_tags)) },
            text = {
                com.bestrong.mobile.ui.components.TagEditor(
                    w.tags,
                    counts.map { it.first } + com.bestrong.mobile.ui.components.defaultTagSuggestions(),
                    { t -> vm.editWorkout(w) { it.copy(tags = t) } },
                )
            },
            confirmButton = { TextButton(onClick = { editingTags = false }) { Text(stringResource(R.string.action_done)) } },
        )
    }
    if (copying) {
        CopyToDayDialog(
            durationMin = w.durationSeconds() / 60,
            exercises = w.exercises.map { it.id to it.exercise.displayName() },
            onCopy = { s, e, ids ->
                copying = false
                vm.copyToDay(w, s, e, ids)
            },
            onDismiss = { copying = false },
        )
    }
    val records by produceState(emptyList<PersonalRecord>(), w) { value = vm.recordsIn(w) }
    val comparisons by produceState(emptyMap<String, com.bestrong.core.stats.WorkoutStats.ExerciseComparison>(), w) { value = vm.comparisonsFor(w) }
    val restByExercise = remember(w) { RestStats.byExercise(w) }
    val routinesById by vm.routinesById.collectAsStateWithLifecycle()
    if (editInfo) {
        EditPastWorkoutInfoDialog(
            w.name, w.notes, w.startedAtEpochMs, w.durationSeconds() / 60,
            onSave = { n, t, range ->
                if (range != null) vm.retime(w, range.first, range.second, n, t) else vm.editWorkout(w) { it.copy(name = n, notes = t) }
                editInfo = false
            },
            onDismiss = { editInfo = false },
        )
    }
    editSet?.let { (weId, setId) ->
        val we = w.exercises.firstOrNull { it.id == weId }
        val set = we?.sets?.firstOrNull { it.id == setId }
        if (we == null || set == null) {
            editSet = null
        } else {
            EditPastSetDialog(
                set = set,
                tracking = we.exercise.tracking,
                weightFactor = vm.weightFactor(we.exercise),
                onSave = { s -> vm.editWorkout(w) { WorkoutEditor.updateSet(it, weId, setId) { s } }; editSet = null },
                onDelete = { vm.removePastSet(w, weId, setId); editSet = null },
                onDismiss = { editSet = null },
            )
        }
    }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(w.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } } },
            actions = {
                // Corregir series es a lo que más se entra en un entreno pasado: a la vista, no en el menú
                if (editing) {
                    IconButton(onClick = { editing = false }) { Icon(Icons.Default.Check, stringResource(R.string.history_edit_done)) }
                } else {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, stringResource(R.string.history_edit)) }
                }
                Box {
                    IconButton(onClick = { WorkoutShareCard.share(context, w, records, unit, vm::weightFactor) }) { Icon(Icons.Default.Share, stringResource(R.string.share_workout)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.history_edit_info)) }, onClick = { menu = false; editInfo = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.cmp_action)) }, onClick = { menu = false; comparing = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.diary_edit_tags)) }, onClick = { menu = false; editingTags = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.repeat_workout)) }, onClick = { menu = false; vm.repeat(w, onWorkoutStarted) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.copy_to_day)) }, onClick = { menu = false; copying = true })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_as_routine)) },
                            onClick = {
                                menu = false
                                vm.saveAsRoutine(w)
                                android.widget.Toast.makeText(context, resources.getString(R.string.saved_as_routine), android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_text)) },
                            onClick = {
                                menu = false
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, vm.shareText(w))
                                context.startActivity(android.content.Intent.createChooser(send, null))
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; vm.delete(w.id); onBack?.invoke() })
                    }
                }
            },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(formatDate(w.startedAtEpochMs), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (editing) item { Text(stringResource(R.string.history_edit_hint), color = MaterialTheme.colorScheme.primary) }
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
                        w.calories?.let { StatTile(stringResource(R.string.stat_calories), (if (w.caloriesEstimated) "≈" else "") + stringResource(R.string.kcal_value, it), Modifier.weight(1f)) }
                    }
                }
                if (w.caloriesSource != null || w.activeCalories != null) {
                    item { caloriesSourceLine(w)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
            if (records.isNotEmpty()) {
                item {
                    // Los récords, destacados con el degradado de marca
                    com.bestrong.mobile.ui.components.HeroCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, Modifier.size(28.dp))
                            Text(pluralStringResource(R.plurals.n_records, records.size, records.size), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                        records.forEach {
                            // Con «suma de las dos mancuernas», el peso y el 1RM del par, como en las series (también en los propios)
                            val ex = w.exercises.firstOrNull { e -> e.exercise.id == it.exerciseId }?.exercise
                            val pairs = it.type == com.bestrong.core.stats.RecordType.HEAVIEST_WEIGHT || it.type == com.bestrong.core.stats.RecordType.BEST_ONE_REP_MAX
                            val factor = if (pairs && ex != null) vm.weightFactor(ex) else 1
                            Text(stringResource(R.string.record_line, it.exerciseLabel(), it.type.label(), it.type.formatValue(it.value * factor, unit)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item { RestSummaryLine(w) }
            item { com.bestrong.mobile.ui.components.WorkoutMuscleChips(w) }
            item { WorkoutGymLine(w, vm) }
            if (w.notes.isNotBlank()) item { Text(w.notes) }
            if (w.tags.isNotEmpty()) item { com.bestrong.mobile.ui.components.TagChips(w.tags) { editingTags = true } }
            items(w.exercises, key = { it.id }) { we ->
                com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                    Column(Modifier.padding(12.dp)) {
                        // Su imagen y el nombre, como en el entreno
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                            com.bestrong.mobile.ui.components.ExerciseThumb(we.exercise, 44.dp, Modifier.padding(end = 12.dp))
                            Text(we.exercise.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        we.sets.forEachIndexed { i, set ->
                            Row(if (editing) Modifier.clickable(onClickLabel = stringResource(R.string.history_edit_set), role = androidx.compose.ui.semantics.Role.Button) { editSet = we.id to set.id }.padding(vertical = 8.dp) else Modifier) {
                                Text(if (set.type.short.isEmpty()) "${i + 1}" else set.type.short, Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold)
                                Text(describeSet(set, we.exercise, unit, vm.weightFactor(we.exercise)), Modifier.weight(1f))
                                if (set.estimatedOneRepMax > 0 && we.exercise.tracking == com.bestrong.core.model.TrackingType.WEIGHT_REPS) {
                                    Text(stringResource(R.string.one_rm_value, unit.format(set.estimatedOneRepMax * vm.weightFactor(we.exercise))), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            // Nota de la serie y si fue con ayuda (se anotan en el entreno)
                            setRemark(set)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp)) }
                        }
                        restByExercise[we.id]?.let { r -> ExerciseRestLine(w, we, r, routinesById, vm) }
                        // Una vez por ejercicio (en su primer bloque) y solo con peso
                        val firstBlock = w.exercises.firstOrNull { it.exercise.id == we.exercise.id }?.id == we.id
                        if (firstBlock) comparisons[we.exercise.id]?.let { ComparisonLine(it, we.exercise.tracking) }
                        if (editing) TextButton(onClick = { vm.addPastSet(w, we.id) }) { Text(stringResource(R.string.add_set)) }
                    }
                }
            }
        }
    }
}

private const val NO_CHANGE_KG = 0.5

/** Línea de comparación de una sola cifra (reps o tiempo), en verde si subió. */
@Composable
private fun SimpleComparisonLine(delta: Double, text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (delta > 0) com.bestrong.mobile.ui.theme.statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** «Frente a la vez anterior: +250 kg de volumen · 1RM +2,5 kg», en verde si mejoró. */
@Composable
private fun ComparisonLine(c: com.bestrong.core.stats.WorkoutStats.ExerciseComparison, tracking: com.bestrong.core.model.TrackingType) {
    when (com.bestrong.core.util.ComparisonKind.of(tracking)) {
        com.bestrong.core.util.ComparisonKind.VOLUME -> WeightComparisonLine(c)
        // Sin peso: reps totales; de tiempo: segundos totales; cardio: distancia (más es mejor en todos)
        com.bestrong.core.util.ComparisonKind.REPS ->
            SimpleComparisonLine(c.repsDelta.toDouble(), stringResource(R.string.history_vs_previous_reps, com.bestrong.core.util.signedCount(c.repsDelta)))
        com.bestrong.core.util.ComparisonKind.TIME ->
            SimpleComparisonLine(c.durationDelta.toDouble(), stringResource(R.string.history_vs_previous_time, com.bestrong.core.util.signedDuration(c.durationDelta)))
        com.bestrong.core.util.ComparisonKind.DISTANCE -> com.bestrong.core.util.distanceComparison(c).let { (delta, text) ->
            SimpleComparisonLine(delta, stringResource(R.string.history_vs_previous_time, text))
        }
        com.bestrong.core.util.ComparisonKind.NONE -> Unit
    }
}

@Composable
private fun WeightComparisonLine(c: com.bestrong.core.stats.WorkoutStats.ExerciseComparison) {
    val unit = LocalWeightUnit.current

    // Lo que no llega a medio kilo es «sin cambio» (±0); si no, con su signo
    fun signed(v: Double, text: String) = when {
        kotlin.math.abs(v) < NO_CHANGE_KG -> "±0"
        v > 0 -> "+$text"
        else -> "−$text"
    }
    val up = c.best1rmDelta > 0.01 || (c.best1rmDelta >= -0.01 && c.volumeDelta > 0)
    Text(
        stringResource(
            R.string.history_vs_previous,
            signed(c.volumeDelta, unit.formatVolume(kotlin.math.abs(c.volumeDelta))),
            signed(c.best1rmDelta, unit.format(kotlin.math.abs(c.best1rmDelta), withUnit = true)),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (up) com.bestrong.mobile.ui.theme.statusColors.good else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Descanso real medio frente al previsto (solo si las series tienen hora de marcado). */
@Composable
private fun RestSummaryLine(w: Workout) {
    val rest = remember(w) { RestStats.summary(w) } ?: return
    Text(
        stringResource(R.string.rest_real_avg, formatDuration(rest.realSeconds.toLong()), formatDuration(rest.plannedSeconds.toLong())),
        style = MaterialTheme.typography.bodySmall,
        color = if (rest.realSeconds > rest.plannedSeconds * OVERRUN_FACTOR) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Descanso real de un ejercicio y, si la rutina lo tiene distinto, botón para usarlo en ella. */
@Composable
private fun ExerciseRestLine(w: Workout, we: com.bestrong.core.model.WorkoutExercise, r: RestSummary, routines: Map<String, com.bestrong.core.model.Routine>, vm: HistoryViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.rest_real_exercise, formatDuration(r.realSeconds.toLong()), formatDuration(r.plannedSeconds.toLong())),
            style = MaterialTheme.typography.bodySmall,
            color = if (r.realSeconds > r.plannedSeconds * OVERRUN_FACTOR) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // La misma aparición del ejercicio en la rutina (puede salir más de una vez)
        val occurrence = w.exercises.takeWhile { it.id != we.id }.count { it.exercise.id == we.exercise.id }
        val planned = w.routineId?.let { routines[it] }?.exercises?.filter { it.exercise.id == we.exercise.id }?.getOrNull(occurrence)?.restSeconds
        if (planned != null && planned != r.suggestedSeconds) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val done = stringResource(R.string.rest_use_real_done, formatDuration(r.suggestedSeconds.toLong()))
            val notFound = stringResource(R.string.routine_not_found)
            TextButton(
                onClick = {
                    vm.useRealRest(w, we.exercise.id, occurrence, r.suggestedSeconds) { ok ->
                        android.widget.Toast.makeText(context, if (ok) done else notFound, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(R.string.rest_use_real, formatDuration(r.suggestedSeconds.toLong())))
            }
        }
    }
}

/** Descansar más de un 50 % de lo previsto se resalta. */
private const val OVERRUN_FACTOR = 1.5

/** Texto del entreno para compartir o arrastrar: nombre, fecha y la mejor serie de cada ejercicio. */
@Composable
private fun workoutText(w: Workout): String {
    val unit = LocalWeightUnit.current
    val header = pluralStringResource(R.plurals.share_workout_text, w.completedSetCount, w.name, formatDate(w.startedAtEpochMs), w.completedSetCount)
    val lines = w.exercises.map { we ->
        val best = we.sets.filter { it.completed }.maxByOrNull { it.estimatedOneRepMax }
        "• " + we.exercise.displayName() + (best?.let { " — " + describeSet(it, we.exercise, unit) } ?: "")
    }
    return (listOf(header) + lines).joinToString("\n")
}

/** De dónde salen las calorías y cuántas son activas (sin el gasto en reposo). */
@Composable
private fun caloriesSourceLine(w: Workout): String? {
    val source = when (w.caloriesSource) {
        CaloriesSource.WATCH -> stringResource(R.string.calories_source_watch)
        CaloriesSource.HEART_RATE -> stringResource(R.string.calories_source_hr)
        CaloriesSource.ESTIMATE -> stringResource(R.string.calories_source_estimate)
        null -> null
    }
    val active = w.activeCalories?.let { stringResource(R.string.active_kcal, it) }
    return listOfNotNull(source, active).joinToString(" · ").ifEmpty { null }
}

/** Filtro por gimnasio (solo con entrenos en dos o más): las cargas de cada sitio no se comparan bien entre sí. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GymFilterRow(vm: HistoryViewModel) {
    val gyms by vm.gymNames.collectAsStateWithLifecycle()
    val selected by vm.gymFilter.collectAsStateWithLifecycle()
    // Con un filtro puesto la fila siempre sale (aunque ya no queden entrenos de ese gimnasio), para poder quitarlo
    val shown = (gyms + listOfNotNull(selected)).distinct()
    if (shown.size < 2 && selected == null) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { g ->
            FilterChip(selected = selected == g, onClick = { vm.gymFilter.value = if (selected == g) null else g }, label = { Text("🏋️ $g", Modifier.spokenAs(g)) })
        }
    }
}

/** Filtro de un toque por rutina (las más hechas); con un filtro puesto sale siempre, para poder quitarlo. */
@Composable
private fun RoutineFilterRow(vm: HistoryViewModel) {
    val top by vm.routineChips.collectAsStateWithLifecycle()
    val selected by vm.routineFilter.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    // La elegida siempre a la vista (aunque se borrara o saliera de las más hechas), para poder quitarla
    val chips = selected?.takeIf { id -> top.none { it.first == id } }
        ?.let { id -> top + (id to (history.firstOrNull { it.routineId == id }?.name ?: id)) } ?: top
    if (chips.size < 2 && selected == null) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { (id, name) ->
            FilterChip(selected = selected == id, onClick = { vm.routineFilter.value = if (selected == id) null else id }, label = { Text(name) })
        }
    }
}

/** «🏋️ Hotel» (con varios gimnasios): tocarlo deja corregir dónde se hizo (p. ej. si se guardó con otro activo). */
@Composable
private fun WorkoutGymLine(w: Workout, vm: HistoryViewModel) {
    val gyms by vm.gymProfileNames.collectAsStateWithLifecycle()
    if (w.gymName == null && gyms.size < 2) return
    var picking by remember(w.id) { mutableStateOf(false) }
    Text(
        "🏋️ " + (w.gymName ?: stringResource(R.string.history_gym_unknown)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Zona de toque de 48 dp: la línea sola mide unos 16
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.history_gym_change)) { picking = true }.minimumInteractiveComponentSize(),
    )
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.history_gym_change)) },
            text = {
                Column {
                    (gyms + listOfNotNull(w.gymName)).distinct().forEach { g ->
                        TextButton(onClick = { vm.changeGym(w, g); picking = false }, modifier = Modifier.fillMaxWidth()) { Text(g, Modifier.fillMaxWidth()) }
                    }
                    TextButton(onClick = { vm.changeGym(w, null); picking = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.history_gym_unknown), Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Hora con que empieza un entreno apuntado en [day]: hace una hora si es hoy (sin pasar a ayer justo después de
 * medianoche); si no, a las 18:00 de ese día.
 */
private fun pastWorkoutStart(day: java.time.LocalDate): Long {
    val zone = java.time.ZoneId.systemDefault()
    return if (day == java.time.LocalDate.now(zone)) {
        maxOf(System.currentTimeMillis() - 60 * 60_000L, day.atStartOfDay(zone).toInstant().toEpochMilli())
    } else {
        day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    }
}

/**
 * Lo que hay en la lista con los filtros puestos (persona, gimnasio, día, búsqueda): entrenos y tiempo, y
 * exportarlo en CSV. Si los filtros lo dejan vacío, lo dice y deja quitarlos.
 */
@Composable
private fun FilteredSummary(vm: HistoryViewModel, filtered: List<Workout>) {
    if (filtered.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_filtered_empty), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = vm::clearFilters) { Text(stringResource(R.string.history_clear_filters)) }
        }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val failed = stringResource(R.string.export_failed)
    val saved = stringResource(R.string.export_done)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportFiltered(it) { ok -> android.widget.Toast.makeText(context, if (ok) saved else failed, android.widget.Toast.LENGTH_SHORT).show() } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.history_filtered_summary, filtered.size, com.bestrong.core.util.formatDuration(filtered.sumOf { it.durationSeconds() })),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { launcher.launch("bestrong-historial.csv") }) { Text(stringResource(R.string.history_export_csv)) }
    }
}

/** «Entreno copiado» con «Abrir» para ir a la copia. */
@Composable
private fun CopiedSnackbar(vm: HistoryViewModel, snackbar: SnackbarHostState, onOpen: (String) -> Unit) {
    val msg by androidx.compose.runtime.rememberUpdatedState(stringResource(R.string.copy_to_day_done))
    val open by androidx.compose.runtime.rememberUpdatedState(stringResource(R.string.action_open))
    val openCopy by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    // Solo con la pantalla a la vista (una copia hecha en segundo plano se avisa al volver); se limpia al
    // recogerla, así no vuelve a salir si se sale con el aviso a la vista
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            vm.copied.collect { id ->
                if (id == null) return@collect
                vm.clearCopied()
                if (snackbar.showSnackbar(msg, open, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) openCopy(id)
            }
        }
    }
}

/** «Deshacer» de los cambios en un entreno pasado: el gimnasio cambiado y la serie borrada. */
@Composable
private fun EditUndoSnackbars(vm: HistoryViewModel, snackbar: SnackbarHostState, undoLabel: String) {
    val gymEdit by vm.gymEdit.collectAsStateWithLifecycle()
    val gymEditMsg = stringResource(R.string.undo_gym_changed)
    LaunchedEffect(gymEdit) {
        if (gymEdit != null) {
            if (snackbar.showSnackbar(gymEditMsg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoGymEdit() else vm.clearGymEdit()
        }
    }
    val setRemoved by vm.setRemoved.collectAsStateWithLifecycle()
    val setRemovedMsg = stringResource(R.string.undo_set_deleted)
    LaunchedEffect(setRemoved) {
        if (setRemoved != null) {
            if (snackbar.showSnackbar(setRemovedMsg, undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoSetRemoved() else vm.clearSetRemoved()
        }
    }
}

/** Cabecera de mes en la lista: «Octubre 2026 · 12 entrenos». */
@Composable
private fun MonthHeader(month: java.time.YearMonth, count: Int) {
    val name = month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))
        .replaceFirstChar { it.titlecase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) }
    Text(
        name + " · " + pluralStringResource(R.plurals.n_workouts_count, count, count),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() },
    )
}
