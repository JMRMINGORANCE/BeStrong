package com.bestrong.core.model

import kotlinx.serialization.Serializable

/** Entreno borrado que se guarda un tiempo en la papelera por si se quiere recuperar. */
@Serializable
data class TrashedWorkout(val deletedAtEpochMs: Long, val workout: Workout) {
    companion object {
        /** Días que se guarda un entreno borrado. */
        const val KEEP_DAYS = 30L

        /** Como mucho estos (cada uno guarda el entreno entero, con su pulso): los más antiguos salen antes. */
        const val MAX_ITEMS = 100

        /** Sin los de hace más de [KEEP_DAYS] días (aunque no se haya vuelto a borrar nada desde entonces). */
        fun fresh(list: List<TrashedWorkout>, nowEpochMs: Long): List<TrashedWorkout> =
            list.filter { it.deletedAtEpochMs >= nowEpochMs - KEEP_DAYS * 86_400_000L }

        /** Lo que queda en la papelera tras añadir [added]: sin los de hace más de [KEEP_DAYS] días, el último arriba. */
        fun keep(current: List<TrashedWorkout>, added: List<TrashedWorkout>, nowEpochMs: Long): List<TrashedWorkout> {
            val ids = added.map { it.workout.id }.toSet()
            return fresh(added + current.filter { it.workout.id !in ids }, nowEpochMs)
                .sortedByDescending { it.deletedAtEpochMs }
                .take(MAX_ITEMS)
        }
    }
}
