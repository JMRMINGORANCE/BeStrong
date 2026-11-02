package com.bestrong.core.stats

import com.bestrong.core.model.Workout

/** Diario de entreno: etiquetas de cada sesión y qué relación tienen con el rendimiento. */
object Journal {
    /** «#Dormí mal » → «dormí-mal»: sin «#», en minúsculas y con guiones en vez de espacios. Vacía si no queda nada. */
    fun normalizeTag(raw: String): String =
        raw.trim().removePrefix("#").trim().lowercase().replace(Regex("\\s+"), "-").take(MAX_TAG_LENGTH)

    /** Añade [tag] (normalizada) si no estaba; las vacías no cuentan. */
    fun withTag(tags: List<String>, tag: String): List<String> {
        val t = normalizeTag(tag)
        return if (t.isEmpty() || t in tags) tags else tags + t
    }

    /** Etiquetas usadas, de la más frecuente a la menos (empates por orden alfabético). */
    fun tagCounts(history: List<Workout>): List<Pair<String, Int>> =
        history.flatMap { it.tags.distinct() }.groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    /**
     * Rendimiento relativo de las sesiones con [tag] frente a las que no la llevan: cada sesión se mide por su volumen
     * frente a la media de las de su misma rutina (o mismo nombre), para no comparar una pierna con un brazo. Devuelve
     * la diferencia (−0,07 = rindes un 7 % menos) o null si hay menos de [MIN_SAMPLES] sesiones comparables en cada grupo.
     */
    fun tagEffect(history: List<Workout>, tag: String): Double? {
        val done = history.filter { it.isFinished && it.totalVolume > 0 }
        val groups = done.groupBy { it.routineId ?: it.name }.filterValues { it.size >= 2 }
        val relative = groups.values.flatMap { list ->
            val avg = list.map { it.totalVolume }.average()
            list.map { it to it.totalVolume / avg }
        }
        val with = relative.filter { tag in it.first.tags }.map { it.second }
        val without = relative.filter { tag !in it.first.tags }.map { it.second }
        if (with.size < MIN_SAMPLES || without.size < MIN_SAMPLES) return null
        return with.average() / without.average() - 1
    }

    const val MIN_SAMPLES = 2
    private const val MAX_TAG_LENGTH = 32
}
