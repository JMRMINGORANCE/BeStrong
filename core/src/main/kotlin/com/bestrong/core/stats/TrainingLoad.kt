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
        /** Últimos 7 días frente a la media semanal de los 28 anteriores (null sin historial suficiente). */
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
        val done = history.filter { it.isFinished }
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mondays = (weeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
        val byWeek = done.groupBy {
            Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.mapValues { (_, ws) -> ws.sumOf(::session) }
        val series = mondays.map { it to (byWeek[it] ?: 0.0) }
        // Ventanas móviles (no la semana natural, que el lunes está casi vacía): últimos 7 días frente
        // a la media semanal de los 28 anteriores
        val end = today.plusDays(1)
        fun load(fromDaysAgo: Long, toDaysAgo: Long) = done.filter {
            val d = Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate()
            !d.isBefore(end.minusDays(fromDaysAgo)) && d.isBefore(end.minusDays(toDaysAgo))
        }.sumOf(::session)
        val previousWeeks = (1..4).map { k -> load(7L * (k + 1), 7L * k) }
        // Sin al menos 3 de esas 4 semanas con entrenos (empezando o volviendo de un parón) la
        // relación no significa nada y daría falsas alarmas
        val ratio = if (previousWeeks.count { it > 0 } >= 3) load(7, 0) / previousWeeks.average() else null
        return Weekly(series, ratio)
    }
}
