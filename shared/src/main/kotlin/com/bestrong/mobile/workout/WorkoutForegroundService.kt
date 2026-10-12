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
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.WorkoutActions
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.RestState
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
        val workout = repository.activeWorkout.value
        if (workout == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            createChannel()
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, build(workout, restTimer.rest.value), fgsType())
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo pasar a primer plano", e)
                stopSelf()
                return START_NOT_STICKY
            }
            lifecycleScope.launch {
                combine(repository.activeWorkout, restTimer.rest) { w, r -> w to r }.collect { (w, r) ->
                    if (w == null) {
                        ServiceCompat.stopForeground(this@WorkoutForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(w, r))
                    }
                }
            }
        }
        return START_STICKY
    }

    private var started = false

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

    private fun build(w: Workout, rest: RestState?): Notification {
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
            b.setContentTitle(getString(R.string.notif_rest_title, w.name))
                .setContentText(getString(R.string.notif_rest_text))
                .setWhen(rest.endsAtEpochMs)
                .setChronometerCountDown(true)
                .addAction(0, getString(R.string.rest_plus_15), action(ACTION_PLUS_15, 1))
                .addAction(0, getString(R.string.rest_skip), action(ACTION_SKIP, 2))
        } else {
            b.setContentTitle(w.name)
                .setContentText(getString(R.string.notif_progress, w.completedSetCount, w.totalSetCount))
                .setWhen(w.startedAtEpochMs)
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
            runCatching { context.startForegroundService(Intent(context, WorkoutForegroundService::class.java)) }
                .onFailure { Log.w(TAG, "No se pudo arrancar el servicio (¿app en segundo plano?)", it) }
        }
    }
}
