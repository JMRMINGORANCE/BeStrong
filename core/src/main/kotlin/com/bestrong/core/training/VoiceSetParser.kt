package com.bestrong.core.training

import java.text.Normalizer

/** Serie dictada: peso (en la unidad dicha o la del usuario), reps y RPE si se mencionan. */
data class SpokenSet(val weight: Double? = null, val reps: Int? = null, val rpe: Double? = null, val pounds: Boolean = false)

/**
 * Entiende frases como «80 kilos 8 repeticiones», «ochenta por ocho», «82 y medio por 5 RPE 8»,
 * «100 pounds 5 reps» o «10 reps». Sin palabras clave, con dos números el primero es el peso
 * y el segundo las reps («60 por 10»); con uno solo, son reps.
 */
object VoiceSetParser {
    private val units = mapOf(
        "cero" to 0, "zero" to 0, "un" to 1, "uno" to 1, "una" to 1, "one" to 1, "dos" to 2, "two" to 2, "tres" to 3, "three" to 3,
        "cuatro" to 4, "four" to 4, "cinco" to 5, "five" to 5, "seis" to 6, "six" to 6, "siete" to 7, "seven" to 7,
        "ocho" to 8, "eight" to 8, "nueve" to 9, "nine" to 9, "diez" to 10, "ten" to 10, "once" to 11, "eleven" to 11,
        "doce" to 12, "twelve" to 12, "trece" to 13, "thirteen" to 13, "catorce" to 14, "fourteen" to 14,
        "quince" to 15, "fifteen" to 15, "dieciseis" to 16, "sixteen" to 16, "diecisiete" to 17, "seventeen" to 17,
        "dieciocho" to 18, "eighteen" to 18, "diecinueve" to 19, "nineteen" to 19, "veinte" to 20, "twenty" to 20,
        "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25,
        "treinta" to 30, "thirty" to 30, "cuarenta" to 40, "forty" to 40, "cincuenta" to 50, "fifty" to 50,
        "sesenta" to 60, "sixty" to 60, "setenta" to 70, "seventy" to 70, "ochenta" to 80, "eighty" to 80,
        "noventa" to 90, "ninety" to 90,
    )
    private val hundreds = mapOf(
        "cien" to 100, "ciento" to 100, "hundred" to 100, "doscientos" to 200, "trescientos" to 300, "cuatrocientos" to 400, "quinientos" to 500,
    )
    private val weightWords = setOf("kg", "kilo", "kilos", "kilogramos", "kgs", "lb", "lbs", "libra", "libras", "pound", "pounds")
    private val poundWords = setOf("lb", "lbs", "libra", "libras", "pound", "pounds")
    private val repWords = setOf("rep", "reps", "repeticion", "repeticiones", "veces", "times", "repetitions", "repetition")
    private val rpeWords = setOf("rpe", "erre", "esfuerzo")

    private sealed interface Tok {
        data class Num(val v: Double) : Tok
        data class Word(val w: String) : Tok
    }

    fun parse(text: String): SpokenSet {
        val tokens = tokenize(text)
        var weight: Double? = null
        var reps: Int? = null
        var rpe: Double? = null
        var pounds = false
        val loose = mutableListOf<Double>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t is Tok.Word && t.w in rpeWords) {
                (tokens.getOrNull(i + 1) as? Tok.Num)?.let { rpe = it.v.coerceIn(1.0, 10.0); i += 2; continue }
            }
            if (t is Tok.Num) {
                val next = (tokens.getOrNull(i + 1) as? Tok.Word)?.w
                when {
                    next != null && next in weightWords -> {
                        weight = t.v
                        if (next in poundWords) pounds = true
                        i += 2
                        continue
                    }
                    next != null && next in repWords -> {
                        reps = t.v.toInt()
                        i += 2
                        continue
                    }
                    else -> loose += t.v
                }
            }
            i++
        }
        // Números sin palabra clave: «60 por 10» → peso y reps
        when {
            weight == null && reps == null && loose.size >= 2 -> {
                weight = loose[0]
                reps = loose[1].toInt()
            }
            weight == null && reps != null && loose.isNotEmpty() -> weight = loose[0]
            reps == null && weight != null && loose.isNotEmpty() -> reps = loose[0].toInt()
            weight == null && reps == null && loose.size == 1 -> reps = loose[0].toInt()
        }
        return SpokenSet(weight, reps?.takeIf { it in 0..200 }, rpe, pounds)
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun tokenize(text: String): List<Tok> {
        val raw = normalize(text)
            .replace(Regex("(\\d)\\s*[x×]\\s*(\\d)"), "$1 por $2")
            .replace(Regex("(\\d),(\\d)"), "$1.$2")
            .split(Regex("[^a-z0-9.]+")).filter { it.isNotBlank() }
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < raw.size) {
            val w = raw[i]
            val n = w.toDoubleOrNull() ?: wordNumber(raw, i)?.let { (v, used) -> i += used - 1; v }
            if (n != null) {
                var value = n
                // «82 y medio», «82 and a half», «82 coma 5»
                val a = raw.getOrNull(i + 1)
                val b = raw.getOrNull(i + 2)
                val c = raw.getOrNull(i + 3)
                when {
                    a == "y" && b == "medio" -> { value += 0.5; i += 2 }
                    a == "and" && b == "a" && c == "half" -> { value += 0.5; i += 3 }
                    a == "and" && b == "half" -> { value += 0.5; i += 2 }
                    (a == "coma" || a == "punto" || a == "point") && b?.toIntOrNull() != null -> {
                        value = "${value.toLong()}.$b".toDouble()
                        i += 2
                    }
                }
                out += Tok.Num(value)
            } else {
                out += Tok.Word(w)
            }
            i++
        }
        return out
    }

    /** Número en palabras desde [start]: «ciento veinte», «ochenta y dos», «two hundred twenty». Devuelve (valor, palabras usadas). */
    private fun wordNumber(words: List<String>, start: Int): Pair<Double, Int>? {
        var i = start
        var total = 0
        var any = false
        hundreds[words.getOrNull(i)]?.let { total += it; i++; any = true }
        // «two hundred»
        if (!any) {
            val u = units[words.getOrNull(i)]
            if (u != null && words.getOrNull(i + 1) == "hundred") {
                total += u * 100
                i += 2
                any = true
            }
        }
        val tens = units[words.getOrNull(i)]
        if (tens != null) {
            total += tens
            i++
            any = true
            // «ochenta y dos»
            if (tens >= 30 && tens % 10 == 0 && words.getOrNull(i) == "y") {
                units[words.getOrNull(i + 1)]?.takeIf { it in 1..9 }?.let { total += it; i += 2 }
            } else if (tens >= 20 && tens % 10 == 0) {
                units[words.getOrNull(i)]?.takeIf { it in 1..9 }?.let { total += it; i++ }
            }
        }
        return if (any) total.toDouble() to (i - start) else null
    }
}
