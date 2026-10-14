package com.bestrong.core.training

import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout

/** Rutinas para empezar con un toque (widget): la de hoy según la planificación y las que más tiempo llevan sin hacerse. */
object QuickRoutines {
    /**
     * Hasta [count] rutinas activas con ejercicios: primero [plannedTodayId] si no se ha hecho ya [today]
     * (desde [todayStartEpochMs]); después, la que lleva más tiempo sin hacerse (las nunca hechas antes).
     */
    fun pick(routines: List<Routine>, history: List<Workout>, plannedTodayId: String?, todayStartEpochMs: Long, count: Int = 2): List<Routine> {
        val usable = routines.filter { !it.archived && it.exercises.isNotEmpty() }
        val last = history.filter { it.routineId != null }.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        val planned = usable.firstOrNull { it.id == plannedTodayId }?.takeIf { (last[it.id] ?: 0L) < todayStartEpochMs }
        val rest = usable.filter { it.id != planned?.id }.sortedBy { last[it.id] ?: 0L }
        return (listOfNotNull(planned) + rest).take(count)
    }
}
