package com.bestrong.core.session

import com.bestrong.core.model.Workout

/**
 * Descanso compartido entre el móvil y el reloj: cada uno lo pone en marcha al marcar una serie, y al llegar del otro
 * una serie marcada más tarde que las propias, el descanso pasa a ser el de esa serie (sin esto, el descanso de una
 * serie anterior avisaba a mitad de la siguiente).
 */
object RestSync {
    /**
     * Segundos de descanso que quedan por la serie que [after] trae marcada después de todas las de [before]; 0 si ese
     * descanso ya pasó o la serie no lleva descanso; null si no hay ninguna serie nueva (no se toca el descanso).
     */
    fun remainingAfterRemote(before: Workout?, after: Workout, nowEpochMs: Long, warmupRestSeconds: Int): Int? {
        if (before?.id != after.id) return null
        val latestBefore = before.exercises.flatMap { it.sets }.mapNotNull { s -> s.completedAtEpochMs?.takeIf { s.completed } }.maxOrNull() ?: 0L
        val newest = after.exercises.withIndex()
            .flatMap { (i, we) -> we.sets.mapNotNull { s -> s.completedAtEpochMs?.takeIf { s.completed && it > latestBefore }?.let { i to it } } }
            .maxByOrNull { it.second }
        return newest?.let { (index, doneAt) ->
            val rest = WorkoutEditor.afterSetCompleted(after, index, warmupRestSeconds).second
            (rest - ((nowEpochMs - doneAt) / 1000).toInt()).coerceAtLeast(0)
        }
    }
}
