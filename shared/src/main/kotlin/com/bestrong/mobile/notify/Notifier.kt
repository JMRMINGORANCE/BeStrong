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
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.PeriodSummary
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.labelRes

/** Notificaciones que no son la del entreno en curso: récords y recordatorios. */
object Notifier {
    private const val CHANNEL_RECORDS = "records"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val ID_REMINDER = 20
    private const val ID_WEEKLY = 21
    private const val ID_RECORDS_BASE = 100

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDS, context.getString(R.string.notif_channel_records), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        AppIntents.main(context).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Nuevos récords personales de un entreno (p. ej. registrado en el reloj). */
    @Suppress("MissingPermission")
    fun records(context: Context, workout: Workout, records: List<PersonalRecord>, unit: WeightUnit) {
        if (records.isEmpty() || !canPost(context)) return
        ensureChannels(context)
        val lines = records.map {
            context.getString(R.string.record_line, it.exerciseName, context.getString(it.type.labelRes()), unit.format(it.value))
        }
        val style = NotificationCompat.InboxStyle()
        lines.take(6).forEach(style::addLine)
        val n = NotificationCompat.Builder(context, CHANNEL_RECORDS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_records_title, records.size))
            .setContentText(lines.first())
            .setStyle(style.setSummaryText(workout.name))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_RECORDS_BASE + (workout.id.hashCode() and 0xFFFF), n)
    }

    /** Recordatorio de entreno del día. */
    @Suppress("MissingPermission")
    fun reminder(context: Context, routineName: String?) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText(routineName?.let { context.getString(R.string.notif_reminder_routine, it) } ?: context.getString(R.string.notif_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_REMINDER, n)
    }

    /** Resumen semanal (domingo por la tarde). */
    /** [highLoadRatio]: relación aguda:crónica si la carga subió demasiado (null = normal). */
    @Suppress("MissingPermission")
    fun weeklySummary(context: Context, week: PeriodSummary, unit: WeightUnit, highLoadRatio: Double? = null) {
        if (!canPost(context)) return
        ensureChannels(context)
        val text = context.getString(R.string.notif_weekly_text, week.workouts, unit.formatVolume(week.volume), week.sets)
        val lines = listOfNotNull(
            text,
            week.records.takeIf { it.isNotEmpty() }?.let { context.getString(R.string.notif_weekly_records, it.size) },
            week.volumeChange?.let { c -> context.getString(R.string.notif_weekly_change, (if (c >= 0) "+" else "−") + "${(kotlin.math.abs(c) * 100).toInt()} %") },
            highLoadRatio?.let { context.getString(R.string.notif_weekly_load_high, com.bestrong.core.util.formatWeight(it)) },
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_weekly_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_WEEKLY, n)
    }
}
