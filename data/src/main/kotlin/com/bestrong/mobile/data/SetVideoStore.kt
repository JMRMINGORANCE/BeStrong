package com.bestrong.mobile.data

import android.content.Context
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

/** Datos de la serie grabada: cuándo y con qué carga (kg × reps, RPE). */
@Serializable
data class SetVideoMeta(
    val recordedAtEpochMs: Long,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null,
    val setNumber: Int = 0,
)

/** Vídeo de una serie con sus datos. */
data class SetVideo(val file: File, val meta: SetVideoMeta)

/**
 * Vídeos de técnica de las series (grabados desde el entreno): files/media/<ejercicio>/sets/<id>.mp4 con sus datos en
 * <id>.json. Solo en el móvil (carpeta privada de la app, sin copias en la nube ni en la galería).
 */
@Singleton
class SetVideoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.filesDir, "media")

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun dir(exerciseId: String) = File(File(root, exerciseId.replace(Regex("[^A-Za-z0-9_-]"), "_")), "sets")

    /** Archivo nuevo (vacío) donde la cámara deja el vídeo; se confirma con [commit] o se borra con [discard]. */
    fun newFile(exerciseId: String): File = File(dir(exerciseId).apply { mkdirs() }, "${UUID.randomUUID()}.mp4")

    suspend fun commit(file: File, meta: SetVideoMeta) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@withContext false
        }
        File(file.parentFile, file.nameWithoutExtension + ".json").writeText(json.encodeToString(SetVideoMeta.serializer(), meta))
        _version.value++
        true
    }

    fun discard(file: File) {
        file.delete()
    }

    /** Los vídeos del ejercicio, el más reciente primero. */
    fun videos(exerciseId: String): List<SetVideo> =
        dir(exerciseId).listFiles().orEmpty()
            .filter { it.extension == "mp4" && it.length() > 0 }
            .map { f ->
                val meta = runCatching {
                    json.decodeFromString(SetVideoMeta.serializer(), File(f.parentFile, f.nameWithoutExtension + ".json").readText())
                }.getOrElse { SetVideoMeta(f.lastModified()) }
                SetVideo(f, meta)
            }
            .sortedByDescending { it.meta.recordedAtEpochMs }

    fun delete(video: SetVideo) {
        video.file.delete()
        File(video.file.parentFile, video.file.nameWithoutExtension + ".json").delete()
        _version.value++
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
