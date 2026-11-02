package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.RestState
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.rememberNow

/**
 * El ejercicio que viene tras este descanso si es otro distinto (no la siguiente serie del mismo ni el compañero de
 * superserie): su índice y su primera serie pendiente. null si el descanso no lleva a un cambio de ejercicio.
 */
internal fun nextExerciseAfterRest(w: Workout, rest: RestState?): Pair<Int, WorkoutSet>? {
    if (rest == null || (rest.workoutId != null && rest.workoutId != w.id)) return null
    val next = WorkoutEditor.upcomingSet(w) ?: return null
    val lastDone = w.exercises.withIndex()
        .mapNotNull { (i, we) -> we.sets.filter { it.completed }.maxOfOrNull { it.completedAtEpochMs ?: 0L }?.let { i to it } }
        .maxByOrNull { it.second }?.first ?: return null
    if (next.first == lastDone) return null
    val from = w.exercises[lastDone]
    val to = w.exercises[next.first]
    // En una superseries se alterna sin cambiar de puesto: no es «siguiente ejercicio»
    if (from.supersetId != null && from.supersetId == to.supersetId) return null
    return next
}

/**
 * «Siguiente ejercicio» (propuesta 09): durante el descanso antes de cambiar de ejercicio, en lugar de la barra de
 * descanso. Imagen, peso y series, discos por lado, nota fija y la cuenta atrás de preparación, con «Empezar ya»
 * (salta el descanso) y «Cambiar» (sustituir el ejercicio).
 */
@Composable
internal fun NextExercisePanel(
    w: Workout,
    next: Pair<Int, WorkoutSet>,
    rest: RestState,
    vm: ActiveWorkoutViewModel,
    pinnedNote: String?,
    barKg: Double?,
    dumbbellTotal: Boolean,
    modifier: Modifier = Modifier,
) {
    val unit = LocalWeightUnit.current
    val we: WorkoutExercise = w.exercises[next.first]
    val set = next.second
    val now = rememberNow()
    val left = ((rest.endsAtEpochMs - now) / 1000).coerceAtLeast(0)
    var substituting by remember { mutableStateOf(false) }
    if (substituting) SubstituteDialog(vm, we.id) { substituting = false }
    val factor = if (dumbbellTotal && we.exercise.equipment == Equipment.DUMBBELL) 2 else 1
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.next_exercise_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%d:%02d".format(left / 60, left % 60),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            com.bestrong.mobile.ui.components.ExerciseThumb(we.exercise, 72.dp)
            Column(Modifier.weight(1f)) {
                Text(we.exercise.displayName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val pending = we.sets.count { !it.completed }
                Text(
                    "$pending × " + describeSet(set, we.exercise, unit, factor),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // Discos por lado (barra) y la nota fija (asiento, agarre…): lo que se prepara antes de empezar
        if (we.exercise.tracking == TrackingType.WEIGHT_REPS && set.weightKg > 0 &&
            (we.exercise.equipment == Equipment.BARBELL || we.exercise.equipment == Equipment.SMITH)
        ) {
            val bar = barKg?.let { unit.fromKg(it) } ?: PlateCalculator.defaultBar(unit)
            val load = PlateCalculator.calculate(unit.fromKg(set.weightKg), unit, bar)
            if (load.perSide.isNotEmpty()) {
                Text(
                    stringResource(R.string.next_exercise_plates, load.perSide.joinToString(" + ") { formatWeight(it) } + " ${unit.label}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        pinnedNote?.takeIf { it.isNotBlank() }?.let { note ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(note, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.adjustRest(-15) }) { Text("−15 s") }
            TextButton(onClick = { vm.adjustRest(15) }) { Text("+15 s") }
            OutlinedButton(onClick = { substituting = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                Text(stringResource(R.string.next_exercise_change), Modifier.padding(start = 6.dp), maxLines = 1)
            }
        }
        // El principal, a todo el ancho (con letra grande o en otros idiomas no se corta)
        Button(onClick = vm::skipRest, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.next_exercise_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
