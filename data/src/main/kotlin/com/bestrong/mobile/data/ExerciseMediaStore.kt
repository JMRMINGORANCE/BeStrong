package com.bestrong.mobile.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Foto o vídeo de técnica guardado en la app. */
data class MediaItem(val file: File, val isVideo: Boolean)

/**
 * Imagen o animación de un ejercicio con su atribución: GIF de ExerciseDB (AscendAPI, dataset
 * gratuito v1 para uso no comercial) o imagen de wger.de (licencia abierta, normalmente CC-BY-SA 4.0).
 */
@Serializable
data class ExerciseImage(
    val url: String,
    val source: String = "wger",
    val isGif: Boolean = false,
    val wgerName: String = "",
    val wgerUrl: String = "",
    val author: String = "",
    val authorUrl: String = "",
    val license: String = "CC-BY-SA 4.0",
    val licenseUrl: String = "https://creativecommons.org/licenses/by-sa/4.0/",
)

/**
 * Notas de técnica por ejercicio: texto y fotos/vídeos que elige el usuario. Se copian a la
 * carpeta privada de la app (files/media/<ejercicio>/), así que no dependen de la galería
 * y no salen del móvil.
 */
@Singleton
class ExerciseMediaStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.filesDir, "media")

    /** Sube cada vez que cambia algo, para refrescar la UI. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun dir(exerciseId: String) = File(root, exerciseId.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    fun items(exerciseId: String): List<MediaItem> =
        dir(exerciseId).listFiles().orEmpty()
            .filter { it.extension in IMAGE_EXT || it.extension in VIDEO_EXT }
            .sortedBy { it.lastModified() }
            .map { MediaItem(it, it.extension in VIDEO_EXT) }

    suspend fun add(exerciseId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.startsWith("video/") -> "mp4"
            mime == "image/png" -> "png"
            else -> "jpg"
        }
        val target = File(dir(exerciseId).apply { mkdirs() }, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("No se pudo leer el archivo")
        _version.value++
    }

    fun delete(item: MediaItem) {
        item.file.delete()
        _version.value++
    }

    fun note(exerciseId: String): String = File(dir(exerciseId), NOTE).takeIf { it.exists() }?.readText().orEmpty()

    suspend fun saveNote(exerciseId: String, text: String) = withContext(Dispatchers.IO) {
        val f = File(dir(exerciseId).apply { mkdirs() }, NOTE)
        if (text.isBlank()) f.delete() else f.writeText(text)
        _version.value++
    }

    /** Todas las notas de técnica (clave = carpeta del ejercicio), para la copia de seguridad. */
    suspend fun allNotes(): Map<String, String> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().mapNotNull { d -> File(d, NOTE).takeIf { it.exists() }?.let { d.name to it.readText() } }.toMap()
    }

    /** Restaura notas de una copia sin pisar las que ya haya. Devuelve cuántas se añadieron. */
    suspend fun restoreNotes(notes: Map<String, String>): Int {
        val fresh = notes.filter { (id, text) -> text.isNotBlank() && withContext(Dispatchers.IO) { note(id).isBlank() } }
        fresh.forEach { (id, text) -> saveNote(id, text) }
        return fresh.size
    }

    /** Imágenes de wger cruzadas con la biblioteca (assets/exercise_media.json; vacío si no existe). */
    val images: Map<String, ExerciseImage> by lazy {
        runCatching {
            context.assets.open("exercise_media.json").use { s ->
                json.decodeFromString<Map<String, ExerciseImage>>(s.readBytes().decodeToString())
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val NOTE = "note.txt"
        val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
        val VIDEO_EXT = setOf("mp4", "webm", "3gp")
        val json = Json { ignoreUnknownKeys = true }
    }
}
