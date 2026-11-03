package com.bestrong.mobile.ui.exercises

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.TrackingType
import com.bestrong.core.util.formatRpe
import com.bestrong.mobile.data.MediaItem
import com.bestrong.mobile.data.SetVideo
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatShortDate

/**
 * Un vídeo de técnica: grabado en el entreno (con los datos de la serie) o añadido a mano desde la galería.
 * Los dos se ven igual: a cámara lenta y comparando dos a la vez.
 */
private data class TechniqueVideo(val file: java.io.File, val recordedAtEpochMs: Long, val set: SetVideo? = null, val own: MediaItem? = null)

/**
 * «Técnica» (todo en un sitio): la nota fija del ejercicio (la que se ve en el entreno y en el reloj), las fotos propias
 * y una sola lista de vídeos (los de las series grabadas en el entreno y los añadidos a mano), con cámara lenta y
 * «Comparar». Todo se queda en este móvil.
 */
@Composable
fun TechniqueSection(vm: ExercisesViewModel, exercise: Exercise) {
    val context = LocalContext.current
    val setVersion by vm.setVideoVersion.collectAsStateWithLifecycle()
    val mediaVersion by vm.mediaVersion.collectAsStateWithLifecycle()
    val media = remember(exercise.id, mediaVersion) { vm.mediaItems(exercise.id) }
    val photos = media.filter { !it.isVideo }
    val videos = remember(exercise.id, setVersion, mediaVersion) {
        (
            vm.setVideos(exercise.id).map { TechniqueVideo(it.file, it.meta.recordedAtEpochMs, set = it) } +
                media.filter { it.isVideo }.map { TechniqueVideo(it.file, it.file.lastModified(), own = it) }
            ).sortedByDescending { it.recordedAtEpochMs }
    }
    var note by rememberSaveable(exercise.id) { mutableStateOf(vm.note(exercise.id)) }
    // Lo guardado de verdad (no la nota restaurada al volver a la vista, que puede estar sin guardar)
    var savedNote by remember(exercise.id) { mutableStateOf(vm.note(exercise.id)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.addMedia(exercise.id, it) } }
    var playing by remember { mutableStateOf<List<TechniqueVideo>>(emptyList()) }
    var comparing by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<TechniqueVideo>() }
    var toDelete by remember { mutableStateOf<TechniqueVideo?>(null) }
    var photoToDelete by remember { mutableStateOf<MediaItem?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.technique_title)) {
            if (videos.size >= 2) {
                TextButton(onClick = { comparing = !comparing; picked.clear() }) {
                    Icon(Icons.Default.CompareArrows, null, Modifier.size(18.dp))
                    Text(stringResource(if (comparing) R.string.action_cancel else R.string.set_video_compare), Modifier.padding(start = 6.dp))
                }
            }
        }
        // Nota fija: asiento, agarre… (la misma que sale en el entreno y en el reloj)
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(R.string.technique_note_label)) },
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
        // Fotos propias y el botón para añadir foto o vídeo
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(photos, key = { it.file.name }) { item ->
                val open = stringResource(R.string.media_open_photo)
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClickLabel = open) { openPhoto(context, item.file) },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(model = item.file, contentDescription = open, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                    // 48 dp de zona táctil y con confirmación: un toque torcido no borra la foto para siempre
                    IconButton(onClick = { photoToDelete = item }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Default.Close, stringResource(R.string.media_delete), Modifier.size(20.dp))
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
        if (comparing) {
            Text(stringResource(R.string.set_video_compare_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        videos.forEach { v ->
            TechniqueVideoRow(
                v,
                exercise,
                comparing = comparing,
                selected = v in picked,
                onClick = {
                    if (!comparing) {
                        playing = listOf(v)
                    } else {
                        if (v in picked) {
                            picked.remove(v)
                        } else if (picked.size < 2) {
                            picked.add(v)
                        }
                        if (picked.size == 2) {
                            playing = picked.toList()
                            comparing = false
                            picked.clear()
                        }
                    }
                },
                onDelete = { toDelete = v },
            )
        }
    }
    TechniqueDeleteDialogs(vm, exercise, toDelete, photoToDelete, onDismissVideo = { toDelete = null }, onDismissPhoto = { photoToDelete = null })
    if (playing.isNotEmpty()) TechniquePlayer(playing, exercise) { playing = emptyList() }
}

/** Confirmación antes de borrar un vídeo (de serie o propio) o una foto: no hay papelera. */
@Composable
private fun TechniqueDeleteDialogs(
    vm: ExercisesViewModel,
    exercise: Exercise,
    video: TechniqueVideo?,
    photo: MediaItem?,
    onDismissVideo: () -> Unit,
    onDismissPhoto: () -> Unit,
) {
    video?.let { v ->
        AlertDialog(
            onDismissRequest = { onDismissVideo() },
            title = { Text(stringResource(R.string.set_video_delete)) },
            text = { Text(videoLabel(v, exercise) + " · " + formatShortDate(v.recordedAtEpochMs)) },
            confirmButton = {
                TextButton(onClick = {
                    v.set?.let(vm::deleteSetVideo)
                    v.own?.let(vm::deleteMedia)
                    onDismissVideo()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { onDismissVideo() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    photo?.let { item ->
        AlertDialog(
            onDismissRequest = { onDismissPhoto() },
            title = { Text(stringResource(R.string.media_delete)) },
            text = { Text(stringResource(R.string.media_open_photo)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteMedia(item); onDismissPhoto() }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { onDismissPhoto() }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Una fila de la lista de vídeos: tocar la reproduce (o la elige para comparar). */
@Composable
private fun TechniqueVideoRow(v: TechniqueVideo, exercise: Exercise, comparing: Boolean, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (comparing) R.string.set_video_pick else R.string.set_video_play), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(if (comparing) Icons.Default.CompareArrows else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(videoLabel(v, exercise), fontWeight = FontWeight.Bold)
            Text(formatShortDate(v.recordedAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!comparing) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.set_video_delete)) }
        }
    }
}

/** Abre una foto propia en el visor del sistema (sin visor instalado no hace nada). */
private fun openPhoto(context: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sin visor instalado: no hacemos nada
    }
}

/** «Serie 3 · 85 kg × 6 @8» para los de las series; «Vídeo propio» para los añadidos a mano. */
@Composable
private fun videoLabel(v: TechniqueVideo, exercise: Exercise): String {
    val m = v.set?.meta ?: return stringResource(R.string.technique_own_video)
    val unit = LocalWeightUnit.current
    val load = when {
        exercise.tracking == TrackingType.WEIGHT_REPS && m.weightKg > 0 -> "${unit.format(m.weightKg, withUnit = true)} × ${m.reps}"
        m.reps > 0 -> "${m.reps} reps"
        else -> ""
    }
    val text = listOfNotNull(
        stringResource(R.string.set_video_set_n, m.setNumber).takeIf { m.setNumber > 0 },
        load.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return text.ifBlank { stringResource(R.string.technique_set_video) } + (m.rpe?.let { " @${formatRpe(it)}" } ?: "")
}

/** Reproductor a pantalla completa: uno o dos vídeos, con la misma velocidad (0,25×, 0,5× o 1×) y «Repetir». */
@Composable
private fun TechniquePlayer(videos: List<TechniqueVideo>, exercise: Exercise, onClose: () -> Unit) {
    var speed by remember { mutableFloatStateOf(0.5f) }
    var restart by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.technique_title), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
                }
                videos.forEach { v ->
                    Text(videoLabel(v, exercise) + " · " + formatShortDate(v.recordedAtEpochMs), fontWeight = FontWeight.Bold)
                    VideoBox(v, speed, restart, Modifier.fillMaxWidth().aspectRatio(if (videos.size > 1) 16f / 10f else 9f / 12f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.25f, 0.5f, 1f).forEach { s ->
                        FilterChip(selected = speed == s, onClick = { speed = s; restart++ }, label = { Text(speedLabel(s)) })
                    }
                    FilledTonalButton(onClick = { restart++ }) {
                        Icon(Icons.Default.Replay, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.set_video_replay), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

private fun speedLabel(s: Float): String = when (s) {
    0.25f -> "0,25×"
    0.5f -> "0,5×"
    else -> "1×"
}

/** Un VideoView en bucle con la velocidad elegida; [restart] lo vuelve a empezar (para ver dos a la vez sincronizados). */
@Composable
private fun VideoBox(v: TechniqueVideo, speed: Float, restart: Int, modifier: Modifier) {
    var player by remember(v.file) { mutableStateOf<MediaPlayer?>(null) }
    Box(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(v.file.absolutePath)
                    // Un vídeo dañado no abre el diálogo de error del sistema: se queda en negro
                    setOnErrorListener { _, _, _ -> true }
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        player = mp
                        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
                        start()
                    }
                }
            },
            update = { view ->
                // Se vuelve a llamar al cambiar la velocidad o pulsar «Repetir»
                if (restart >= 0) {
                    player?.let { mp ->
                        runCatching {
                            mp.playbackParams = mp.playbackParams.setSpeed(speed)
                            mp.seekTo(0)
                        }
                        view.start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    DisposableEffect(v.file) { onDispose { player = null } }
}
