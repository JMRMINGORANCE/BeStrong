package com.bestrong.mobile.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.shared.R
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/**
 * Una vez al día (con conexión) se pregunta a Google Play si hay versión nueva. Si la hay y la app lleva días sin
 * abrirse (al abrirla ya sale el diálogo con «Actualizar»), se avisa con una notificación, una sola vez por versión.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val info = runCatching { AppUpdateManagerFactory.create(applicationContext).appUpdateInfo.await() }.getOrNull()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (info == null) return Result.success()
        val version = info.availableVersionCode()
        val lastOpen = prefs.getLong(KEY_LAST_OPEN, 0)
        val now = System.currentTimeMillis()
        when {
            // Descargada pero sin instalar (se aceptó y se cerró la app): «Lista para instalar», una vez por versión
            info.installStatus() == InstallStatus.DOWNLOADED ->
                if (shouldNotify(version, prefs.getInt(KEY_NOTIFIED_READY, 0), lastOpen, now) && notify(applicationContext, ready = true)) {
                    prefs.edit().putInt(KEY_NOTIFIED_READY, version).apply()
                }
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ->
                if (shouldNotify(version, prefs.getInt(KEY_NOTIFIED_VERSION, 0), lastOpen, now) && notify(applicationContext, ready = false)) {
                    prefs.edit().putInt(KEY_NOTIFIED_VERSION, version).apply()
                }
        }
        return Result.success()
    }

    /** Al tocarla se abre la app, que enseña el diálogo para actualizar. */
    @Suppress("MissingPermission")
    private fun notify(context: Context, ready: Boolean): Boolean {
        // El mismo canal que los avisos del equipo («Avisos de BeStrong», ver AnnouncementsWorker)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_announcements), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            AppIntents.main(context).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(if (ready) R.string.update_ready_title else R.string.update_available_title))
            .setContentText(context.getString(if (ready) R.string.update_ready_text else R.string.update_notification_text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        return true
    }

    companion object {
        const val PREFS = "app_updates"
        const val KEY_LAST_OPEN = "last_open"
        private const val KEY_NOTIFIED_VERSION = "notified_version"
        private const val KEY_NOTIFIED_READY = "notified_ready"
        private const val NAME = "update-check"
        private const val CHANNEL = "announcements"
        private const val NOTIFICATION_ID = 28
        private const val EVERY_HOURS = 24L
        private const val UNUSED_MS = 2 * 86_400_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Solo una vez por versión, y solo si la app lleva más de 2 días sin abrirse. */
        fun shouldNotify(available: Int, notifiedVersion: Int, lastOpen: Long, now: Long): Boolean =
            available != notifiedVersion && now - lastOpen >= UNUSED_MS
    }
}
