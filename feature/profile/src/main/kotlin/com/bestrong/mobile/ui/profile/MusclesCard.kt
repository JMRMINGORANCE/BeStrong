package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.label

/**
 * Todo lo de los músculos en una tarjeta (antes, cuatro sueltas): Semana (silueta y lista con el objetivo),
 * 30 días, Tendencia (8 semanas) y Recuperación (días desde la última vez y agujetas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusclesCard(stats: ProfileStats, advanced: AdvancedStats, target: Int, onTarget: (Int) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.mu_tab_week),
        stringResource(R.string.mu_tab_30d),
        stringResource(R.string.mu_tab_trend),
        stringResource(R.string.mu_tab_recovery),
    )
    com.bestrong.mobile.ui.components.Panel(contentPadding = 16.dp) {
        SectionHeader(stringResource(R.string.mu_title))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                    icon = {},
                    // Poco sitio en el móvil plegado: la etiqueta encoge antes de cortarse («Recuper…»)
                    label = {
                        androidx.compose.foundation.text.BasicText(
                            t,
                            style = MaterialTheme.typography.labelMedium.copy(color = androidx.compose.material3.LocalContentColor.current),
                            maxLines = 1,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                        )
                    },
                )
            }
        }
        when (tab) {
            0 -> WeeklyMuscleContent(stats.weeklyMuscles, target, onTarget)
            1 -> Muscles30dContent(stats.muscles30d)
            2 -> MuscleTrendContent(stats.muscleTrend, target)
            else -> MuscleRecoveryContent(advanced)
        }
    }
}

/** Series por músculo de los últimos 30 días, de más a menos. */
@Composable
private fun Muscles30dContent(muscles: Map<MuscleGroup, Double>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (muscles.isEmpty()) Text(stringResource(R.string.no_data_yet), style = MaterialTheme.typography.bodySmall)
        val max = muscles.values.maxOrNull() ?: 1.0
        muscles.forEach { (m, sets) ->
            Row {
                Text(m.label(), Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { (sets / max).toFloat() }, modifier = Modifier.weight(0.45f).padding(top = 8.dp))
                Text(formatWeight(sets), Modifier.weight(0.15f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
