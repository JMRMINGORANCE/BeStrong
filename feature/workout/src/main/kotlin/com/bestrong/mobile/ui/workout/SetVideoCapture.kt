package com.bestrong.mobile.ui.workout

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.mobile.shared.R
import java.io.File

/**
 * Grabar la serie en vídeo (propuesta 04): abre la cámara del sistema y deja el vídeo en la carpeta privada de la app
 * con los datos de la serie que toca (o la última hecha). Se revisa luego en la ficha del ejercicio. No sale del móvil.
 * Devuelve la acción para lanzarlo.
 */
@Composable
internal fun rememberSetVideoRecorder(vm: ActiveWorkoutViewModel, we: WorkoutExercise): () -> Unit {
    val context = LocalContext.current
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
        vm.saveSetVideo(file, ok, set, index + 1)
        if (ok) Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
    }
    return {
        val file = vm.newSetVideoFile(we.exercise.id)
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
