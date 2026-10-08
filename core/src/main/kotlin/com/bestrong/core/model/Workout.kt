package com.bestrong.core.model

import com.bestrong.core.util.newId
import kotlinx.serialization.Serializable

@Serializable
enum class SetType(val short: String) { WARMUP("C"), NORMAL(""), DROP("D"), FAILURE("F") }

@Serializable
enum class WorkoutSource { PHONE, WATCH }

@Serializable
data class WorkoutSet(
    val id: String = newId(),
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val durationSec: Int = 0,
    val type: SetType = SetType.NORMAL,
    val rpe: Double? = null,
    val completed: Boolean = false,
) {
    /** 1RM estimado (Epley). Las series de calentamiento no cuentan. */
    val estimatedOneRepMax: Double
        get() = when {
            type == SetType.WARMUP || reps <= 0 || weightKg <= 0 -> 0.0
            reps == 1 -> weightKg
            else -> weightKg * (1 + reps / 30.0)
        }

    val volume: Double get() = if (type == SetType.WARMUP) 0.0 else weightKg * reps
}

@Serializable
data class WorkoutExercise(
    val id: String = newId(),
    val exercise: Exercise,
    val sets: List<WorkoutSet> = listOf(WorkoutSet()),
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val notes: String = "",
    /** Ejercicios con el mismo id forman una superserie (se alternan sin descanso). */
    val supersetId: String? = null,
) {
    val completedSets: List<WorkoutSet> get() = sets.filter { it.completed }

    companion object {
        const val DEFAULT_REST_SECONDS = 120
    }
}

@Serializable
data class Workout(
    val id: String = newId(),
    val name: String,
    val routineId: String? = null,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
    val notes: String = "",
    val source: WorkoutSource = WorkoutSource.PHONE,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    /** Última modificación: resuelve conflictos al sincronizar móvil ↔ reloj en vivo. */
    val updatedAtEpochMs: Long = startedAtEpochMs,
) {
    val isFinished: Boolean get() = finishedAtEpochMs != null

    val totalVolume: Double
        get() = exercises.sumOf { ex -> ex.completedSets.sumOf { it.volume } }

    val completedSetCount: Int
        get() = exercises.sumOf { it.completedSets.size }

    val totalSetCount: Int
        get() = exercises.sumOf { it.sets.size }

    fun durationSeconds(nowEpochMs: Long = System.currentTimeMillis()): Long =
        ((finishedAtEpochMs ?: nowEpochMs) - startedAtEpochMs).coerceAtLeast(0) / 1000
}
