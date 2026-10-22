package com.bestrong.core.training

import com.bestrong.core.model.Equipment
import com.bestrong.core.util.WeightUnit
import com.bestrong.core.util.newId
import kotlinx.serialization.Serializable

/**
 * Perfil de gimnasio: material disponible, barra, discos y mancuernas. Las sugerencias de peso,
 * el calentamiento y la calculadora de discos usan solo lo que hay en el gimnasio activo.
 * Pesos en kg.
 */
@Serializable
data class GymProfile(
    val id: String = newId(),
    val name: String,
    val equipment: Set<Equipment> = Equipment.entries.toSet(),
    val barKg: Double = 20.0,
    /** Discos por lado disponibles (kg). */
    val platesKg: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
    /** Mancuernas disponibles (kg); vacío = cualquier peso. */
    val dumbbellsKg: List<Double> = emptyList(),
    /** Peso de los dos cierres juntos (p. ej. 5 kg los de competición); 0 = sin cierres o no cuentan. */
    val collarKg: Double = 0.0,
) {
    /** Barra con sus cierres: lo mínimo que se levanta y la base sobre la que se ponen discos. */
    val loadedBarKg: Double get() = barKg + collarKg

    /**
     * Discos del gimnasio en [unit]. En libras, los elegidos que son de libras (el editor los guarda en kg: 2,5 lb =
     * 1,134 kg); si no hay ninguno (un perfil con discos de kg), los estándar de libras.
     */
    fun platesIn(unit: WeightUnit): List<Double> {
        if (unit == WeightUnit.KG) return platesKg.ifEmpty { PlateCalculator.defaultPlates(unit) }
        val lb = platesKg.filter { it > 0 }.map { unit.fromKg(it) }.filter { kotlin.math.abs(it * 4 - kotlin.math.round(it * 4)) < 0.01 }
            .map { kotlin.math.round(it * 4) / 4 }
        return lb.ifEmpty { PlateCalculator.defaultPlates(unit) }
    }

    /** Discos por lado para [targetKg] con la barra y los discos de este gimnasio, en [unit]. */
    fun plateLoad(targetKg: Double, unit: WeightUnit): PlateLoad {
        val plates = platesIn(unit)
        val bar = barIn(unit, barKg) + unit.fromKg(collarKg)
        return PlateCalculator.calculate(unit.fromKg(targetKg), unit, bar, plates)
    }

    /** Peso más cercano alcanzable con este material para el ejercicio dado. */
    fun nearestLoad(targetKg: Double, equipment: Equipment, unit: WeightUnit = WeightUnit.KG): Double = when {
        equipment == Equipment.BARBELL || equipment == Equipment.SMITH -> nearestBarbell(targetKg, unit)
        // Las kettlebells no van con el juego de mancuernas (32 kg no pasa a 30 si las mancuernas acaban ahí)
        equipment == Equipment.DUMBBELL -> if (dumbbellsKg.isEmpty()) targetKg else dumbbellsKg.minBy { kotlin.math.abs(it - targetKg) }
        else -> targetKg
    }

    /**
     * El peso más bajo que se puede montar por encima de [currentKg]: para que una subida no se pierda al
     * redondear (p. ej. +1,25 kg con discos de 1,25 por lado en una barra = +2,5).
     */
    fun loadAbove(currentKg: Double, equipment: Equipment, unit: WeightUnit = WeightUnit.KG): Double = when {
        equipment == Equipment.BARBELL || equipment == Equipment.SMITH -> {
            val step = unit.toKg((platesIn(unit).filter { it > 0 }.minOrNull() ?: unit.fromKg(1.25)) * 2)
            nearestBarbell(currentKg + step, unit).let { if (it > currentKg + 1e-6) it else currentKg + step }
        }
        // Las mancuernas se guardan en kg (también las de libras): se comparan igual en las dos unidades
        equipment == Equipment.DUMBBELL -> dumbbellsKg.filter { it > currentKg + 1e-6 }.minOrNull() ?: currentKg
        else -> currentKg
    }

    /**
     * Peso alcanzable con la barra y los discos (lo mismo que monta la calculadora de discos, por lado). En libras,
     * con la barra y los discos de libras, como la calculadora; el resultado siempre en kg.
     */
    fun nearestBarbell(targetKg: Double, unit: WeightUnit = WeightUnit.KG): Double {
        val plates = platesIn(unit)
        val base = if (unit == WeightUnit.KG) loadedBarKg else barIn(unit, barKg) + unit.fromKg(collarKg)
        val target = unit.fromKg(targetKg)
        if (target <= base) return unit.toKg(base)
        val below = base + PlateCalculator.bestSide((target - base) / 2, plates).sum() * 2
        // Si queda un hueco grande, probamos con el disco más pequeño de más
        val above = below + (plates.filter { it > 0 }.minOrNull() ?: 0.0) * 2
        return unit.toKg(if (target - below <= above - target) below else above)
    }

    companion object {
        fun home() = GymProfile(
            name = "Casa",
            equipment = setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT, Equipment.BAND, Equipment.KETTLEBELL),
            dumbbellsKg = (1..15).map { it * 2.0 },
        )
        fun gym() = GymProfile(name = "Gimnasio")

        private val METRIC_TO_LB = mapOf(20.0 to 45.0, 15.0 to 35.0, 10.0 to 25.0)

        /**
         * La barra en [unit]. En libras, la estándar si la del perfil (en kg) se le parece: una de
         * 20 kg es la de 45 lb del gimnasio; si no, su peso real a media libra.
         */
        fun barIn(unit: WeightUnit, barKg: Double): Double {
            // En libras, solo las barras métricas estándar (las que vienen por defecto) pasan a la de libras;
            // una escrita en libras se respeta (a media libra)
            return when {
                unit == WeightUnit.KG -> barKg
                // Con margen: guardar el gimnasio en libras deja la barra en 20,003 kg (44,1 lb tecleado)
                else -> METRIC_TO_LB.entries.firstOrNull { kotlin.math.abs(it.key - barKg) < 0.05 }?.value ?: (Math.round(unit.fromKg(barKg) * 2) / 2.0)
            }
        }
    }
}
