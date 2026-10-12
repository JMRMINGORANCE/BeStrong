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
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.formatDuration
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.labelRes
import java.io.File
import java.io.FileOutputStream

/**
 * Tarjeta del entreno en formato 4:5 (1080 × 1350) para compartir en Instagram, WhatsApp…
 * Se dibuja con Canvas para no depender de que la UI esté en pantalla.
 */
object WorkoutShareCard {
    private const val W = 1080
    private const val H = 1350
    private const val PAD = 72f
    private const val MAX_EXERCISES = 8

    fun render(context: Context, w: Workout, records: List<PersonalRecord>, unit: WeightUnit): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), Color.rgb(18, 18, 24), Color.rgb(40, 20, 12), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        val accent = BeStrongPalette.OrangeArgb

        fun text(size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
        fun fit(s: String, p: TextPaint, width: Float) = TextUtils.ellipsize(s, p, width, TextUtils.TruncateAt.END).toString()
        val contentWidth = W - 2 * PAD

        var y = PAD + 40f
        c.drawText("BeStrong", PAD, y, text(44f, accent, bold = true))
        y += 90f
        val title = text(68f, bold = true)
        c.drawText(fit(w.name, title, contentWidth), PAD, y, title)
        y += 56f
        c.drawText(formatDate(w.startedAtEpochMs), PAD, y, text(36f, Color.LTGRAY))

        // Cifras principales
        y += 70f
        val stats = listOf(
            context.getString(R.string.stat_duration) to formatDuration(w.durationSeconds()),
            context.getString(R.string.stat_volume) to unit.formatVolume(w.totalVolume),
            context.getString(R.string.stat_sets) to "${w.completedSetCount}",
        )
        val boxW = (contentWidth - 2 * 24f) / 3
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) }
        stats.forEachIndexed { i, (label, value) ->
            val x = PAD + i * (boxW + 24f)
            c.drawRoundRect(RectF(x, y, x + boxW, y + 170f), 28f, 28f, box)
            val lp = text(30f, Color.LTGRAY)
            c.drawText(fit(label, lp, boxW - 48f), x + 24f, y + 58f, lp)
            val vp = text(54f, bold = true)
            c.drawText(fit(value, vp, boxW - 48f), x + 24f, y + 132f, vp)
        }
        y += 240f

        // Ejercicios con su mejor serie
        val done = w.exercises.filter { e -> e.sets.any { it.completed } }
        // Con pocos ejercicios, filas más grandes para llenar la tarjeta
        val roomy = done.size <= 4
        val rowHeight = if (roomy) 96f else 64f
        val nameP = text(if (roomy) 48f else 40f, bold = true)
        val detailP = text(if (roomy) 42f else 36f, Color.LTGRAY)
        done.take(MAX_EXERCISES).forEach { e ->
            val sets = e.sets.filter { it.completed && it.type != SetType.WARMUP }.ifEmpty { e.sets.filter { it.completed } }
            val best = sets.maxByOrNull { it.estimatedOneRepMax.takeIf { v -> v > 0 } ?: (it.reps + it.durationSec).toDouble() }
            val detail = when {
                best == null -> ""
                e.exercise.tracking == TrackingType.DURATION -> "${sets.size} × ${best.durationSec} s"
                best.weightKg > 0 -> "${sets.size} × ${unit.format(best.weightKg, withUnit = true)} × ${best.reps}"
                else -> "${sets.size} × ${best.reps}"
            }
            val dw = detailP.measureText(detail)
            c.drawText(fit(e.exercise.displayName(context), nameP, contentWidth - dw - 32f), PAD, y, nameP)
            c.drawText(detail, W - PAD - dw, y, detailP)
            y += rowHeight
        }
        if (done.size > MAX_EXERCISES) {
            c.drawText(context.getString(R.string.share_more_exercises, done.size - MAX_EXERCISES), PAD, y, detailP)
            y += 64f
        }

        // Récords
        if (records.isNotEmpty() && y < H - 220f) {
            y += 24f
            c.drawText("🏆 " + context.getString(R.string.n_records, records.size), PAD, y, text(44f, accent, bold = true))
            y += 60f
            records.take(3).forEach { pr ->
                if (y > H - 120f) return@forEach
                val line = context.getString(R.string.record_line, pr.exerciseName, context.getString(pr.type.labelRes()), unit.format(pr.value))
                c.drawText(fit(line, detailP, contentWidth), PAD, y, detailP)
                y += 54f
            }
        }

        val hr = w.avgHeartRate?.let { context.getString(R.string.bpm_value, it) }
        val footer = listOfNotNull(hr, w.calories?.let { (if (w.caloriesEstimated) "≈" else "") + context.getString(R.string.kcal_value, it) }).joinToString("  ·  ")
        if (footer.isNotEmpty()) c.drawText(footer, PAD, H - PAD, text(34f, Color.LTGRAY))
        return bmp
    }

    /** Genera la imagen en la caché y abre la hoja de compartir del sistema. */
    fun share(context: Context, w: Workout, records: List<PersonalRecord>, unit: WeightUnit) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "bestrong-workout.png")
        val bmp = render(context, w, records, unit)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, w.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_workout)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
