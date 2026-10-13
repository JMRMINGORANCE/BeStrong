package com.bestrong.core.stats

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise

/** Descanso real frente al planificado, en segundos (medias). */
data class RestSummary(val realSeconds: Int, val plannedSeconds: Int, val samples: Int) {
    /** El descanso real redondeado a 15 s, para llevarlo a la rutina. */
    val suggestedSeconds: Int get() = (Math.round(realSeconds / 15.0) * 15).toInt().coerceAtLeast(15)
}

object RestStats {
    /** Más que esto entre dos series no es descanso (pausa, cambio de máquina, charla…). */
    private const val MAX_GAP_MS = 15 * 60_000L

    /**
     * Descanso medio real entre series seguidas del mismo ejercicio: lo que pasa desde que se marca
     * una serie hasta que empieza la siguiente (se supone que una serie dura [SetTimeline.DEFAULT_SET_MS]).
     * Se ignoran calentamientos, drop sets y superseries (tienen su propio descanso) y las series sin hora de marcado.
     * Las series por tiempo duran lo suyo.
     */
    fun summary(workout: Workout): RestSummary? = summarize(workout.exercises.flatMap(::gaps))

    /** Lo mismo por ejercicio (id del ejercicio del entreno → resumen), para la tarjeta de cada uno. */
    fun byExercise(workout: Workout): Map<String, RestSummary> =
        workout.exercises.mapNotNull { we -> summarize(gaps(we))?.let { we.id to it } }.toMap()

    private fun summarize(gaps: List<Pair<Long, Int>>): RestSummary? {
        if (gaps.isEmpty()) return null
        return RestSummary(gaps.map { it.first }.average().toInt(), gaps.map { it.second }.average().toInt(), gaps.size)
    }

    /** (segundos reales, segundos previstos) de cada descanso entre series seguidas de [we]. */
    private fun gaps(we: WorkoutExercise): List<Pair<Long, Int>> {
        if (we.restSeconds <= 0 || we.supersetId != null) return emptyList()
        // Series marcadas de golpe («marcar las que faltan») comparten la hora: no hubo descanso medible
        val bulk = we.completedSets.mapNotNull { it.completedAtEpochMs }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return we.completedSets.zipWithNext().mapNotNull { (a, b) ->
            if (a.type == SetType.WARMUP || b.type == SetType.DROP || b.completedAtEpochMs in bulk) return@mapNotNull null
            val end = a.completedAtEpochMs ?: return@mapNotNull null
            val nextStart = b.startedAtEpochMs ?: ((b.completedAtEpochMs ?: return@mapNotNull null) - maxOf(SetTimeline.DEFAULT_SET_MS, b.durationSec * 1000L))
            val gap = nextStart - end
            if (gap <= 0 || gap > MAX_GAP_MS) null else gap / 1000 to we.restSeconds
        }
    }
}
