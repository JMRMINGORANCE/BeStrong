package com.bestrong.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.TrackingType
import com.bestrong.mobile.shared.R

/** RPE objetivo que se pueden elegir: técnica, volumen, el habitual (8) y series a tope. */
private val TARGET_RPES = listOf(6.0, 7.0, 8.0, 8.5, 9.0, 10.0)

/**
 * RPE objetivo de las series efectivas («—» = sin objetivo); solo en los ejercicios de peso × reps. Se usa en
 * el editor de rutinas y en el de programas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetRpeChips(tracking: TrackingType, current: Double?, onPick: (Double?) -> Unit) {
    if (tracking != TrackingType.WEIGHT_REPS) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.target_rpe), style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
        FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text("—") })
        for (r in TARGET_RPES) FilterChip(selected = current == r, onClick = { onPick(r) }, label = { Text(com.bestrong.core.util.formatRpe(r)) })
    }
}
