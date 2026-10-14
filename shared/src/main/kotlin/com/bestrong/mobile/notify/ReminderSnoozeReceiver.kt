package com.bestrong.mobile.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** «Dentro de 1 h» en el recordatorio de entrenar: lo quita y lo vuelve a mostrar una hora después. */
class ReminderSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.cancelReminder(context)
        val request = OneTimeWorkRequestBuilder<SnoozedReminderWorker>()
            .setInitialDelay(SNOOZE_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(EXTRA_NAME to intent.getStringExtra(EXTRA_NAME), EXTRA_ID to intent.getStringExtra(EXTRA_ID)))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val EXTRA_NAME = "routine_name"
        const val EXTRA_ID = "routine_id"
        private const val WORK_NAME = "workout-reminder-snooze"
        private const val SNOOZE_MINUTES = 60L
    }
}

/** El recordatorio pospuesto (no se repite si mientras tanto ya se ha entrenado o hay un entreno abierto). */
class SnoozedReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = dagger.hilt.android.EntryPointAccessors.fromApplication(applicationContext, com.bestrong.mobile.di.WorkerEntryPoint::class.java)
        val repo = deps.repository()
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val trainedToday = repo.historyOnce().any { java.time.Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() == today }
        if (!trainedToday && repo.activeOnce() == null) {
            Notifier.reminder(applicationContext, inputData.getString(ReminderSnoozeReceiver.EXTRA_NAME), inputData.getString(ReminderSnoozeReceiver.EXTRA_ID))
        }
        return Result.success()
    }
}
