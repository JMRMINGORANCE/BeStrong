package com.bestrong.core.training

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlinx.serialization.Serializable

/** Serie prescrita como % del training max (TM). [amrap] = «+»: tantas reps como puedas. */
data class PercentSet(val percent: Double, val reps: Int, val amrap: Boolean = false)

/** Cómo se calcula la carga de un ejercicio del programa. */
sealed interface LoadScheme {
    /** Porcentajes del TM que cambian cada semana del ciclo (5/3/1). [weeks] = una lista de series por semana. */
    data class Weekly(val weeks: List<List<PercentSet>>) : LoadScheme

    /** Mismo % del TM todas las semanas (p. ej. BBB 5 × 10 al 50 %). */
    data class Fixed(val sets: List<PercentSet>) : LoadScheme

    /**
     * Progresión lineal (GZCLP): sube [incrementKg] cada vez que se completan todas las series;
     * dos fallos seguidos → descarga al 90 %. La primera vez se parte de [startPercent] del 1RM estimado.
     */
    data class Linear(val sets: Int, val reps: Int, val incrementKg: Double, val startPercent: Double, val lastSetAmrap: Boolean = false) : LoadScheme
}

data class PeriodizedItem(val exerciseId: String, val scheme: LoadScheme, val restSeconds: Int = 120, val tier: String? = null)

data class ProgramDay(val nameEs: String, val nameEn: String, val items: List<PeriodizedItem>)

/**
 * Programa con progresión: semanas con porcentajes del training max y subida automática
 * al acabar cada ciclo, o progresión lineal sesión a sesión.
 */
data class PeriodizedProgram(
    val id: String,
    val nameEs: String,
    val nameEn: String,
    val descriptionEs: String,
    val descriptionEn: String,
    val days: List<ProgramDay>,
    /** Semanas por ciclo (1 = no hay ciclo; solo progresión lineal). */
    val weeksPerCycle: Int,
    /** Subida del TM al terminar un ciclo, por ejercicio (kg). */
    val tmIncrementKg: Map<String, Double> = emptyMap(),
    /** Ejercicios que necesitan training max. */
    val mainLifts: List<String> = emptyList(),
) {
    val daysPerWeek: Int get() = days.size
    fun name(lang: String) = if (lang == "en") nameEn else nameEs
    fun description(lang: String) = if (lang == "en") descriptionEn else descriptionEs
}

/** Programa activo del usuario: qué programa, desde cuándo y con qué training max. */
@Serializable
data class ActiveProgram(
    val programId: String,
    val startedAtEpochMs: Long,
    /** Training max inicial por ejercicio (kg). */
    val trainingMaxKg: Map<String, Double> = emptyMap(),
    /** Sesiones por semana que el usuario se propone (para la adherencia). */
    val weeklyGoal: Int = 3,
)

/** Lo que toca hoy dentro del programa. */
data class ProgramSession(
    val program: PeriodizedProgram,
    val dayIndex: Int,
    /** Semana dentro del ciclo, empezando en 1. */
    val week: Int,
    /** Ciclo, empezando en 1. */
    val cycle: Int,
    val completedSessions: Int,
    val routine: Routine,
)

object Periodization {
    const val ROUTINE_PREFIX = "program:"
    private const val PLATE_STEP = 2.5

    fun routineId(programId: String, dayIndex: Int) = "$ROUTINE_PREFIX$programId:$dayIndex"

    /** Entrenos del historial hechos dentro del programa activo. */
    fun sessionsDone(active: ActiveProgram, history: List<Workout>): List<Workout> =
        history.filter { it.routineId?.startsWith("$ROUTINE_PREFIX${active.programId}:") == true && it.startedAtEpochMs >= active.startedAtEpochMs }
            .sortedBy { it.startedAtEpochMs }

    /** Training max sugerido: 90 % del mejor 1RM estimado (redondeado a 2,5 kg). */
    fun suggestedTrainingMax(exerciseId: String, history: List<Workout>): Double? =
        WorkoutStats.personalRecords(history)[exerciseId]?.get(RecordType.BEST_ONE_REP_MAX)?.value?.let { round(it * 0.9) }

    fun round(kg: Double, step: Double = PLATE_STEP): Double = floor(kg / step + 0.5) * step

    /** Siguiente sesión del programa (día, semana, ciclo y cargas calculadas). */
    fun nextSession(active: ActiveProgram, history: List<Workout>, lang: String): ProgramSession? {
        val program = PeriodizedPrograms.byId(active.programId) ?: return null
        val done = sessionsDone(active, history)
        val n = done.size
        val dayIndex = n % program.days.size
        val sessionsPerCycle = program.days.size * program.weeksPerCycle
        val cycle = n / sessionsPerCycle
        val week = (n % sessionsPerCycle) / program.days.size
        val day = program.days[dayIndex]
        val exercises = day.items.mapNotNull { item ->
            val ex = ExerciseLibrary.byId(item.exerciseId) ?: return@mapNotNull null
            val sets = setsFor(item, program, active, cycle, week, done)
            val amrapNote = if (sets.second) (if (lang == "en") "Last set: as many reps as possible (+)" else "Última serie: máximas repeticiones (+)") else ""
            val tier = item.tier?.let { "$it · " }.orEmpty()
            RoutineExercise(exercise = ex, sets = sets.first, restSeconds = item.restSeconds, notes = (tier + amrapNote).trimEnd(' ', '·'))
        }
        val weekLabel = if (program.weeksPerCycle > 1) (if (lang == "en") " · W${week + 1}" else " · S${week + 1}") else ""
        val routine = Routine(
            id = routineId(program.id, dayIndex),
            name = "${program.name(lang)} · ${if (lang == "en") day.nameEn else day.nameEs}$weekLabel",
            exercises = exercises,
        )
        return ProgramSession(program, dayIndex, week + 1, cycle + 1, n, routine)
    }

    private fun trainingMax(exerciseId: String, program: PeriodizedProgram, active: ActiveProgram, cycle: Int): Double {
        val base = active.trainingMaxKg[exerciseId] ?: 0.0
        return base + cycle * (program.tmIncrementKg[exerciseId] ?: 0.0)
    }

    /** Series de un ejercicio y si la última es AMRAP. */
    private fun setsFor(item: PeriodizedItem, program: PeriodizedProgram, active: ActiveProgram, cycle: Int, week: Int, done: List<Workout>): Pair<List<RoutineSet>, Boolean> =
        when (val s = item.scheme) {
            is LoadScheme.Weekly -> {
                val tm = trainingMax(item.exerciseId, program, active, cycle)
                val sets = s.weeks[week % s.weeks.size]
                sets.map { RoutineSet(weightKg = round(tm * it.percent), reps = it.reps) } to sets.any { it.amrap }
            }
            is LoadScheme.Fixed -> {
                val tm = trainingMax(item.exerciseId, program, active, cycle)
                s.sets.map { RoutineSet(weightKg = round(tm * it.percent), reps = it.reps) } to s.sets.any { it.amrap }
            }
            is LoadScheme.Linear -> {
                // Solo cuentan las sesiones de los días donde este ejercicio tiene el mismo nivel (T1/T2/T3)
                val dayIds = program.days.indices.filter { d -> program.days[d].items.any { it.exerciseId == item.exerciseId && it.scheme == s } }
                    .map { routineId(program.id, it) }.toSet()
                val weight = linearWeight(item.exerciseId, s, active, done.filter { it.routineId in dayIds })
                List(s.sets) { RoutineSet(weightKg = weight, reps = s.reps) } to s.lastSetAmrap
            }
        }

    /** Peso de la próxima sesión en progresión lineal según las dos últimas veces que se hizo. */
    internal fun linearWeight(exerciseId: String, s: LoadScheme.Linear, active: ActiveProgram, done: List<Workout>): Double {
        val attempts = done.mapNotNull { w -> w.exercises.lastOrNull { it.exercise.id == exerciseId } }
            .map { we ->
                val work = we.sets.filter { it.type != SetType.WARMUP }
                val weight = work.maxOfOrNull { it.weightKg } ?: 0.0
                val success = work.size >= s.sets && work.take(s.sets).all { it.completed && it.reps >= s.reps }
                weight to success
            }
        val last = attempts.lastOrNull()
        if (last == null) {
            val tm = active.trainingMaxKg[exerciseId] ?: return 0.0
            // El TM es el 90 % del 1RM: se convierte al porcentaje de inicio del 1RM
            return round(tm / 0.9 * s.startPercent)
        }
        val (weight, success) = last
        return when {
            success -> weight + s.incrementKg
            attempts.size >= 2 && !attempts[attempts.size - 2].second -> round(weight * 0.9)
            else -> weight
        }
    }

    /** Días entrenados de la semana de [day] (lunes a domingo). */
    fun trainedDaysOfWeek(history: List<Workout>, day: LocalDate, zone: ZoneId): Set<DayOfWeek> {
        val start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return history.map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
            .filter { !it.isBefore(start) && it.isBefore(start.plusDays(7)) }
            .map { it.dayOfWeek }.toSet()
    }

    /**
     * Adherencia de las últimas [weeks] semanas completas: sesiones hechas / objetivo (máx. 100 % por semana).
     * Devuelve un valor entre 0 y 1, o null si no hay semanas que evaluar.
     */
    fun adherence(history: List<Workout>, weeklyGoal: Int, today: LocalDate, zone: ZoneId, weeks: Int = 8, since: LocalDate? = null): Double? {
        if (weeklyGoal <= 0) return null
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dates = history.map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
        val ratios = (1..weeks).mapNotNull { back ->
            val start = thisMonday.minusWeeks(back.toLong())
            if (since != null && start.plusDays(6).isBefore(since)) return@mapNotNull null
            val count = dates.count { !it.isBefore(start) && it.isBefore(start.plusDays(7)) }
            minOf(1.0, count.toDouble() / weeklyGoal)
        }
        return if (ratios.isEmpty()) null else ratios.average()
    }
}

object PeriodizedPrograms {
    private fun w(vararg s: PercentSet) = s.toList()
    private val fiveThreeOne = LoadScheme.Weekly(
        listOf(
            w(PercentSet(0.65, 5), PercentSet(0.75, 5), PercentSet(0.85, 5, amrap = true)),
            w(PercentSet(0.70, 3), PercentSet(0.80, 3), PercentSet(0.90, 3, amrap = true)),
            w(PercentSet(0.75, 5), PercentSet(0.85, 3), PercentSet(0.95, 1, amrap = true)),
            w(PercentSet(0.40, 5), PercentSet(0.50, 5), PercentSet(0.60, 5)), // descarga
        ),
    )
    private val bbb = LoadScheme.Fixed(List(5) { PercentSet(0.50, 10) })

    private fun t1(id: String, lower: Boolean) = PeriodizedItem(id, LoadScheme.Linear(5, 3, if (lower) 5.0 else 2.5, 0.85, lastSetAmrap = true), 180, "T1")
    private fun t2(id: String, lower: Boolean) = PeriodizedItem(id, LoadScheme.Linear(3, 10, if (lower) 5.0 else 2.5, 0.65), 120, "T2")
    private fun t3(id: String) = PeriodizedItem(id, LoadScheme.Linear(3, 15, 2.5, 0.0, lastSetAmrap = true), 60, "T3")

    val all: List<PeriodizedProgram> = listOf(
        PeriodizedProgram(
            id = "wendler_531_bbb",
            nameEs = "5/3/1 Boring But Big",
            nameEn = "5/3/1 Boring But Big",
            descriptionEs = "Ciclos de 4 semanas (5s, 3s, 5/3/1 y descarga) sobre tu training max, más 5 × 10 al 50 %. El TM sube solo al acabar cada ciclo.",
            descriptionEn = "4-week cycles (5s, 3s, 5/3/1 and deload) on your training max, plus 5 × 10 at 50%. The TM goes up automatically after each cycle.",
            days = listOf(
                ProgramDay("Press militar", "Overhead press", listOf(PeriodizedItem("overhead_press", fiveThreeOne, 180), PeriodizedItem("overhead_press", bbb, 90), PeriodizedItem("lat_pulldown", LoadScheme.Linear(5, 10, 2.5, 0.0), 90))),
                ProgramDay("Peso muerto", "Deadlift", listOf(PeriodizedItem("deadlift", fiveThreeOne, 180), PeriodizedItem("deadlift", bbb, 120), PeriodizedItem("hanging_leg_raise", LoadScheme.Linear(5, 12, 0.0, 0.0), 60))),
                ProgramDay("Press banca", "Bench press", listOf(PeriodizedItem("bench_press", fiveThreeOne, 180), PeriodizedItem("bench_press", bbb, 90), PeriodizedItem("db_row", LoadScheme.Linear(5, 10, 2.5, 0.0), 90))),
                ProgramDay("Sentadilla", "Squat", listOf(PeriodizedItem("squat", fiveThreeOne, 180), PeriodizedItem("squat", bbb, 120), PeriodizedItem("leg_curl", LoadScheme.Linear(5, 12, 2.5, 0.0), 60))),
            ),
            weeksPerCycle = 4,
            tmIncrementKg = mapOf("overhead_press" to 2.5, "bench_press" to 2.5, "deadlift" to 5.0, "squat" to 5.0),
            mainLifts = listOf("squat", "bench_press", "deadlift", "overhead_press"),
        ),
        PeriodizedProgram(
            id = "gzclp",
            nameEs = "GZCLP",
            nameEn = "GZCLP",
            descriptionEs = "Progresión lineal por niveles: T1 pesado (5 × 3+), T2 volumen (3 × 10) y T3 accesorios (3 × 15+). Sube carga cada vez que completas las series; dos fallos seguidos → descarga al 90 %.",
            descriptionEn = "Tiered linear progression: heavy T1 (5 × 3+), volume T2 (3 × 10) and T3 accessories (3 × 15+). Weight goes up every time you complete all sets; two misses in a row → deload to 90%.",
            days = listOf(
                ProgramDay("A1", "A1", listOf(t1("squat", true), t2("bench_press", false), t3("lat_pulldown"))),
                ProgramDay("B1", "B1", listOf(t1("overhead_press", false), t2("deadlift", true), t3("db_row"))),
                ProgramDay("A2", "A2", listOf(t1("bench_press", false), t2("squat", true), t3("lat_pulldown"))),
                ProgramDay("B2", "B2", listOf(t1("deadlift", true), t2("overhead_press", false), t3("db_row"))),
            ),
            weeksPerCycle = 1,
            mainLifts = listOf("squat", "bench_press", "deadlift", "overhead_press"),
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
