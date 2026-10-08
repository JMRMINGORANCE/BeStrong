package com.bestrong.core.stats

import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class RecordType(val label: String) {
    HEAVIEST_WEIGHT("Peso máximo"),
    BEST_ONE_REP_MAX("Mejor 1RM estimado"),
    BEST_SET_VOLUME("Mejor volumen en una serie"),
    MOST_REPS("Más repeticiones"),
}

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val type: RecordType,
    val value: Double,
    val workoutId: String,
    val dateEpochMs: Long,
)

data class ExerciseProgressPoint(
    val dateEpochMs: Long,
    val bestOneRepMax: Double,
    val heaviestWeight: Double,
    val volume: Double,
    val bestReps: Int,
)

data class WeekBucket(val weekStart: LocalDate, val workouts: Int, val volume: Double, val durationSec: Long)

enum class VolumeStatus { NONE, LOW, OPTIMAL, HIGH }

/** Series semanales recomendadas por músculo (rango habitual en hipertrofia: 10–20). */
object MuscleTargets {
    const val MIN_WEEKLY_SETS = 10.0
    const val MAX_WEEKLY_SETS = 20.0

    fun status(sets: Double): VolumeStatus = when {
        sets <= 0.0 -> VolumeStatus.NONE
        sets < MIN_WEEKLY_SETS -> VolumeStatus.LOW
        sets <= MAX_WEEKLY_SETS -> VolumeStatus.OPTIMAL
        else -> VolumeStatus.HIGH
    }
}

/** Cálculos sobre el historial. Todas las funciones son puras: [history] = entrenamientos terminados. */
object WorkoutStats {

    private fun Workout.finishedAt(): Long = finishedAtEpochMs ?: startedAtEpochMs

    /** Series de la última vez que se hizo el ejercicio (columna "Anterior" de Hevy). */
    fun previousSets(exerciseId: String, history: List<Workout>, excludeWorkoutId: String? = null): List<WorkoutSet> =
        history.asSequence()
            .filter { it.id != excludeWorkoutId && it.isFinished }
            .sortedByDescending { it.startedAtEpochMs }
            .mapNotNull { w -> w.exercises.firstOrNull { it.exercise.id == exerciseId }?.completedSets?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
            .orEmpty()

    private fun recordValue(type: RecordType, set: WorkoutSet): Double = when (type) {
        RecordType.HEAVIEST_WEIGHT -> set.weightKg
        RecordType.BEST_ONE_REP_MAX -> set.estimatedOneRepMax
        RecordType.BEST_SET_VOLUME -> set.volume
        RecordType.MOST_REPS -> set.reps.toDouble()
    }

    /** Mejores marcas por ejercicio y tipo. */
    fun personalRecords(history: List<Workout>): Map<String, Map<RecordType, PersonalRecord>> {
        val result = mutableMapOf<String, MutableMap<RecordType, PersonalRecord>>()
        history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.forEach { w ->
            w.exercises.forEach { we ->
                we.completedSets.filter { it.type != SetType.WARMUP }.forEach { set ->
                    RecordType.entries.forEach { type ->
                        val value = recordValue(type, set)
                        val current = result[we.exercise.id]?.get(type)
                        if (value > 0 && (current == null || value > current.value)) {
                            result.getOrPut(we.exercise.id) { mutableMapOf() }[type] =
                                PersonalRecord(we.exercise.id, we.exercise.name, type, value, w.id, w.finishedAt())
                        }
                    }
                }
            }
        }
        return result
    }

    /** Récords que [workout] bate respecto al historial anterior. */
    fun newRecordsIn(workout: Workout, history: List<Workout>): List<PersonalRecord> {
        val before = personalRecords(history.filter { it.id != workout.id && it.startedAtEpochMs < workout.startedAtEpochMs })
        val records = mutableListOf<PersonalRecord>()
        workout.exercises.forEach { we ->
            val sets = we.completedSets.filter { it.type != SetType.WARMUP }
            if (sets.isEmpty()) return@forEach
            val previous = before[we.exercise.id] ?: return@forEach // la primera vez no cuenta como récord
            RecordType.entries.forEach { type ->
                val best = sets.maxOf { recordValue(type, it) }
                val old = previous[type]?.value ?: 0.0
                if (best > old && best > 0) {
                    records += PersonalRecord(we.exercise.id, we.exercise.name, type, best, workout.id, workout.finishedAt())
                }
            }
        }
        return records
    }

    fun exerciseProgress(exerciseId: String, history: List<Workout>): List<ExerciseProgressPoint> =
        history.filter { it.isFinished }
            .sortedBy { it.startedAtEpochMs }
            .mapNotNull { w ->
                val sets = w.exercises.filter { it.exercise.id == exerciseId }
                    .flatMap { it.completedSets }.filter { it.type != SetType.WARMUP }
                if (sets.isEmpty()) {
                    null
                } else {
                    ExerciseProgressPoint(
                        dateEpochMs = w.startedAtEpochMs,
                        bestOneRepMax = sets.maxOf { it.estimatedOneRepMax },
                        heaviestWeight = sets.maxOf { it.weightKg },
                        volume = sets.sumOf { it.volume },
                        bestReps = sets.maxOf { it.reps },
                    )
                }
            }

    fun weekStart(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Últimas [weeks] semanas (de más antigua a más reciente), incluidas las vacías. */
    fun weeklySummary(history: List<Workout>, weeks: Int, today: LocalDate, zone: ZoneId): List<WeekBucket> {
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val grouped = history.filter { it.isFinished }.groupBy { weekStart(it.startedAtEpochMs, zone) }
        return (weeks - 1 downTo 0).map { offset ->
            val start = currentWeek.minusWeeks(offset.toLong())
            val ws = grouped[start].orEmpty()
            WeekBucket(start, ws.size, ws.sumOf { it.totalVolume }, ws.sumOf { it.durationSeconds() })
        }
    }

    /**
     * Racha semanal: semanas consecutivas con al menos un entrenamiento. La semana actual
     * sin entrenar todavía no rompe la racha.
     */
    fun weeklyStreak(history: List<Workout>, today: LocalDate, zone: ZoneId): Int {
        val weeksTrained = history.filter { it.isFinished }.map { weekStart(it.startedAtEpochMs, zone) }.toSet()
        var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (week !in weeksTrained) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeksTrained) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /** Días con entrenamiento (para el calendario). */
    fun trainingDays(history: List<Workout>, zone: ZoneId): Set<LocalDate> =
        history.filter { it.isFinished }
            .map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
            .toSet()

    /** Series por músculo de la semana (lunes a domingo) que contiene [day]. */
    fun weeklySetsPerMuscle(history: List<Workout>, day: LocalDate, zone: ZoneId): Map<MuscleGroup, Double> {
        val start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(7)
        val inWeek = history.filter {
            val d = Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate()
            !d.isBefore(start) && d.isBefore(end)
        }
        return setsPerMuscle(inWeek, 0)
    }

    /** Series efectivas por grupo muscular (el principal cuenta 1, los secundarios 0,5). */
    fun setsPerMuscle(history: List<Workout>, sinceEpochMs: Long): Map<MuscleGroup, Double> {
        val result = mutableMapOf<MuscleGroup, Double>()
        history.filter { it.isFinished && it.startedAtEpochMs >= sinceEpochMs }.forEach { w ->
            w.exercises.forEach { we ->
                val n = we.completedSets.count { it.type != SetType.WARMUP }.toDouble()
                if (n == 0.0) return@forEach
                result.merge(we.exercise.primaryMuscle, n, Double::plus)
                we.exercise.secondaryMuscles.forEach { result.merge(it, n * 0.5, Double::plus) }
            }
        }
        return result.toList().sortedByDescending { it.second }.toMap()
    }
}
