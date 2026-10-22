package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.util.WeightUnit
import kotlin.math.round

/**
 * Series de aproximación hacia un peso de trabajo: barra × 10, 40 % × 5, 60 % × 3, 80 % × 2.
 * Los pesos se redondean al incremento de la unidad y se omiten los repetidos o menores que la barra.
 */
object WarmupGenerator {
    private val steps = listOf(0.4 to 5, 0.6 to 3, 0.8 to 2)

    /**
     * [barKg] 0 = sin barra (mancuernas, máquina, polea): sin la serie con la barra sola. [loadable]: el peso montable
     * más cercano con el material del gimnasio (discos o mancuernas que hay).
     */
    fun generate(
        workingWeightKg: Double,
        unit: WeightUnit,
        barKg: Double = unit.toKg(PlateCalculator.defaultBar(unit)),
        loadable: (Double) -> Double = { it },
    ): List<WorkoutSet> {
        if (workingWeightKg <= barKg) return emptyList()
        val inc = unit.stepKg()
        val sets = if (barKg > 0) mutableListOf(WorkoutSet(weightKg = barKg, reps = 10, type = SetType.WARMUP)) else mutableListOf()
        steps.forEach { (pct, reps) ->
            val w = loadable((round(workingWeightKg * pct / inc) * inc).coerceAtLeast(barKg))
            if (w > (sets.lastOrNull()?.weightKg ?: 0.0) && w < workingWeightKg) sets += WorkoutSet(weightKg = w, reps = reps, type = SetType.WARMUP)
        }
        return sets
    }
}
