package com.bestrong.wear.health

import android.annotation.SuppressLint
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
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.pauseExercise
import androidx.health.services.client.resumeExercise
import androidx.health.services.client.startExercise
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.CardioEnergy
import com.bestrong.core.training.CardioKind
import com.bestrong.wear.MainActivity
import com.bestrong.wear.R
import com.bestrong.wear.session.CardioSession
import com.bestrong.wear.session.CardioType
import com.bestrong.wear.session.WorkoutSession
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Servicio en primer plano durante el entrenamiento:
 * - Sesión de Health Services (ExerciseType.WEIGHTLIFTING) con pulso y calorías del sensor BioActive.
 * - Ongoing Activity: el entreno aparece en la esfera y en el lanzador mientras dura.
 * - Mantiene vivo el proceso con la pantalla apagada.
 */
@AndroidEntryPoint
class ExerciseService : LifecycleService() {

    @Inject lateinit var session: WorkoutSession

    @Inject lateinit var cardio: CardioSession

    private val exerciseClient by lazy { HealthServices.getClient(this).exerciseClient }
    private var exerciseStarted = false

    /** Tipo del ejercicio abierto en Health Services (pesas o la máquina de cardio en marcha). */
    private var currentType: ExerciseType? = null

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val m = update.latestMetrics
            // Todas las lecturas del lote, con su hora real (no solo la última)
            val boot = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            if (cardio.isActive) {
                m.getData(DataType.HEART_RATE_BPM).forEach { p -> cardio.onHeartRate(p.value.roundToInt(), p.getTimeInstant(boot).toEpochMilli()) }
                m.getData(DataType.CALORIES_TOTAL)?.let { cardio.onCalories(it.total) }
                m.getData(DataType.DISTANCE_TOTAL)?.let { cardio.onDistance(it.total) }
                m.getData(DataType.SPEED).lastOrNull()?.let { cardio.onSpeed(it.value) }
                return
            }
            m.getData(DataType.HEART_RATE_BPM).forEach { p ->
                session.onHeartRate(p.value.roundToInt(), p.getTimeInstant(boot).toEpochMilli())
            }
            m.getData(DataType.HEART_RATE_BPM_STATS)?.let { session.onHeartRateStats(it.average.roundToInt(), it.max.roundToInt()) }
            m.getData(DataType.CALORIES_TOTAL)?.let { session.onCalories(it.total) }
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
            lifecycleScope.launch {
                startExercise()
                followCardio()
            }
            followPause()
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dumbbell)
            .setContentTitle("BeStrong")
            .setContentText(getString(R.string.notif_text))
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)

        val startedAt = cardio.state.value?.startedAtEpochMs ?: session.workout.value?.startedAtEpochMs ?: System.currentTimeMillis()
        val elapsedBase = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAt)
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_dumbbell)
            .setTouchIntent(openApp)
            .setStatus(
                Status.Builder()
                    .addTemplate("#name# · #time#")
                    .addPart("name", Status.TextPart(cardio.state.value?.let { com.bestrong.wear.ui.cardioName(this, it.type) } ?: session.workout.value?.name ?: getString(R.string.training)))
                    .addPart("time", Status.StopwatchPart(elapsedBase))
                    .build(),
            )
            .build()
            .apply(applicationContext)

        // Con GPS (cardio y permiso concedido) el servicio también es de ubicación
        val gps = cardio.isActive && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val type = if (gps) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), type)
            true
        } catch (e: Exception) {
            // Sin permiso de sensores no se puede usar el tipo "health"
            Log.w(TAG, "No se pudo iniciar en primer plano", e)
            false
        }
    }

    private suspend fun startExercise() {
        if (cardio.isActive) return startCardio()
        try {
            exerciseClient.setUpdateCallback(callback)
            val type = supportedOrNull(wantedType()) ?: supportedOrNull(ExerciseType.WEIGHTLIFTING)
            // Si Android reinició la app con el ejercicio aún en marcha, se sigue con él: empezar
            // otro pondría las calorías y las estadísticas de pulso a cero
            if (type != null && !ownsExerciseInProgress()) startSegment(type)
            currentType = type
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar la sesión de Health Services", e)
        }
    }

    /** Pesas, o la máquina del bloque de cardio con el cronómetro en marcha. */
    private fun wantedType(): ExerciseType =
        session.workout.value?.let { WorkoutEditor.runningCardio(it) }
            ?.let { (we, _) -> CardioEnergy.kindOf(we.exercise.id) }
            ?.let(::exerciseTypeFor)
            ?: ExerciseType.WEIGHTLIFTING

    private suspend fun supportedOrNull(type: ExerciseType): ExerciseType? =
        type.takeIf { it in exerciseClient.getCapabilities().supportedExerciseTypes }

    private suspend fun startSegment(type: ExerciseType) {
        val supported = exerciseClient.getCapabilities().getExerciseTypeCapabilities(type).supportedDataTypes
        val wanted = setOf(DataType.HEART_RATE_BPM, DataType.HEART_RATE_BPM_STATS, DataType.CALORIES_TOTAL)
        exerciseClient.startExercise(
            ExerciseConfig.builder(type)
                .setDataTypes(wanted.filter { it in supported }.toSet())
                .setIsAutoPauseAndResumeEnabled(false)
                .setIsGpsEnabled(false)
                .build(),
        )
    }

    /**
     * Cronómetro de cardio dentro de la rutina: se cierra el ejercicio de pesas y se abre el de la
     * máquina (cinta, elíptica…) para que el reloj calcule las calorías con su modelo; al pararlo,
     * vuelta a pesas. Las calorías de cada tramo se suman en [WorkoutSession.onHealthSegmentChanged].
     */
    private fun followCardio() {
        lifecycleScope.launch {
            session.workout
                .map { w -> w?.let { WorkoutEditor.runningCardio(it) }?.first?.exercise?.id }
                .distinctUntilChanged()
                .collect { if (!cardio.isActive && session.workout.value != null) switchTo(wantedType()) }
        }
    }

    private suspend fun switchTo(type: ExerciseType) {
        val target = runCatching { supportedOrNull(type) }.getOrNull() ?: ExerciseType.WEIGHTLIFTING
        if (target == currentType || currentType == null) return
        runCatching {
            exerciseClient.endExercise()
            // Health Services tarda un momento en cerrar el ejercicio anterior
            withTimeoutOrNull(SWITCH_TIMEOUT_MS) { while (ownsExerciseInProgress()) delay(SWITCH_POLL_MS) }
            session.onHealthSegmentChanged()
            startSegment(target)
            currentType = target
        }.onFailure { Log.w(TAG, "No se pudo cambiar de ejercicio en Health Services", it) }
    }

    private fun exerciseTypeFor(kind: CardioKind): ExerciseType = when (kind) {
        CardioKind.RUN -> ExerciseType.RUNNING
        CardioKind.WALK -> ExerciseType.WALKING
        CardioKind.TREADMILL -> ExerciseType.RUNNING_TREADMILL
        CardioKind.BIKE -> ExerciseType.BIKING
        CardioKind.STATIONARY_BIKE -> ExerciseType.BIKING_STATIONARY
        CardioKind.ELLIPTICAL -> ExerciseType.ELLIPTICAL
        CardioKind.ROWER -> ExerciseType.ROWING_MACHINE
        CardioKind.STAIRS -> ExerciseType.STAIR_CLIMBING_MACHINE
        CardioKind.JUMP_ROPE -> ExerciseType.JUMP_ROPE
    }

    /** Carrera, caminata o bici con GPS: distancia, velocidad, pulso y calorías. */
    private suspend fun startCardio() {
        try {
            val capabilities = exerciseClient.getCapabilities()
            val type = when (cardio.state.value?.type) {
                CardioType.WALKING -> ExerciseType.WALKING
                CardioType.CYCLING -> ExerciseType.BIKING
                else -> ExerciseType.RUNNING
            }
            if (type !in capabilities.supportedExerciseTypes) return
            val supported = capabilities.getExerciseTypeCapabilities(type).supportedDataTypes
            val wanted = setOf(DataType.HEART_RATE_BPM, DataType.CALORIES_TOTAL, DataType.DISTANCE_TOTAL, DataType.SPEED)
            val gps = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            exerciseClient.setUpdateCallback(callback)
            exerciseClient.startExercise(
                ExerciseConfig.builder(type)
                    .setDataTypes(wanted.filter { it in supported }.toSet())
                    .setIsAutoPauseAndResumeEnabled(false)
                    .setIsGpsEnabled(gps)
                    .build(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar el cardio", e)
        }
    }

    /**
     * ¿Sigue en marcha nuestro ejercicio (la app se reinició a mitad del entreno)? Es el uso
     * documentado de Health Services; la versión alfa marca la constante como restringida.
     */
    @SuppressLint("RestrictedApi")
    private suspend fun ownsExerciseInProgress(): Boolean =
        runCatching { exerciseClient.getCurrentExerciseInfo().exerciseTrackedStatus }.getOrNull() ==
            ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS

    /** La pausa del entreno (desde el móvil o el reloj) también pausa pulso y calorías. */
    private fun followPause() {
        lifecycleScope.launch {
            session.workout.map { it?.isPaused == true }.distinctUntilChanged().collect { paused ->
                if (cardio.isActive) return@collect
                runCatching { if (paused) exerciseClient.pauseExercise() else exerciseClient.resumeExercise() }
            }
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
        private const val SWITCH_TIMEOUT_MS = 5_000L
        private const val SWITCH_POLL_MS = 250L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ExerciseService::class.java)) }
                .onFailure { Log.w(TAG, "No se pudo arrancar el servicio", it) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, ExerciseService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
