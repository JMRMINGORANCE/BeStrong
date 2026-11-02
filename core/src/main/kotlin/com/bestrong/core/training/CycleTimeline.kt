package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Bloque de una semana del ciclo. */
enum class CyclePhase { ACCUMULATION, INTENSIFICATION, DELOAD }

/** Una semana del ciclo: su lunes, su bloque y las series planificadas frente a las hechas. */
data class CycleWeek(
    /** Semana dentro del ciclo, desde 1. */
    val index: Int,
    val monday: LocalDate,
    val phase: CyclePhase,
    val plannedSets: Int,
    val doneSets: Int,
    val current: Boolean,
)

/** % del training max de un ejercicio en cada semana del ciclo (para la rejilla de intensidad). */
data class IntensityRow(val exerciseId: String, val tier: String?, val percents: List<Double?>)

/**
 * Línea temporal del ciclo en curso (programa con semanas, como 5/3/1, o mesociclo con descarga): bloques,
 * semana actual, carga prevista frente a hecha y próxima descarga.
 */
data class CycleTimeline(
    val kind: Kind,
    /** Ciclo en curso, desde 1 (en el mesociclo, cuántas veces se ha repetido). */
    val cycle: Int,
    val weeks: List<CycleWeek>,
    val intensity: List<IntensityRow> = emptyList(),
) {
    enum class Kind { PROGRAM, MESOCYCLE }

    val current: CycleWeek? get() = weeks.firstOrNull { it.current }

    /** La próxima semana de descarga desde la actual (incluida), o null si no hay. */
    val nextDeload: CycleWeek? get() = current?.let { c -> weeks.firstOrNull { it.phase == CyclePhase.DELOAD && it.index >= c.index } }

    /** Semanas que faltan hasta la próxima descarga (0 = es esta). */
    val weeksToDeload: Int? get() = nextDeload?.let { d -> current?.let { d.index - it.index } }
}

object CycleTimelines {
    /** Por debajo de este % del training max en la serie más pesada, la semana es de descarga (5/3/1: 60 %). */
    private const val DELOAD_TOP = 0.7

    /**
     * Ciclo de un programa con varias semanas por ciclo ([PeriodizedProgram.weeksPerCycle] > 1). [week] y [cycle]:
     * los de la siguiente sesión ([ProgramSession]). null si el programa no tiene ciclo (progresión lineal).
     */
    fun forProgram(program: PeriodizedProgram, week: Int, cycle: Int, today: LocalDate, history: List<Workout>, zone: ZoneId): CycleTimeline? {
        val n = program.weeksPerCycle
        if (n <= 1) return null
        val tops = (0 until n).map { w -> topPercent(program, w) }
        val heaviest = tops.filterNotNull().filter { it >= DELOAD_TOP }.maxOrNull()
        val start = monday(today).minusWeeks((week - 1).toLong())
        val weeks = (0 until n).map { w ->
            val top = tops[w]
            val phase = when {
                top != null && top < DELOAD_TOP -> CyclePhase.DELOAD
                top != null && heaviest != null && top >= heaviest - 1e-6 -> CyclePhase.INTENSIFICATION
                else -> CyclePhase.ACCUMULATION
            }
            val mon = start.plusWeeks(w.toLong())
            CycleWeek(w + 1, mon, phase, plannedSetsFor(program, w), doneSets(history, mon, zone), w + 1 == week)
        }
        return CycleTimeline(CycleTimeline.Kind.PROGRAM, cycle, weeks, intensityRows(program))
    }

    /**
     * Mesociclo: [Mesocycle.accumulationWeeks] de carga (la última, de intensificación si son 3 o más) y una de
     * descarga. [plannedSets]: series planificadas para la semana que empieza ese lunes (las de la semana de
     * descarga se reducen a la mitad, como hace la descarga automática).
     */
    fun forMesocycle(m: Mesocycle, today: LocalDate, history: List<Workout>, zone: ZoneId, plannedSets: (LocalDate) -> Int): CycleTimeline {
        val first = LocalDate.ofEpochDay(m.startEpochDay)
        val sinceStart = ChronoUnit.WEEKS.between(first, today).toInt().coerceAtLeast(0)
        val cycleIndex = sinceStart / m.length
        val start = monday(first.plusWeeks((cycleIndex * m.length).toLong()))
        val current = m.weekOf(today)
        val weeks = (1..m.length).map { i ->
            val phase = when {
                i == m.length -> CyclePhase.DELOAD
                m.accumulationWeeks >= 3 && i == m.accumulationWeeks -> CyclePhase.INTENSIFICATION
                else -> CyclePhase.ACCUMULATION
            }
            val mon = start.plusWeeks((i - 1).toLong())
            val planned = plannedSets(mon).let { if (phase == CyclePhase.DELOAD) (it + 1) / 2 else it }
            CycleWeek(i, mon, phase, planned, doneSets(history, mon, zone), i == current)
        }
        return CycleTimeline(CycleTimeline.Kind.MESOCYCLE, cycleIndex + 1, weeks)
    }

    /** Series hechas (sin calentamiento) en la semana que empieza en [monday]. */
    fun doneSets(history: List<Workout>, monday: LocalDate, zone: ZoneId): Int {
        val end = monday.plusDays(7)
        return history.filter { it.isFinished }.filter { w ->
            val d = Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone).toLocalDate()
            !d.isBefore(monday) && d.isBefore(end)
        }.sumOf { w -> w.exercises.sumOf { we -> we.completedSets.count { it.type != SetType.WARMUP } } }
    }

    /** El % más alto prescrito en la semana [w] (solo esquemas por semanas); null si no hay. */
    private fun topPercent(program: PeriodizedProgram, w: Int): Double? =
        program.days.flatMap { it.items }.mapNotNull { (it.scheme as? LoadScheme.Weekly)?.weeks?.getOrNull(w)?.maxOfOrNull { s -> s.percent } }.maxOrNull()

    private fun plannedSetsFor(program: PeriodizedProgram, w: Int): Int = program.days.sumOf { day ->
        day.items.sumOf { item ->
            when (val s = item.scheme) {
                is LoadScheme.Weekly -> s.weeks.getOrNull(w)?.size ?: 0
                is LoadScheme.Fixed -> s.sets.size
                is LoadScheme.Linear -> s.sets
            }
        }
    }

    /** Un renglón por ejercicio con % (el primero de cada uno y su tier): la serie más pesada de cada semana. */
    private fun intensityRows(program: PeriodizedProgram): List<IntensityRow> {
        val n = program.weeksPerCycle
        return program.days.flatMap { it.items }.mapNotNull { item ->
            val per = when (val s = item.scheme) {
                is LoadScheme.Weekly -> (0 until n).map { w -> s.weeks.getOrNull(w)?.maxOfOrNull { it.percent } }
                is LoadScheme.Fixed -> List(n) { s.sets.maxOfOrNull { it.percent } }
                is LoadScheme.Linear -> null
            } ?: return@mapNotNull null
            IntensityRow(item.exerciseId, item.tier, per)
        }.distinctBy { it.exerciseId to it.tier to ((it.percents.firstOrNull() ?: 0.0) > DELOAD_TOP) }
    }

    private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
