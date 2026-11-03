package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.formatClock

/** Lo que necesita la hora de fin además del entreno: descanso en marcha y descanso tras calentar. */
internal data class EtaInputs(
    val restEndsAtEpochMs: Long? = null,
    val warmupRestSeconds: Int = WorkoutEditor.WARMUP_REST_SECONDS,
)

/** «≈ 25 min para terminar · hacia las 18:45»: solo informa. */
@Composable
internal fun RemainingTimeLine(remainingSeconds: Int, now: Long) {
    val ends = now + remainingSeconds * 1000L
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(Icons.Default.Schedule, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.remaining_time_eta, (remainingSeconds + 59) / 60, formatClock(ends, is24)),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
