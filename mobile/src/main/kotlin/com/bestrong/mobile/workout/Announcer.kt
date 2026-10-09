package com.bestrong.mobile.workout

import android.content.Context
import android.speech.tts.TextToSpeech
import com.bestrong.mobile.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avisos por voz (TextToSpeech): la siguiente serie al empezar el descanso y «¡a por ella!»
 * al acabarlo. Se oyen por los auriculares (Galaxy Buds u otros) sin mirar el móvil.
 */
@Singleton
class Announcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    private fun engine(): TextToSpeech = tts ?: TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = context.resources.configuration.locales[0] ?: Locale.getDefault()
            pending?.let { say(it) }
            pending = null
        }
    }.also { tts = it }

    fun say(text: String) {
        if (!settings.settings.value.voiceAnnouncements) return
        val e = engine()
        if (!ready) {
            pending = text
            return
        }
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bestrong")
    }
}
