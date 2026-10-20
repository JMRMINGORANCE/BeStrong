package com.bestrong.core.model

import com.bestrong.core.util.newId
import kotlinx.serialization.Serializable

@Serializable
data class BodyMeasurement(
    val id: String = newId(),
    val dateEpochMs: Long,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    /**
     * El peso vino de Health Connect (báscula, Samsung Health): no se exporta, ni aunque se corrija a mano, porque
     * allí ya está el suyo y saldría dos veces.
     */
    val weightFromHealth: Boolean = false,
) {
    /** Esta medida con lo que le falta tomado de [row] (lo que ya tenía no se toca): juntar dos del mismo día. */
    fun filledFrom(row: BodyMeasurement): BodyMeasurement = copy(
        weightKg = weightKg ?: row.weightKg,
        bodyFatPct = bodyFatPct ?: row.bodyFatPct,
        waistCm = waistCm ?: row.waistCm,
        chestCm = chestCm ?: row.chestCm,
        armCm = armCm ?: row.armCm,
        thighCm = thighCm ?: row.thighCm,
    )
}
