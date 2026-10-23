package com.bestrong.mobile.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Foto de progreso guardada solo en el móvil (no entra en las copias de Android ni sale del dispositivo). */
data class ProgressPhoto(val file: File, val dateEpochMs: Long)

@Singleton
class ProgressPhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "progress")
    private val _photos = MutableStateFlow(read())
    val photos: StateFlow<List<ProgressPhoto>> = _photos

    private fun read(): List<ProgressPhoto> = dir.listFiles().orEmpty()
        .mapNotNull { f -> f.nameWithoutExtension.substringBefore('_').toLongOrNull()?.let { ProgressPhoto(f, it) } }
        .sortedByDescending { it.dateEpochMs }

    suspend fun add(uri: Uri, dateEpochMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        // La fecha en que se hizo la foto (si la galería la sabe), no la de hoy: varias antiguas a la vez se ordenan bien
        val date = takenAt(uri)?.takeIf { it in 1..System.currentTimeMillis() } ?: dateEpochMs
        val target = File(dir, "${date}_${System.nanoTime() % 100000}.jpg")
        // Si la copia falla a medias, no queda una foto rota
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: error("No se pudo leer la foto")
        }.onFailure { target.delete() }.getOrThrow()
        _photos.value = read()
    }

    private fun takenAt(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    suspend fun delete(photo: ProgressPhoto) = withContext(Dispatchers.IO) {
        photo.file.delete()
        _photos.value = read()
    }
}
