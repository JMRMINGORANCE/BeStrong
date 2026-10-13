package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.formatClock
import java.time.Instant
import java.time.ZoneId

/** Lo que necesita la hora de fin además del entreno: descanso en marcha, descanso tras calentar y hora límite. */
internal data class EtaInputs(
    val restEndsAtEpochMs: Long? = null,
    val warmupRestSeconds: Int = WorkoutEditor.WARMUP_REST_SECONDS,
    val deadlineEpochMs: Long? = null,
    val onDeadline: (Long?) -> Unit = {},
)

/**
 * «≈ 25 min para terminar · hacia las 18:45». Al tocarla se pone una hora límite («tengo que irme a las
 * 19:30»): si la estimación se pasa, la línea se pone en rojo y propone acortar.
 */
@Composable
internal fun RemainingTimeLine(remainingSeconds: Int, now: Long, eta: EtaInputs) {
    var picking by remember { mutableStateOf(false) }
    if (picking) DeadlineDialog(eta.deadlineEpochMs ?: (now + remainingSeconds * 1000L), now, { eta.onDeadline(it); picking = false }) { picking = false }
    val ends = now + remainingSeconds * 1000L
    val deadline = eta.deadlineEpochMs
    val over = deadline != null && ends > deadline
    val text = when {
        deadline == null -> stringResource(R.string.remaining_time_eta, (remainingSeconds + 59) / 60, formatClock(ends))
        over -> stringResource(R.string.deadline_over, ((ends - deadline) / 60_000 + 1).toInt(), formatClock(deadline))
        else -> stringResource(R.string.remaining_time_eta, (remainingSeconds + 59) / 60, formatClock(ends)) + " · " +
            stringResource(R.string.deadline_ok, formatClock(deadline))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.deadline_hint)) { picking = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineDialog(initialEpochMs: Long, now: Long, onSet: (Long?) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(initialEpochMs).atZone(zone)
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deadline_title)) },
        text = { TimeInput(state) },
        confirmButton = { TextButton(onClick = { onSet(deadlineAt(state.hour, state.minute, now, zone)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { onSet(null) }) { Text(stringResource(R.string.deadline_clear)) } },
    )
}

/** Hoy a esa hora; si ya pasó hace más de una hora, se entiende que es mañana (entreno de madrugada). */
internal fun deadlineAt(hour: Int, minute: Int, now: Long, zone: ZoneId): Long {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    return if (today < now - 60 * 60_000L) today + 24 * 60 * 60_000L else today
}
