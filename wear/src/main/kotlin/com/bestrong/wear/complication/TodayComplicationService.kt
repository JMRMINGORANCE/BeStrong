package com.bestrong.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import com.bestrong.wear.data.WearStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Complicación «Hoy toca»: la rutina del día; al tocarla se empieza directamente. */
@AndroidEntryPoint
class TodayComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var store: WearStore

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val routine = store.todayRoutine()
        return build(request.complicationType, routine?.name, routine?.id)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "Full Body A", null)

    private fun build(type: ComplicationType, name: String?, routineId: String?): ComplicationData? {
        val intent = Intent(this, MainActivity::class.java).apply { routineId?.let { putExtra(MainActivity.EXTRA_ROUTINE, it) } }
        val tap = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_dumbbell)).build()
        val text = name ?: getString(R.string.complication_today_none)
        val description = PlainComplicationText.Builder(getString(R.string.complication_today_desc, text)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(text.take(7)).build(),
                description,
            ).setTitle(PlainComplicationText.Builder(getString(R.string.complication_today_label)).build())
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder(text).build(),
                description,
            ).setTitle(PlainComplicationText.Builder(getString(R.string.complication_today_label)).build())
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            else -> null
        }
    }
}
