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

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    fun write(rows: List<List<String>>): String = rows.joinToString("\n") { r -> r.joinToString(",") { escape(it) } } + "\n"
}
