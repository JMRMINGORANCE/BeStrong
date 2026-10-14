package com.bestrong.mobile.ui.workout

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout

// Ajustes de carga del entreno que no dependen del estado del ViewModel: cambio de gimnasio y peso por RPE

/** Series pendientes con peso de un ejercicio, al peso más cercano que se puede montar en el gimnasio activo. */
internal fun refitToGym(e: com.bestrong.core.model.WorkoutExercise, s: com.bestrong.mobile.data.UserSettings): com.bestrong.core.model.WorkoutExercise {
    val gym = s.gymFor(e.exercise.id)
    // En libras no hay discos del perfil; si el gimnasio nuevo no tiene ese material no hay a qué ajustar
    // (se verá el aviso de material)
    if (s.unit != com.bestrong.core.util.WeightUnit.KG || e.exercise.equipment !in gym.equipment) return e
    val barbell = e.exercise.equipment == com.bestrong.core.model.Equipment.BARBELL || e.exercise.equipment == com.bestrong.core.model.Equipment.SMITH
    return e.copy(
        sets = e.sets.map { set ->
            // Por debajo de la barra (técnica con barra ligera) no se sube a la barra
            val fits = set.weightKg > 0 && !(barbell && set.weightKg < gym.loadedBarKg)
            if (!set.completed && fits) set.copy(weightKg = gym.nearestLoad(set.weightKg, e.exercise.equipment)) else set
        },
    )
}

/**
 * Peso orientativo para el RPE objetivo del ejercicio (kg) y las reps de su siguiente serie, con el 1RM
 * estimado reciente [oneRepMaxKg] y redondeado al material del gimnasio; null sin objetivo, sin reps o sin historial.
 */
internal fun targetRpeLoad(we: com.bestrong.core.model.WorkoutExercise, oneRepMaxKg: Double?, s: com.bestrong.mobile.data.UserSettings): Pair<Double, Int>? {
    val set = we.sets.firstOrNull { !it.completed && it.type == SetType.NORMAL } ?: we.sets.firstOrNull { it.type == SetType.NORMAL }
    val reps = set?.reps ?: 0
    val rpe = we.targetRpe?.takeIf { reps > 0 } ?: return null
    return oneRepMaxKg?.let {
        val kg = com.bestrong.core.stats.RpeLoad.weightFor(it, reps, rpe)
        com.bestrong.core.stats.RpeLoad.roundToLoadable(kg, s.unit, s.stepKgFor(we.exercise.id), s.gymFor(we.exercise.id), we.exercise.equipment) to reps
    }
}

internal fun withRpeLoads(
    e: com.bestrong.core.model.WorkoutExercise,
    e1rm: Double?,
    s: com.bestrong.mobile.data.UserSettings,
    history: List<Workout>,
    actions: com.bestrong.mobile.WorkoutActions,
): com.bestrong.core.model.WorkoutExercise {
    val rpe = e.targetRpe
    if (rpe == null || e1rm == null) return e
    val pending = e.sets.filter { !it.completed && it.type == SetType.NORMAL && it.reps > 0 }
    val raw = pending.map { set ->
        val kg = com.bestrong.core.stats.RpeLoad.weightFor(e1rm, set.reps, rpe)
        set.copy(weightKg = com.bestrong.core.stats.RpeLoad.roundToLoadable(kg, s.unit, s.stepKgFor(e.exercise.id), s.gymFor(e.exercise.id), e.exercise.equipment))
    }
    val loaded = actions.loadable(e.exercise, raw, null, history = history).associateBy { it.id }
    return e.copy(sets = e.sets.map { loaded[it.id] ?: it })
}
