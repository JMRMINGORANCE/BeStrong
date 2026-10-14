package com.bestrong.mobile.ui.profile

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bestrong.core.stats.YearSummary
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label

/** «Tu año»: entrenos, días, volumen, récords, mejor racha, músculo estrella y mayores subidas; se puede compartir. */
@Composable
fun YearInReviewCard(y: YearSummary) {
    val unit = LocalWeightUnit.current
    val context = LocalContext.current
    val lines = buildList {
        add(stringResource(R.string.year_workouts, y.period.workouts, y.trainingDays, y.period.durationSec / 3600))
        add(stringResource(R.string.year_volume, unit.formatVolume(y.period.volume)))
        add(stringResource(R.string.year_records, y.period.records.size) + " · " + pluralStringResource(R.plurals.year_streak, y.longestWeeklyStreak, y.longestWeeklyStreak))
        y.topMuscle?.let { add(stringResource(R.string.year_top_muscle, it.label())) }
        y.biggestGains.forEach { (ex, from, to) ->
            add(stringResource(R.string.year_gain, ex.displayName(), unit.format(from), unit.format(to, withUnit = true)))
        }
    }
    val title = stringResource(R.string.year_title, y.year)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(title)
            lines.forEachIndexed { i, line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal)
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, (listOf(title) + lines).joinToString("\n"))
                    context.startActivity(Intent.createChooser(send, null))
                },
            ) {
                Icon(Icons.Default.Share, null)
                Text(stringResource(R.string.share_as_text), Modifier.padding(start = 8.dp))
            }
        }
    }
}
