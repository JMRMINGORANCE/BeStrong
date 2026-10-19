package com.bestrong.mobile.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.stats.PeriodSummary
import com.bestrong.core.stats.formatValue
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatDuration
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.labelRes
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Informe mensual (1080 × 1350) como imagen o PDF: cifras, días entrenados, ejercicios top y récords. */
object MonthlyReport {
    private const val W = 1080
    private const val H = 1350
    private const val PAD = 72f

    fun render(context: Context, month: YearMonth, s: PeriodSummary, trainedDays: Set<LocalDate>, unit: WeightUnit): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), context, month, s, trainedDays, unit)
        return bmp
    }

    private fun draw(c: Canvas, context: Context, month: YearMonth, s: PeriodSummary, trainedDays: Set<LocalDate>, unit: WeightUnit) {
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(18, 18, 24), Color.rgb(40, 20, 12), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val accent = BeStrongPalette.OrangeArgb
        fun text(size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        fun fit(t: String, p: TextPaint, width: Float) = TextUtils.ellipsize(t, p, width, TextUtils.TruncateAt.END).toString()
        val contentWidth = W - 2 * PAD
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

        var y = PAD + 40f
        c.drawText("BeStrong", PAD, y, text(44f, accent, bold = true))
        y += 90f
        val monthName = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
        c.drawText(context.getString(R.string.report_title, monthName, month.year), PAD, y, text(64f, bold = true))

        // Cifras
        y += 50f
        val stats = listOf(
            context.getString(R.string.stat_workouts) to "${s.workouts}",
            context.getString(R.string.stat_volume) to unit.formatVolume(s.volume),
            context.getString(R.string.stat_sets) to "${s.sets}",
            context.getString(R.string.stat_time) to formatDuration(s.durationSec),
        )
        val boxW = (contentWidth - 24f) / 2
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) }
        stats.forEachIndexed { i, (label, value) ->
            val x = PAD + (i % 2) * (boxW + 24f)
            val top = y + (i / 2) * 170f
            c.drawRoundRect(RectF(x, top, x + boxW, top + 150f), 28f, 28f, box)
            c.drawText(fit(label, text(30f, Color.LTGRAY), boxW - 48f), x + 24f, top + 52f, text(30f, Color.LTGRAY))
            val vp = text(52f, bold = true)
            c.drawText(fit(value, vp, boxW - 48f), x + 24f, top + 120f, vp)
        }
        y += 360f
        s.volumeChange?.let { ch ->
            val t = context.getString(R.string.report_vs_last, (if (ch >= 0) "+" else "−") + "${(abs(ch) * 100).toInt()} %")
            val p = text(34f, if (ch >= 0) BeStrongPalette.Success.toArgb() else Color.LTGRAY)
            c.drawText(fit(t, p, contentWidth), PAD, y, p)
            y += 60f
        }

        // Calendario: un punto por día, naranja si se entrenó
        val days = month.lengthOfMonth()
        val firstDow = month.atDay(1).dayOfWeek.value - 1
        val cell = contentWidth / 7f
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        for (d in 1..days) {
            val idx = firstDow + d - 1
            val cx = PAD + (idx % 7) * cell + cell / 2
            val cy = y + (idx / 7) * 56f + 20f
            dot.color = if (month.atDay(d) in trainedDays) accent else Color.argb(50, 255, 255, 255)
            c.drawCircle(cx, cy, 18f, dot)
        }
        y += ((firstDow + days + 6) / 7) * 56f + 50f

        // Ejercicios con más volumen
        if (s.topExercises.isNotEmpty()) {
            c.drawText(context.getString(R.string.report_top_exercises), PAD, y, text(40f, accent, bold = true))
            y += 56f
            s.topExercises.forEach { (id, vol) ->
                val name = ExerciseLibrary.byId(id)?.displayName(context) ?: id
                val v = unit.formatVolume(vol)
                val vp = text(36f, Color.LTGRAY)
                val vw = vp.measureText(v)
                c.drawText(fit(name, text(38f, bold = true), contentWidth - vw - 32f), PAD, y, text(38f, bold = true))
                c.drawText(v, W - PAD - vw, y, vp)
                y += 52f
            }
        }
        // Récords
        if (s.records.isNotEmpty() && y < H - 160f) {
            y += 20f
            c.drawText(context.resources.getQuantityString(R.plurals.n_records, s.records.size, s.records.size), PAD, y, text(40f, accent, bold = true))
            y += 54f
            s.records.sortedByDescending { it.value }.take(3).forEach { pr ->
                if (y > H - PAD) return@forEach
                val line = context.getString(R.string.record_line, pr.exerciseName, context.getString(pr.type.labelRes()), pr.type.formatValue(pr.value, unit))
                c.drawText(fit(line, text(34f, Color.LTGRAY), contentWidth), PAD, y, text(34f, Color.LTGRAY))
                y += 48f
            }
        }
    }

    /** Comparte el informe como PNG o como PDF de una página. */
    fun share(context: Context, month: YearMonth, s: PeriodSummary, trainedDays: Set<LocalDate>, unit: WeightUnit, pdf: Boolean) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val name = "bestrong-" + month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val file: File
        val mime: String
        if (pdf) {
            file = File(dir, "$name.pdf")
            mime = "application/pdf"
            val doc = PdfDocument()
            // A4 aprox. en puntos con la misma proporción 4:5
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
            draw(page.canvas, context, month, s, trainedDays, unit)
            doc.finishPage(page)
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
        } else {
            file = File(dir, "$name.png")
            mime = "image/png"
            val bmp = render(context, month, s, trainedDays, unit)
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
