package com.bestrong.mobile.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bestrong.mobile.data.WEIGH_IN_HOUR
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Una vez por semana, el día elegido por la mañana: si aún no te has pesado hoy, lo recuerda. */
class WeighInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val day = deps.settings().settings.value.weighInDay
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val weighedToday = deps.repository().measurementsOnce()
                .any { it.weightKg != null && Instant.ofEpochMilli(it.dateEpochMs).atZone(zone).toLocalDate() == today }
            if (day != null && today.dayOfWeek.value == day && !weighedToday) Notifier.weighIn(applicationContext)
        } finally {
            runCatching { schedule(applicationContext, day, force = true) }.onFailure { Log.w("WeighInWorker", "No se pudo reprogramar", it) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "weigh-in-reminder"

        /** Programa (o cancela si [day] es null) el próximo aviso. */
        fun schedule(context: Context, day: Int?, force: Boolean = false) {
            if (day == null) {
                ExactWork.cancel(context, NAME)
                return
            }
            val next = ReminderWorker.nextFire(ZonedDateTime.now(), setOf(day), WEIGH_IN_HOUR, 0)
            ExactWork.enqueue(context, NAME, "$day-$WEIGH_IN_HOUR", next, WeighInWorker::class.java, force)
        }
    }
}
