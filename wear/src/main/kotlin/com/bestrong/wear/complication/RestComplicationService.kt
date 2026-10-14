package com.bestrong.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import com.bestrong.wear.session.WorkoutSession
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/**
 * Complicación «Descanso»: la cuenta atrás del descanso en curso en cualquier esfera (la cuenta la lleva la
 * esfera sola, sin actualizaciones por segundo); sin descanso, «—». Al tocarla se abre el entreno.
 */
@AndroidEntryPoint
class RestComplicationService : SuspendingComplicationDataSourceService() {
    @Inject lateinit var session: WorkoutSession

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val endsAt = session.rest.value?.endsAtEpochMs?.takeIf { it > System.currentTimeMillis() }
        return build(request.complicationType, endsAt, tapAction())
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, System.currentTimeMillis() + PREVIEW_MS, null)

    private fun tapAction(): PendingIntent =
        PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(type: ComplicationType, endsAtEpochMs: Long?, tap: PendingIntent?): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_dumbbell)).build()
        val text = if (endsAtEpochMs != null) {
            TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.STOPWATCH, CountDownTimeReference(Instant.ofEpochMilli(endsAtEpochMs))).build()
        } else {
            PlainComplicationText.Builder("—").build()
        }
        val description = PlainComplicationText.Builder(getString(if (endsAtEpochMs != null) R.string.complication_rest_desc else R.string.complication_rest_none)).build()
        return ShortTextComplicationData.Builder(text, description)
            .setTitle(PlainComplicationText.Builder(getString(R.string.complication_rest_label)).build())
            .setMonochromaticImage(icon)
            .setTapAction(tap)
            .build()
    }

    private companion object {
        const val PREVIEW_MS = 90_000L
    }
}
