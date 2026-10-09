package com.bestrong.core.stats

import kotlin.math.exp
import kotlin.math.pow

enum class Sex { MALE, FEMALE }

/** Puntuaciones de powerlifting para comparar entre pesos corporales. */
object Scores {
    /** DOTS (2019). */
    fun dots(totalKg: Double, bodyWeightKg: Double, sex: Sex): Double {
        if (totalKg <= 0 || bodyWeightKg <= 0) return 0.0
        val c = if (sex == Sex.MALE) {
            doubleArrayOf(-307.75076, 24.0900756, -0.1918759221, 0.0007391293, -0.000001093)
        } else {
            doubleArrayOf(-57.96288, 13.6175032, -0.1126655495, 0.0005158568, -0.0000010706)
        }
        val bw = if (sex == Sex.MALE) bodyWeightKg.coerceIn(40.0, 210.0) else bodyWeightKg.coerceIn(40.0, 150.0)
        val denom = c[0] + c[1] * bw + c[2] * bw.pow(2) + c[3] * bw.pow(3) + c[4] * bw.pow(4)
        return totalKg * 500.0 / denom
    }

    /** Wilks (fórmula original). */
    fun wilks(totalKg: Double, bodyWeightKg: Double, sex: Sex): Double {
        if (totalKg <= 0 || bodyWeightKg <= 0) return 0.0
        val c = if (sex == Sex.MALE) {
            doubleArrayOf(-216.0475144, 16.2606339, -0.002388645, -0.00113732, 7.01863e-6, -1.291e-8)
        } else {
            doubleArrayOf(594.31747775582, -27.23842536447, 0.82112226871, -0.00930733913, 4.731582e-5, -9.054e-8)
        }
        val bw = if (sex == Sex.MALE) bodyWeightKg.coerceIn(40.0, 201.9) else bodyWeightKg.coerceIn(26.51, 154.53)
        val denom = c[0] + c[1] * bw + c[2] * bw.pow(2) + c[3] * bw.pow(3) + c[4] * bw.pow(4) + c[5] * bw.pow(5)
        return totalKg * 500.0 / denom
    }

    /** IPF GL Points (powerlifting clásico, sin equipo). [benchOnly] para competiciones solo de press banca. */
    fun ipfGl(totalKg: Double, bodyWeightKg: Double, sex: Sex, benchOnly: Boolean = false): Double {
        if (totalKg <= 0 || bodyWeightKg < 35) return 0.0
        val (a, b, c) = when {
            sex == Sex.MALE && !benchOnly -> Triple(1199.72839, 1025.18162, 0.00921)
            sex == Sex.FEMALE && !benchOnly -> Triple(610.32796, 1045.59282, 0.03048)
            sex == Sex.MALE -> Triple(320.98041, 281.40258, 0.01008)
            else -> Triple(142.40398, 442.52671, 0.04724)
        }
        return totalKg * 100.0 / (a - b * exp(-c * bodyWeightKg))
    }

    /**
     * Intentos de competición a partir del 1RM: apertura ≈ 91 %, segundo ≈ 96 %, tercero ≈ 101 %
     * redondeados a 2,5 kg (las reglas IPF permiten saltos de 2,5).
     */
    fun attempts(oneRepMaxKg: Double): Triple<Double, Double, Double> {
        fun r(x: Double) = Math.round(x / 2.5) * 2.5
        return Triple(r(oneRepMaxKg * 0.91), r(oneRepMaxKg * 0.96), r(oneRepMaxKg * 1.01))
    }
}
