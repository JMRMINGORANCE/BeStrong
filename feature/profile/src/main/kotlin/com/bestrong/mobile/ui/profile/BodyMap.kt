package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.MuscleTargets
import com.bestrong.core.stats.VolumeStatus
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.label

/** Una zona del cuerpo en coordenadas de una figura de 120 × 250. */
private data class Region(val muscle: MuscleGroup, val rect: Rect, val oval: Boolean = false)

private fun r(l: Float, t: Float, rr: Float, b: Float) = Rect(l, t, rr, b)

private val FRONT = listOf(
    Region(MuscleGroup.SHOULDERS, r(20f, 46f, 42f, 68f), oval = true),
    Region(MuscleGroup.SHOULDERS, r(78f, 46f, 100f, 68f), oval = true),
    Region(MuscleGroup.CHEST, r(40f, 50f, 80f, 80f)),
    Region(MuscleGroup.BICEPS, r(14f, 68f, 31f, 102f), oval = true),
    Region(MuscleGroup.BICEPS, r(89f, 68f, 106f, 102f), oval = true),
    Region(MuscleGroup.FOREARMS, r(8f, 104f, 24f, 142f), oval = true),
    Region(MuscleGroup.FOREARMS, r(96f, 104f, 112f, 142f), oval = true),
    Region(MuscleGroup.ABS, r(43f, 83f, 77f, 128f)),
    Region(MuscleGroup.QUADRICEPS, r(39f, 134f, 58f, 192f)),
    Region(MuscleGroup.QUADRICEPS, r(62f, 134f, 81f, 192f)),
    Region(MuscleGroup.CALVES, r(42f, 197f, 56f, 242f)),
    Region(MuscleGroup.CALVES, r(64f, 197f, 78f, 242f)),
)

private val BACK = listOf(
    Region(MuscleGroup.TRAPS, r(40f, 40f, 80f, 58f)),
    Region(MuscleGroup.SHOULDERS, r(20f, 46f, 42f, 68f), oval = true),
    Region(MuscleGroup.SHOULDERS, r(78f, 46f, 100f, 68f), oval = true),
    Region(MuscleGroup.BACK, r(38f, 60f, 82f, 126f)),
    Region(MuscleGroup.TRICEPS, r(14f, 68f, 31f, 102f), oval = true),
    Region(MuscleGroup.TRICEPS, r(89f, 68f, 106f, 102f), oval = true),
    Region(MuscleGroup.FOREARMS, r(8f, 104f, 24f, 142f), oval = true),
    Region(MuscleGroup.FOREARMS, r(96f, 104f, 112f, 142f), oval = true),
    Region(MuscleGroup.GLUTES, r(40f, 129f, 80f, 154f)),
    Region(MuscleGroup.HAMSTRINGS, r(39f, 156f, 58f, 194f)),
    Region(MuscleGroup.HAMSTRINGS, r(62f, 156f, 81f, 194f)),
    Region(MuscleGroup.CALVES, r(42f, 197f, 56f, 242f)),
    Region(MuscleGroup.CALVES, r(64f, 197f, 78f, 242f)),
)

private const val FIG_W = 120f
private const val FIG_H = 250f
private const val GAP = 24f

/**
 * Silueta frontal y trasera coloreada por las series de la semana (propuesta 3): bajo / óptimo / alto con los
 * colores de estado (también daltónicos). Tocar un músculo enseña su detalle debajo; la lista de abajo sigue
 * siendo la versión accesible.
 */
@Composable
internal fun BodyMuscleMap(weekly: Map<MuscleGroup, Double>, target: Int) {
    val statusOf = { m: MuscleGroup -> MuscleTargets.status(weekly[m] ?: 0.0, target.toDouble()) }
    val default = remember(weekly, target) {
        MAP_MUSCLES_BODY.firstOrNull { statusOf(it) == VolumeStatus.HIGH }
            ?: MAP_MUSCLES_BODY.filter { statusOf(it) == VolumeStatus.LOW }.minByOrNull { weekly[it] ?: 0.0 }
            ?: MAP_MUSCLES_BODY.maxByOrNull { weekly[it] ?: 0.0 }
    }
    var selected by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    val current = selected ?: default
    val colors = MAP_MUSCLES_BODY.associateWith { statusOf(it).color() }
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    val outline = MaterialTheme.colorScheme.onSurface
    val description = stringResource(R.string.rd_body_map_desc)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio((FIG_W * 2 + GAP) / FIG_H)
                    .semantics { contentDescription = description }
                    .pointerInput(Unit) {
                        detectTapGestures { p ->
                            val scale = size.height / FIG_H
                            val x = p.x / scale
                            val y = p.y / scale
                            val (regions, fx) = if (x < FIG_W + GAP / 2) FRONT to x else BACK to (x - FIG_W - GAP)
                            regions.lastOrNull { it.rect.contains(Offset(fx, y)) }?.let { selected = it.muscle }
                        }
                    },
            ) {
                val scale = size.height / FIG_H
                drawFigure(FRONT, 0f, scale, colors, neutral, outline, current)
                drawFigure(BACK, (FIG_W + GAP) * scale, scale, colors, neutral, outline, current)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Text(stringResource(R.string.rd_body_front), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.rd_body_back), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        current?.let { MuscleDetail(it, weekly[it] ?: 0.0, target) }
    }
}

private fun DrawScope.drawFigure(
    regions: List<Region>,
    offsetX: Float,
    scale: Float,
    colors: Map<MuscleGroup, Color>,
    neutral: Color,
    outline: Color,
    selected: MuscleGroup?,
) {
    fun o(x: Float, y: Float) = Offset(offsetX + x * scale, y * scale)
    fun s(w: Float, h: Float) = Size(w * scale, h * scale)
    // Cabeza, cuello, manos y pies en neutro
    drawCircle(neutral, 15f * scale, o(60f, 20f))
    drawRoundRect(neutral, o(53f, 33f), s(14f, 10f), CornerRadius(3f * scale))
    drawCircle(neutral, 6f * scale, o(16f, 148f))
    drawCircle(neutral, 6f * scale, o(104f, 148f))
    regions.forEach { reg ->
        val color = colors[reg.muscle]?.takeIf { it != Color.Unspecified } ?: neutral
        val topLeft = o(reg.rect.left, reg.rect.top)
        val size = s(reg.rect.width, reg.rect.height)
        if (reg.oval) drawOval(color, topLeft, size) else drawRoundRect(color, topLeft, size, CornerRadius(6f * scale))
        if (reg.muscle == selected) {
            val stroke = Stroke(width = 2.5f * scale)
            if (reg.oval) drawOval(outline, topLeft, size, style = stroke) else drawRoundRect(outline, topLeft, size, CornerRadius(6f * scale), style = stroke)
        }
    }
}

/** El músculo elegido: series, estado, barra con la zona óptima y un consejo. */
@Composable
private fun MuscleDetail(m: MuscleGroup, sets: Double, target: Int) {
    val status = MuscleTargets.status(sets, target.toDouble())
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.label(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(10.dp).clip(CircleShape).background(status.color()))
            Text(
                status.label() + " · " + stringResource(R.string.n_sets_value, formatWeight(sets)),
                Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        // Barra hasta 25 series, con la zona óptima (mínimo – 20) marcada
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        val zone = com.bestrong.mobile.ui.theme.statusColors.good.copy(alpha = 0.25f)
        val fill = status.color()
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val cr = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = cr)
            val from = (target / BAR_MAX).toFloat().coerceIn(0f, 1f) * size.width
            val to = (MuscleTargets.MAX_WEEKLY_SETS / BAR_MAX).toFloat() * size.width
            drawRoundRect(zone, Offset(from, 0f), Size(to - from, size.height), cr)
            val w = (sets / BAR_MAX).toFloat().coerceIn(0f, 1f) * size.width
            if (w > 0f) drawRoundRect(fill, Offset.Zero, Size(w, size.height), cr)
        }
        val advice = when (status) {
            VolumeStatus.HIGH -> stringResource(R.string.rd_body_high)
            VolumeStatus.OPTIMAL -> stringResource(R.string.rd_body_optimal)
            VolumeStatus.LOW, VolumeStatus.NONE -> {
                val missing = (target - sets).coerceAtLeast(1.0)
                val ex = ExerciseLibrary.all.firstOrNull { it.primaryMuscle == m }
                if (ex != null) stringResource(R.string.rd_body_low_with, formatWeight(missing), ex.displayName()) else stringResource(R.string.rd_body_low, formatWeight(missing))
            }
        }
        Text(advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
    }
}

private val MAP_MUSCLES_BODY = (FRONT + BACK).map { it.muscle }.distinct()
private const val BAR_MAX = 25.0
