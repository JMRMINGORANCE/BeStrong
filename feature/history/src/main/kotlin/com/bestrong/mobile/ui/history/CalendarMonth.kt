package com.bestrong.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.shared.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendario mensual con los días entrenados marcados; tocar uno filtra el historial por ese día y tocar un día
 * pasado sin entreno ([onEmptyDay]) deja apuntar el que se hizo sin registrar.
 */
@Composable
fun CalendarMonth(
    trainingDays: Set<LocalDate>,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    onEmptyDay: ((LocalDate) -> Unit)? = null,
    onSelect: (LocalDate?) -> Unit = {},
) {
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val month = YearMonth.now().plusMonths(monthOffset)
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val count = trainingDays.count { YearMonth.from(it) == month }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.month_previous)) }
            Text(
                pluralStringResource(R.plurals.calendar_title, count, month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.uppercase() }, month.year, count),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.month_next)) }
        }
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW_STANDALONE, locale) }.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
        val firstDow = month.atDay(1).dayOfWeek.value - 1 // lunes = 0
        val cells = firstDow + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - firstDow + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val trained = date in trainingDays
                        val onClick: (() -> Unit)? = when {
                            trained -> ({ onSelect(if (date == selected) null else date) })
                            onEmptyDay != null && !date.isAfter(today) -> ({ onEmptyDay(date) })
                            else -> null
                        }
                        DayCell(date, trained, date == selected, date == today, locale, Modifier.weight(1f), onClick)
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

/**
 * Un día del mes. Toda la celda es la zona táctil (el círculo solo mide ~33 dp) y TalkBack dice la fecha y si
 * se entrenó; [onClick] null = día sin acción (futuro o sin poder apuntar).
 */
@Composable
private fun DayCell(date: LocalDate, trained: Boolean, isSelected: Boolean, isToday: Boolean, locale: Locale, modifier: Modifier, onClick: (() -> Unit)?) {
    val longDate = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale))
    val description = if (trained) stringResource(R.string.calendar_day_trained, longDate) else longDate
    val label = stringResource(if (trained) R.string.calendar_day_filter else R.string.log_past_title)
    Box(
        modifier.aspectRatio(1f)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(CircleShape)
                .background(if (trained) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.tertiary, CircleShape) else Modifier)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Black else FontWeight.Normal,
                color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
