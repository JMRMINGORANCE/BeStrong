package com.bestrong.core.community

/**
 * Cuándo pedir una idea al terminar un entreno: en un buen momento (un hito o un récord) y sin agobiar (como mucho
 * una vez cada [MIN_GAP_DAYS] días, y nunca más si el usuario lo pide). Todo se decide y se guarda en el móvil.
 */
object ProposalPrompt {
    /** Entrenos terminados con los que se pregunta. */
    val MILESTONES = setOf(5, 20, 50, 100)
    const val MIN_GAP_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Con un récord se pregunta solo a partir de unos cuantos entrenos (el primero siempre es «récord»). */
    private const val MIN_WORKOUTS_FOR_RECORD = 3

    fun shouldAsk(finished: Int, hasRecords: Boolean, lastAskedMs: Long, nowMs: Long, optedOut: Boolean): Boolean {
        val tooSoon = lastAskedMs > 0 && nowMs - lastAskedMs < MIN_GAP_DAYS * DAY_MS
        val goodMoment = finished in MILESTONES || (hasRecords && finished >= MIN_WORKOUTS_FOR_RECORD)
        return !optedOut && !tooSoon && goodMoment
    }

    /** Hay respuestas nuevas del equipo: alguna propuesta con más mensajes suyos que los ya vistos. */
    fun hasUnread(latest: Map<Int, Int>, seen: Map<Int, Int>): Boolean = latest.any { (number, count) -> count > (seen[number] ?: 0) }

    /** «3:1,5:2» ↔ {3=1, 5=2} (número de propuesta → mensajes del equipo), para guardarlo en preferencias. */
    fun encode(counts: Map<Int, Int>): String = counts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(text: String?): Map<Int, Int> = text.orEmpty().split(',').mapNotNull { pair ->
        val parts = pair.split(':')
        val number = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (number == null || count == null) null else number to count
    }.toMap()
}
