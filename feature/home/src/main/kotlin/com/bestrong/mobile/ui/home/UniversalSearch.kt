package com.bestrong.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.Journal
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.components.label
import com.bestrong.mobile.ui.components.uiLanguage

/** Qué se enseña: todo o una sola clase de resultado. */
private enum class SearchScope { ALL, ROUTINES, EXERCISES, WORKOUTS, SETTINGS }

/**
 * Algo buscable de Ajustes: la página que abre (el nombre de su `SettingsPageId`), cómo se llama esa página
 * y el título y resumen de lo buscado (las palabras con que se encuentra).
 */
private data class SettingEntry(val page: String, val pageTitle: Int, val title: Int, val summary: Int)

private val SETTINGS = listOf(
    SettingEntry("TRAINING", R.string.s_training_title, R.string.s_training_title, R.string.s_training_summary),
    SettingEntry("TRAINING", R.string.s_training_title, R.string.s_body_title, R.string.body_data_body),
    SettingEntry("TRAINING", R.string.s_training_title, R.string.settings_during, R.string.s_during_summary),
    SettingEntry("APPEARANCE", R.string.appearance, R.string.appearance, R.string.style_footer),
    SettingEntry("DATA", R.string.s_data_title, R.string.s_data_title, R.string.s_data_summary),
    SettingEntry("DATA", R.string.s_data_title, R.string.s_health_title, R.string.s_health_summary),
    SettingEntry("DATA", R.string.s_data_title, R.string.s_privacy_title, R.string.s_privacy_summary),
    SettingEntry("CONNECTIONS", R.string.perm_title, R.string.perm_title, R.string.perm_subtitle),
    SettingEntry("CONNECTIONS", R.string.perm_title, R.string.s_notif_title, R.string.reminders),
    SettingEntry("ADVANCED", R.string.s_advanced_title, R.string.s_advanced_title, R.string.s_advanced_summary),
)

/** Búsquedas recientes (mientras la app sigue abierta; no se guardan en disco). */
private object SearchRecents {
    val items = mutableStateListOf<String>()
    fun add(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        items.remove(t)
        items.add(0, t)
        while (items.size > MAX_RECENTS) items.removeAt(items.lastIndex)
    }
}

/**
 * Búsqueda universal (propuesta 01): una barra que busca a la vez rutinas, ejercicios, entrenos pasados (nombre,
 * fecha, nota, ejercicio o etiqueta) y páginas de Ajustes, sin tildes. Sin texto, acciones sugeridas (lo que toca hoy,
 * repetir el último) y las búsquedas recientes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalSearchScreen(
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenWorkout: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenPastWorkout: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    val routines by vm.routines.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val suggested by vm.suggestedRoutine.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf(SearchScope.ALL) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val context = LocalContext.current
    val lang = uiLanguage()
    val q = WorkoutStats.plainText(query.trim())

    // Lo buscable de cada entreno se calcula una vez (con la fecha escrita: «octubre», «31 oct»)
    val workoutText = remember(history, lang) {
        history.filter { it.isFinished }.associate { w ->
            w.id to WorkoutStats.searchText(w) { it.displayName(context) } + "\n" + WorkoutStats.plainText(formatDate(w.startedAtEpochMs) + " " + formatShortDate(w.startedAtEpochMs))
        }
    }
    val bests = remember(history) { WorkoutStats.personalRecords(history).mapValues { (_, r) -> r[com.bestrong.core.stats.RecordType.BEST_ONE_REP_MAX]?.value ?: 0.0 } }
    val settingTexts = SETTINGS.map { it to stringResource(it.title) + " " + stringResource(it.summary) }

    val foundExercises = remember(q, exercises, lang) { if (q.isEmpty()) emptyList() else ExerciseLibrary.search(exercises, query) { ExerciseLibrary.displayName(it, lang) } }
    val foundRoutines = remember(q, routines) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            routines.filter { !it.archived }.filter { r ->
                WorkoutStats.plainText(r.displayName).contains(q) || r.exercises.any { WorkoutStats.plainText(ExerciseLibrary.displayName(it.exercise, lang)).contains(q) }
            }
        }
    }
    val foundWorkouts = remember(q, workoutText) { if (q.isEmpty()) emptyList() else history.filter { workoutText[it.id]?.contains(q) == true } }
    val foundSettings = if (q.isEmpty()) emptyList() else settingTexts.filter { WorkoutStats.plainText(it.second).contains(q) }.map { it.first }
    val total = foundExercises.size + foundRoutines.size + foundWorkouts.size + foundSettings.size

    // Al abrir un resultado, lo buscado pasa a «Recientes»
    fun tracked(action: () -> Unit): () -> Unit = { SearchRecents.add(query); action() }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                placeholder = { Text(stringResource(R.string.srch_open) + "…", maxLines = 1) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.srch_clear)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { SearchRecents.add(query) }),
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun label(res: Int, n: Int) = if (q.isEmpty()) res to null else res to n
            listOf(
                SearchScope.ALL to label(R.string.srch_all, total),
                SearchScope.ROUTINES to label(R.string.srch_routines, foundRoutines.size),
                SearchScope.EXERCISES to label(R.string.srch_exercises, foundExercises.size),
                SearchScope.WORKOUTS to label(R.string.srch_workouts, foundWorkouts.size),
                SearchScope.SETTINGS to label(R.string.srch_settings, foundSettings.size),
            ).forEach { (s, l) ->
                val (res, n) = l
                FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(stringResource(res) + (n?.let { " · $it" } ?: "")) })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.isEmpty()) {
                suggestions(
                    today = today?.let { it.routine.name to { vm.startProgramSession(it, onOpenWorkout); Unit } },
                    suggested = suggested?.takeIf { today == null }?.let { r -> r.displayName to { vm.startRoutine(r, onOpenWorkout); Unit } },
                    last = history.firstOrNull { it.isFinished }?.let { w -> w.name to { vm.repeatLast(onOpenWorkout); Unit } },
                )
                if (SearchRecents.items.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.srch_recent), Modifier.weight(1f))
                            TextButton(onClick = { SearchRecents.items.clear() }) { Text(stringResource(R.string.srch_clear_recent)) }
                        }
                    }
                    items(SearchRecents.items.toList(), key = { "r-$it" }) { r ->
                        ResultRow(Icons.Default.History, r, null, onClick = { query = r })
                    }
                }
                item { SectionLabel(stringResource(R.string.srch_try)) }
                item {
                    // Ejemplos sacados de lo tuyo: tu ejercicio más hecho, tu etiqueta más usada y tu gimnasio
                    val examples = listOfNotNull(
                        history.flatMap { w -> w.exercises.map { it.exercise } }.groupingBy { it.id }.eachCount().maxByOrNull { it.value }?.key
                            ?.let { id -> exercises.firstOrNull { it.id == id }?.displayName() },
                        Journal.tagCounts(history).firstOrNull()?.first?.let { "#$it" },
                        history.firstNotNullOfOrNull { it.gymName },
                        stringResource(R.string.appearance).substringBefore(' '),
                    ).distinct()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        examples.forEach { e -> SuggestionChip(onClick = { query = e }, label = { Text("«$e»") }) }
                    }
                }
                return@LazyColumn
            }
            if (total == 0) {
                item { Text(stringResource(R.string.srch_no_results, query.trim()), Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@LazyColumn
            }
            val all = scope == SearchScope.ALL
            val limit = if (all) PREVIEW else Int.MAX_VALUE
            if ((all || scope == SearchScope.EXERCISES) && foundExercises.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.srch_exercises), count = foundExercises.size) }
                items(foundExercises.take(limit), key = { "e-${it.id}" }) { e -> ExerciseResult(e, bests[e.id], tracked { onOpenExercise(e.id) }) }
            }
            if ((all || scope == SearchScope.ROUTINES) && foundRoutines.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.srch_routines), count = foundRoutines.size) }
                items(foundRoutines.take(limit), key = { "r-${it.id}" }) { r ->
                    RoutineResult(r, onStart = tracked { vm.startRoutine(r, onOpenWorkout) }, onEdit = tracked { onEditRoutine(r.id) })
                }
            }
            if ((all || scope == SearchScope.WORKOUTS) && foundWorkouts.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.srch_workouts), count = foundWorkouts.size) }
                items(foundWorkouts.take(limit), key = { "w-${it.id}" }) { w -> WorkoutResult(w, q, tracked { onOpenPastWorkout(w.id) }) }
            }
            if ((all || scope == SearchScope.SETTINGS) && foundSettings.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.srch_settings), count = foundSettings.size) }
                items(foundSettings, key = { "s-${it.page}-${it.title}" }) { s ->
                    ResultRow(Icons.Default.Settings, stringResource(s.title), stringResource(R.string.srch_settings_path, stringResource(s.pageTitle)), onClick = tracked { onOpenSettings(s.page) })
                }
            }
        }
    }
}

/** Sin texto: lo que toca hoy (o la rutina sugerida) y repetir el último entreno. */
private fun LazyListScope.suggestions(today: Pair<String, () -> Unit>?, suggested: Pair<String, () -> Unit>?, last: Pair<String, () -> Unit>?) {
    if (today == null && suggested == null && last == null) return
    item { SectionLabel(stringResource(R.string.srch_suggested)) }
    today?.let { (name, go) -> item { ActionCard(Icons.Default.PlayArrow, stringResource(R.string.srch_start, name), stringResource(R.string.srch_today_badge), primary = true, onClick = go) } }
    suggested?.let { (name, go) -> item { ActionCard(Icons.Default.PlayArrow, stringResource(R.string.srch_start, name), stringResource(R.string.srch_suggested_badge), primary = true, onClick = go) } }
    last?.let { (name, go) -> item { ActionCard(Icons.Default.Replay, stringResource(R.string.srch_repeat, name), null, primary = false, onClick = go) } }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        count?.let { Text("$it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Acción grande («Empezar Torso A · Hoy toca»). */
@Composable
private fun ActionCard(icon: ImageVector, title: String, badge: String?, primary: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(if (primary) scheme.primaryContainer else scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(if (primary) scheme.primary else scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (primary) scheme.onPrimary else scheme.onSurface)
        }
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (primary) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        badge?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (primary) scheme.onPrimaryContainer else scheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
    }
}

/** Fila genérica: icono, título, subtítulo y flecha. */
@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = stringResource(R.string.action_open), onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke() ?: Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseResult(e: Exercise, best: Double?, onOpen: () -> Unit) {
    val unit = LocalWeightUnit.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClickLabel = stringResource(R.string.srch_exercise_open), onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.bestrong.mobile.ui.components.ExerciseThumb(e, 40.dp, enlargeOnTap = false)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.displayName(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                best?.takeIf { it > 0 }?.let { stringResource(R.string.srch_best_1rm, unit.format(it * com.bestrong.mobile.ui.components.pairFactor(e), withUnit = true)) },
                e.primaryMuscle.label(),
            )
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        FilledTonalButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(stringResource(R.string.srch_exercise_open)) }
    }
}

@Composable
private fun RoutineResult(r: Routine, onStart: () -> Unit, onEdit: () -> Unit) {
    ResultRow(
        Icons.AutoMirrored.Filled.List,
        r.displayName,
        pluralStringResource(R.plurals.exercise_count, r.exercises.size, r.exercises.size),
        onClick = onEdit,
        trailing = {
            androidx.compose.material3.Button(onClick = onStart, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Text(stringResource(R.string.srch_start_short), Modifier.padding(start = 4.dp))
            }
        },
    )
}

/** Un entreno: nombre y fecha; debajo, lo que coincide (la nota o el ejercicio). */
@Composable
private fun WorkoutResult(w: Workout, q: String, onOpen: () -> Unit) {
    val noteHit = w.notes.takeIf { WorkoutStats.plainText(it).contains(q) }
    val lang = uiLanguage()
    fun name(e: Exercise) = ExerciseLibrary.displayName(e, lang)
    val exHit = w.exercises.firstOrNull { WorkoutStats.plainText(name(it.exercise)).contains(q) }?.exercise?.let(::name)
    val tagHit = w.tags.firstOrNull { WorkoutStats.plainText(it).contains(q.removePrefix("#")) }
    val subtitle = when {
        noteHit != null -> stringResource(R.string.srch_note, noteHit)
        tagHit != null -> "#$tagHit"
        exHit != null -> exHit
        else -> w.exercises.take(3).joinToString(" · ") { name(it.exercise) }
    }
    ResultRow(if (noteHit != null) Icons.Default.EventNote else Icons.Default.FitnessCenter, "${w.name} · ${formatShortDate(w.startedAtEpochMs)}", subtitle, onClick = onOpen)
}

/** Resultados de cada clase que se ven en «Todo» (el resto, en su pestaña). */
private const val PREVIEW = 4
private const val MAX_RECENTS = 6
