package com.bestrong.wear.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.startExercise
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.bestrong.wear.BeStrongWearApp
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano durante el entrenamiento:
 * - Sesión de Health Services (ExerciseType.WEIGHTLIFTING) con pulso y calorías del sensor BioActive.
 * - Ongoing Activity: el entreno aparece en la esfera y en el lanzador mientras dura.
 * - Mantiene vivo el proceso con la pantalla apagada.
 */
class ExerciseService : LifecycleService() {

    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }
    private val session get() = (application as BeStrongWearApp).container.session
    private var exerciseStarted = false

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { session.onHeartRate(it.value.toInt()) }
            update.latestMetrics.getData(DataType.CALORIES_TOTAL)?.let { session.onCalories(it.total) }
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "No se pudo registrar el callback de ejercicio", throwable)
        }
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!exerciseStarted) {
            exerciseStarted = true
            lifecycleScope.launch { startExercise() }
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Entrenamiento", NotificationManager.IMPORTANCE_DEFAULT))
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dumbbell)
            .setContentTitle("BeStrong")
            .setContentText("Entrenamiento en curso")
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)

        val startedAt = session.workout.value?.startedAtEpochMs ?: System.currentTimeMillis()
        val elapsedBase = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAt)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_dumbbell)
            .setTouchIntent(openApp)
            .setStatus(
                Status.Builder()
                    .addTemplate("#name# · #time#")
                    .addPart("name", Status.TextPart(session.workout.value?.name ?: "Entrenando"))
                    .addPart("time", Status.StopwatchPart(elapsedBase))
                    .build(),
            )
            .build()
            .apply(applicationContext)

        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            true
        } catch (e: Exception) {
            // Sin permiso de sensores no se puede usar el tipo "health"
            Log.w(TAG, "No se pudo iniciar en primer plano", e)
            false
        }
    }

    private suspend fun startExercise() {
        try {
            val capabilities = exerciseClient.getCapabilities()
            val type = ExerciseType.WEIGHTLIFTING
            if (type !in capabilities.supportedExerciseTypes) {
                Log.i(TAG, "El dispositivo no soporta WEIGHTLIFTING; sin métricas")
                return
            }
            val supported = capabilities.getExerciseTypeCapabilities(type).supportedDataTypes
            val dataTypes = setOf(DataType.HEART_RATE_BPM, DataType.CALORIES_TOTAL).filter { it in supported }.toSet()
            exerciseClient.setUpdateCallback(callback)
            exerciseClient.startExercise(
                ExerciseConfig.builder(type)
                    .setDataTypes(dataTypes)
                    .setIsAutoPauseAndResumeEnabled(false)
                    .setIsGpsEnabled(false)
                    .build(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar la sesión de Health Services", e)
        }
    }

    private fun stopEverything() {
        lifecycleScope.launch {
            if (exerciseStarted) {
                runCatching { exerciseClient.endExercise() }
                runCatching { exerciseClient.clearUpdateCallback(callback) }
            }
            ServiceCompat.stopForeground(this@ExerciseService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "ExerciseService"
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.bestrong.wear.STOP"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ExerciseService::class.java)) }
                .onFailure { Log.w(TAG, "No se pudo arrancar el servicio", it) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, ExerciseService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
