package com.bestrong.mobile.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bestrong.core.model.Workout
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.RestState
import com.bestrong.mobile.ui.components.displayName
import com.bestrong.mobile.ui.components.formatClock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano mientras hay un entreno activo en el móvil.
 * Mantiene vivo el temporizador de descanso con la pantalla apagada y lo muestra en la
 * notificación (cuenta atrás, saltar, +15 s). En One UI aparece también en la pantalla de bloqueo.
 */
@AndroidEntryPoint
class WorkoutForegroundService : LifecycleService() {

    @Inject lateinit var repository: BeStrongRepository

    @Inject lateinit var restTimer: RestTimerController

    @Inject lateinit var actions: WorkoutActions

    @Inject lateinit var settings: com.bestrong.mobile.data.SettingsStore

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SKIP -> restTimer.skip()
            ACTION_PLUS_15 -> restTimer.adjust(15)
            ACTION_COMPLETE -> lifecycleScope.launch { actions.completeNextSet() }
            ACTION_STOP -> {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!started) {
            started = true
            createChannel()
            // startForeground enseguida (obligatorio tras startForegroundService), aunque en arranque
            // en frío el entreno aún no se haya leído de la base de datos
            val now = repository.activeWorkout.value
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, now?.let { build(it, restTimer.rest.value) } ?: placeholder(), fgsType())
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo pasar a primer plano", e)
                stopSelf()
                return START_NOT_STICKY
            }
            collector?.cancel()
            collector = lifecycleScope.launch {
                if (repository.activeOnce() == null) return@launch stop()
                combine(repository.activeWorkout, restTimer.rest, repository.partnerWorkout) { w, r, p -> Triple(w, r, p) }.collect { (w, r, p) ->
                    when {
                        w != null -> getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(w, r, p))
                        // El StateFlow empieza en null: se comprueba en la base de datos antes de parar
                        repository.activeOnce() == null -> stop()
                    }
                }
            }
        }
        return START_STICKY
    }

    private var started = false

    /** Lo que mantiene la notificación al día (se cancela al parar para no duplicarlo si vuelve a arrancar). */
    private var collector: kotlinx.coroutines.Job? = null

    /** «3/12 series · ≈ 25 min, fin 18:45» (la hora de fin no cambia cada minuto, así que sirve aunque no se refresque). */
    private fun progressText(w: Workout): String {
        val now = System.currentTimeMillis()
        val remaining = WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = now, warmupRestSeconds = settings.settings.value.warmupRestSeconds)
        // En pausa la hora de fin no significa nada (no se refresca mientras tanto)
        if (remaining <= 0 || w.isPaused) return getString(R.string.notif_progress, w.completedSetCount, w.totalSetCount)
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        return getString(R.string.notif_progress_eta, w.completedSetCount, w.totalSetCount, (remaining + 59) / 60, formatClock(now + remaining * 1000L, is24))
    }

    private fun stop() {
        // Si el mismo servicio vuelve a arrancar (deshacer o empezar otro enseguida), tiene que volver a primer plano
        started = false
        collector?.cancel()
        collector = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Notificación provisional mientras se lee el entreno en curso. */
    private fun placeholder(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_dumbbell)
        .setContentTitle(getString(R.string.notif_channel))
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .build()

    private fun fgsType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            },
        )
    }

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, WorkoutForegroundService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(w: Workout, rest: RestState?, partner: Workout? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            AppIntents.main(this).setAction(AppIntents.ACTION_OPEN_WORKOUT).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setUsesChronometer(true)
            // Android 16 «Live Updates»: chip en la barra de estado, pantalla de bloqueo y Now Bar de One UI 8
            .setRequestPromotedOngoing(true)
        workoutProgress(w)?.let(b::setStyle)
        if (rest != null) {
            // El descanso puede ser de la pareja: su nombre y su siguiente serie, no los tuyos
            val shown = partner?.takeIf { it.id == rest.workoutId } ?: w
            // Qué viene después (lo que hay que cargar en la barra), visible en la pantalla de bloqueo
            val next = WorkoutEditor.upcomingSet(shown)?.let { (i, set) ->
                val ex = shown.exercises[i].exercise
                val prefs = settings.settings.value
                // Con «suma de las dos mancuernas», el total del par (como en la fila de la serie)
                val factor = if (prefs.dumbbellTotal && ex.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1
                getString(R.string.rest_next, ex.displayName(this) + " · " + com.bestrong.mobile.ui.workout.describeSet(set, ex, prefs.unit, factor))
            }
            b.setContentTitle(getString(R.string.notif_rest_title, shown.name))
                .setContentText(next ?: getString(R.string.notif_rest_text))
                .setWhen(rest.endsAtEpochMs)
                .setChronometerCountDown(true)
                .addAction(0, getString(R.string.rest_plus_15), action(ACTION_PLUS_15, 1))
                .addAction(0, getString(R.string.rest_skip), action(ACTION_SKIP, 2))
        } else {
            // El cronómetro descuenta las pausas (como la app) y en pausa se queda quieto
            b.setContentTitle(w.name)
                .setContentText(progressText(w))
                // Base fija (no salta al refrescar); en pausa, el tiempo congelado va en el texto
                .setWhen(w.startedAtEpochMs + w.pausedTotalMs)
                .setUsesChronometer(!w.isPaused)
                .setShowWhen(!w.isPaused)
                .apply { if (w.isPaused) setSubText(com.bestrong.core.util.formatDuration(w.durationSeconds())) }
                .setChronometerCountDown(false)
                // Texto corto del chip (en descanso se muestra la cuenta atrás)
                .setShortCriticalText("${w.completedSetCount}/${w.totalSetCount}")
            // Desde la pantalla de bloqueo: completar la siguiente serie sin abrir la app
            if (w.completedSetCount < w.totalSetCount) b.addAction(0, getString(R.string.notif_complete_set), action(ACTION_COMPLETE, 3))
        }
        return b.build()
    }

    /** Barra de progreso por segmentos: un segmento por ejercicio, avanza con cada serie hecha. */
    private fun workoutProgress(w: Workout): NotificationCompat.ProgressStyle? {
        val segments = w.exercises.filter { it.sets.isNotEmpty() }.map { NotificationCompat.ProgressStyle.Segment(it.sets.size) }
        if (segments.isEmpty()) return null
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(segments)
            .setProgress(w.completedSetCount)
            .setStyledByProgress(true)
    }

    companion object {
        private const val TAG = "WorkoutFgs"
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_SKIP = "com.bestrong.REST_SKIP"
        private const val ACTION_PLUS_15 = "com.bestrong.REST_PLUS_15"
        private const val ACTION_STOP = "com.bestrong.WORKOUT_STOP"
        private const val ACTION_COMPLETE = "com.bestrong.SET_COMPLETE"

        fun start(context: Context) {
            // Ya se está entrenando: el «hoy toca» no sigue en la barra (su «Empezar» abriría otro entreno)
            com.bestrong.mobile.notify.Notifier.cancelReminder(context)
            runCatching { context.startForegroundService(Intent(context, WorkoutForegroundService::class.java)) }
                .onFailure { Log.w(TAG, "No se pudo arrancar el servicio (¿app en segundo plano?)", it) }
        }
    }
}
