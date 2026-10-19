package com.bestrong.mobile.ui.workout

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.share.MusicApps
import com.bestrong.mobile.shared.R

/**
 * Controles de la música que suene (Apple Music, Spotify, YouTube Music…) sin salir del entreno.
 * Envía las teclas multimedia del sistema: no necesita cuentas ni permisos. El último botón
 * abre la app de música elegida en Ajustes.
 */
@Composable
fun MediaControls(musicApp: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = remember(musicApp) { MusicApps.preferred(context, musicApp) }
    fun send(code: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
    // En una píldora con su nota musical: que no se confunda con la pausa del entreno
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            androidx.compose.ui.Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.MusicNote, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            IconButton(onClick = { send(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.media_previous)) }
            IconButton(onClick = { send(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.media_play_pause)) }
            IconButton(onClick = { send(KeyEvent.KEYCODE_MEDIA_NEXT) }) { Icon(Icons.Default.SkipNext, stringResource(R.string.media_next)) }
            app?.let { a ->
                IconButton(onClick = { MusicApps.open(context, a) }) { Icon(Icons.Default.LibraryMusic, stringResource(R.string.media_open_app, a.label)) }
            }
        }
    }
}
