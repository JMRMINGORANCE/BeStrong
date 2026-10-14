package com.bestrong.core.stats

/** Tendencia del peso corporal: media de los últimos 7 días frente a la de los 7 anteriores. */
object BodyWeightTrend {
    private const val WEEK_MS = 7 * 86_400_000L

    data class Trend(val averageKg: Double, val changeKg: Double?)

    /** [points]: (fecha, kg). Null si no hay pesadas en la última semana. */
    fun of(points: List<Pair<Long, Double>>, nowEpochMs: Long): Trend? {
        fun avg(from: Long, to: Long) = points.filter { it.first in from until to }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
        val current = avg(nowEpochMs - WEEK_MS, nowEpochMs + 1) ?: return null
        val previous = avg(nowEpochMs - 2 * WEEK_MS, nowEpochMs - WEEK_MS)
        return Trend(current, previous?.let { current - it })
    }
}

/** Cómo vas hacia un peso objetivo: lo que falta (kg, con signo hacia el objetivo) y cuándo llegarías. */
data class BodyWeightGoalProgress(val remainingKg: Double, val reached: Boolean, val etaEpochMs: Long?)

object BodyWeightGoal {
    /** Margen para dar el objetivo por cumplido (el peso baila con el agua y la comida). */
    private const val TOLERANCE_KG = 0.3

    /** Semanas recientes con las que se estima la fecha (un volumen de hace años no dice nada del ritmo de hoy). */
    private const val TREND_WEEKS = 12L

    /**
     * [points] (fecha, kg) ordenados. Sirve para bajar y para subir: la dirección la marca [startKg], el peso
     * cuando se puso el objetivo (si no se guardó, [inferStart]). Cumplido al cruzarlo o quedarse a menos de 0,3 kg.
     */
    /**
     * Peso de partida cuando no se guardó (objetivos antiguos): el primero de las últimas semanas, no el de
     * ahora, que tras pasarse del objetivo invertiría la dirección.
     */
    /** Kg por semana necesarios para llegar a tiempo (null si la fecha ya pasó). */
    fun neededWeeklyKg(remainingKg: Double, nowEpochMs: Long, dateEpochMs: Long): Double? {
        val weeks = (dateEpochMs - nowEpochMs) / (7 * 86_400_000.0)
        return if (weeks <= 0) null else remainingKg / weeks
    }

    fun inferStart(points: List<Pair<Long, Double>>, nowEpochMs: Long = System.currentTimeMillis()): Double? =
        points.firstOrNull { it.first >= nowEpochMs - TREND_WEEKS * 7 * 86_400_000L }?.second ?: points.firstOrNull()?.second

    fun progress(points: List<Pair<Long, Double>>, goalKg: Double, startKg: Double? = null, nowEpochMs: Long = System.currentTimeMillis()): BodyWeightGoalProgress? {
        if (points.isEmpty()) return null
        val current = points.last().second
        val recent = points.filter { it.first >= nowEpochMs - TREND_WEEKS * 7 * 86_400_000L }
        val losing = (startKg ?: inferStart(points, nowEpochMs)!!) > goalKg
        val crossed = if (losing) current <= goalKg else current >= goalKg
        val reached = crossed || kotlin.math.abs(current - goalKg) <= TOLERANCE_KG
        // Para bajar se invierte el signo: la predicción busca una línea que sube
        val sign = if (losing) -1.0 else 1.0
        val eta = if (reached) null else Strength.predictDate(recent.map { it.first to it.second * sign }, goalKg * sign, nowEpochMs)
        return BodyWeightGoalProgress(remainingKg = kotlin.math.abs(current - goalKg), reached = reached, etaEpochMs = eta)
    }
}

/** Ritmo de cambio de peso demasiado rápido: más del 1 % del peso por semana (se pierde músculo o se gana grasa). */
object WeightRate {
    private const val MAX_WEEKLY_FRACTION = 0.01
    private const val WINDOW_MS = 21 * 86_400_000L
    private const val MIN_SPAN_MS = 14 * 86_400_000L
    private const val MIN_POINTS = 3

    /**
     * Cambio semanal (kg/semana) por la tendencia de las 3 últimas semanas: hace falta al menos [MIN_POINTS]
     * pesajes repartidos en 2 semanas (con uno por semana, un día de retención de líquidos no es una tendencia).
     */
    fun weeklyChange(points: List<Pair<Long, Double>>, nowEpochMs: Long): Double? {
        val recent = points.filter { it.first in (nowEpochMs - WINDOW_MS)..nowEpochMs }
        if (recent.size < MIN_POINTS || recent.last().first - recent.first().first < MIN_SPAN_MS) return null
        val xs = recent.map { (it.first - recent.first().first) / (7 * 86_400_000.0) }
        val ys = recent.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        return if (sxx == 0.0) null else xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
    }

    /** True si el cambio semanal [changeKg] sobre una media de [averageKg] pasa del 1 %. */
    fun tooFast(averageKg: Double, changeKg: Double?): Boolean =
        changeKg != null && averageKg > 0 && kotlin.math.abs(changeKg) > averageKg * MAX_WEEKLY_FRACTION
}
