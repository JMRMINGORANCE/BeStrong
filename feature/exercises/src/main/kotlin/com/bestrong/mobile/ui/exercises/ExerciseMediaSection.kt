package com.bestrong.mobile.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bestrong.core.model.Exercise
import com.bestrong.mobile.shared.R

/** Imagen del ejercicio (wger o ExerciseDB, con atribución) y lo que va debajo (qué trabaja y cómo se hace). */
@Composable
fun ExerciseMediaSection(vm: ExercisesViewModel, exercise: Exercise, afterImage: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val image = remember(exercise.id) { vm.image(exercise.id) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        image?.let { img ->
            com.bestrong.mobile.ui.components.Panel(contentPadding = 0.dp) {
                // La animación que va en la app (sin conexión); si no la hay, la de internet
                val thumb = remember(exercise.id) { com.bestrong.mobile.ui.components.ExerciseImageIndex.thumb(context, exercise.id) }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(220.dp).background(com.bestrong.mobile.ui.components.exerciseImageBackground()).then(com.bestrong.mobile.ui.components.exerciseImageDim())) {
                    AsyncImage(
                        // La de la app, sin fundido (llega al instante)
                        model = coil.request.ImageRequest.Builder(context).data(thumb ?: img.url).crossfade(thumb == null).build(),
                        contentDescription = stringResource(R.string.exercise_image_desc, img.wgerName.ifBlank { exercise.name }),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                // Atribución obligatoria: AscendAPI (ExerciseDB) o autor + licencia CC-BY-SA (wger)
                Text(
                    if (img.source == "exercisedb") {
                        stringResource(R.string.exercise_gif_credit)
                    } else {
                        stringResource(R.string.exercise_image_credit, img.author.ifBlank { "wger.de" }, img.license)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(img.wgerUrl.ifBlank { img.licenseUrl }) }.padding(8.dp),
                )
            }
        }
        // Debajo de la animación: qué trabaja y cómo se hace
        afterImage()
    }
}
