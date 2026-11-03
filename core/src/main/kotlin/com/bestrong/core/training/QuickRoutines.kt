package com.bestrong.core.training

import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout

/** Rutinas para empezar con un toque (widget, reloj, recordatorio): las que más tiempo llevan sin hacerse. */
object QuickRoutines {
    /** Hasta [count] rutinas activas con ejercicios, la que lleva más tiempo sin hacerse primero (las nunca hechas antes). */
    fun pick(routines: List<Routine>, history: List<Workout>, count: Int = 2): List<Routine> {
        val usable = routines.filter { !it.archived && it.exercises.isNotEmpty() }
        val last = history.filter { it.routineId != null }.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        return usable.sortedBy { last[it.id] ?: 0L }.take(count)
    }
}
