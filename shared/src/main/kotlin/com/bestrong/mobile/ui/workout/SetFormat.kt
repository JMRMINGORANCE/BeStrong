package com.bestrong.mobile.ui.workout

import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.util.WeightUnit

/** Texto corto de una serie («80 kg × 8 @8»), usado en el entreno, el historial y la ficha del ejercicio. */
fun describeSet(set: WorkoutSet, tracking: TrackingType, unit: WeightUnit, weightFactor: Int = 1): String = when (tracking) {
    TrackingType.WEIGHT_REPS -> "${unit.format(set.weightKg * weightFactor)} ${unit.label} × ${set.reps}"
    TrackingType.REPS_ONLY -> if (set.weightKg > 0) "+${unit.format(set.weightKg)} ${unit.label} × ${set.reps}" else "${set.reps} reps"
    TrackingType.DURATION -> "${set.durationSec} s"
    TrackingType.DISTANCE -> (if (set.weightKg > 0) "${unit.format(set.weightKg)} ${unit.label} · " else "") + "${set.distanceM.toInt()} m"
    TrackingType.CARDIO -> cardioText(set.durationSec, set.distanceM)
} + (set.repsRight?.let { " | $it" } ?: "") + (set.rpe?.let { " @${it.toString().removeSuffix(".0")}" } ?: "")

/** «20 min · 3,20 km» (o «12:30» si no son minutos exactos). */
fun cardioText(durationSec: Int, distanceM: Double): String {
    val time = if (durationSec % 60 == 0) "${durationSec / 60} min" else "%d:%02d".format(durationSec / 60, durationSec % 60)
    return if (distanceM > 0) "$time · ${"%.2f".format(distanceM / 1000)} km" else time
}
