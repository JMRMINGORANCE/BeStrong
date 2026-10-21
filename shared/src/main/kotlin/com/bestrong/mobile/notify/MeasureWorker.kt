package com.bestrong.mobile.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bestrong.mobile.data.WEIGH_IN_HOUR
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalTime
import java.time.ZonedDateTime

/** El día 1 de cada mes por la mañana: recordar las medidas con cinta (cambian despacio, una vez al mes basta). */
class MeasureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val enabled = deps.settings().settings.value.measureReminder
        try {
            // Solo el día 1 (si se retrasó hasta el 10 porque el móvil estaba apagado, ya no)
            if (enabled && !ExactWork.isStale(inputData, LATE_MS, sameDay = true)) Notifier.measure(applicationContext)
        } finally {
            runCatching { schedule(applicationContext, enabled, force = true) }.onFailure { Log.w("MeasureWorker", "No se pudo reprogramar", it) }
        }
        return Result.success()
    }

    companion object {
        private const val LATE_MS = 12 * 60 * 60 * 1000L
        private const val NAME = "measure-reminder"

        fun schedule(context: Context, enabled: Boolean, force: Boolean = false) {
            if (!enabled) {
                ExactWork.cancel(context, NAME)
                return
            }
            ExactWork.enqueue(context, NAME, "monthly-$WEIGH_IN_HOUR", nextFire(ZonedDateTime.now()), MeasureWorker::class.java, force)
        }

        /** El próximo día 1 a las [WEIGH_IN_HOUR] (estrictamente después de [now]). */
        fun nextFire(now: ZonedDateTime): ZonedDateTime {
            val thisMonth = now.toLocalDate().withDayOfMonth(1).atTime(LocalTime.of(WEIGH_IN_HOUR, 0)).atZone(now.zone)
            return if (thisMonth.isAfter(now)) thisMonth else thisMonth.plusMonths(1)
        }
    }
}
