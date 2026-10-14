package com.bestrong.core.io

import com.bestrong.core.model.BodyMeasurement
import java.time.Instant
import java.time.ZoneId

/** Medidas en CSV (para una hoja de cálculo o el nutricionista): fecha y cada medida, vacía si no se tomó. */
object MeasurementCsv {
    fun export(list: List<BodyMeasurement>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("date,weight_kg,body_fat_pct,waist_cm,chest_cm,arm_cm,thigh_cm")
        list.sortedBy { it.dateEpochMs }.forEach { m ->
            val date = Instant.ofEpochMilli(m.dateEpochMs).atZone(zone).toLocalDate()
            val values = listOf(m.weightKg, m.bodyFatPct, m.waistCm, m.chestCm, m.armCm, m.thighCm).map { v -> v?.let { "%.2f".format(java.util.Locale.ROOT, it) }.orEmpty() }
            appendLine((listOf(date.toString()) + values).joinToString(","))
        }
    }
}
