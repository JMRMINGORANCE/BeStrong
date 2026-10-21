package com.bestrong.core.training

import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise

/** Qué hacer la próxima vez con un ejercicio: subir, mantener o bajar la carga. */
data class NextTimeSuggestion(
    val exerciseId: String,
    val exerciseName: String,
    val weightKg: Double,
    val reps: Int,
    val direction: Int,
    /** Se mantiene por falta de datos (una sola serie pesada sin plan ni RPE): apuntando el RPE podría subir. */
    val needsRpe: Boolean = false,
)

/**
 * Doble progresión sencilla al terminar el entreno:
 * - todas las series con el peso más alto con las reps de la primera de ellas y RPE < 9,5 → subir un escalón
 *   (las de descarga, más ligeras, no cuentan);
 * - alguna serie a RPE ≥ 9,5 con menos reps que la primera → bajar un 5 %;
 * - en otro caso, repetir el mismo peso e intentar sumar reps.
 */
object NextTime {
    fun suggest(we: WorkoutExercise, stepKg: Double): NextTimeSuggestion? {
        if (we.exercise.tracking != TrackingType.WEIGHT_REPS) return null
        val work = we.sets.filter { it.completed && it.type != SetType.WARMUP && it.countsForRecords && it.weightKg > 0 }
        if (work.isEmpty()) return null
        val top = work.maxOf { it.weightKg }
        // Solo las series con el peso más alto: las de descarga (más ligeras, más o menos reps) no marcan el objetivo
        val topSets = work.filter { it.weightKg >= top }
        val target = targetOf(topSets)
        val maxRpe = topSets.mapNotNull { it.rpe }.maxOrNull()
        val evidence = evidenceFor(work, topSets)
        val allReps = allRepsDone(topSets, target, evidence, we.skippedWorkSets)
        val name = we.exercise.name
        val high = Autoregulation.high(we.targetRpe)
        return when {
            allReps && (maxRpe == null || maxRpe < high) -> NextTimeSuggestion(we.exercise.id, name, top + stepKg, target, 1)
            !allReps && maxRpe != null && maxRpe >= high -> NextTimeSuggestion(we.exercise.id, name, lowered(top, stepKg), target, -1)
            else -> NextTimeSuggestion(we.exercise.id, name, top, target, 0, needsRpe = !evidence)
        }
    }

    /** Todas las pesadas con sus reps y con datos para saberlo; con series saltadas no, aunque las hechas salieran bien. */
    private fun allRepsDone(topSets: List<com.bestrong.core.model.WorkoutSet>, target: Int, evidence: Boolean, skipped: Boolean): Boolean =
        !skipped && evidence && topSets.all { it.reps >= target }

    private fun evidenceFor(work: List<com.bestrong.core.model.WorkoutSet>, topSets: List<com.bestrong.core.model.WorkoutSet>): Boolean =
        hasEvidence(topSets) || topThenBackOffs(work, topSets)

    /**
     * Sin plan: una serie top seguida de descargas cuenta si la top llegó a las reps de las descargas (100×5 y
     * luego 85×5); si se quedó corta (100×2 y luego 85×5), no se sube.
     */
    private fun topThenBackOffs(work: List<com.bestrong.core.model.WorkoutSet>, topSets: List<com.bestrong.core.model.WorkoutSet>): Boolean {
        // Solo descargas normales: una serie descendente o al fallo después no dice si la top salió
        val backOffs = work.filter { it !in topSets }
        val normal = backOffs.isNotEmpty() && backOffs.all { it.type == SetType.NORMAL }
        return normal && work.last() !in topSets && topSets.first().reps >= backOffs.minOf { it.reps }
    }

    /** Lo planeado en la rutina si lo hay; si no, las reps de la primera serie pesada. */
    private fun targetOf(topSets: List<com.bestrong.core.model.WorkoutSet>): Int = topSets.first().targetReps ?: topSets.first().reps

    /** Un 5 % menos, redondeado al escalón y al menos un escalón por debajo. */
    private fun lowered(top: Double, stepKg: Double): Double {
        val lower = if (stepKg > 0) Math.floor(top * 0.95 / stepKg + 0.5) * stepKg else top * 0.95
        return lower.coerceAtMost(top - stepKg).coerceAtLeast(0.0)
    }

    /**
     * Con una sola serie pesada, sin reps planeadas ni RPE, no hay con qué comparar (¿salieron las que tocaban o
     * menos?): no se sube a ciegas.
     */
    private fun hasEvidence(topSets: List<com.bestrong.core.model.WorkoutSet>): Boolean =
        topSets.size >= 2 || topSets.any { it.rpe != null || it.targetReps != null }

    fun forWorkout(workout: Workout, stepKg: Double): List<NextTimeSuggestion> = forWorkout(workout) { stepKg }

    /** Con incremento propio por ejercicio (mancuernas de 2 kg, máquinas de 5 kg…). */
    fun forWorkout(workout: Workout, stepFor: (exerciseId: String) -> Double): List<NextTimeSuggestion> =
        workout.exercises.mapNotNull { suggest(it, stepFor(it.exercise.id)) }
}
