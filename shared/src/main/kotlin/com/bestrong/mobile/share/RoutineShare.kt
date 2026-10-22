package com.bestrong.mobile.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.bestrong.core.io.RoutineLink
import com.bestrong.core.model.Routine
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.util.formatDuration
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

/** Rutinas compartidas: enlace, código QR y PDF imprimible. */
object RoutineShare {
    fun link(routine: Routine): String = RoutineLink.encode(routine)

    /** QR del enlace (corrección de errores media: aguanta pantallas con brillo o arañazos). */
    fun qr(text: String, size: Int = 720): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2))
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    fun shareLink(context: Context, routine: Routine) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, routine.name + "\n" + link(routine))
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_routine_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** PDF A4 con los ejercicios, series × reps, descansos, RPE objetivo y tempo, para imprimir o enviar. */
    fun sharePdf(context: Context, routines: List<Routine>, title: String) {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD }
        val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var y = 56f
        fun ensure(space: Float) {
            if (y + space > h - 40) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++pageNo).create())
                y = 48f
            }
        }
        page.canvas.drawText(title, 40f, y, titleP)
        y += 28f
        routines.forEach { r ->
            ensure(40f)
            page.canvas.drawText(r.name, 40f, y, headP)
            y += 20f
            r.exercises.forEach { re ->
                ensure(16f)
                val work = re.sets.filter { it.type != SetType.WARMUP }
                val reps = targetText(re.exercise.tracking, work.firstOrNull())
                val line = "• " + re.exercise.displayName(context) + "  —  " + context.getString(R.string.pdf_routine_sets, work.size, reps) +
                    "  ·  " + context.getString(R.string.pdf_rest, formatDuration(re.restSeconds.toLong()))
                page.canvas.drawText(line, 52f, y, bodyP)
                y += 16f
                // RPE objetivo y tempo, si los tiene, en otra línea (drawText no parte el texto)
                val extras = listOfNotNull(
                    re.targetRpe?.takeIf { re.exercise.tracking == TrackingType.WEIGHT_REPS }?.let { "RPE " + com.bestrong.core.util.formatRpe(it) },
                    re.tempo?.let { context.getString(R.string.tempo) + " " + it },
                )
                if (extras.isNotEmpty()) {
                    ensure(14f)
                    page.canvas.drawText(extras.joinToString("  ·  "), 64f, y, bodyP)
                    y += 14f
                }
            }
            y += 10f
        }
        doc.finishPage(page)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "bestrong-routine.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_routine_pdf)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Objetivo de la serie en el PDF: repeticiones, segundos o minutos de cardio. */
private fun targetText(tracking: TrackingType, set: com.bestrong.core.model.RoutineSet?): String = when (tracking) {
    TrackingType.DURATION -> "${set?.durationSec ?: 0} s"
    TrackingType.CARDIO -> com.bestrong.mobile.ui.workout.cardioText(set?.durationSec ?: 0, 0.0)
    else -> "${set?.reps ?: 0}"
}
