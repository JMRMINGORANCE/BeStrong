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
        val target = File(dir, "${dateEpochMs}_${System.nanoTime() % 100000}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("No se pudo leer la foto")
        _photos.value = read()
    }

    fun delete(photo: ProgressPhoto) {
        photo.file.delete()
        _photos.value = read()
    }
}
