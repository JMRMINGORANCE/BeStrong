package com.bestrong.mobile.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.MainActivity
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.labelRes
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.ZoneId

/**
 * Widget «Músculos esta semana»: series de cada músculo frente al objetivo de 10 (✓ al llegar). Primero los
 * que normalmente entrenas y van por detrás, luego los más trabajados.
 */
class MuscleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repo = entry.repository()
        val target = entry.settings().settings.value.weeklySetsTarget.toDouble()
        val history = repo.historyOnce()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val week = WorkoutStats.weeklySetsPerMuscle(history, today, zone)
        val lagging = WorkoutStats.laggingMuscles(history, today, zone, limit = MAX_ROWS, minSets = target)
        val rows = (lagging.map { it.first } + week.keys).distinct().take(MAX_ROWS).map { it to (week[it] ?: 0.0) }
        provideContent { GlanceTheme { Content(context, rows, target) } }
    }

    @Composable
    private fun Content(context: Context, rows: List<Pair<MuscleGroup, Double>>, target: Double) {
        val brand = ColorProvider(Color(0xFFFF6B1A))
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            Text(context.getString(R.string.widget_muscles_title), style = TextStyle(color = brand, fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) {
                Text(context.getString(R.string.widget_muscles_empty), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
            rows.forEach { (muscle, sets) ->
                // Cada fila abre la biblioteca con los ejercicios de ese músculo
                val open = Intent(context, MainActivity::class.java).setAction(AppIntents.ACTION_OPEN_MUSCLE).putExtra(AppIntents.EXTRA_MUSCLE, muscle.name)
                    .setData(android.net.Uri.parse("bestrong-widget://muscle/" + muscle.name))
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(actionStartActivity(open)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(context.getString(muscle.labelRes()), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    val value = "%.1f".format(sets).removeSuffix(",0").removeSuffix(".0")
                    Text(
                        (if (sets >= target) "✓ " else "") + "$value/${target.toInt()}",
                        style = TextStyle(color = if (sets >= target) brand else GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                    )
                }
            }
        }
    }

    companion object {
        private const val MAX_ROWS = 5

        suspend fun refresh(context: Context) {
            runCatching { MuscleWidget().updateAll(context) }
        }
    }
}

class MuscleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MuscleWidget()
}
