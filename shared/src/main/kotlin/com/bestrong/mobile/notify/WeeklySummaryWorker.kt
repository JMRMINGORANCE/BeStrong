package com.bestrong.mobile.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.TrainingLoad
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Domingo por la tarde: resumen de la semana (entrenos, volumen, récords) si se ha entrenado. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        try {
            val settings = deps.settings().settings.value
            val zone = ZoneId.systemDefault()
            val history = deps.repository().historyOnce()
            // Programado el domingo a las 19:00: si el sistema lo retrasa al lunes, se resume la semana que acabó
            val today = LocalDate.now(zone).let { if (it.dayOfWeek == java.time.DayOfWeek.MONDAY) it.minusDays(1) else it }
            val week = Strength.weekSummary(history, today, zone)
            // Si la carga ha subido de golpe, el resumen lo avisa (riesgo de lesión)
            val loadRatio = TrainingLoad.weekly(history, today, zone).takeIf { it.tooHigh }?.acuteChronicRatio
            if (week.workouts > 0) Notifier.weeklySummary(applicationContext, week, settings.unit, loadRatio)
        } finally {
            runCatching { schedule(applicationContext, force = true) }.onFailure { Log.w("WeeklySummary", "No se pudo reprogramar", it) }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly-summary"

        /** Próximo domingo a las 19:00 (OneTimeWork que se reprograma al acabar, ver [ExactWork]). */
        fun schedule(context: Context, force: Boolean = false) {
            val now = ZonedDateTime.now()
            var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.of(19, 0)).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            ExactWork.enqueue(context, NAME, "sunday-19:00", next, WeeklySummaryWorker::class.java, force)
        }
    }
}
