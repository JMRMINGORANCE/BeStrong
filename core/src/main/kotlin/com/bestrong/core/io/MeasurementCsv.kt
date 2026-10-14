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
     * Lee un CSV como el que se exporta y los de hojas de cálculo: separador «,» o «;» (con coma decimal), campos
     * entre comillas, fechas AAAA-MM-DD o DD/MM/AAAA y columnas por su nombre si hay cabecera (fecha, peso, grasa,
     * cintura, pecho, brazo, muslo, en español o en inglés); sin cabecera, en el orden de la exportación. Las filas
     * sin fecha válida o sin ninguna medida se saltan.
     */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<BodyMeasurement> {
        val lines = text.lines().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() }
        val first = lines.firstOrNull() ?: return emptyList()
        val delimiter = if (first.contains(';')) ';' else ','
        val header = split(first, delimiter)
        val hasHeader = header.firstOrNull()?.firstOrNull()?.isDigit() == false
        val columns = if (hasHeader) columnsOf(header) else DEFAULT_COLUMNS
        return lines.drop(if (hasHeader) 1 else 0).mapNotNull { line -> parseLine(split(line, delimiter), columns, delimiter == ';', zone) }
    }

    /** Campos de una línea, respetando las comillas («"80,5"» es un solo campo). */
    private fun split(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        line.forEach { ch ->
            when {
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> cells += cur.toString().trim().also { cur.clear() }
                else -> cur.append(ch)
            }
        }
        cells += cur.toString().trim()
        return cells
    }

    private val DEFAULT_COLUMNS = listOf("date", "weight", "fat", "waist", "chest", "arm", "thigh")

    /** Nombre de cada columna según la cabecera (las que no se reconocen, vacías). */
    private fun columnsOf(header: List<String>): List<String> = header.map { h ->
        val n = java.text.Normalizer.normalize(h.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        KEYWORDS.entries.firstOrNull { (_, words) -> words.any { n.contains(it) } }?.key.orEmpty()
    }

    private val KEYWORDS = linkedMapOf(
        "date" to listOf("date", "fecha", "data", "dia"),
        "fat" to listOf("fat", "grasa", "greix", "graxa", "gordura"),
        "waist" to listOf("waist", "cintura"),
        "chest" to listOf("chest", "pecho", "pit", "peito"),
        "arm" to listOf("arm", "brazo", "brac", "braco"),
        "thigh" to listOf("thigh", "muslo", "cuixa", "coxa"),
        "weight" to listOf("weight", "peso", "pes"),
    )

    private val DAY_FIRST = java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy")

    private fun dateOf(text: String): java.time.LocalDate? =
        runCatching { java.time.LocalDate.parse(text) }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(text.replace('-', '/').replace('.', '/'), DAY_FIRST) }.getOrNull()

    private fun parseLine(cells: List<String>, columns: List<String>, decimalComma: Boolean, zone: ZoneId): BodyMeasurement? {
        fun cell(name: String) = columns.indexOf(name).takeIf { it >= 0 }?.let { cells.getOrNull(it) }
        val date = cell("date")?.let(::dateOf) ?: return null
        fun v(name: String) = cell(name)?.let { if (decimalComma || it.count { c -> c == ',' } == 1 && !it.contains('.')) it.replace(',', '.') else it }
            ?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
        val m = BodyMeasurement(
            dateEpochMs = date.atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli(),
            weightKg = v("weight"), bodyFatPct = v("fat"), waistCm = v("waist"), chestCm = v("chest"), armCm = v("arm"), thighCm = v("thigh"),
        )
        return m.takeIf { listOf(it.weightKg, it.bodyFatPct, it.waistCm, it.chestCm, it.armCm, it.thighCm).any { v -> v != null } }
    }

    private const val NOON = 12
}
