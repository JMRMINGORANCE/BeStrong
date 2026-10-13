package com.bestrong.core.io

/** Lector/escritor CSV mínimo (RFC 4180): comillas, comas/; dentro de campos y saltos de línea. */
object Csv {
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("﻿")
        while (i < src.length) {
            val c = src[i]
            when {
                inQuotes && c == '"' && i + 1 < src.length && src[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < src.length && src[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotEmpty() }) rows.add(row)
        }
        return rows
    }

    fun detectDelimiter(text: String): Char {
        val header = text.removePrefix("﻿").lineSequence().firstOrNull().orEmpty()
        return if (header.count { it == ';' } > header.count { it == ',' }) ';' else ','
    }

    fun escape(value: String): String {
        val safe = neutralizeFormula(value)
        return if (safe.any { it in NEEDS_QUOTES }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val NEEDS_QUOTES = setOf(',', '"', '\n', '\r')
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Un texto que empieza por = + - @ (p. ej. el nombre de una rutina compartida) se ejecutaría como
     * fórmula al abrir el CSV en Excel o Sheets: se le antepone ' (los números negativos no se tocan).
     */
    fun neutralizeFormula(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_START && value.replace(',', '.').toDoubleOrNull() == null) "'$value" else value

    /** Lo contrario de [neutralizeFormula], al importar. */
    fun restoreFormula(value: String): String =
        if (value.length >= 2 && value[0] == '\'' && value[1] in FORMULA_START) value.substring(1) else value

    fun write(rows: List<List<String>>): String = rows.joinToString("\n") { r -> r.joinToString(",") { escape(it) } } + "\n"
}
