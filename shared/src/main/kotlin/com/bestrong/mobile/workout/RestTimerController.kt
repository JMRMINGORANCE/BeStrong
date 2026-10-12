package com.bestrong.mobile.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
import com.bestrong.mobile.ui.components.RestState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Temporizador de descanso a nivel de proceso (no de pantalla): sigue funcionando con la app
 * en segundo plano gracias a [WorkoutForegroundService], que además lo muestra en la notificación.
 */
@Singleton
class RestTimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val announcer: Announcer,
) {
    private val vibrateEnabled: () -> Boolean = { settings.settings.value.vibrateOnRestEnd }

    /** El descanso se guarda: si Android cierra la app, al volver sigue donde estaba. */
    private val prefs = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
    private val _rest = MutableStateFlow(restore())
    val rest: StateFlow<RestState?> = _rest.asStateFlow()
    private var job: Job? = null

    /** Cuándo terminó el último descanso sin que se haya empezado la siguiente serie (aviso de descanso excesivo). */
    private val _restEndedAt = MutableStateFlow<Long?>(null)
    val restEndedAt: StateFlow<Long?> = _restEndedAt.asStateFlow()

    init {
        if (_rest.value != null) schedule()
    }

    private fun restore(): RestState? {
        val ends = prefs.getLong("ends", 0)
        val total = prefs.getInt("total", 0)
        return if (ends > System.currentTimeMillis() && total > 0) RestState(ends, total) else null
    }

    private fun persist(r: RestState?) {
        prefs.edit().apply { if (r == null) clear() else putLong("ends", r.endsAtEpochMs).putInt("total", r.totalSeconds) }.apply()
    }

    fun start(seconds: Int) {
        if (seconds <= 0) return skip()
        _restEndedAt.value = null
        _rest.value = RestState(System.currentTimeMillis() + seconds * 1000L, seconds)
        persist(_rest.value)
        schedule()
    }

    fun adjust(deltaSeconds: Int) {
        val r = _rest.value ?: return
        _rest.value = RestState(
            (r.endsAtEpochMs + deltaSeconds * 1000L).coerceAtLeast(System.currentTimeMillis()),
            (r.totalSeconds + deltaSeconds).coerceAtLeast(1),
        )
        persist(_rest.value)
        schedule()
    }

    fun skip() {
        job?.cancel()
        _restEndedAt.value = null
        _rest.value = null
        persist(null)
    }

    private fun schedule() {
        job?.cancel()
        job = scope.launch {
            val r = _rest.value ?: return@launch
            delay((r.endsAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0))
            _rest.value = null
            persist(null)
            if (vibrateEnabled()) vibrate()
            announcer.say(context.getString(com.bestrong.mobile.R.string.tts_go))
            // Si pasa un buen rato sin empezar la serie, un toque suave de recordatorio
            val ended = System.currentTimeMillis()
            _restEndedAt.value = ended
            delay(OVERRUN_ALERT_MS)
            if (_restEndedAt.value == ended && vibrateEnabled()) vibrate(short = true)
        }
    }

    /** Ya no hace falta el aviso (serie empezada o entreno terminado). */
    fun clearOverrun() {
        _restEndedAt.value = null
    }

    private fun vibrate(short: Boolean = false) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        val pattern = if (short) longArrayOf(0, 120) else settings.settings.value.restVibration.pattern
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        /** Margen tras el descanso antes de avisar de que se está alargando. */
        const val OVERRUN_ALERT_MS = 60_000L
    }
}
