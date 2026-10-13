package com.bestrong.core.util

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

/** Los pesos se guardan siempre en kg; la unidad solo afecta a cómo se muestran y se introducen. */
@Serializable
enum class WeightUnit(val label: String, val kgPerUnit: Double, val defaultIncrement: Double) {
    KG("kg", 1.0, 2.5),
    LB("lb", 0.45359237, 5.0),
    ;

    fun fromKg(kg: Double): Double = kg / kgPerUnit
    fun toKg(value: Double): Double = value * kgPerUnit

    /** Formatea un peso guardado en kg en esta unidad, sin decimales innecesarios. */
    fun format(kg: Double, withUnit: Boolean = false): String {
        val v = (fromKg(kg) * 10).roundToLong() / 10.0
        val text = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')
        return if (withUnit) "$text $label" else text
    }

    /** Volumen total (kg·reps) en esta unidad: "12.345 kg". */
    fun formatVolume(kg: Double): String = com.bestrong.core.util.formatVolume(fromKg(kg)) + " " + label

    /** Convierte texto introducido por el usuario ("82,5") a kg. */
    fun parseToKg(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.let(::toKg)

    /** Paso del bisel / botones ±. */
    fun stepKg(): Double = toKg(defaultIncrement)

    /** Incrementos que se pueden elegir por ejercicio, en esta unidad. */
    fun incrementChoices(): List<Double> = when (this) {
        KG -> listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0, 10.0)
        LB -> listOf(1.0, 2.5, 5.0, 10.0, 20.0)
    }

    /** Formatea un incremento con hasta dos decimales («1,25 kg»), que [format] redondearía. */
    fun formatStep(kg: Double): String {
        val v = (fromKg(kg) * 100).roundToLong() / 100.0
        return (if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')) + " " + label
    }
}
