package com.bestrong.mobile.notify

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
import java.util.concurrent.TimeUnit

/**
 * Con la app cerrada: dos veces al día (con conexión), si este móvil ha enviado alguna propuesta, se mira si alguna
 * se ha hecho y se avisa con una notificación, una sola vez por propuesta. Las avisadas así ya no salen en el banner
 * de [ImplementedProposals]; si las notificaciones están desactivadas no se marca nada y avisa el banner al abrir.
 */
class ImplementedProposalsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val device = ImplementedProposals.device(context)
        // Sin conexión o sin respuesta del buzón (list = null): se prueba en la siguiente vuelta
        val list = if (device != null && canNotify(context)) runCatching { ImplementedProposals.fetch(device) }.getOrNull() else null
        ImplementedProposals.toShow(list.orEmpty(), ImplementedProposals.shown(context)).forEach { p ->
            notify(context, p)
            ImplementedProposals.markShown(context, p.number)
        }
        return Result.success()
    }

    private fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** En el canal de avisos de BeStrong (como los del equipo); al tocarla se abre la app. */
    @Suppress("MissingPermission")
    private fun notify(context: Context, proposal: ImplementedProposal) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_announcements), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val id = NOTIFICATION_BASE + proposal.number % NOTIFICATION_SLOTS
        val open = PendingIntent.getActivity(
            context, id,
            AppIntents.main(context).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(R.string.implemented_banner_text, proposal.title)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.implemented_banner_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    companion object {
        private const val NAME = "implemented-proposals"
        private const val CHANNEL = "announcements"
        private const val NOTIFICATION_BASE = 1000
        private const val NOTIFICATION_SLOTS = 1000
        private const val EVERY_HOURS = 12L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ImplementedProposalsWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
