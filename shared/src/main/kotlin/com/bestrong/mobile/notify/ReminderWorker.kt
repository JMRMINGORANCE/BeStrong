package com.bestrong.mobile.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Se ejecuta a la hora elegida y se vuelve a programar para la siguiente. Si hoy es uno de los
 * días marcados y todavía no se ha entrenado, avisa (sugiriendo la rutina que toca: la que lleva más tiempo sin hacerse).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val settings = deps.settings().settings.value
        try {
            remind(deps, settings.reminderDays)
        } finally {
            // Siguiente aviso a la hora exacta (también si este falla)
            runCatching { schedule(applicationContext, settings.reminderDays, settings.reminderHour, settings.reminderMinute, force = true) }
                .onFailure { Log.w("ReminderWorker", "No se pudo reprogramar", it) }
        }
        return Result.success()
    }

    private suspend fun remind(deps: WorkerEntryPoint, days: Set<Int>) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        if (today.dayOfWeek.value !in days) return
        val repo = deps.repository()
        val history = repo.historyOnce()
        val trainedToday = history.any { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() == today }
        if (trainedToday || repo.activeWorkout.value != null) return
        val lastDone = history.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        val next = repo.routinesOnce().minByOrNull { lastDone[it.id] ?: 0L }
        Notifier.reminder(applicationContext, next?.name)
    }

    companion object {
        private const val NAME = "workout-reminder"

        /** Programa (o cancela si no hay días) el próximo aviso: el primer día marcado a la hora indicada. */
        fun schedule(context: Context, days: Set<Int>, hour: Int, minute: Int, force: Boolean = false) {
            if (days.isEmpty()) {
                ExactWork.cancel(context, NAME)
                return
            }
            ExactWork.enqueue(context, NAME, "${days.sorted()}-$hour:$minute", nextFire(ZonedDateTime.now(), days, hour, minute), ReminderWorker::class.java, force)
        }

        /** Próximo instante (estrictamente después de [now]) en un día de [days] a hh:mm, respetando cambios de hora. */
        fun nextFire(now: ZonedDateTime, days: Set<Int>, hour: Int, minute: Int): ZonedDateTime =
            (0L..7L).asSequence()
                .map { now.toLocalDate().plusDays(it) }
                .filter { it.dayOfWeek.value in days }
                .map { it.atTime(LocalTime.of(hour, minute)).atZone(now.zone) }
                .first { it.isAfter(now) }
    }
}
