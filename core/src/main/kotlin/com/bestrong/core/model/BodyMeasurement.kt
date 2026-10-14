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
    /** El peso vino de Health Connect (báscula, Samsung Health): no se vuelve a exportar (estaría dos veces). */
    val weightFromHealth: Boolean = false,
)
