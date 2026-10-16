package com.bestrong.mobile.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.ImageRequest
import com.bestrong.core.model.Exercise
import com.bestrong.mobile.shared.R
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Imagen de cada ejercicio para las listas: la animación de la biblioteca (ExerciseDB / wger) o, en los propios,
 * la primera foto de técnica que se le haya añadido. Se lee una vez del asset y se guarda en memoria.
 */
object ExerciseImageIndex {
    @Volatile private var urls: Map<String, String>? = null

    @Volatile private var bundled: Set<String>? = null

    fun url(context: Context, exerciseId: String): Any? {
        val map = urls ?: load(context).also { urls = it }
        return map[exerciseId] ?: ownPhoto(context, exerciseId)
    }

    /**
     * Para las listas: la miniatura que va dentro de la app (se ve sin conexión y al instante); si no hay,
     * la imagen de internet o la foto propia.
     */
    fun thumb(context: Context, exerciseId: String): Any? {
        val inApp = bundled ?: runCatching { context.assets.list(THUMBS).orEmpty().toSet() }.getOrDefault(emptySet()).also { bundled = it }
        return if ("$exerciseId.webp" in inApp) "file:///android_asset/$THUMBS/$exerciseId.webp" else url(context, exerciseId)
    }

    private const val THUMBS = "exercise_thumbs"

    private fun load(context: Context): Map<String, String> = runCatching {
        val text = context.assets.open("exercise_media.json").use { it.readBytes().decodeToString() }
        Json.parseToJsonElement(text).jsonObject.mapValues { (_, v) -> (v as JsonObject)["url"]!!.jsonPrimitive.content }
    }.getOrDefault(emptyMap())

    /** Misma carpeta que las fotos de técnica (files/media/<ejercicio>/). */
    private fun ownPhoto(context: Context, exerciseId: String): File? =
        File(File(context.filesDir, "media"), exerciseId.replace(Regex("[^A-Za-z0-9_-]"), "_")).listFiles().orEmpty()
            .filter { it.extension in setOf("jpg", "jpeg", "png", "webp") }
            .minByOrNull { it.lastModified() }
}

/**
 * Miniatura del ejercicio (fotograma fijo, ligera para listas largas); tocarla abre la animación en grande.
 * Sin imagen, un icono sobre el color de su grupo.
 */
@Composable
fun ExerciseThumb(exercise: Exercise, size: Dp = 52.dp, modifier: Modifier = Modifier, enlargeOnTap: Boolean = true) {
    val context = LocalContext.current
    val model = remember(exercise.id) { ExerciseImageIndex.url(context, exercise.id) }
    val thumb = remember(exercise.id) { ExerciseImageIndex.thumb(context, exercise.id) }
    var big by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.28f)
    val description = stringResource(R.string.exercise_image_desc, exercise.displayName())
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest),
                ),
            )
            .then(
                if (enlargeOnTap && model != null) {
                    Modifier.clickable(onClickLabel = stringResource(R.string.exercise_image_open)) { big = true }
                        .semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // El icono hasta que llega la imagen (o si no hay conexión); con ella, fondo blanco como el de las animaciones
        var loaded by remember(exercise.id) { mutableStateOf(false) }
        if (!loaded) Icon(Icons.Default.FitnessCenter, null, Modifier.size(size * 0.5f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        if (thumb != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumb).decoderFactory(BitmapFactoryDecoder.Factory())
                    // La de la app llega al instante: el fundido solo para la de internet
                    .crossfade(thumb !is String || !thumb.startsWith("file:")).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.size(size).then(if (loaded) Modifier.background(androidx.compose.ui.graphics.Color.White) else Modifier),
            )
        }
    }
    if (big && model != null) {
        AlertDialog(
            onDismissRequest = { big = false },
            title = { Text(exercise.displayName()) },
            text = {
                // Animada (GIF) en grande, para ver la técnica; debajo la miniatura, por si no hay conexión
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White)) {
                    if (thumb != null && thumb != model) {
                        AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = stringResource(R.string.exercise_image_desc, exercise.displayName()),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { big = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** Las miniaturas de los primeros ejercicios de una rutina, solapadas, y «+N» con el resto. */
@Composable
fun ExerciseThumbStrip(exercises: List<Exercise>, size: Dp = 40.dp, max: Int = 5, modifier: Modifier = Modifier) {
    if (exercises.isEmpty()) return
    val shown = exercises.distinctBy { it.id }.take(max)
    val rest = exercises.distinctBy { it.id }.size - shown.size
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, e ->
            ExerciseThumb(
                e,
                size,
                Modifier.offset(x = -(size * 0.22f) * i)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(size * 0.28f)),
                enlargeOnTap = false,
            )
        }
        if (rest > 0) {
            Text(
                "+$rest",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = -(size * 0.22f) * (shown.size - 1) + 6.dp),
            )
        }
    }
}
