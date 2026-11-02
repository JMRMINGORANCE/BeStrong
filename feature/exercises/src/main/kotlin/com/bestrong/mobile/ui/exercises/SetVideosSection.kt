package com.bestrong.mobile.ui.exercises

import android.media.MediaPlayer
import android.widget.VideoView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.TrackingType
import com.bestrong.core.util.formatRpe
import com.bestrong.mobile.data.SetVideo
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatShortDate

/**
 * «Vídeos de técnica» (propuesta 04): las series grabadas desde el entreno, con su fecha y carga. Tocar uno lo abre a
 * cámara lenta; con «Comparar» se eligen dos y se ven a la vez, a la misma velocidad.
 */
@Composable
fun SetVideosSection(vm: ExercisesViewModel, exercise: Exercise) {
    val version by vm.setVideoVersion.collectAsStateWithLifecycle()
    val videos = remember(exercise.id, version) { vm.setVideos(exercise.id) }
    if (videos.isEmpty()) return
    var playing by remember { mutableStateOf<List<SetVideo>>(emptyList()) }
    var comparing by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<SetVideo>() }
    var toDelete by remember { mutableStateOf<SetVideo?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.set_video_section)) {
            if (videos.size >= 2) {
                TextButton(onClick = { comparing = !comparing; picked.clear() }) {
                    Icon(Icons.Default.CompareArrows, null, Modifier.size(18.dp))
                    Text(stringResource(if (comparing) R.string.action_cancel else R.string.set_video_compare), Modifier.padding(start = 6.dp))
                }
            }
        }
        if (comparing) {
            Text(stringResource(R.string.set_video_compare_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        videos.forEach { v ->
            val selected = v in picked
            val label = setLabel(v, exercise)
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .clickable(role = Role.Button, onClickLabel = stringResource(if (comparing) R.string.set_video_pick else R.string.set_video_play)) {
                        if (comparing) {
                            if (selected) {
                                picked.remove(v)
                            } else if (picked.size < 2) {
                                picked.add(v)
                            }
                            if (picked.size == 2) {
                                playing = picked.toList()
                                comparing = false
                                picked.clear()
                            }
                        } else {
                            playing = listOf(v)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(if (comparing) Icons.Default.CompareArrows else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(formatShortDate(v.meta.recordedAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!comparing) {
                    IconButton(onClick = { toDelete = v }) { Icon(Icons.Default.Delete, stringResource(R.string.set_video_delete)) }
                }
            }
        }
    }
    toDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.set_video_delete)) },
            text = { Text(setLabel(v, exercise) + " · " + formatShortDate(v.meta.recordedAtEpochMs)) },
            confirmButton = { TextButton(onClick = { vm.deleteSetVideo(v); toDelete = null }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (playing.isNotEmpty()) SetVideoPlayer(playing, exercise) { playing = emptyList() }
}

/** «Serie 3 · 85 kg × 6 @8». */
@Composable
private fun setLabel(v: SetVideo, exercise: Exercise): String {
    val unit = LocalWeightUnit.current
    val m = v.meta
    val load = when {
        exercise.tracking == TrackingType.WEIGHT_REPS && m.weightKg > 0 -> "${unit.format(m.weightKg, withUnit = true)} × ${m.reps}"
        m.reps > 0 -> "${m.reps} reps"
        else -> ""
    }
    return listOfNotNull(
        stringResource(R.string.set_video_set_n, m.setNumber).takeIf { m.setNumber > 0 },
        load.takeIf { it.isNotBlank() },
    ).joinToString(" · ") + (m.rpe?.let { " @${formatRpe(it)}" } ?: "")
}

/** Reproductor a pantalla completa: uno o dos vídeos, con la misma velocidad (0,25×, 0,5× o 1×) y «Repetir». */
@Composable
private fun SetVideoPlayer(videos: List<SetVideo>, exercise: Exercise, onClose: () -> Unit) {
    var speed by remember { mutableFloatStateOf(0.5f) }
    var restart by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.set_video_section), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) }
                }
                videos.forEach { v ->
                    Text(setLabel(v, exercise) + " · " + formatShortDate(v.meta.recordedAtEpochMs), fontWeight = FontWeight.Bold)
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
private fun VideoBox(v: SetVideo, speed: Float, restart: Int, modifier: Modifier) {
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
