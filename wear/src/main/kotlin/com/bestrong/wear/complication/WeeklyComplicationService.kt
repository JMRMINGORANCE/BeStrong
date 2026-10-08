package com.bestrong.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.wear.data.WearStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import java.time.LocalDate
import java.time.ZoneId

/** Complicación para la esfera: entrenos de esta semana (objetivo: 4). */
@AndroidEntryPoint
class WeeklyComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var store: WearStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val history = store.history.value
        val zone = ZoneId.systemDefault()
        val count = WorkoutStats.weeklySummary(history, 1, LocalDate.now(zone), zone).first().workouts
        return build(request.complicationType, count)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 3)

    private fun build(type: ComplicationType, count: Int): ComplicationData? {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_dumbbell)).build()
        val description = PlainComplicationText.Builder("$count entrenamientos esta semana").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("$count").build(),
                description,
            ).setTitle(PlainComplicationText.Builder("sem").build())
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = count.toFloat().coerceAtMost(WEEKLY_GOAL),
                min = 0f,
                max = WEEKLY_GOAL,
                contentDescription = description,
            ).setText(PlainComplicationText.Builder("$count").build())
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            else -> null
        }
    }

    private companion object {
        const val WEEKLY_GOAL = 4f
    }
}
