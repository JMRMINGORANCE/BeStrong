package com.bestrong.mobile.share

import android.content.ClipData
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import com.bestrong.designsystem.BeStrongPalette

/**
 * Arrastrar y soltar entre apps (pantalla dividida del Fold): un entreno se suelta como texto
 * en notas, chats o correo. La «sombra» es una etiqueta pequeña con el nombre.
 */
object DragShare {
    fun start(view: View, label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        view.startDragAndDrop(clip, LabelShadow(label, view.resources.displayMetrics.density), null, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
    }

    private class LabelShadow(private val label: String, density: Float) : View.DragShadowBuilder() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16 * density
        }
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BeStrongPalette.OrangeArgb }
        private val pad = 12 * density
        private val width = (paint.measureText(label) + 2 * pad).toInt().coerceAtMost((320 * density).toInt())
        private val height = (paint.textSize + 2 * pad).toInt()

        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(width, height)
            outShadowTouchPoint.set(width / 2, height / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), pad, pad, bg)
            canvas.drawText(label, pad, height - pad - paint.descent() / 2, paint)
        }
    }
}
