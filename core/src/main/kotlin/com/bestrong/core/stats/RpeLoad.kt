package com.bestrong.core.stats

import com.bestrong.core.model.Workout

/**
 * Carga para unas repeticiones a un RPE: el RPE dice cuántas reps quedaban en recámara (RPE 8 = 2), así que
 * 5 reps a RPE 8 equivalen a una serie de 7 al fallo. Se invierte la misma fórmula del 1RM estimado de la app
 * (Epley), para que la tabla y las marcas cuadren.
 */
object RpeLoad {
    private const val EPLEY = 30.0
    private const val MAX_RPE = 10.0
    private const val RECENT_WEEKS = 8L
    private const val WEEK_MS = 7 * 86_400_000L

    /** Fracción del 1RM para [reps] a [rpe] (1 rep a RPE 10 = 100 %). */
    fun fraction(reps: Int, rpe: Double): Double {
        val toFailure = reps + (MAX_RPE - rpe).coerceAtLeast(0.0)
        return if (toFailure <= 1.0) 1.0 else 1 / (1 + toFailure / EPLEY)
    }

    fun weightFor(oneRepMaxKg: Double, reps: Int, rpe: Double): Double = oneRepMaxKg * fraction(reps, rpe)

    /**
     * Mejor 1RM estimado de las últimas [RECENT_WEEKS] semanas (una marca de hace años no dice lo que levantas
     * hoy); si no hay, el mejor de todo el historial. Null si nunca se hizo con peso.
     */
    fun recentOneRepMax(exerciseId: String, history: List<Workout>, nowEpochMs: Long): Double? {
        val best = { ws: List<Workout> ->
            ws.flatMap { w -> w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets } }
                .maxOfOrNull { it.estimatedOneRepMax }?.takeIf { it > 0 }
        }
        val finished = history.filter { it.isFinished }
        return best(finished.filter { it.startedAtEpochMs >= nowEpochMs - RECENT_WEEKS * WEEK_MS }) ?: best(finished)
    }
}
