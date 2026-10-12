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
} + (set.repsRight?.let { " | $it" } ?: "") + (set.rpe?.let { " @${it.toString().removeSuffix(".0")}" } ?: "")
