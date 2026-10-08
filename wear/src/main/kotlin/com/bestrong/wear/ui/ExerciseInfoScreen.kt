package com.bestrong.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.SetType
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.training.Tempo
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession

/**
 * Progreso del ejercicio actual en el reloj: la última vez, mejor 1RM estimado con su
 * evolución, discos por lado para la serie actual y guía de tempo con vibraciones.
 */
@Composable
fun ExerciseInfoScreen(session: WorkoutSession) {
    val workout by session.workout.collectAsStateWithLifecycle()
    val index by session.exerciseIndex.collectAsStateWithLifecycle()
    val tempoActive by session.tempoActive.collectAsStateWithLifecycle()
    val unit = LocalWeightUnit.current
    val we = workout?.exercises?.getOrNull(index) ?: return
    val progress = remember(we.exercise.id, workout?.id) { session.progressFor(we.exercise.id).takeLast(12) }
    val previous = remember(we.exercise.id) { session.previousFor(we.exercise.id).filter { it.type != SetType.WARMUP } }
    val set = session.currentSet()
    val listState = rememberScalingLazyListState()
    val brand = MaterialTheme.colorScheme.primary

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text(we.exercise.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) } }
            item {
                val text = if (previous.isEmpty()) {
                    stringResource(R.string.info_first_time)
                } else {
                    stringResource(R.string.info_last_time, previous.joinToString("  ") { "${unit.format(it.weightKg)}×${it.reps}" })
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            progress.maxOfOrNull { it.bestOneRepMax }?.takeIf { it > 0 }?.let { best ->
                item {
                    Text(
                        stringResource(R.string.info_best_1rm, unit.format(best, withUnit = true)),
                        style = MaterialTheme.typography.titleMedium,
                        color = brand,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (progress.size >= 2) {
                item {
                    val values = progress.map { it.bestOneRepMax }
                    val description = stringResource(R.string.info_chart_desc, unit.format(values.first()), unit.format(values.last()))
                    Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 24.dp).semantics { contentDescription = description }) {
                        val min = values.min()
                        val range = (values.max() - min).takeIf { it > 0 } ?: 1.0
                        val path = Path()
                        values.forEachIndexed { i, v ->
                            val x = size.width * i / (values.size - 1)
                            val y = size.height - (size.height * ((v - min) / range)).toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, brand, style = Stroke(width = 3.dp.toPx()))
                        val lastY = size.height - (size.height * ((values.last() - min) / range)).toFloat()
                        drawCircle(brand, 4.dp.toPx(), Offset(size.width, lastY))
                    }
                }
            }
            if (we.exercise.equipment == Equipment.BARBELL && set != null && set.weightKg > 0) {
                item {
                    val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit)
                    val plates = load.perSide.joinToString(" · ") { unit.format(unit.toKg(it)) }.ifEmpty { "—" }
                    Text(
                        stringResource(R.string.info_plates, plates, unit.format(unit.toKg(load.barWeight))),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Tempo.parse(we.tempo)?.let { tempo ->
                item {
                    SwitchButton(
                        checked = tempoActive,
                        onCheckedChange = { session.toggleTempo() },
                        label = { Text(stringResource(R.string.info_tempo, tempo.toString())) },
                        secondaryLabel = { Text(stringResource(R.string.info_tut, tempo.timeUnderTension(set?.reps ?: 10))) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
