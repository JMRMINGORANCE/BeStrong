package com.bestrong.mobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Routine
import com.bestrong.mobile.shared.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Inicio y duración de un entreno, editables (fecha, hora «HH:MM» y minutos). */
class WorkoutTimeState(startEpochMs: Long, durationMin: Long) {
    private val zone = ZoneId.systemDefault()
    private val start = Instant.ofEpochMilli(startEpochMs).atZone(zone)
    var date by mutableStateOf(start.toLocalDate())
    var time by mutableStateOf("%02d:%02d".format(start.hour, start.minute))
    var minutes by mutableStateOf(durationMin.coerceAtLeast(1).toString())

    private val parsedTime: LocalTime? get() = runCatching { LocalTime.parse(time.trim().padStart(5, '0')) }.getOrNull()

    // Hasta una semana: un entreno que se quedó abierto por error también se puede corregir
    private val parsedMinutes: Long? get() = minutes.trim().toLongOrNull()?.takeIf { it in 1..7 * 24 * 60 }

    /** Inicio y fin en milisegundos, o null si algo no es válido o queda en el futuro. */
    val range: Pair<Long, Long>? get() {
        val t = parsedTime ?: return null
        val m = parsedMinutes ?: return null
        val startMs = LocalDateTime.of(date, t).atZone(zone).toInstant().toEpochMilli()
        if (startMs > System.currentTimeMillis()) return null
        return startMs to startMs + m * 60_000
    }

    /** Lo que se mostró al abrir: si [range] sigue igual, el usuario no tocó fecha, hora ni duración. */
    private val initialRange = range
    val changed: Boolean get() = range != initialRange
}

@Composable
fun rememberWorkoutTimeState(startEpochMs: Long, durationMin: Long) = remember(startEpochMs) { WorkoutTimeState(startEpochMs, durationMin) }

/** Campos de fecha, hora y duración; la fecha se elige en un calendario (sin días futuros). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTimeFields(state: WorkoutTimeState) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.log_past_date, state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state.time, { state.time = it.take(5) },
                label = { Text(stringResource(R.string.log_past_time)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.minutes, { state.minutes = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.log_past_minutes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.range == null) {
            Text(stringResource(R.string.log_past_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (picking) {
        // El DatePicker trabaja en UTC: el día elegido se toma como fecha local
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { state.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(pickerState) }
    }
}

/**
 * Registrar un entreno que no se apuntó (se olvidó empezar el cronómetro, otro móvil…):
 * se elige la rutina, la fecha y la duración; las series salen completadas con los pesos de la última vez.
 */
@Composable
fun LogPastWorkoutDialog(
    routines: List<Routine>,
    onLog: (routine: Routine, startMs: Long, endMs: Long, forPartner: Boolean) -> Unit,
    onDismiss: () -> Unit,
    startEpochMs: Long? = null,
    partner: String? = null,
    forPartnerInitially: Boolean = false,
) {
    var selected by remember { mutableStateOf(routines.firstOrNull()) }
    var forPartner by remember { mutableStateOf(forPartnerInitially && partner != null) }
    // Sin clave: el estado no se reinicia si la lista de rutinas cambia mientras se escribe
    val time = remember { WorkoutTimeState(startEpochMs ?: (System.currentTimeMillis() - 60 * 60_000L), 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_past_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (routines.isEmpty()) {
                    Text(stringResource(R.string.log_past_no_routines))
                } else {
                    Text(stringResource(R.string.log_past_routine), style = MaterialTheme.typography.labelLarge)
                    routines.forEach { r ->
                        FilterChip(
                            selected = selected?.id == r.id,
                            onClick = { selected = r },
                            label = { Text(listOfNotNull(r.emoji, r.name).joinToString(" ")) },
                        )
                    }
                    // Con pareja: para quién se apunta (sus pesos de la última vez, en su historial)
                    partner?.let { name ->
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = !forPartner, onClick = { forPartner = false }, label = { Text(stringResource(R.string.athlete_me)) })
                            FilterChip(selected = forPartner, onClick = { forPartner = true }, label = { Text(name) })
                        }
                    }
                    WorkoutTimeFields(time)
                    Text(stringResource(R.string.log_past_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val range = time.range
            TextButton(
                onClick = { selected?.let { r -> range?.let { (s, e) -> onLog(r, s, e, forPartner) } } },
                enabled = selected != null && range != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** «Copiar a otro día»: se elige la fecha, la hora y la duración (la de aquel entreno para empezar). */
@Composable
fun CopyToDayDialog(durationMin: Long, onCopy: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    val time = remember { WorkoutTimeState(System.currentTimeMillis() - durationMin * 60_000L, durationMin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_to_day)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutTimeFields(time)
                Text(stringResource(R.string.copy_to_day_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val range = time.range
            TextButton(onClick = { range?.let { (s, e) -> onCopy(s, e) } }, enabled = range != null) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
