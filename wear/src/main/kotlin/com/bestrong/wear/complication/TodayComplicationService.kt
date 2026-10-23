package com.bestrong.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bestrong.core.sync.SyncContract
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
        return build(request.complicationType, routine?.name, tapAction(routine?.id))
    }

    // La vista previa no lleva acción: compartir el PendingIntent con la real (mismo request code y
    // FLAG_UPDATE_CURRENT) le borraría la rutina a la complicación ya puesta en la esfera.
    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, "Full Body A", null)

    /**
     * Abre el reloj y empieza la rutina. La rutina va en el URI del deep link (no en un extra): así
     * cada rutina tiene su propio PendingIntent y ninguna actualización pisa la de otra.
     */
    private fun tapAction(routineId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        if (routineId != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.Builder()
                .scheme(SyncContract.DEEP_LINK_SCHEME)
                .authority(SyncContract.DEEP_LINK_HOST)
                .appendQueryParameter(SyncContract.DEEP_LINK_PARAM_ROUTINE, routineId)
                .build()
        }
        return PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun build(type: ComplicationType, name: String?, tap: PendingIntent?): ComplicationData? {
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_dumbbell)).build()
        val text = name ?: getString(R.string.complication_today_none)
        val description = PlainComplicationText.Builder(getString(R.string.complication_today_desc, text)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                // Cortado con «…» (y sin partir un emoji): «Pierna B» y «Pierna A» no salen iguales
                PlainComplicationText.Builder(if (text.codePointCount(0, text.length) > SHORT_MAX) text.take(text.offsetByCodePoints(0, SHORT_MAX - 1)).trimEnd() + "…" else text).build(),
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

private const val SHORT_MAX = 7
