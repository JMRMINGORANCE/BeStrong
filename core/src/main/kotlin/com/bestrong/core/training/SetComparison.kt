package com.bestrong.core.training

import com.bestrong.core.model.WorkoutSet

/** Diferencia de una serie frente a la misma serie de la sesión anterior. */
data class SetDelta(val weightKg: Double, val reps: Int, val durationSec: Int) {
    val isZero: Boolean get() = weightKg == 0.0 && reps == 0 && durationSec == 0
}

object SetComparison {
    /** null si no hay serie anterior con la que comparar. */
    fun delta(current: WorkoutSet, previous: WorkoutSet?): SetDelta? {
        if (previous == null) return null
        return SetDelta(
            weightKg = current.weightKg - previous.weightKg,
            reps = current.reps - previous.reps,
            durationSec = current.durationSec - previous.durationSec,
        )
    }

    /** Si la serie supera a la anterior en 1RM estimado (para resaltar en verde). */
    fun beats(current: WorkoutSet, previous: WorkoutSet?): Boolean? {
        previous ?: return null
        return if (current.weightKg > 0 || previous.weightKg > 0) {
            current.estimatedOneRepMax > previous.estimatedOneRepMax + 1e-9
        } else {
            current.reps + current.durationSec > previous.reps + previous.durationSec
        }
    }
}
