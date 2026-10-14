package com.bestrong.core.model

import kotlinx.serialization.Serializable

/** Entreno borrado que se guarda un tiempo en la papelera por si se quiere recuperar. */
@Serializable
data class TrashedWorkout(val deletedAtEpochMs: Long, val workout: Workout) {
    companion object {
        /** Días que se guarda un entreno borrado. */
        const val KEEP_DAYS = 30L

        /** Lo que queda en la papelera tras añadir [added]: sin los de hace más de [KEEP_DAYS] días, el último arriba. */
        fun keep(current: List<TrashedWorkout>, added: List<TrashedWorkout>, nowEpochMs: Long): List<TrashedWorkout> {
            val ids = added.map { it.workout.id }.toSet()
            return (added + current.filter { it.workout.id !in ids })
                .filter { it.deletedAtEpochMs >= nowEpochMs - KEEP_DAYS * 86_400_000L }
                .sortedByDescending { it.deletedAtEpochMs }
        }
    }
}
