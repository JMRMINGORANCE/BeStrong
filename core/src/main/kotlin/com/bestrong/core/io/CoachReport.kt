package com.bestrong.core.io

import com.bestrong.core.model.Equipment
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
        dumbbellTotal: Boolean = false,
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        return history.filter { it.isFinished && it.startedAtEpochMs >= fromEpochMs }.sortedBy { it.startedAtEpochMs }
            .joinToString("\n\n") { w ->
                val header = "▶ " + dateFmt.format(Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone)) + " · " + w.name +
                    " (" + (w.durationSeconds() / 60) + " min · " + unit.label + ")"
                val lines = w.exercises.filter { it.completedSets.isNotEmpty() }.map { we ->
                    // Con «suma de las dos» el peso se da como en la app (se guarda por mancuerna)
                    val factor = if (dumbbellTotal && we.exercise.equipment == Equipment.DUMBBELL && we.exercise.tracking == TrackingType.WEIGHT_REPS) 2 else 1
                    val sets = we.completedSets.joinToString(" / ") { set(it.copy(weightKg = it.weightKg * factor), we.exercise.tracking, unit) }
                    "• " + exerciseName(we.exercise) + ": " + sets + we.notes.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                }
                (listOf(header) + lines + listOfNotNull(w.notes.takeIf { it.isNotBlank() }?.let { "✎ $it" })).joinToString("\n")
            }
    }

    private fun set(s: WorkoutSet, tracking: TrackingType, unit: WeightUnit): String {
        // Unilaterales: izquierda|derecha, como en la app («/» ya separa series)
        val reps = s.repsRight?.let { "${s.reps}|$it" } ?: "${s.reps}"
        val body = when (tracking) {
            TrackingType.WEIGHT_REPS -> "${unit.format(s.weightKg)}×$reps"
            // Lastre (+) o asistencia (−): sin peso, solo las reps
            TrackingType.REPS_ONLY -> when {
                s.weightKg > 0 -> "+${unit.format(s.weightKg)}×$reps"
                s.weightKg < 0 -> "−${unit.format(-s.weightKg)}×$reps"
                else -> reps
            }
            TrackingType.DURATION -> "${s.durationSec}s"
            TrackingType.DISTANCE -> "${s.distanceM.toInt()}m"
            TrackingType.CARDIO -> "${s.durationSec / 60}min" + if (s.distanceM > 0) " " + decimal(s.distanceM / 1000) + "km" else ""
        }
        // Misma abreviatura que en el historial de la app
        val warm = if (s.type == SetType.WARMUP) SetType.WARMUP.short + ":" else ""
        return warm + body + extras(s)
    }

    /** RPE y nota de la serie: « @8,5 (lento)». */
    private fun extras(s: WorkoutSet): String = (s.rpe?.let { " @" + decimal(it) }.orEmpty()) + (if (s.note.isNotBlank()) " (${s.note})" else "")

    /** Un decimal como mucho y con coma, igual que los pesos. */
    private fun decimal(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().replace('.', ',')
    }
}
