package com.bestrong.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.shared.R

/** Rangos habituales para la doble progresión: fuerza, hipertrofia y resistencia. */
val REP_RANGE_CHOICES = listOf("3-5", "5-8", "6-10", "8-12", "10-15", "12-20")

/** Chips para elegir el rango de repeticiones («Sin rango» = progresión normal). Se usa en el entreno, la ficha y el editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepRangeChips(current: String?, onPick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text(stringResource(R.string.rep_range_none)) })
        REP_RANGE_CHOICES.forEach { r ->
            FilterChip(selected = current == r, onClick = { onPick(r) }, label = { Text(r.replace("-", "–")) })
        }
    }
}
