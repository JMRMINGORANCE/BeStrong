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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bestrong.core.model.Routine
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.MainActivity
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.shared.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId

/** Widget: racha, entrenos de la semana, "Entrenar" y dos rutinas: la planificada para hoy y la que más tiempo lleva sin hacerse. */
class BeStrongWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repo = entry.repository()
        val history = repo.historyOnce()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // La planificada para hoy primero y luego la que más tiempo lleva sin hacerse
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val plannedToday = entry.planning().state.value.planFor(weekStart).routineFor(today.dayOfWeek)
        val routines = com.bestrong.core.training.QuickRoutines.pick(repo.routinesOnce(), history, plannedToday, today.atStartOfDay(zone).toInstant().toEpochMilli())
        val week = WorkoutStats.weeklySummary(history, 1, today, zone).first().workouts
        val streak = WorkoutStats.weeklyStreak(history, today, zone)
        provideContent { GlanceTheme { Content(context, week, streak, routines) } }
    }

    @Composable
    private fun Content(context: Context, week: Int, streak: Int, routines: List<Routine>) {
        val brand = ColorProvider(Color(0xFFFF6B1A))
        val onBrand = ColorProvider(Color.Black)
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text("BeStrong", style = TextStyle(color = brand, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(context.getString(R.string.widget_streak, streak), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
            }
            Text(
                context.getString(R.string.widget_week, week),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(8.dp))
            WidgetButton(context.getString(R.string.nav_train), context.getString(R.string.widget_start, context.getString(R.string.nav_train)), brand, onBrand, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_START_EMPTY))
            routines.forEach { r ->
                Spacer(GlanceModifier.height(6.dp))
                WidgetButton(
                    r.name,
                    context.getString(R.string.widget_start, r.name),
                    GlanceTheme.colors.secondaryContainer,
                    GlanceTheme.colors.onSecondaryContainer,
                    Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_START_ROUTINE).putExtra(MainActivity.EXTRA_ROUTINE_ID, r.id),
                )
            }
        }
    }

    @Composable
    private fun WidgetButton(text: String, description: String, bg: ColorProvider, fg: ColorProvider, intent: Intent) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().background(bg).cornerRadius(14.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionStartActivity(intent))
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("▶  $text", maxLines = 1, style = TextStyle(color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp))
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { BeStrongWidget().updateAll(context) }
        }
    }
}

/** Los widgets no admiten @AndroidEntryPoint: se accede al grafo de Hilt por un EntryPoint. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): BeStrongRepository
    fun settings(): com.bestrong.mobile.data.SettingsStore
    fun planning(): com.bestrong.mobile.data.PlanningStore
}

class BeStrongWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BeStrongWidget()
}
