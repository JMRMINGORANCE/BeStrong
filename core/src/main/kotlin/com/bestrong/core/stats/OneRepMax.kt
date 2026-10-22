package com.bestrong.core.stats

import kotlin.math.exp
import kotlin.math.pow

/** Calculadora de 1RM con varias fórmulas y tabla de porcentajes. */
object OneRepMax {
    enum class Formula { EPLEY, BRZYCKI, LOMBARDI, LANDER, MAYHEW, OCONNER, WATHAN }

    fun estimate(weightKg: Double, reps: Int, formula: Formula): Double {
        if (weightKg <= 0 || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        val r = reps.toDouble()
        return when (formula) {
            Formula.EPLEY -> weightKg * (1 + r / 30.0)
            // Brzycki y Lander solo valen hasta ~12 reps: cerca de 37 se disparan (30 reps con 100 kg daban 514 kg)
            Formula.BRZYCKI -> if (r > MAX_REPS_FRACTIONAL) 0.0 else weightKg * 36.0 / (37.0 - r)
            Formula.LOMBARDI -> weightKg * r.pow(0.10)
            Formula.LANDER -> if (r > MAX_REPS_FRACTIONAL) 0.0 else 100.0 * weightKg / (101.3 - 2.67123 * r)
            Formula.MAYHEW -> 100.0 * weightKg / (52.2 + 41.9 * exp(-0.055 * r))
            Formula.OCONNER -> weightKg * (1 + 0.025 * r)
            Formula.WATHAN -> 100.0 * weightKg / (48.8 + 53.8 * exp(-0.075 * r))
        }
    }

    /** Hasta cuántas reps se usan las fórmulas con denominador (Brzycki, Lander). */
    private const val MAX_REPS_FRACTIONAL = 12

    fun all(weightKg: Double, reps: Int): Map<Formula, Double> = Formula.entries.associateWith { estimate(weightKg, reps, it) }

    /** Media de las fórmulas (más estable que cualquiera por separado, sobre todo con muchas reps). */
    fun average(weightKg: Double, reps: Int): Double = all(weightKg, reps).values.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0

    /** Tabla clásica: porcentaje del 1RM → reps aproximadas. */
    val PERCENT_TABLE: List<Pair<Int, Int>> = listOf(100 to 1, 95 to 2, 93 to 3, 90 to 4, 87 to 5, 85 to 6, 83 to 7, 80 to 8, 77 to 9, 75 to 10, 70 to 12, 65 to 15, 60 to 20)

    /** Peso para cada fila de la tabla a partir de un 1RM. */
    fun table(oneRepMaxKg: Double): List<Triple<Int, Int, Double>> = PERCENT_TABLE.map { (pct, reps) -> Triple(pct, reps, oneRepMaxKg * pct / 100.0) }

    /** Reps que deberías poder hacer con [weightKg] dado un 1RM (inversa de Epley). */
    fun repsAt(oneRepMaxKg: Double, weightKg: Double): Int {
        if (weightKg <= 0 || oneRepMaxKg <= 0 || weightKg > oneRepMaxKg) return 0
        return Math.round(30.0 * (oneRepMaxKg / weightKg - 1)).toInt().coerceAtLeast(1)
    }

    /**
     * Repeticiones mínimas con [weightKg] para superar un 1RM estimado de [bestOneRepMaxKg] (con la misma
     * fórmula que las series: 1 rep = el peso; más, Epley). null si harían falta más de [maxReps].
     */
    fun repsToBeat(bestOneRepMaxKg: Double, weightKg: Double, maxReps: Int = 20): Int? {
        return when {
            weightKg <= 0 || bestOneRepMaxKg <= 0 -> null
            weightKg > bestOneRepMaxKg + 1e-6 -> 1
            else -> (2..maxReps).firstOrNull { weightKg * (1 + it / 30.0) > bestOneRepMaxKg + 1e-6 }
        }
    }
}
