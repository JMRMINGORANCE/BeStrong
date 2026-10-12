package com.bestrong.mobile.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bestrong.core.stats.Strength
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Domingo por la tarde: resumen de la semana (entrenos, volumen, récords) si se ha entrenado. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val settings = deps.settings().settings.value
        val zone = ZoneId.systemDefault()
        val week = Strength.weekSummary(deps.repository().historyOnce(), LocalDate.now(zone), zone)
        if (week.workouts > 0) Notifier.weeklySummary(applicationContext, week, settings.unit)
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly-summary"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var first = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(19, 0))
            if (!first.isAfter(now)) first = first.plusWeeks(1)
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, first).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
