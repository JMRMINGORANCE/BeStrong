package com.bestrong.mobile.workout

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
import com.bestrong.mobile.ui.components.RestState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
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

    /** Descanso programado más reciente: un job cancelado no suelta el WakeLock del siguiente. Va antes del init, que ya lo usa. */
    private val generation = AtomicInteger()

    /**
     * Con la pantalla apagada la CPU puede dormirse y el delay() no despertaría a tiempo: se mantiene
     * despierta (solo CPU) durante el descanso, con un tope de seguridad por si algo falla.
     */
    private val wakeLock: PowerManager.WakeLock? by lazy {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeStrong:rest")
            ?.apply { setReferenceCounted(false) }
    }

    private fun holdWakeLock(ms: Long) {
        runCatching { wakeLock?.acquire(ms + WAKE_MARGIN_MS) }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    /** Cuándo terminó el último descanso sin que se haya empezado la siguiente serie (aviso de descanso excesivo). */
    private val _restEndedAt = MutableStateFlow<Long?>(null)
    val restEndedAt: StateFlow<Long?> = _restEndedAt.asStateFlow()

    init {
        // El aviso de un descanso perdido lo decide quien sabe si sigue habiendo entreno ([alertIfMissed])
        if (_rest.value != null) schedule()
    }

    /**
     * Android cerró la app durante el descanso y vuelve justo después de que acabara: se avisa igualmente (una vez),
     * que si no se seguía esperando sin saber que ya tocaba. Pasados unos minutos ya no tiene sentido.
     */
    fun alertIfMissed() {
        val ends = prefs.getLong("ends", 0)
        val late = System.currentTimeMillis() - ends
        if (ends <= 0 || late !in 0..MISSED_END_MS) return
        persist(null)
        if (vibrateEnabled()) vibrate()
        announcer.say(context.getString(com.bestrong.mobile.shared.R.string.tts_go))
    }

    private fun restore(): RestState? {
        val ends = prefs.getLong("ends", 0)
        val total = prefs.getInt("total", 0)
        return if (ends > System.currentTimeMillis() && total > 0) RestState(ends, total, prefs.getString("workout", null)) else null
    }

    private fun persist(r: RestState?) {
        prefs.edit().apply { if (r == null) clear() else putLong("ends", r.endsAtEpochMs).putInt("total", r.totalSeconds).putString("workout", r.workoutId) }.apply()
    }

    fun start(seconds: Int, workoutId: String? = null) {
        if (seconds <= 0) return skip()
        _restEndedAt.value = null
        _rest.value = RestState(System.currentTimeMillis() + seconds * 1000L, seconds, workoutId)
        persist(_rest.value)
        schedule()
    }

    /** Retoma un descanso que ya estaba en marcha (al deshacer un descarte), con su duración total. */
    fun restore(endsAtEpochMs: Long, totalSeconds: Int, workoutId: String? = null) {
        if (endsAtEpochMs <= System.currentTimeMillis()) return skip()
        _restEndedAt.value = null
        _rest.value = RestState(endsAtEpochMs, totalSeconds.coerceAtLeast(1), workoutId)
        persist(_rest.value)
        schedule()
    }

    fun adjust(deltaSeconds: Int) {
        val r = _rest.value ?: return
        _rest.value = r.copy(
            endsAtEpochMs = (r.endsAtEpochMs + deltaSeconds * 1000L).coerceAtLeast(System.currentTimeMillis()),
            totalSeconds = (r.totalSeconds + deltaSeconds).coerceAtLeast(1),
        )
        persist(_rest.value)
        schedule()
    }

    /**
     * Quita el descanso solo si es del entreno [workoutId] (o de origen desconocido): pausar o desmarcar en el de la
     * pareja no corta el tuyo, ni al revés.
     */
    fun skipFor(workoutId: String?) {
        val owner = _rest.value?.workoutId
        if (owner == null || owner == workoutId) skip()
    }

    fun skip() {
        job?.cancel()
        releaseWakeLock()
        _restEndedAt.value = null
        _rest.value = null
        persist(null)
    }

    private fun schedule() {
        job?.cancel()
        val gen = generation.incrementAndGet()
        _rest.value?.let { holdWakeLock((it.endsAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)) }
        job = scope.launch {
            val r = _rest.value ?: return@launch
            try {
                // 3-2-1: tres toques cortos en los últimos tres segundos (si se eligió y hay tiempo)
                val countdownAt = r.endsAtEpochMs - COUNTDOWN_MS
                if (settings.settings.value.restCountdown && vibrateEnabled() && countdownAt > System.currentTimeMillis()) {
                    delay(countdownAt - System.currentTimeMillis())
                    countdown()
                }
                delay((r.endsAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0))
                // Solo se quita el descanso de este temporizador: si justo empezó otro (serie marcada a la vez en el
                // móvil o el reloj), ese sigue y aquí no se avisa de nada
                if (generation.get() != gen || !_rest.compareAndSet(r, null)) return@launch
                persist(null)
                if (vibrateEnabled()) vibrate()
                announcer.say(context.getString(com.bestrong.mobile.shared.R.string.tts_go))
            } finally {
                if (generation.get() == gen) releaseWakeLock()
            }
            if (generation.get() != gen) return@launch
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

    private fun countdown() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 940, 60, 940, 60), -1))
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

        /** La cuenta atrás de los últimos segundos. */
        private const val COUNTDOWN_MS = 3_000L

        /** Hasta cuánto después de acabar un descanso perdido (app cerrada por Android) aún se avisa. */
        private const val MISSED_END_MS = 2 * 60_000L

        /** Margen del WakeLock sobre el descanso (se suelta antes al terminar). */
        private const val WAKE_MARGIN_MS = 10_000L
    }
}
