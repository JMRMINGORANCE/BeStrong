package com.bestrong.core.training

import com.bestrong.core.model.Exercise
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats

enum class SuggestionKind {
    /** Subir el peso (todas las series completadas). */
    INCREASE_WEIGHT,

    /** Mismo peso, buscar [ProgressionSuggestion.targetReps] en todas las series. */
    ADD_REPS,

    /** Ejercicio sin peso: +1 repetición por serie. */
    ADD_ONE_REP,

    /** Ejercicio por tiempo: +5 s por serie. */
    ADD_TIME,

    /** RPE alto: repetir y consolidar. */
    REPEAT,

    /** Estancado [ProgressionSuggestion.stallSessions] sesiones: descarga al 90 %. */
    DELOAD,
}

data class ProgressionSuggestion(
    val kind: SuggestionKind,
    /** Series de trabajo propuestas para hoy (peso en kg). */
    val sets: List<WorkoutSet>,
    /** Explicación en español (la app la traduce a partir de [kind]). */
    val reason: String,
    val targetReps: Int = 0,
    val stallSessions: Int = 0,
)

/**
 * Sobrecarga progresiva por "doble progresión":
 * - Si la última vez completaste todas las reps en todas las series (y el RPE, si lo anotaste, fue ≤ 9) → +peso.
 * - Si alguna serie se quedó corta → mismo peso, intenta igualar las reps de la primera serie.
 * - Si llevas [stallSessions] sesiones sin mejorar el 1RM estimado → descarga al 90 %.
 */
object Progression {

    fun suggest(
        exercise: Exercise,
        history: List<Workout>,
        incrementKg: Double = 2.5,
        stallSessions: Int = 3,
    ): ProgressionSuggestion? {
        val last = WorkoutStats.previousSets(exercise.id, history).filter { it.type != SetType.WARMUP }
        if (last.isEmpty()) return null

        if (isStalled(exercise.id, history, stallSessions)) {
            val sets = last.map { it.copy(weightKg = roundTo(it.weightKg * 0.9, incrementKg), completed = false, rpe = null) }
            return ProgressionSuggestion(SuggestionKind.DELOAD, sets, "Llevas $stallSessions sesiones sin progresar: descarga al 90 %", stallSessions = stallSessions)
        }

        val targetReps = last.first().reps
        val hitAll = last.all { it.reps >= targetReps }
        val easy = last.all { (it.rpe ?: 8.0) <= 9.0 }

        return when {
            exercise.tracking == TrackingType.DURATION -> {
                val sets = last.map { it.copy(durationSec = it.durationSec + 5, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.ADD_TIME, sets, "+5 s por serie")
            }
            exercise.tracking == TrackingType.REPS_ONLY && last.all { it.weightKg == 0.0 } -> {
                val sets = last.map { it.copy(reps = it.reps + 1, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.ADD_ONE_REP, sets, "+1 repetición por serie")
            }
            hitAll && easy -> {
                val sets = last.map { it.copy(weightKg = it.weightKg + incrementKg, reps = targetReps, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.INCREASE_WEIGHT, sets, "Completaste todas las series: sube el peso")
            }
            !hitAll -> {
                val sets = last.map { it.copy(reps = targetReps, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.ADD_REPS, sets, "Mismo peso: intenta $targetReps reps en todas las series", targetReps = targetReps)
            }
            else -> ProgressionSuggestion(SuggestionKind.REPEAT, last.map { it.copy(completed = false, rpe = null) }, "RPE alto: repite y consolida")
        }
    }

    /** True si el mejor 1RM estimado no ha mejorado en las últimas [sessions] sesiones. */
    fun isStalled(exerciseId: String, history: List<Workout>, sessions: Int = 3): Boolean {
        val points = WorkoutStats.exerciseProgress(exerciseId, history)
        if (points.size < sessions + 1) return false
        val before = points.dropLast(sessions).maxOf { it.bestOneRepMax }
        return points.takeLast(sessions).all { it.bestOneRepMax <= before }
    }

    private fun roundTo(value: Double, step: Double) = Math.round(value / step) * step
}
