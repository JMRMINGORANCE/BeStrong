package com.bestrong.mobile.workout

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Pitido corto y discreto al acabar el descanso (propuesta #8). Va por el volumen de notificaciones: en silencio o
 * en «No molestar» no suena, y con los auriculares puestos se oye por ellos. No necesita permisos ni archivos de sonido.
 */
object RestChime {
    /** Volumen del tono sobre el de notificaciones (0-100): suave, para no sobresaltar en el gimnasio. */
    private const val VOLUME = 60

    /** Dos pitidos breves, unos 300 ms en total. */
    private const val DURATION_MS = 300

    /** Margen antes de soltar el generador (después de que termine el tono). */
    private const val RELEASE_MS = 600L

    fun play() {
        // Algunos móviles no dejan crear el generador (sin audio libre): entonces no suena y no pasa nada más
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME) }.getOrNull() ?: return
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_ACK, DURATION_MS) }
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, RELEASE_MS)
    }
}
