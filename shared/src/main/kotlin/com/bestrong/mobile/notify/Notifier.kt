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
import com.bestrong.core.stats.formatValue
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.labelRes

/** Notificaciones que no son la del entreno en curso: récords y recordatorios. */
object Notifier {
    private const val CHANNEL_RECORDS = "records"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_GOALS = "goals"
    private const val ID_REMINDER = 20
    private const val ID_WEEKLY = 21
    private const val ID_WEIGH_IN = 22
    private const val ID_MEASURE = 23
    private const val ID_BACKUP = 24
    private const val ID_CARDIO_GOAL = 25
    private const val ID_CARDIO_NUDGE = 26
    private const val ID_RECORDS_BASE = 100

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDS, context.getString(R.string.notif_channel_records), NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.notif_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
        )
        // Objetivos cumplidos (cardio de la semana): se pueden silenciar sin perder los récords
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GOALS, context.getString(R.string.notif_channel_goals), NotificationManager.IMPORTANCE_DEFAULT),
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
            context.getString(R.string.record_line, it.exerciseName, context.getString(it.type.labelRes()), it.type.formatValue(it.value, unit))
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
    fun reminder(context: Context, routineName: String?, routineId: String? = null) {
        if (!canPost(context)) return
        ensureChannels(context)
        val b = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText(routineName?.let { context.getString(R.string.notif_reminder_routine, it) } ?: context.getString(R.string.notif_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        // «Empezar» abre la app con la rutina ya en marcha
        if (routineId != null) {
            val start = PendingIntent.getActivity(
                context, ID_REMINDER,
                AppIntents.main(context).setAction(AppIntents.ACTION_START_ROUTINE).putExtra(AppIntents.EXTRA_ROUTINE_ID, routineId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(R.drawable.ic_stat_dumbbell, context.getString(R.string.notif_reminder_start), start)
        }
        // «Dentro de 1 h»: el mismo aviso más tarde
        val snooze = PendingIntent.getBroadcast(
            context, ID_REMINDER + 1,
            Intent(context, ReminderSnoozeReceiver::class.java).setPackage(context.packageName)
                .putExtra(ReminderSnoozeReceiver.EXTRA_NAME, routineName).putExtra(ReminderSnoozeReceiver.EXTRA_ID, routineId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        b.addAction(R.drawable.ic_stat_dumbbell, context.getString(R.string.notif_reminder_snooze), snooze)
        NotificationManagerCompat.from(context).notify(ID_REMINDER, b.build())
    }

    /** Quita el recordatorio (al posponerlo). */
    fun cancelReminder(context: Context) = NotificationManagerCompat.from(context).cancel(ID_REMINDER)

    /** Recordatorio semanal de pesarse: se puede contestar con el peso sin abrir la app (o tocar para abrirla). */
    @Suppress("MissingPermission")
    fun weighIn(context: Context, retry: Boolean = false) {
        if (!canPost(context)) return
        ensureChannels(context)
        val input = androidx.core.app.RemoteInput.Builder(WeighInReplyReceiver.KEY_WEIGHT)
            .setLabel(context.getString(R.string.notif_weigh_in_reply_hint))
            .build()
        // Mutable: el sistema añade la respuesta al intent
        val reply = android.app.PendingIntent.getBroadcast(
            context, ID_WEIGH_IN,
            android.content.Intent(context, WeighInReplyReceiver::class.java).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_dumbbell, context.getString(R.string.notif_weigh_in_reply), reply)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_weigh_in_title))
            .setContentText(context.getString(if (retry) R.string.notif_weigh_in_invalid else R.string.notif_weigh_in_text))
            // Al volver tras una respuesta no entendida no vuelve a sonar
            .setOnlyAlertOnce(retry)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(action)
            .build()
        NotificationManagerCompat.from(context).notify(ID_WEIGH_IN, n)
    }

    /**
     * La copia automática se ha parado: la carpeta ya no está ([folderLost]) o lleva más de dos semanas fallando.
     * Al tocarla se abre la app (en Perfil se elige otra carpeta).
     */
    @Suppress("MissingPermission")
    fun backupStopped(context: Context, folderLost: Boolean) {
        if (!canPost(context)) return
        ensureChannels(context)
        val b = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_backup_title))
            .setContentText(context.getString(if (folderLost) R.string.auto_backup_lost else R.string.notif_backup_failing))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(if (folderLost) R.string.auto_backup_lost else R.string.notif_backup_failing)))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        // Si la carpeta sigue ahí (falló por la conexión, Drive…), se puede reintentar sin abrir la app
        if (!folderLost) {
            val now = PendingIntent.getBroadcast(
                context, ID_BACKUP,
                Intent(context, com.bestrong.mobile.backup.BackupNowReceiver::class.java).setPackage(context.packageName)
                    .putExtra(com.bestrong.mobile.backup.BackupNowReceiver.EXTRA_NOTIFICATION_ID, ID_BACKUP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(R.drawable.ic_stat_dumbbell, context.getString(R.string.notif_backup_now), now)
        }
        NotificationManagerCompat.from(context).notify(ID_BACKUP, b.build())
    }

    /** Recordatorio mensual de medirse con cinta (cintura, pecho, brazo, muslo). */
    @Suppress("MissingPermission")
    fun measure(context: Context) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_measure_title))
            .setContentText(context.getString(R.string.notif_measure_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_MEASURE, n)
    }

    /** Tras contestar: confirmación con el peso guardado; se quita sola y sin volver a sonar. */
    @Suppress("MissingPermission")
    fun weighInResult(context: Context, saved: String) {
        // El canal puede no existir aún (instalación nueva): sin él, el aviso se pierde
        ensureChannels(context)
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_weigh_in_saved))
            .setContentText(saved)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_TIMEOUT_MS)
            .build()
        NotificationManagerCompat.from(context).notify(ID_WEIGH_IN, n)
    }

    private const val RESULT_TIMEOUT_MS = 8_000L

    /** A mitad de semana, si el cardio va por detrás del objetivo: lo que falta y a qué ritmo. */
    @Suppress("MissingPermission")
    fun cardioNudge(context: Context, pace: String) {
        // El canal puede no existir aún (instalación nueva): sin él, el aviso se pierde
        ensureChannels(context)
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_GOALS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_cardio_nudge))
            .setContentText(pace)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_CARDIO_NUDGE, n)
    }

    /** Objetivo semanal de cardio cumplido (una vez por semana). */
    @Suppress("MissingPermission")
    fun cardioGoal(context: Context, progress: String) {
        // El canal puede no existir aún (instalación nueva): sin él, el aviso se pierde
        ensureChannels(context)
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_GOALS)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(context.getString(R.string.notif_cardio_goal))
            .setContentText(progress)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_CARDIO_GOAL, n)
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
            // Con cardio: kilómetros y tiempo de la semana
            week.takeIf { it.cardioDistanceM > 0 || it.cardioSec > 0 }?.let {
                context.getString(R.string.notif_weekly_cardio, com.bestrong.mobile.ui.workout.cardioText(it.cardioSec.toInt(), it.cardioDistanceM, withPace = false))
            },
            week.volumeChange?.let { c -> context.getString(R.string.notif_weekly_change, (if (c >= 0) "+" else "−") + "${(kotlin.math.abs(c) * 100).toInt()} %") },
            highLoadRatio?.let { context.getString(R.string.notif_weekly_load_high, String.format(java.util.Locale.getDefault(), "%.1f", it)) },
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
