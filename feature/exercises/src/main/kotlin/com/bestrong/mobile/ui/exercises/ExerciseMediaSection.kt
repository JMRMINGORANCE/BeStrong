package com.bestrong.mobile.ui.exercises

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bestrong.core.model.Exercise
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader

/** Imagen del ejercicio (wger, con atribución) y notas de técnica propias con foto/vídeo. */
@Composable
fun ExerciseMediaSection(vm: ExercisesViewModel, exercise: Exercise) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version by vm.mediaVersion.collectAsStateWithLifecycle()
    val items = remember(exercise.id, version) { vm.mediaItems(exercise.id) }
    val image = remember(exercise.id) { vm.image(exercise.id) }
    var note by rememberSaveable(exercise.id) { mutableStateOf(vm.note(exercise.id)) }
    var savedNote by remember(exercise.id) { mutableStateOf(note) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.addMedia(exercise.id, it) } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        image?.let { img ->
            Card(Modifier.fillMaxWidth()) {
                // La animación de internet sobre la miniatura de la app: sin conexión se ve al menos la postura
                val thumb = remember(exercise.id) { com.bestrong.mobile.ui.components.ExerciseImageIndex.thumb(context, exercise.id) }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(220.dp).background(androidx.compose.ui.graphics.Color.White)) {
                    if (thumb != null && thumb != img.url) {
                        AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
                    }
                    AsyncImage(
                        model = img.url,
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
        SectionHeader(stringResource(R.string.technique_notes))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(R.string.technique_notes_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (note != savedNote) {
            TextButton(onClick = {
                vm.saveNote(exercise.id, note)
                savedNote = note
            }) { Text(stringResource(R.string.action_save)) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(items, key = { it.file.name }) { item ->
                val open = stringResource(if (item.isVideo) R.string.media_open_video else R.string.media_open_photo)
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClickLabel = open) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", item.file)
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (item.isVideo) "video/*" else "image/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                // Sin visor instalado: no hacemos nada
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.isVideo) {
                        Icon(Icons.Default.PlayCircle, open, Modifier.size(40.dp))
                    } else {
                        AsyncImage(model = item.file, contentDescription = open, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                    }
                    IconButton(onClick = { vm.deleteMedia(item) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.media_delete))
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AddAPhoto, null)
                        Text(stringResource(R.string.media_add), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
