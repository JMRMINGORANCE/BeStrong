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
        if (info != null && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            val version = info.availableVersionCode()
            val notify = shouldNotify(version, prefs.getInt(KEY_NOTIFIED_VERSION, 0), prefs.getLong(KEY_LAST_OPEN, 0), System.currentTimeMillis())
            if (notify && notify(applicationContext)) prefs.edit().putInt(KEY_NOTIFIED_VERSION, version).apply()
        }
        return Result.success()
    }

    /** Al tocarla se abre la app, que enseña el diálogo para actualizar. */
    @Suppress("MissingPermission")
    private fun notify(context: Context): Boolean {
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
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(context.getString(R.string.update_notification_text))
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
