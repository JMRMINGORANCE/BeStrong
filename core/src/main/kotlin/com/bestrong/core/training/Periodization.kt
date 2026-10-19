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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Serie prescrita como % del training max (TM). [amrap] = «+»: tantas reps como puedas. */
@Serializable
data class PercentSet(val percent: Double, val reps: Int, val amrap: Boolean = false)

/** Cómo se calcula la carga de un ejercicio del programa. */
@Serializable
sealed interface LoadScheme {
    /** Porcentajes del TM que cambian cada semana del ciclo (5/3/1). [weeks] = una lista de series por semana. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val weeks: List<List<PercentSet>>) : LoadScheme

    /** Mismo % del TM todas las semanas (p. ej. BBB 5 × 10 al 50 %). */
    @Serializable
    @SerialName("fixed")
    data class Fixed(val sets: List<PercentSet>) : LoadScheme

    /**
     * Progresión lineal (GZCLP): sube [incrementKg] cada vez que se completan todas las series;
     * dos fallos seguidos → descarga al 90 %. La primera vez se parte de [startPercent] del 1RM estimado.
     */
    @Serializable
    @SerialName("linear")
    data class Linear(val sets: Int, val reps: Int, val incrementKg: Double, val startPercent: Double, val lastSetAmrap: Boolean = false) : LoadScheme
}

@Serializable
data class PeriodizedItem(
    val exerciseId: String,
    val scheme: LoadScheme,
    val restSeconds: Int = 120,
    val tier: String? = null,
    /** RPE objetivo de las series (programas propios); pasa a la sesión como en una rutina. */
    val targetRpe: Double? = null,
)

@Serializable
data class ProgramDay(val nameEs: String, val nameEn: String, val items: List<PeriodizedItem>)

/**
 * Programa con progresión: semanas con porcentajes del training max y subida automática
 * al acabar cada ciclo, o progresión lineal sesión a sesión.
 * Con [weeksPerCycle] = 1 el training max sube cada semana (Texas Method, Madcow).
 */
@Serializable
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
    /** Creado por el usuario en el editor de programas. */
    val custom: Boolean = false,
) {
    val daysPerWeek: Int get() = days.size

    // Los del usuario tal cual (su «Fuerza» no se traduce); los de la app, en su idioma
    fun name(lang: String) = if (custom) nameEs else com.bestrong.core.i18n.ContentText.pick(nameEs, nameEn, lang)
    fun description(lang: String) = if (custom) descriptionEs else com.bestrong.core.i18n.ContentText.pick(descriptionEs, descriptionEn, lang)

    fun dayName(day: ProgramDay, lang: String) = if (custom) day.nameEs else com.bestrong.core.i18n.ContentText.pick(day.nameEs, day.nameEn, lang)
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
    fun nextSession(
        active: ActiveProgram,
        history: List<Workout>,
        lang: String,
        custom: List<PeriodizedProgram> = emptyList(),
        texts: ProgramTexts = ProgramTexts.forLang(lang),
    ): ProgramSession? {
        val program = PeriodizedPrograms.byId(active.programId) ?: custom.firstOrNull { it.id == active.programId } ?: return null
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
            val amrapNote = if (sets.second) texts.amrapNote else ""
            val tier = item.tier?.let { "$it · " }.orEmpty()
            RoutineExercise(exercise = ex, sets = sets.first, restSeconds = item.restSeconds, notes = (tier + amrapNote).trimEnd(' ', '·'), targetRpe = item.targetRpe)
        }
        val weekLabel = if (program.weeksPerCycle > 1) " · ${texts.weekPrefix}${week + 1}" else ""
        val routine = Routine(
            id = routineId(program.id, dayIndex),
            name = "${program.name(lang)} · ${program.dayName(day, lang)}$weekLabel",
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
            // Dos fallos seguidos con el mismo peso (el fallo que ya provocó una descarga no cuenta otra vez)
            attempts.size >= 2 && attempts[attempts.size - 2].let { (w, ok) -> !ok && w == weight } -> round(weight * 0.9)
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

    private fun fixed(id: String, sets: Int, reps: Int, percent: Double, rest: Int) =
        if (percent > 0) PeriodizedItem(id, LoadScheme.Fixed(List(sets) { PercentSet(percent, reps) }), rest) else PeriodizedItem(id, LoadScheme.Linear(sets, reps, 2.5, 0.0), rest)
    private fun ramp(id: String, percents: List<Double>, reps: Int) = PeriodizedItem(id, LoadScheme.Fixed(percents.map { PercentSet(it, reps) }), 150)
    private val nsunsT1 = LoadScheme.Fixed(
        listOf(
            PercentSet(0.75, 5), PercentSet(0.85, 3), PercentSet(0.95, 1, amrap = true), PercentSet(0.90, 3), PercentSet(0.85, 3),
            PercentSet(0.80, 3), PercentSet(0.75, 5), PercentSet(0.70, 5), PercentSet(0.65, 5, amrap = true),
        ),
    )
    private val nsunsT2 = LoadScheme.Fixed(
        listOf(PercentSet(0.50, 6), PercentSet(0.60, 5), PercentSet(0.70, 3), PercentSet(0.70, 5), PercentSet(0.70, 7), PercentSet(0.70, 4), PercentSet(0.70, 6), PercentSet(0.70, 8)),
    )

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
        // Periodización ondulante diaria: el mismo básico pesado, medio y ligero en la semana
        PeriodizedProgram(
            id = "dup_3day",
            nameEs = "Ondulante (DUP) 3 días",
            nameEn = "Daily undulating (DUP) 3 days",
            descriptionEs = "Sentadilla, banca y peso muerto cada semana en tres registros: fuerza (5 × 3 al 85 %), hipertrofia (4 × 8 al 70 %) y potencia/técnica (6 × 2 al 75 %). El TM sube cada 3 semanas.",
            descriptionEn = "Squat, bench and deadlift every week in three flavours: strength (5 × 3 at 85%), hypertrophy (4 × 8 at 70%) and power/technique (6 × 2 at 75%). TM goes up every 3 weeks.",
            days = listOf(
                ProgramDay("Fuerza", "Strength", listOf(fixed("squat", 5, 3, 0.85, 180), fixed("bench_press", 5, 3, 0.85, 180), fixed("barbell_row", 4, 6, 0.0, 90))),
                ProgramDay("Hipertrofia", "Hypertrophy", listOf(fixed("bench_press", 4, 8, 0.70, 120), fixed("deadlift", 4, 6, 0.70, 150), fixed("pull_up", 4, 8, 0.0, 90))),
                ProgramDay("Potencia", "Power", listOf(fixed("squat", 6, 2, 0.75, 120), fixed("overhead_press", 4, 6, 0.70, 120), fixed("deadlift", 6, 2, 0.75, 150))),
            ),
            weeksPerCycle = 3,
            tmIncrementKg = mapOf("squat" to 5.0, "bench_press" to 2.5, "deadlift" to 5.0, "overhead_press" to 2.5),
            mainLifts = listOf("squat", "bench_press", "deadlift", "overhead_press"),
        ),
        PeriodizedProgram(
            id = "texas_method",
            nameEs = "Texas Method",
            nameEn = "Texas Method",
            descriptionEs = "Volumen el lunes (5 × 5), recuperación el miércoles (ligero) e intensidad el viernes (1 × 5 récord). El TM sube cada semana.",
            descriptionEn = "Volume on Monday (5 × 5), recovery on Wednesday (light) and intensity on Friday (1 × 5 PR). TM goes up every week.",
            days = listOf(
                ProgramDay("Volumen", "Volume", listOf(fixed("squat", 5, 5, 0.80, 180), fixed("bench_press", 5, 5, 0.80, 180), fixed("deadlift", 1, 5, 0.85, 180))),
                ProgramDay("Recuperación", "Recovery", listOf(fixed("squat", 2, 5, 0.64, 120), fixed("overhead_press", 3, 5, 0.80, 120), fixed("chin_up", 3, 8, 0.0, 90))),
                ProgramDay("Intensidad", "Intensity", listOf(fixed("squat", 1, 5, 0.89, 240), fixed("bench_press", 1, 5, 0.89, 240), fixed("barbell_row", 3, 5, 0.0, 120))),
            ),
            weeksPerCycle = 1,
            tmIncrementKg = mapOf("squat" to 2.5, "bench_press" to 1.25, "deadlift" to 2.5, "overhead_press" to 1.25),
            mainLifts = listOf("squat", "bench_press", "deadlift", "overhead_press"),
        ),
        PeriodizedProgram(
            id = "madcow_5x5",
            nameEs = "Madcow 5 × 5",
            nameEn = "Madcow 5 × 5",
            descriptionEs = "Series en rampa: lunes pesado (5 × 5 hasta el 80 %), miércoles ligero y viernes récord de 5 con una de 3 y una de 8. Sube un 2,5 % cada semana.",
            descriptionEn = "Ramping sets: heavy Monday (5 × 5 up to 80%), light Wednesday and a 5-rep PR on Friday plus a triple and a set of 8. Goes up ~2.5% every week.",
            days = listOf(
                ProgramDay("Pesado", "Heavy", listOf(ramp("squat", listOf(0.50, 0.60, 0.70, 0.75, 0.80), 5), ramp("bench_press", listOf(0.50, 0.60, 0.70, 0.75, 0.80), 5), ramp("barbell_row", listOf(0.50, 0.60, 0.70, 0.75, 0.80), 5))),
                ProgramDay("Ligero", "Light", listOf(ramp("squat", listOf(0.50, 0.60, 0.70, 0.70), 5), ramp("overhead_press", listOf(0.55, 0.65, 0.75, 0.80), 5), ramp("deadlift", listOf(0.55, 0.65, 0.75, 0.80), 5))),
                ProgramDay(
                    "Récord",
                    "PR day",
                    listOf(
                        PeriodizedItem("squat", LoadScheme.Fixed(listOf(PercentSet(0.50, 5), PercentSet(0.60, 5), PercentSet(0.70, 5), PercentSet(0.75, 5), PercentSet(0.84, 5), PercentSet(0.88, 3), PercentSet(0.70, 8))), 180),
                        PeriodizedItem("bench_press", LoadScheme.Fixed(listOf(PercentSet(0.50, 5), PercentSet(0.60, 5), PercentSet(0.70, 5), PercentSet(0.75, 5), PercentSet(0.84, 5), PercentSet(0.88, 3), PercentSet(0.70, 8))), 180),
                        PeriodizedItem("barbell_row", LoadScheme.Fixed(listOf(PercentSet(0.50, 5), PercentSet(0.60, 5), PercentSet(0.70, 5), PercentSet(0.75, 5), PercentSet(0.84, 5), PercentSet(0.88, 3), PercentSet(0.70, 8))), 120),
                    ),
                ),
            ),
            weeksPerCycle = 1,
            tmIncrementKg = mapOf("squat" to 2.5, "bench_press" to 2.5, "barbell_row" to 2.5, "deadlift" to 2.5, "overhead_press" to 1.25),
            mainLifts = listOf("squat", "bench_press", "deadlift", "overhead_press", "barbell_row"),
        ),
        PeriodizedProgram(
            id = "nsuns_4day",
            nameEs = "nSuns 5/3/1 LP (4 días)",
            nameEn = "nSuns 5/3/1 LP (4 days)",
            descriptionEs = "Mucho volumen con porcentajes: 9 series del básico (con una de 1+ y otra de máximas reps) y 8 de un secundario. El TM sube cada semana según tus AMRAP.",
            descriptionEn = "High volume with percentages: 9 sets of the main lift (with a 1+ and a max-reps set) and 8 of a secondary. TM goes up every week based on your AMRAPs.",
            days = listOf(
                ProgramDay("Banca + press", "Bench + OHP", listOf(PeriodizedItem("bench_press", nsunsT1, 150, "T1"), PeriodizedItem("overhead_press", nsunsT2, 90, "T2"))),
                ProgramDay("Sentadilla + muerto", "Squat + sumo", listOf(PeriodizedItem("squat", nsunsT1, 180, "T1"), PeriodizedItem("deadlift", nsunsT2, 120, "T2"))),
                ProgramDay("Press + banca", "OHP + incline", listOf(PeriodizedItem("overhead_press", nsunsT1, 150, "T1"), PeriodizedItem("incline_bench_press", nsunsT2, 90, "T2"))),
                ProgramDay("Muerto + frontal", "Deadlift + front squat", listOf(PeriodizedItem("deadlift", nsunsT1, 180, "T1"), PeriodizedItem("front_squat", nsunsT2, 120, "T2"))),
            ),
            weeksPerCycle = 1,
            tmIncrementKg = mapOf("bench_press" to 2.5, "overhead_press" to 2.5, "squat" to 5.0, "deadlift" to 5.0, "incline_bench_press" to 2.5, "front_squat" to 2.5),
            mainLifts = listOf("squat", "bench_press", "deadlift", "overhead_press", "incline_bench_press", "front_squat"),
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Textos que el programa escribe en la sesión, en el idioma de la app (los pone la interfaz desde sus recursos). */
data class ProgramTexts(val amrapNote: String, val weekPrefix: String) {
    companion object {
        val ES = ProgramTexts("Última serie: máximas repeticiones (+)", "S")
        val EN = ProgramTexts("Last set: as many reps as possible (+)", "W")
        fun forLang(lang: String) = if (lang == "en") EN else ES

        private val TIER = Regex("^\\s*T\\d\\b")
        private val REPEATED_DOTS = Regex("\\s*·\\s*(·\\s*)+")

        /**
         * Lo que escribió el usuario en la nota de un ejercicio de programa: se quitan el nivel (T1/T2/T3)
         * y la frase de AMRAP que pone el programa ([generated], en los idiomas que se hayan usado).
         */
        fun userPart(note: String, generated: Collection<String>): String {
            var rest = generated.filter { it.isNotBlank() }.fold(note) { acc, g -> acc.replace(g, "") }
            rest = rest.replace(TIER, "").replace(REPEATED_DOTS, " · ")
            return rest.trim { it.isWhitespace() || it == '·' }
        }
    }
}
