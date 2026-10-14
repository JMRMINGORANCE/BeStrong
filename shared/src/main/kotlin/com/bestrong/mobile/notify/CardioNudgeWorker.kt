package com.bestrong.mobile.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bestrong.core.stats.CardioPace
import com.bestrong.core.stats.CardioTotals
import com.bestrong.mobile.di.WorkerEntryPoint
import com.bestrong.mobile.shared.R
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * El día elegido (jueves por defecto) por la tarde: si hay objetivo semanal de cardio y no se lleva ni la mitad, un empujón con lo que falta y
 * el ritmo para llegar el domingo («Faltan 12 km · 3 km al día»).
 */
class CardioNudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        try {
            val s = deps.settings().settings.value
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val (meters, seconds) = CardioTotals.since(deps.repository().historyOnce(), monday.atStartOfDay(zone).toInstant().toEpochMilli())
            val pace = CardioPace.of(s.weeklyCardioKm, s.weeklyCardioMin, meters, seconds, 8 - today.dayOfWeek.value).takeIf { s.cardioNudge }
            val goal = s.weeklyCardioMin?.toDouble() ?: s.weeklyCardioKm ?: 0.0
            // Solo si va por detrás del ritmo de la semana (con margen): el jueves, menos de la mitad hecha; el lunes,
            // menos de un octavo
            if (pace != null && goal - pace.left < goal * today.dayOfWeek.value / NUDGE_DAYS) Notifier.cardioNudge(applicationContext, text(pace))
        } finally {
            runCatching { schedule(applicationContext, deps.settings().settings.value.cardioNudgeDay, force = true) }.onFailure { Log.w("CardioNudge", "No se pudo reprogramar", it) }
        }
        return Result.success()
    }

    private fun text(pace: CardioPace): String = if (pace.inMinutes) {
        applicationContext.getString(R.string.cardio_pace_min, kotlin.math.ceil(pace.left).toInt(), kotlin.math.ceil(pace.perDay).toInt())
    } else {
        applicationContext.getString(R.string.cardio_pace_km, "%.1f".format(pace.left), "%.1f".format(maxOf(pace.perDay, 0.1)))
    }

    companion object {
        private const val NAME = "cardio-nudge"
        private const val NUDGE_DAYS = 8.0

        /** Próximo [day] (1 = lunes) a las 18:00 (OneTimeWork que se reprograma al acabar, ver [ExactWork]). */
        fun schedule(context: Context, day: Int, force: Boolean = false) {
            val now = ZonedDateTime.now()
            val dow = DayOfWeek.of(day.coerceIn(1, 7))
            var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow)).atTime(LocalTime.of(18, 0)).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusWeeks(1)
            // Cambiar el día cambia la clave: se sustituye lo programado
            ExactWork.enqueue(context, NAME, "$dow-18:00", next, CardioNudgeWorker::class.java, force)
        }
    }
}
