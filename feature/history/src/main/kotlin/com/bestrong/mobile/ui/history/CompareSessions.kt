package com.bestrong.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.SessionCompare
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatShortDate
import com.bestrong.mobile.ui.theme.LocalScreenColor
import com.bestrong.mobile.ui.theme.statusColors
import com.bestrong.mobile.ui.workout.describeSet
import kotlin.math.abs

/**
 * Comparador de sesiones (propuesta 02): [w] frente a otra (por defecto la anterior de la misma rutina o la que más
 * ejercicios comparte). Cifras generales con su diferencia y, por ejercicio, las series de las dos. Con el Fold
 * desplegado las dos sesiones van en columnas; plegado, cada serie con la de la otra debajo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompareSessionsDialog(w: Workout, vm: HistoryViewModel, onDismiss: () -> Unit) {
    val partners by produceState<Pair<Workout?, List<Workout>>?>(null, w) { value = vm.comparePartners(w) }
    var otherId by rememberSaveable(w.id) { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val p = partners
    val other = p?.let { (def, all) -> otherId?.let { id -> all.firstOrNull { it.id == id } } ?: def }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = LocalScreenColor.current, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.cmp_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                    actions = {
                        if (!p?.second.isNullOrEmpty()) IconButton(onClick = { picking = true }) { Icon(Icons.Default.SwapHoriz, stringResource(R.string.cmp_change)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                when {
                    p == null -> Unit
                    other == null -> Text(stringResource(R.string.cmp_none), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> CompareContent(SessionCompare.compare(w, other), vm)
                }
            }
        }
    }
    if (picking && p != null) {
        PartnerPicker(w, p.second, other?.id, onPick = { otherId = it; picking = false }) { picking = false }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompareContent(r: SessionCompare.Result, vm: HistoryViewModel) {
    val unit = LocalWeightUnit.current
    // Del mismo día (mañana y tarde): con la hora, para distinguirlas
    val context = androidx.compose.ui.platform.LocalContext.current
    val sameDay = formatShortDate(r.a.startedAtEpochMs) == formatShortDate(r.b.startedAtEpochMs)
    fun label(w: Workout) = formatShortDate(w.startedAtEpochMs) +
        if (sameDay) " " + com.bestrong.mobile.ui.components.formatClock(w.startedAtEpochMs, android.text.format.DateFormat.is24HourFormat(context)) else ""
    val otherDate = label(r.b)
    val thisDate = label(r.a)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                // Las dos sesiones: esta en el color de acento, la otra en gris
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SessionLabel(stringResource(R.string.cmp_this), "${r.a.name} · $thisDate", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    SessionLabel(otherDate, r.b.name, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                }
            }
            item {
                val dv = r.totalsA.volumeKg - r.totalsB.volumeKg
                if (r.totalsB.volumeKg > 0 && abs(dv) > 0.5) {
                    val pct = "%+d %%".format((dv / r.totalsB.volumeKg * 100).toInt())
                    Text(
                        stringResource(if (dv > 0) R.string.cmp_verdict_up else R.string.cmp_verdict_down, otherDate, pct),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (dv > 0) statusColors.good else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (wide) 5 else 2) {
                    val m = Modifier.weight(1f)
                    Metric(stringResource(R.string.stat_volume), unit.formatVolume(r.totalsA.volumeKg), unit.formatVolume(r.totalsB.volumeKg), r.totalsA.volumeKg - r.totalsB.volumeKg, signed(r.totalsA.volumeKg - r.totalsB.volumeKg) { unit.formatVolume(it) }, m)
                    Metric(stringResource(R.string.cmp_best1rm), unit.format(r.totalsA.bestOneRepMaxKg, withUnit = true), unit.format(r.totalsB.bestOneRepMaxKg, withUnit = true), r.totalsA.bestOneRepMaxKg - r.totalsB.bestOneRepMaxKg, signed(r.totalsA.bestOneRepMaxKg - r.totalsB.bestOneRepMaxKg) { unit.format(it, withUnit = true) }, m)
                    Metric(stringResource(R.string.stat_sets), "${r.totalsA.workSets}", "${r.totalsB.workSets}", (r.totalsA.workSets - r.totalsB.workSets).toDouble(), "%+d".format(r.totalsA.workSets - r.totalsB.workSets), m)
                    // Duración y descanso: ni mejor ni peor, sin color
                    Metric(stringResource(R.string.stat_duration), formatDuration(r.totalsA.durationSec), formatDuration(r.totalsB.durationSec), null, null, m)
                    Metric(
                        stringResource(R.string.cmp_rest),
                        r.totalsA.avgRestSec?.let { formatDuration(it.toLong()) } ?: stringResource(R.string.cmp_missing),
                        r.totalsB.avgRestSec?.let { formatDuration(it.toLong()) } ?: stringResource(R.string.cmp_missing),
                        null,
                        null,
                        m,
                    )
                }
            }
            items(r.rows, key = { it.exerciseId }) { row ->
                val ex = (r.a.exercises + r.b.exercises).first { it.exercise.id == row.exerciseId }.exercise
                ExerciseCompare(row, ex, vm.weightFactor(ex), otherDate, wide)
            }
        }
    }
}

private fun signed(d: Double, fmt: (Double) -> String): String = (if (d >= 0) "+" else "−") + fmt(abs(d))

@Composable
private fun SessionLabel(title: String, subtitle: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
            Text(title, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una cifra: la de esta sesión en grande, la otra debajo y la diferencia en color (verde si sube, si tiene sentido). */
@Composable
private fun Metric(label: String, a: String, b: String, delta: Double?, deltaText: String?, modifier: Modifier) {
    val color = when {
        delta == null || abs(delta) < 1e-6 -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0 -> statusColors.good
        else -> statusColors.bad
    }
    Column(
        modifier.widthIn(min = 140.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
            .clearAndSetSemantics { contentDescription = listOfNotNull(label, a, b, deltaText).joinToString(", ") }
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(a, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            if (deltaText != null && delta != null && abs(delta) > 1e-6) {
                Text(
                    (if (delta > 0) statusColors.goodMark else statusColors.badMark) + deltaText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Un ejercicio: series de las dos sesiones emparejadas por orden (las de trabajo), con el cambio de cada una. */
@Composable
private fun ExerciseCompare(row: SessionCompare.ExerciseRow, ex: Exercise, factor: Int, otherDate: String, wide: Boolean) {
    val unit = LocalWeightUnit.current
    val a = SessionCompare.workSets(row.setsA).ifEmpty { row.setsA }
    val b = SessionCompare.workSets(row.setsB).ifEmpty { row.setsB }
    com.bestrong.mobile.ui.components.Panel(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.bestrong.mobile.ui.components.ExerciseThumb(ex, 36.dp, Modifier.padding(end = 10.dp), enlargeOnTap = false)
            Text(ex.displayName(), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val d = row.volumeA - row.volumeB
            if (row.setsA.isNotEmpty() && row.setsB.isNotEmpty() && abs(d) > 0.5) {
                Text(
                    signed(d) { unit.formatVolume(it) },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (d > 0) statusColors.good else statusColors.bad,
                )
            }
        }
        when {
            row.setsB.isEmpty() -> Text(stringResource(R.string.cmp_only_this), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            row.setsA.isEmpty() -> Text(stringResource(R.string.cmp_only_other, otherDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val n = maxOf(a.size, b.size)
        // En columnas: cabecera con cada sesión
        if (wide && n > 0) {
            Row(Modifier.fillMaxWidth()) {
                Text("", Modifier.width(28.dp))
                Text(stringResource(R.string.cmp_this), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(otherDate, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        for (i in 0 until n) {
            val sa = a.getOrNull(i)
            val sb = b.getOrNull(i)
            if (wide) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SetCell(sa, sb, ex, factor, Modifier.weight(1f), emphasize = true)
                    SetCell(sb, null, ex, factor, Modifier.weight(1f), emphasize = false)
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(stringResource(R.string.cmp_set_n, i + 1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SetCell(sa, sb, ex, factor, Modifier.fillMaxWidth(), emphasize = true)
                    SetCell(sb, null, ex, factor, Modifier.fillMaxWidth().padding(start = 12.dp), emphasize = false, prefix = "$otherDate · ")
                }
            }
        }
    }
}

/** Una serie («82,5 kg × 8») con una flecha de color frente a [vs] si es mejor o peor; «—» si no la hubo. */
@Composable
private fun SetCell(set: WorkoutSet?, vs: WorkoutSet?, ex: Exercise, factor: Int, modifier: Modifier, emphasize: Boolean, prefix: String = "") {
    val unit = LocalWeightUnit.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            prefix + (set?.let { describeSet(it, ex, unit, factor) } ?: stringResource(R.string.cmp_missing)),
            style = if (emphasize) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (set != null && vs != null) {
            when (SessionCompare.setDirection(set, vs)) {
                1 -> Text(" ▲", color = statusColors.good, fontWeight = FontWeight.Bold)
                -1 -> Text(" ▼", color = statusColors.bad, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Elegir la otra sesión: primero las que comparten ejercicios, de la más reciente. */
@Composable
private fun PartnerPicker(w: Workout, candidates: List<Workout>, selected: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val mine = remember(w) { w.exercises.map { it.exercise.id }.toSet() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cmp_pick)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(candidates.take(MAX_CANDIDATES), key = { it.id }) { c ->
                    val shared = c.exercises.map { it.exercise.id }.distinct().count { it in mine }
                    val meta = when {
                        w.routineId != null && c.routineId == w.routineId -> stringResource(R.string.cmp_same_routine)
                        shared > 0 -> stringResource(R.string.cmp_shared, shared)
                        else -> null
                    }
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = c.id == selected, role = Role.RadioButton) { onPick(c.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c.id == selected, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("${c.name} · ${formatShortDate(c.startedAtEpochMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private val WIDE = 600.dp
private const val MAX_CANDIDATES = 60
