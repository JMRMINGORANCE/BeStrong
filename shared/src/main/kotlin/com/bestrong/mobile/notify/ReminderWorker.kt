package com.bestrong.mobile.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Se ejecuta una vez al día a la hora elegida. Si hoy es uno de los días marcados y todavía
 * no se ha entrenado, avisa (sugiriendo la rutina que toca: la que lleva más tiempo sin hacerse).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val settings = deps.settings().settings.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        if (today.dayOfWeek.value !in settings.reminderDays) return Result.success()
        val repo = deps.repository()
        val history = repo.historyOnce()
        val trainedToday = history.any { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() == today }
        if (trainedToday || repo.activeWorkout.value != null) return Result.success()
        val lastDone = history.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        val next = repo.routinesOnce().minByOrNull { lastDone[it.id] ?: 0L }
        Notifier.reminder(applicationContext, next?.name)
        return Result.success()
    }

    companion object {
        private const val NAME = "workout-reminder"

        /** Programa (o cancela si no hay días) el aviso diario a la hora indicada. */
        fun schedule(context: Context, days: Set<Int>, hour: Int, minute: Int) {
            val wm = WorkManager.getInstance(context)
            if (days.isEmpty()) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(LocalTime.of(hour, minute))
            if (!first.isAfter(now)) first = first.plusDays(1)
            val delay = Duration.between(now, first)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
