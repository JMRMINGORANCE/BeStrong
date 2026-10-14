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

    /**
     * Lee un CSV como el que se exporta (también con «;» y coma decimal, como lo guarda Excel en español): fecha
     * (AAAA-MM-DD) y las medidas en el mismo orden. Las filas sin fecha válida o sin ninguna medida se saltan.
     */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<BodyMeasurement> = text.lines()
        .map { it.trim().removePrefix("\uFEFF") }
        .filter { it.isNotEmpty() && it.first().isDigit() }
        .mapNotNull { line -> parseLine(line, zone) }

    private fun parseLine(line: String, zone: ZoneId): BodyMeasurement? {
        val semicolon = line.contains(';')
        val cells = line.split(if (semicolon) ';' else ',').map { it.trim().trim('"') }
        val date = runCatching { java.time.LocalDate.parse(cells[0]) }.getOrNull() ?: return null
        fun v(i: Int) = cells.getOrNull(i)?.let { if (semicolon) it.replace(',', '.') else it }?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
        val m = BodyMeasurement(
            dateEpochMs = date.atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli(),
            weightKg = v(1), bodyFatPct = v(2), waistCm = v(3), chestCm = v(4), armCm = v(5), thighCm = v(6),
        )
        return m.takeIf { listOf(it.weightKg, it.bodyFatPct, it.waistCm, it.chestCm, it.armCm, it.thighCm).any { v -> v != null } }
    }

    private const val NOON = 12
}
