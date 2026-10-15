package com.bestrong.core.io

import com.bestrong.core.model.BodyMeasurement
import java.time.Instant
import java.time.LocalDate
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

    /** Lo leído y cuántas filas con datos se saltaron (sin fecha que se entienda o sin ninguna medida). */
    data class Result(val rows: List<BodyMeasurement>, val skipped: Int)

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<BodyMeasurement> = read(text, zone).rows

    /**
     * Lee un CSV como el que se exporta y los de hojas de cálculo y básculas: separador «,» o «;» (con coma
     * decimal), campos entre comillas, fechas AAAA-MM-DD, DD/MM/AAAA o MM/DD/AAAA (si algún día pasa de 12 en el
     * segundo número) con o sin hora, y columnas por su nombre si hay cabecera (en es, en, ca, gl, pt, fr y de; un
     * peso «(lb)» se pasa a kg y la masa grasa en kg no se toma por porcentaje). Sin cabecera, en el orden de la
     * exportación.
     */
    fun read(text: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        val lines = text.lines().map { it.trim().removePrefix("﻿") }.filter { it.isNotEmpty() }
        val first = lines.firstOrNull() ?: return Result(emptyList(), 0)
        val delimiter = if (first.contains(';')) ';' else ','
        val header = split(first, delimiter)
        val hasHeader = header.firstOrNull()?.firstOrNull()?.isDigit() == false
        val columns = if (hasHeader) header.map(::columnOf) else DEFAULT_COLUMNS.map { Column(it) }
        val data = lines.drop(if (hasHeader) 1 else 0).map { split(it, delimiter) }
        val dateIndex = columns.indexOfFirst { it.field == "date" }
        // Mes primero solo si alguna fecha lo delata (03/14/2026); si no, día primero, como en España
        val monthFirst = dateIndex >= 0 && data.any { cells -> cells.getOrNull(dateIndex)?.let(::secondPartOver12) == true }
        val rows = data.mapNotNull { cells -> parseLine(cells, columns, delimiter == ';', monthFirst, zone) }
        return Result(rows, data.size - rows.size)
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

    /** Qué medida es una columna y en qué unidad viene el peso (factor a kg). */
    private data class Column(val field: String, val toKg: Double = 1.0)

    private val DEFAULT_COLUMNS = listOf("date", "weight", "fat", "waist", "chest", "arm", "thigh")

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_LETTERS = Regex("[^a-z0-9]+")

    /**
     * Por palabras enteras de la cabecera (no por trozos: «pes» no es «Pescoço» ni «dia» es «Peso diario»), o que
     * empiecen por la clave si es larga (≥ 4 letras: «weight_kg», «thighs»).
     */
    private fun columnOf(header: String): Column {
        val words = java.text.Normalizer.normalize(header.lowercase(), java.text.Normalizer.Form.NFD).replace(MARKS, "")
            .split(NON_LETTERS).filter { it.isNotEmpty() }
        fun has(key: String) = words.any { it == key || key.length >= 4 && it.startsWith(key) }
        val field = KEYWORDS.entries.firstOrNull { (_, keys) -> keys.any(::has) }?.key.orEmpty()
        return when {
            // «Fat mass (kg)», «masa grasa», «Fat-free…»: no son el % de grasa
            field == "fat" && listOf("mass", "masa", "massa", "masse", "kg", "free", "libre", "lb", "lbs").any(::has) -> Column("")
            field == "weight" && listOf("lb", "lbs", "pounds", "libras").any(::has) -> Column(field, LB_TO_KG)
            else -> Column(field)
        }
    }

    // El orden importa: la fecha antes («Time of Measurement») y la grasa antes que el peso («Body fat weight»)
    private val KEYWORDS = linkedMapOf(
        "date" to listOf("date", "fecha", "data", "dia", "datum", "time", "day", "jour"),
        "fat" to listOf("fat", "grasa", "greix", "graxa", "gordura", "fett", "graisse"),
        "waist" to listOf("waist", "cintura", "taille", "bauchumfang", "taillenumfang"),
        "chest" to listOf("chest", "pecho", "pit", "peito", "poitrine", "brust", "brustumfang"),
        "arm" to listOf("arm", "arms", "brazo", "brac", "braco", "bras", "oberarm"),
        "thigh" to listOf("thigh", "muslo", "cuixa", "coxa", "cuisse", "oberschenkel"),
        "weight" to listOf("weight", "peso", "pes", "poids", "gewicht"),
    )

    private const val LB_TO_KG = 0.45359237

    private fun numbers(text: String) = text.substringBefore('T').substringBefore(' ').split('/', '-', '.')

    private fun secondPartOver12(text: String): Boolean {
        val parts = numbers(text)
        return parts.size == 3 && parts[0].length <= 2 && (parts[1].toIntOrNull() ?: 0) > 12
    }

    /** Fecha (sin la hora si la trae): AAAA-MM-DD, o con barras/puntos/guiones día y mes en el orden indicado. */
    private fun dateOf(text: String, monthFirst: Boolean): LocalDate? {
        val parts = numbers(text).mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 && numbers(text).size == 3 } ?: return null
        val (a, b, c) = parts
        return runCatching {
            when {
                a > 31 -> LocalDate.of(a, b, c)
                monthFirst -> LocalDate.of(c, a, b)
                else -> LocalDate.of(c, b, a)
            }
        }.getOrNull()
    }

    private fun parseLine(cells: List<String>, columns: List<Column>, decimalComma: Boolean, monthFirst: Boolean, zone: ZoneId): BodyMeasurement? {
        fun cell(name: String) = columns.indexOfFirst { it.field == name }.takeIf { it >= 0 }?.let { cells.getOrNull(it) }
        val date = cell("date")?.let { dateOf(it, monthFirst) } ?: return null
        fun v(name: String) = cell(name)?.let { if (decimalComma || it.count { c -> c == ',' } == 1 && !it.contains('.')) it.replace(',', '.') else it }
            ?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
        val weightToKg = columns.firstOrNull { it.field == "weight" }?.toKg ?: 1.0
        val m = BodyMeasurement(
            dateEpochMs = date.atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli(),
            weightKg = v("weight")?.let { it * weightToKg },
            bodyFatPct = v("fat"),
            waistCm = v("waist"),
            chestCm = v("chest"),
            armCm = v("arm"),
            thighCm = v("thigh"),
        )
        return m.takeIf { listOf(it.weightKg, it.bodyFatPct, it.waistCm, it.chestCm, it.armCm, it.thighCm).any { v -> v != null } }
    }

    private const val NOON = 12
}
