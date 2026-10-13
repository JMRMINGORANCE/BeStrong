package com.bestrong.core.training

import com.bestrong.core.util.WeightUnit
import kotlin.math.round

data class PlateLoad(
    /** Discos por lado, de mayor a menor, en la unidad elegida. */
    val perSide: List<Double>,
    val barWeight: Double,
    /** Peso total conseguido (puede ser menor que el objetivo si no cuadra). */
    val achieved: Double,
    val remainder: Double,
)

/** Calcula qué discos poner en cada lado de la barra. Trabaja en la unidad indicada. */
object PlateCalculator {
    val KG_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    val LB_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    fun defaultBar(unit: WeightUnit) = if (unit == WeightUnit.KG) 20.0 else 45.0
    fun defaultPlates(unit: WeightUnit) = if (unit == WeightUnit.KG) KG_PLATES else LB_PLATES

    fun calculate(target: Double, unit: WeightUnit, bar: Double = defaultBar(unit), plates: List<Double> = defaultPlates(unit)): PlateLoad {
        if (target <= bar) return PlateLoad(emptyList(), bar, bar, (target - bar).coerceAtLeast(0.0))
        var remaining = (target - bar) / 2
        val perSide = mutableListOf<Double>()
        // Un disco de 0 (o negativo) dejaría el bucle sin terminar
        plates.filter { it > 0 }.sortedDescending().forEach { p ->
            while (remaining + 1e-9 >= p) {
                perSide += p
                remaining -= p
            }
        }
        val achieved = bar + perSide.sum() * 2
        return PlateLoad(perSide, bar, achieved, round((target - achieved) * 100) / 100)
    }
}
