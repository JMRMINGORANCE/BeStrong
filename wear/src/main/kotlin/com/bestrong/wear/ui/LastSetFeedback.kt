package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.SetComparison
import com.bestrong.core.util.WeightUnit
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession

/** La serie recién hecha frente a la suya de la última vez: para decirlo en el descanso sin abrir nada. */
data class LastSetFeedback(val exercise: WorkoutExercise, val previous: WorkoutSet, val trend: Int)

/**
 * La última serie marcada del entreno (de cualquier tipo: tras un calentamiento o un drop no se compara una vieja)
 * y su pareja de la última sesión. Null si no es una serie normal de peso y reps o no hay con qué comparar.
 */
fun lastSetFeedback(w: Workout, previousFor: (String) -> List<WorkoutSet>): LastSetFeedback? {
    val (we, set) = w.exercises.flatMap { e -> e.sets.filter { it.completed }.map { e to it } }
        .maxByOrNull { it.second.completedAtEpochMs ?: 0L } ?: return null
    val comparable = set.type == SetType.NORMAL && we.exercise.tracking == TrackingType.WEIGHT_REPS
    val prev = if (comparable) WorkoutEditor.previousMatch(we.sets, we.sets.indexOf(set), previousFor(we.exercise.id)) else null
    return prev?.let {
        val trend = when {
            SetComparison.beats(set, it) == true -> 1
            set.estimatedOneRepMax < it.estimatedOneRepMax - 1e-9 -> -1
            else -> 0
        }
        LastSetFeedback(we, it, trend)
    }
}

/** «▲ Última vez: 80×7» en verde si la serie recién hecha la supera; en gris si no. */
@Composable
fun LastSetLine(session: WorkoutSession, workout: Workout?, unit: WeightUnit) {
    val feedback = remember(workout?.completedSetCount) { workout?.let { lastSetFeedback(it, session::previousFor) } } ?: return
    val factor = session.settings.collectAsStateWithLifecycle().value.weightFactor(feedback.exercise.exercise)
    val arrow = when {
        feedback.trend > 0 -> "▲ "
        feedback.trend < 0 -> "▼ "
        else -> "= "
    }
    Text(
        arrow + stringResource(R.string.info_last_time, setText(feedback.previous, feedback.exercise.exercise.tracking, unit, stringResource(R.string.unit_reps), factor)),
        color = if (feedback.trend > 0) Done else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
