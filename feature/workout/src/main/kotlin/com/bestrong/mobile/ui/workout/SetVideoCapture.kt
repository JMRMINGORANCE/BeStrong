package com.bestrong.mobile.ui.workout

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.mobile.data.SetVideoMeta
import com.bestrong.mobile.data.SetVideoStore
import com.bestrong.mobile.shared.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.launch

/**
 * Grabar la serie en vídeo (propuesta 04): abre la cámara del sistema y deja el vídeo en la carpeta privada de la app
 * con los datos de la serie que toca (o la última hecha). Se revisa luego en la ficha del ejercicio. No sale del móvil.
 * Devuelve la acción para lanzarlo.
 */
@Composable
internal fun rememberSetVideoRecorder(we: WorkoutExercise): () -> Unit {
    val context = LocalContext.current
    // El almacén de vídeos, sin pasar por el ViewModel del entreno (ya hace demasiado)
    val store = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, SetVideoEntryPoint::class.java).setVideos() }
    val scope = rememberCoroutineScope()
    // Sobrevive a que el sistema cierre la app mientras la cámara está abierta
    var pendingPath by rememberSaveable(we.id) { mutableStateOf<String?>(null) }
    val noCamera = stringResource(R.string.set_video_no_camera)
    val saved = stringResource(R.string.set_video_saved)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = pendingPath?.let(::File) ?: return@rememberLauncherForActivityResult
        pendingPath = null
        // La serie que se grabó: la primera sin hacer o, si están todas, la última
        val index = we.sets.indexOfFirst { !it.completed }.takeIf { it >= 0 } ?: we.sets.lastIndex
        val set = we.sets.getOrNull(index) ?: return@rememberLauncherForActivityResult
        if (ok) {
            scope.launch { store.commit(file, SetVideoMeta(System.currentTimeMillis(), set.weightKg, set.reps, set.rpe, index + 1)) }
        } else {
            store.discard(file)
        }
        if (ok) Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
    }
    return {
        val file = store.newFile(we.exercise.id)
        pendingPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingPath = null
            file.delete()
            Toast.makeText(context, noCamera, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Acceso al almacén de vídeos de técnica desde la interfaz. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SetVideoEntryPoint {
    fun setVideos(): SetVideoStore
}
