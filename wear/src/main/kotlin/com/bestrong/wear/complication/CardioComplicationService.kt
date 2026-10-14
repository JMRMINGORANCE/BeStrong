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
import com.bestrong.core.stats.CardioTotals
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import com.bestrong.wear.data.WearStore
import dagger.hilt.android.AndroidEntryPoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Complicación con el cardio de esta semana: km (o minutos, si el objetivo elegido en el móvil va en minutos)
 * frente al objetivo; sin objetivo, solo lo hecho.
 */
@AndroidEntryPoint
class CardioComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var store: WearStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val zone = ZoneId.systemDefault()
        val monday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val (meters, seconds) = CardioTotals.since(store.history.value, monday.atStartOfDay(zone).toInstant().toEpochMilli())
        val s = store.settings.value
        val goalMin = s.weeklyCardioMin
        return if (goalMin != null) {
            build(request.complicationType, (seconds / 60).toFloat(), goalMin.toFloat(), "min")
        } else {
            build(request.complicationType, (meters / 1000).toFloat(), s.weeklyCardioKm?.toFloat(), "km")
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 12f, 20f, "km")

    private fun build(type: ComplicationType, done: Float, goal: Float?, unit: String): ComplicationData? {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_dumbbell)).build()
        val value = if (unit == "km" && done < 100) "%.1f".format(done) else "%.0f".format(done)
        val text = PlainComplicationText.Builder(value).build()
        val description = PlainComplicationText.Builder(getString(R.string.cardio_complication_desc, value, unit)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text, description)
                .setTitle(PlainComplicationText.Builder(unit).build())
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            ComplicationType.RANGED_VALUE -> {
                // Sin objetivo, la barra se llena con lo hecho
                val max = (goal ?: done).coerceAtLeast(1f)
                RangedValueComplicationData.Builder(value = done.coerceAtMost(max), min = 0f, max = max, contentDescription = description)
                    .setText(text)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            }
            else -> null
        }
    }
}
