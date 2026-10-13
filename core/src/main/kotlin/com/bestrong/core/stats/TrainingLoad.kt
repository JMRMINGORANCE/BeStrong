package com.bestrong.core.stats

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Carga de entrenamiento por sesión (método sRPE de Foster): esfuerzo medio × minutos.
 * El esfuerzo es el RPE medio de las series efectivas; sin RPE anotado se toma [DEFAULT_RPE].
 */
object TrainingLoad {
    const val DEFAULT_RPE = 7.0

    /** Por encima de esta relación aguda:crónica el riesgo de lesión sube (Gabbett). */
    const val HIGH_RATIO = 1.5

    data class Weekly(
        /** Carga de cada semana, de la más antigua a la actual (lunes a domingo). */
        val weeks: List<Pair<LocalDate, Double>>,
        /** Semana actual frente a la media de las 4 anteriores (null sin historial previo). */
        val acuteChronicRatio: Double?,
    ) {
        val tooHigh: Boolean get() = (acuteChronicRatio ?: 0.0) > HIGH_RATIO
    }

    fun session(w: Workout): Double {
        val rpes = w.exercises.flatMap { it.completedSets }.filter { it.type != SetType.WARMUP }.mapNotNull { it.rpe }
        val effort = if (rpes.isEmpty()) DEFAULT_RPE else rpes.average()
        return effort * w.durationSeconds() / 60.0
    }

    fun weekly(history: List<Workout>, today: LocalDate, zone: ZoneId, weeks: Int = 8): Weekly {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mondays = (weeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
        val byWeek = history.filter { it.isFinished }.groupBy {
            Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.mapValues { (_, ws) -> ws.sumOf(::session) }
        val series = mondays.map { it to (byWeek[it] ?: 0.0) }
        // Media de las 4 semanas anteriores a la actual; sin carga previa no hay relación que dar
        val chronic = (1..4).map { byWeek[thisMonday.minusWeeks(it.toLong())] ?: 0.0 }.average()
        val ratio = if (chronic > 0) (byWeek[thisMonday] ?: 0.0) / chronic else null
        return Weekly(series, ratio)
    }
}
