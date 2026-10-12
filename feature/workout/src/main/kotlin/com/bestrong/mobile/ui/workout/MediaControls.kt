package com.bestrong.mobile.ui.workout

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bestrong.mobile.shared.R

/**
 * Controles de la música que suene (Spotify, YouTube Music…) sin salir del entreno.
 * Envía las teclas multimedia del sistema: no necesita cuentas ni permisos.
 */
@Composable
fun MediaControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun send(code: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { send(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { Icon(Icons.Default.SkipPrevious, stringResource(R.string.media_previous)) }
        IconButton(onClick = { send(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.media_play_pause)) }
        IconButton(onClick = { send(KeyEvent.KEYCODE_MEDIA_NEXT) }) { Icon(Icons.Default.SkipNext, stringResource(R.string.media_next)) }
    }
}
