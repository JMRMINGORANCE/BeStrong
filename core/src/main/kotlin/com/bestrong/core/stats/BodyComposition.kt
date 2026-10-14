package com.bestrong.core.stats

/** Cómo queda un índice frente a las referencias habituales. */
enum class BodyRange { LOW, HEALTHY, HIGH }

/**
 * Índices de composición corporal a partir de las medidas:
 * - cintura/altura: riesgo cardiometabólico; por debajo de 0,5 es lo recomendado.
 * - FFMI (índice de masa libre de grasa, normalizado a 1,80 m): cuánto músculo hay para tu altura.
 */
object BodyComposition {
    private const val WHTR_HEALTHY_MAX = 0.5
    private const val WHTR_LOW = 0.4
    private const val FFMI_REFERENCE_HEIGHT_M = 1.8
    private const val FFMI_NORMALIZE = 6.1

    fun waistToHeight(waistCm: Double, heightCm: Int): Double? = waistCm.takeIf { heightCm > 0 && it > 0 }?.let { it / heightCm }

    fun waistToHeightRange(ratio: Double): BodyRange = when {
        ratio < WHTR_LOW -> BodyRange.LOW
        ratio < WHTR_HEALTHY_MAX -> BodyRange.HEALTHY
        else -> BodyRange.HIGH
    }

    /** FFMI normalizado (Kouri et al.): masa magra / altura² + 6,1 × (1,8 − altura). */
    fun ffmi(weightKg: Double, bodyFatPct: Double, heightCm: Int): Double? {
        if (heightCm <= 0 || weightKg <= 0 || bodyFatPct !in 0.0..70.0) return null
        val h = heightCm / 100.0
        val lean = weightKg * (1 - bodyFatPct / 100)
        return lean / (h * h) + FFMI_NORMALIZE * (FFMI_REFERENCE_HEIGHT_M - h)
    }
}
