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
        val perSide = exactSide((target - bar) / 2, plates.filter { it > 0 })
        val achieved = bar + perSide.sum() * 2
        return PlateLoad(perSide, bar, achieved, round((target - achieved) * 100) / 100)
    }

    /**
     * Discos de un lado que más se acercan a [side] sin pasarse, con los menos discos posibles (no a lo bruto de
     * mayor a menor: con {15, 10} y 20 por lado son 10 + 10, no 15 y sobran 5). En centésimas, para kg y lb.
     */
    private fun exactSide(side: Double, plates: List<Double>): List<Double> {
        val amount = Math.round(side * 100).toInt().coerceIn(0, MAX_SIDE_CENTS)
        val units = plates.map { Math.round(it * 100).toInt() }.filter { it > 0 }.distinct().sortedDescending()
        if (amount == 0 || units.isEmpty()) return emptyList()
        // Menos discos para llegar justo a cada cantidad (y con qué disco se llegó)
        val count = IntArray(amount + 1) { Int.MAX_VALUE }
        val last = IntArray(amount + 1)
        count[0] = 0
        for (a in 1..amount) {
            units.forEach { u ->
                if (u <= a && count[a - u] != Int.MAX_VALUE && count[a - u] + 1 < count[a]) {
                    count[a] = count[a - u] + 1
                    last[a] = u
                }
            }
        }
        var a = (amount downTo 0).first { count[it] != Int.MAX_VALUE }
        val out = mutableListOf<Double>()
        while (a > 0) {
            out += last[a] / 100.0
            a -= last[a]
        }
        return out.sortedDescending()
    }

    /** Tope por lado (500 kg o lb): más no cabe en una barra y así la tabla es pequeña. */
    private const val MAX_SIDE_CENTS = 50_000
}
