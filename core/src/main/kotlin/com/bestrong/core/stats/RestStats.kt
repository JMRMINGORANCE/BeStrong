package com.bestrong.core.stats

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout

/** Descanso real frente al planificado, en segundos (medias). */
data class RestSummary(val realSeconds: Int, val plannedSeconds: Int, val samples: Int)

object RestStats {
    /** Más que esto entre dos series no es descanso (pausa, cambio de máquina, charla…). */
    private const val MAX_GAP_MS = 15 * 60_000L

    /**
     * Descanso medio real entre series seguidas del mismo ejercicio: lo que pasa desde que se marca
     * una serie hasta que empieza la siguiente (se supone que una serie dura [SetTimeline.DEFAULT_SET_MS]).
     * Se ignoran calentamientos, drop sets y superseries (tienen su propio descanso) y las series sin hora de marcado.
     * Las series por tiempo duran lo suyo.
     */
    fun summary(workout: Workout): RestSummary? {
        val gaps = workout.exercises.filter { it.restSeconds > 0 && it.supersetId == null }.flatMap { we ->
            we.completedSets.zipWithNext().mapNotNull { (a, b) ->
                if (a.type == SetType.WARMUP || b.type == SetType.DROP) return@mapNotNull null
                val end = a.completedAtEpochMs ?: return@mapNotNull null
                val nextStart = b.startedAtEpochMs ?: ((b.completedAtEpochMs ?: return@mapNotNull null) - maxOf(SetTimeline.DEFAULT_SET_MS, b.durationSec * 1000L))
                val gap = nextStart - end
                if (gap <= 0 || gap > MAX_GAP_MS) null else gap / 1000 to we.restSeconds
            }
        }
        if (gaps.isEmpty()) return null
        return RestSummary(gaps.map { it.first }.average().toInt(), gaps.map { it.second }.average().toInt(), gaps.size)
    }
}
