package com.bestrong.mobile.ui.workout

import com.bestrong.core.model.WorkoutSet

/** Lo que el modo gimnasio necesita además del entreno: historial, ajustes del usuario y métricas del reloj. */
internal data class GymInputs(
    val previous: (String) -> List<WorkoutSet>,
    val dumbbellTotal: Boolean,
    val barKgFor: (String) -> Double?,
    val pinnedNote: (String) -> String?,
    /** Mejor 1RM estimado por ejercicio (para el chip «récord con N»). */
    val bestOneRepMax: (String) -> Double?,
    val heartRate: Int?,
)
