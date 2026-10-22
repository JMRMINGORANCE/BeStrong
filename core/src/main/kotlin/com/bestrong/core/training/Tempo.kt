package com.bestrong.core.training

/**
 * Tempo de una repetición en segundos: bajada (excéntrica), pausa abajo, subida (concéntrica)
 * y pausa arriba. Se escribe "3-1-1-0"; con tres cifras la pausa arriba es 0. "X" = explosivo (0 s).
 */
data class Tempo(val eccentric: Int, val bottomPause: Int, val concentric: Int, val topPause: Int = 0) {
    /** Duración de una repetición. */
    val repSeconds: Int get() = eccentric + bottomPause + concentric + topPause

    /** Fases con duración > 0, en orden, para guiar con vibraciones. */
    val phases: List<Pair<Phase, Int>>
        get() = listOf(Phase.ECCENTRIC to eccentric, Phase.BOTTOM to bottomPause, Phase.CONCENTRIC to concentric, Phase.TOP to topPause).filter { it.second > 0 }

    /**
     * Fases para guiar con vibraciones: como [phases], pero una subida explosiva (X, 0 s) también avisa y se le
     * da 1 s (si no, tras la pausa abajo no habría ninguna señal de subir).
     */
    val guidePhases: List<Pair<Phase, Int>>
        get() = listOf(Phase.ECCENTRIC to eccentric, Phase.BOTTOM to bottomPause, Phase.CONCENTRIC to concentric.coerceAtLeast(1), Phase.TOP to topPause)
            .filter { it.second > 0 }

    /** Tiempo bajo tensión de una serie de [reps] repeticiones. */
    fun timeUnderTension(reps: Int): Int = repSeconds * reps

    /** Subida de 0 s = explosiva: se escribe «X», como se teclea («3-1-X-0»). */
    override fun toString() = "$eccentric-$bottomPause-${if (concentric == 0) "X" else concentric}-$topPause"

    enum class Phase { ECCENTRIC, BOTTOM, CONCENTRIC, TOP }

    companion object {
        private const val MAX_PHASE = 10

        fun parse(text: String?): Tempo? {
            // Un punto junto a otros separadores es un decimal («2.5-1-1»), no otra fase: no vale
            val t0 = text?.trim() ?: return null
            if ('.' in t0 && t0.any { it in "-/ " }) return null
            val parts = t0.split('-', '/', ' ', '.').filter { it.isNotBlank() }
            if (parts.size !in 3..4) return null
            val values = parts.map { p -> if (p.equals("x", ignoreCase = true)) 0 else p.toIntOrNull()?.takeIf { it in 0..MAX_PHASE } ?: return null }
            val t = Tempo(values[0], values[1], values[2], values.getOrElse(3) { 0 })
            return t.takeIf { it.repSeconds > 0 }
        }
    }
}
