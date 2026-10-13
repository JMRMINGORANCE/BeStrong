package com.bestrong.core.io

import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.util.WeightUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Resumen en texto para mandar al entrenador (WhatsApp, correo…): cada entreno del periodo con
 * sus series, RPE y notas. [exerciseName] da el nombre en el idioma de la app.
 */
object CoachReport {
    fun text(
        history: List<Workout>,
        fromEpochMs: Long,
        unit: WeightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        exerciseName: (com.bestrong.core.model.Exercise) -> String = { it.name },
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        return history.filter { it.isFinished && it.startedAtEpochMs >= fromEpochMs }.sortedBy { it.startedAtEpochMs }
            .joinToString("\n\n") { w ->
                val header = "▶ " + dateFmt.format(Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone)) + " · " + w.name +
                    " (" + (w.durationSeconds() / 60) + " min)"
                val lines = w.exercises.filter { it.completedSets.isNotEmpty() }.map { we ->
                    val sets = we.completedSets.joinToString(", ") { set(it, we.exercise.tracking, unit) }
                    "• " + exerciseName(we.exercise) + ": " + sets + we.notes.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                }
                (listOf(header) + lines + listOfNotNull(w.notes.takeIf { it.isNotBlank() }?.let { "✎ $it" })).joinToString("\n")
            }
    }

    private fun set(s: WorkoutSet, tracking: TrackingType, unit: WeightUnit): String {
        val body = when (tracking) {
            TrackingType.WEIGHT_REPS -> "${unit.format(s.weightKg)}×${s.reps}"
            TrackingType.REPS_ONLY -> if (s.weightKg > 0) "+${unit.format(s.weightKg)}×${s.reps}" else "${s.reps}"
            TrackingType.DURATION -> "${s.durationSec}s"
            TrackingType.DISTANCE -> "${s.distanceM.toInt()}m"
            TrackingType.CARDIO -> "${s.durationSec / 60}min" + if (s.distanceM > 0) " ${"%.1f".format(Locale.ROOT, s.distanceM / 1000)}km" else ""
        }
        val warm = if (s.type == SetType.WARMUP) "C:" else ""
        return warm + body + (s.rpe?.let { " @" + it.toString().removeSuffix(".0") }.orEmpty()) + (if (s.note.isNotBlank()) " (${s.note})" else "")
    }
}
