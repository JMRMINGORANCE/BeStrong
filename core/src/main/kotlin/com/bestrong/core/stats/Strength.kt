package com.bestrong.core.stats

import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Nivel de fuerza de un levantamiento (1RM relativo al peso corporal). */
enum class StrengthLevel { BEGINNER, NOVICE, INTERMEDIATE, ADVANCED, ELITE }

data class StrengthStanding(
    val exerciseId: String,
    val oneRepMaxKg: Double,
    val ratio: Double,
    /** null si aún no llega a principiante. */
    val level: StrengthLevel?,
    /** 1RM necesario para el siguiente nivel (null en élite). */
    val nextLevelKg: Double?,
    /** Progreso hacia el siguiente nivel (0..1). */
    val progress: Double,
)

data class Balance(val push: Double, val pull: Double, val upper: Double, val lower: Double) {
    /** Empuje/tirón: > 1,5 o < 0,67 se considera descompensado. */
    val pushPullRatio: Double get() = if (pull <= 0) (if (push > 0) Double.POSITIVE_INFINITY else 1.0) else push / pull
    val upperLowerRatio: Double get() = if (lower <= 0) (if (upper > 0) Double.POSITIVE_INFINITY else 1.0) else upper / lower
    val pushPullImbalanced: Boolean get() = push + pull >= 6 && (pushPullRatio > 1.5 || pushPullRatio < 0.67)
    val upperLowerImbalanced: Boolean get() = upper + lower >= 6 && (upperLowerRatio > 2.5 || upperLowerRatio < 0.4)
}

data class Achievement(val id: String, val current: Double, val target: Double) {
    val unlocked: Boolean get() = current >= target
    val progress: Double get() = if (target <= 0) 1.0 else (current / target).coerceIn(0.0, 1.0)
}

data class PeriodSummary(
    val workouts: Int,
    val volume: Double,
    val sets: Int,
    val durationSec: Long,
    val records: List<PersonalRecord>,
    /** Variación de volumen frente al periodo anterior (fracción; null si el anterior estaba vacío). */
    val volumeChange: Double?,
    val topExercises: List<Pair<String, Double>>,
)

/** Resumen del año: lo del periodo más días entrenados, mejor racha, músculo estrella y mayores subidas de 1RM. */
data class YearSummary(
    val year: Int,
    val period: PeriodSummary,
    val trainingDays: Int,
    /** Semanas seguidas entrenando (la mejor racha dentro del año). */
    val longestWeeklyStreak: Int,
    val topMuscle: MuscleGroup?,
    /** (ejercicio, 1RM estimado de la primera sesión del año, mejor 1RM del año), las mayores subidas primero. */
    val biggestGains: List<Triple<com.bestrong.core.model.Exercise, Double, Double>>,
)

/** Estadísticas avanzadas (fase 3): niveles, equilibrio, logros, resúmenes y curvas. */
object Strength {
    // Umbrales de 1RM / peso corporal para principiante, novato, intermedio, avanzado y élite
    private val MALE = mapOf(
        "squat" to listOf(0.75, 1.25, 1.5, 2.25, 2.75),
        "bench_press" to listOf(0.5, 0.75, 1.25, 1.75, 2.0),
        "deadlift" to listOf(1.0, 1.5, 2.0, 2.5, 3.0),
        "overhead_press" to listOf(0.4, 0.55, 0.8, 1.1, 1.4),
        "barbell_row" to listOf(0.5, 0.75, 1.0, 1.5, 1.75),
    )
    private val FEMALE = mapOf(
        "squat" to listOf(0.5, 0.75, 1.25, 1.5, 2.0),
        "bench_press" to listOf(0.25, 0.5, 0.75, 1.0, 1.5),
        "deadlift" to listOf(0.5, 1.0, 1.25, 1.75, 2.5),
        "overhead_press" to listOf(0.2, 0.35, 0.5, 0.75, 1.0),
        "barbell_row" to listOf(0.25, 0.4, 0.65, 0.9, 1.2),
    )

    val STANDARD_LIFTS: List<String> get() = MALE.keys.toList()

    fun standing(exerciseId: String, oneRepMaxKg: Double, bodyWeightKg: Double, sex: Sex): StrengthStanding? {
        val thresholds = (if (sex == Sex.MALE) MALE else FEMALE)[exerciseId] ?: return null
        if (bodyWeightKg <= 0) return null
        val ratio = oneRepMaxKg / bodyWeightKg
        val reached = thresholds.indexOfLast { ratio >= it }
        val level = if (reached < 0) null else StrengthLevel.entries[reached]
        val next = thresholds.getOrNull(reached + 1)
        val prev = thresholds.getOrNull(reached) ?: 0.0
        val progress = if (next == null) 1.0 else ((ratio - prev) / (next - prev)).coerceIn(0.0, 1.0)
        return StrengthStanding(exerciseId, oneRepMaxKg, ratio, level, next?.let { it * bodyWeightKg }, progress)
    }

    /** Mejor 1RM estimado por ejercicio en todo el historial. */
    fun bestOneRepMax(history: List<Workout>): Map<String, Double> =
        WorkoutStats.personalRecords(history).mapValues { (_, r) -> r[RecordType.BEST_ONE_REP_MAX]?.value ?: 0.0 }

    fun standings(history: List<Workout>, bodyWeightKg: Double, sex: Sex): List<StrengthStanding> {
        val best = bestOneRepMax(history)
        return STANDARD_LIFTS.mapNotNull { id -> best[id]?.takeIf { it > 0 }?.let { standing(id, it, bodyWeightKg, sex) } }
    }

    private val PUSH = setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
    private val PULL = setOf(MuscleGroup.BACK, MuscleGroup.BICEPS, MuscleGroup.TRAPS, MuscleGroup.FOREARMS)
    private val LOWER = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)

    /** Equilibrio por series de trabajo (músculo principal) en los últimos [days] días. */
    fun balance(history: List<Workout>, nowEpochMs: Long, days: Int = 28): Balance {
        val since = nowEpochMs - days * 86_400_000L
        var push = 0.0
        var pull = 0.0
        var lower = 0.0
        history.filter { it.isFinished && it.startedAtEpochMs >= since }.forEach { w ->
            w.exercises.forEach { we ->
                val n = we.completedSets.count { it.type != SetType.WARMUP }.toDouble()
                val m = we.exercise.primaryMuscle
                when {
                    m in PUSH -> push += n
                    m in PULL -> pull += n
                    m in LOWER -> lower += n
                }
            }
        }
        return Balance(push, pull, push + pull, lower)
    }

    /**
     * Volumen efectivo: solo cuentan las series cercanas al fallo.
     * RPE ≥ 7 (o sin anotar) = 1; RPE 6–7 = 0,5; RPE < 6 = 0. Los calentamientos no cuentan.
     */
    fun effectiveSets(workout: Workout): Double = workout.exercises.sumOf { we ->
        we.completedSets.filter { it.type != SetType.WARMUP }.sumOf { s ->
            val rpe = s.rpe
            when {
                rpe == null || rpe >= 7.0 -> 1.0
                rpe >= 6.0 -> 0.5
                else -> 0.0
            }
        }
    }

    /** Línea de tiempo de récords: cada vez que se mejoró una marca (del más antiguo al más reciente). */
    fun recordTimeline(history: List<Workout>, types: Set<RecordType> = setOf(RecordType.BEST_ONE_REP_MAX, RecordType.HEAVIEST_WEIGHT)): List<PersonalRecord> {
        val best = mutableMapOf<Pair<String, RecordType>, Double>()
        val events = mutableListOf<PersonalRecord>()
        history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.forEach { w ->
            w.exercises.forEach { we ->
                val sets = we.completedSets.filter { it.countsForRecords && it.type != SetType.WARMUP }
                if (sets.isEmpty()) return@forEach
                types.forEach { type ->
                    val value = sets.maxOf { recordValueOf(type, it) }
                    val key = we.exercise.id to type
                    val old = best[key]
                    if (value > 0 && (old == null || value > old)) {
                        best[key] = value
                        if (old != null) events += PersonalRecord(we.exercise.id, we.exercise.name, type, value, w.id, w.finishedAtEpochMs ?: w.startedAtEpochMs)
                    }
                }
            }
        }
        return events
    }

    private fun recordValueOf(type: RecordType, set: com.bestrong.core.model.WorkoutSet): Double = when (type) {
        RecordType.HEAVIEST_WEIGHT -> set.weightKg
        RecordType.BEST_ONE_REP_MAX -> set.estimatedOneRepMax
        RecordType.BEST_SET_VOLUME -> set.volume
        RecordType.MOST_REPS -> set.reps.toDouble()
    }

    /** Logros: hitos de constancia, volumen y fuerza. */
    fun achievements(history: List<Workout>, today: LocalDate, zone: ZoneId, bodyWeightKg: Double?): List<Achievement> {
        val done = history.filter { it.isFinished }
        val count = done.size.toDouble()
        val volumeT = done.sumOf { it.totalVolume } / 1000.0
        val sets = done.sumOf { it.completedSetCount }.toDouble()
        val streak = WorkoutStats.weeklyStreak(done, today, zone).toDouble()
        val best = bestOneRepMax(done)
        val list = mutableListOf(
            Achievement("first_workout", count, 1.0),
            Achievement("workouts_10", count, 10.0),
            Achievement("workouts_50", count, 50.0),
            Achievement("workouts_100", count, 100.0),
            Achievement("workouts_250", count, 250.0),
            Achievement("streak_4", streak, 4.0),
            Achievement("streak_12", streak, 12.0),
            Achievement("streak_26", streak, 26.0),
            Achievement("volume_10t", volumeT, 10.0),
            Achievement("volume_100t", volumeT, 100.0),
            Achievement("volume_1000t", volumeT, 1000.0),
            Achievement("sets_1000", sets, 1000.0),
        )
        if (bodyWeightKg != null && bodyWeightKg > 0) {
            list += Achievement("bench_bw", (best["bench_press"] ?: 0.0) / bodyWeightKg, 1.0)
            list += Achievement("squat_1_5bw", (best["squat"] ?: 0.0) / bodyWeightKg, 1.5)
            list += Achievement("deadlift_2bw", (best["deadlift"] ?: 0.0) / bodyWeightKg, 2.0)
        }
        return list
    }

    /**
     * Predicción por regresión lineal de cuándo se alcanzará [target] (p. ej. un 1RM de 100 kg)
     * a partir de puntos (fecha, valor). Null si no hay progreso o pocos datos.
     */
    fun predictDate(points: List<Pair<Long, Double>>, target: Double): Long? {
        if (points.size < 3) return null
        if (points.maxOf { it.second } >= target) return points.first { it.second >= target }.first
        val x0 = points.first().first.toDouble()
        val xs = points.map { (it.first - x0) / 86_400_000.0 }
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx
        if (slope <= 1e-6) return null
        val intercept = my - slope * mx
        val day = (target - intercept) / slope
        if (day > 3 * 365) return null // demasiado lejos para ser útil
        return (x0 + day * 86_400_000.0).toLong()
    }

    /** Resumen de un periodo [from, to) frente al periodo anterior de la misma duración. */
    fun summary(history: List<Workout>, from: Long, to: Long, all: List<Workout> = history): PeriodSummary {
        val done = history.filter { it.isFinished }
        val inPeriod = done.filter { it.startedAtEpochMs in from until to }
        val before = done.filter { it.startedAtEpochMs in (from - (to - from)) until from }
        val volume = inPeriod.sumOf { it.totalVolume }
        val prevVolume = before.sumOf { it.totalVolume }
        val ids = inPeriod.mapTo(HashSet()) { it.id }
        val byWorkout = WorkoutStats.newRecordsInEach(all + inPeriod.filter { w -> all.none { it.id == w.id } }) { it.id in ids }
        val records = inPeriod.flatMap { byWorkout[it.id].orEmpty() }
        val top = inPeriod.flatMap { it.exercises }.groupBy { it.exercise.id }
            .mapValues { (_, l) -> l.sumOf { we -> we.completedSets.sumOf { it.volume } } }
            .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
        return PeriodSummary(
            workouts = inPeriod.size,
            volume = volume,
            sets = inPeriod.sumOf { it.completedSetCount },
            durationSec = inPeriod.sumOf { it.durationSeconds() },
            records = records,
            volumeChange = if (prevVolume > 0) (volume - prevVolume) / prevVolume else null,
            topExercises = top,
        )
    }

    fun weekSummary(history: List<Workout>, day: LocalDate, zone: ZoneId): PeriodSummary {
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Fin de semana por calendario: con cambio de hora la semana no dura 7 × 24 h
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return summary(history, start, end)
    }

    /** Resumen de [year] (null si no entrenaste ese año). */
    fun yearSummary(history: List<Workout>, year: Int, zone: ZoneId): YearSummary? {
        val from = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val inYear = history.filter { it.isFinished && it.startedAtEpochMs in from until to }.sortedBy { it.startedAtEpochMs }
        if (inYear.isEmpty()) return null
        val days = inYear.map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }.toSet()
        val weeks = days.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedSet()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        weeks.forEach { w ->
            run = if (prev?.plusWeeks(1) == w) run + 1 else 1
            best = maxOf(best, run)
            prev = w
        }
        // 1RM de la primera sesión del año frente al mejor del año, por ejercicio
        val firstAndBest = mutableMapOf<String, Pair<Double, Double>>()
        val byId = mutableMapOf<String, com.bestrong.core.model.Exercise>()
        inYear.forEach { w ->
            w.exercises.forEach { we ->
                val e1rm = we.completedSets.filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
                if (e1rm <= 0) return@forEach
                byId[we.exercise.id] = we.exercise
                val cur = firstAndBest[we.exercise.id]
                firstAndBest[we.exercise.id] = if (cur == null) e1rm to e1rm else cur.first to maxOf(cur.second, e1rm)
            }
        }
        val gains = firstAndBest.filter { (_, v) -> v.second > v.first + 1e-6 }
            .entries.sortedByDescending { it.value.second - it.value.first }.take(3)
            .map { (id, v) -> Triple(byId.getValue(id), v.first, v.second) }
        return YearSummary(
            year = year,
            period = summary(history, from, to),
            trainingDays = days.size,
            longestWeeklyStreak = best,
            topMuscle = WorkoutStats.setsPerMuscle(inYear, 0).keys.firstOrNull(),
            biggestGains = gains,
        )
    }

    fun monthSummary(history: List<Workout>, month: YearMonth, zone: ZoneId): PeriodSummary {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return summary(history, from, to)
    }

    /** Días desde la última vez que se entrenó cada músculo (como principal). Null = nunca. */
    fun daysSinceTrained(history: List<Workout>, today: LocalDate, zone: ZoneId): Map<MuscleGroup, Int?> {
        val last = mutableMapOf<MuscleGroup, LocalDate>()
        history.filter { it.isFinished }.forEach { w ->
            val d = Instant.ofEpochMilli(w.startedAtEpochMs).atZone(zone).toLocalDate()
            w.exercises.filter { we -> we.completedSets.isNotEmpty() }.forEach { we ->
                val m = we.exercise.primaryMuscle
                if (last[m]?.isBefore(d) != false) last[m] = d
            }
        }
        return MuscleGroup.entries.filter { it != MuscleGroup.CARDIO && it != MuscleGroup.FULL_BODY }
            .associateWith { m -> last[m]?.let { ChronoUnit.DAYS.between(it, today).toInt() } }
    }

    /**
     * Fatiga estimada (0..1) por músculo para el mapa de agujetas: series de las últimas 72 h,
     * pesando más las recientes; 10 series ayer ≈ fatiga alta.
     */
    fun soreness(history: List<Workout>, nowEpochMs: Long): Map<MuscleGroup, Double> {
        val result = mutableMapOf<MuscleGroup, Double>()
        history.filter { it.isFinished && nowEpochMs - it.startedAtEpochMs in 0..(72 * 3_600_000L) }.forEach { w ->
            val hours = (nowEpochMs - w.startedAtEpochMs) / 3_600_000.0
            val decay = (1.0 - hours / 72.0).coerceIn(0.0, 1.0)
            w.exercises.forEach { we ->
                val n = we.completedSets.count { it.type != SetType.WARMUP }
                result[we.exercise.primaryMuscle] = (result[we.exercise.primaryMuscle] ?: 0.0) + n * decay
                we.exercise.secondaryMuscles.forEach { m -> result[m] = (result[m] ?: 0.0) + n * decay * 0.5 }
            }
        }
        return result.mapValues { (it.value / 10.0).coerceIn(0.0, 1.0) }
    }

    /** Curva de fuerza: mejor peso conseguido para cada número de reps (1..[maxReps]). */
    fun strengthCurve(exerciseId: String, history: List<Workout>, maxReps: Int = 15): Map<Int, Double> {
        val best = sortedMapOf<Int, Double>()
        history.filter { it.isFinished }.forEach { w ->
            w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets }
                .filter { it.countsForRecords && it.type != SetType.WARMUP && it.reps in 1..maxReps && it.weightKg > 0 }
                .forEach { s -> if (s.weightKg > (best[s.reps] ?: 0.0)) best[s.reps] = s.weightKg }
        }
        return best
    }

    /** Relación peso corporal / fuerza: (fecha, peso corporal, 1RM del ejercicio) cuando hay ambos datos cercanos. */
    fun bodyWeightVsStrength(
        progress: List<ExerciseProgressPoint>,
        bodyWeights: List<Pair<Long, Double>>,
        maxGapDays: Int = 14,
    ): List<Triple<Long, Double, Double>> = progress.mapNotNull { p ->
        val nearest = bodyWeights.minByOrNull { kotlin.math.abs(it.first - p.dateEpochMs) } ?: return@mapNotNull null
        if (kotlin.math.abs(nearest.first - p.dateEpochMs) > maxGapDays * 86_400_000L) null else Triple(p.dateEpochMs, nearest.second, p.bestOneRepMax)
    }
}
