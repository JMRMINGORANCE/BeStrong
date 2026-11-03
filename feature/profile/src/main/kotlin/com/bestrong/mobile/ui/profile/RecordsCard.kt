package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.Exercise
import com.bestrong.core.stats.Achievement
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.exerciseLabel
import com.bestrong.mobile.ui.components.formatShortDate

/**
 * «Récords e hitos»: el siguiente reto, el mejor 1RM estimado de cada ejercicio (tocarlo abre su detalle, donde
 * están el resto de marcas) y los logros conseguidos, con el más cercano de los que faltan.
 */
@Composable
fun RecordsCard(
    best: List<PersonalRecord>,
    stats: AdvancedStats,
    exerciseOf: (String) -> Exercise?,
    onOpenExercise: (String) -> Unit,
) {
    val unit = LocalWeightUnit.current
    com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader(stringResource(R.string.records_milestones))
            stats.challenge?.let { (lift, target, eta) ->
                Text(stringResource(R.string.challenge_eta, unit.format(target, withUnit = true), liftName(lift), formatShortDate(eta)), fontWeight = FontWeight.SemiBold)
            }
            SubHeader(stringResource(R.string.best_1rm))
            if (best.isEmpty()) Text(stringResource(R.string.complete_sets_for_records), style = MaterialTheme.typography.bodyMedium)
            best.forEach { pr ->
                // Con mancuernas como total del par, el mismo número que en el detalle del ejercicio
                // (también los propios, que no están en la biblioteca)
                val pair = (exerciseOf(pr.exerciseId) ?: com.bestrong.core.library.ExerciseLibrary.byId(pr.exerciseId))
                    ?.let { com.bestrong.mobile.ui.components.pairFactor(it) } ?: 1
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpenExercise(pr.exerciseId) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pr.exerciseLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(unit.format(pr.value * pair, withUnit = true), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (stats.achievements.isNotEmpty()) Achievements(stats.achievements)
        }
    }
}

/** Los logros conseguidos y el que está más cerca de conseguirse (con su %). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Achievements(all: List<Achievement>) {
    val unlocked = all.filter { it.unlocked }
    SubHeader(stringResource(R.string.achievements) + " · " + stringResource(R.string.achievements_count, unlocked.size, all.size))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        unlocked.forEach { a -> com.bestrong.mobile.ui.components.MetaPill("🏆 ${achievementName(a)}") }
        all.filter { !it.unlocked }.maxByOrNull { it.progress }?.let { a ->
            // El % con el formato del idioma («45 %» / «45%»)
            val percent = java.text.NumberFormat.getPercentInstance(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
                .apply { roundingMode = java.math.RoundingMode.FLOOR }.format(a.progress)
            com.bestrong.mobile.ui.components.MetaPill("${achievementName(a)} · $percent")
        }
    }
}

@Composable
private fun achievementName(a: Achievement): String = com.bestrong.mobile.ui.components.achievementLabelRes(a.id)?.let { stringResource(it) } ?: a.id

@Composable
private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}
