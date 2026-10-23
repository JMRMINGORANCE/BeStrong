package com.bestrong.core.training

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.WorkoutStats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

// --- Fatiga, mesociclos y descargas ---------------------------------------------------------

/** Señales de fatiga acumulada en las últimas semanas. */
data class FatigueReport(
    /** Básicos cuyo 1RM estimado lleva 3 sesiones bajando o estancado. */
    val stalledLifts: List<String>,
    /** RPE medio de las series de trabajo de los últimos 7 días (null si no hay RPE). */
    val avgRpe: Double?,
    /** Energía media (1–5) y dolor medio (1–5) de las valoraciones recientes. */
    val avgEnergy: Double?,
    val avgPain: Double?,
) {
    /** Número de señales activas: estancamiento, RPE muy alto, poca energía o dolor. */
    val signals: Int
        get() = listOf(
            stalledLifts.size >= 2,
            (avgRpe ?: 0.0) >= 9.0,
            (avgEnergy ?: 5.0) <= 2.0,
            (avgPain ?: 1.0) >= 3.5,
        ).count { it }

    /** Con dos o más señales conviene una semana de descarga. */
    val deloadRecommended: Boolean get() = signals >= 2
}

/** Mesociclo: [accumulationWeeks] de carga y una semana de descarga. */
@Serializable
data class Mesocycle(val startEpochDay: Long, val accumulationWeeks: Int = 4) {
    val length: Int get() = accumulationWeeks + 1

    /** Semana del mesociclo (1..length) para [today]. */
    fun weekOf(today: LocalDate): Int {
        val weeks = ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(startEpochDay), today).toInt()
        // Antes de empezar se muestra la primera semana, no la de descarga
        return if (weeks < 0) 1 else weeks.mod(length) + 1
    }

    fun isDeloadWeek(today: LocalDate): Boolean = weekOf(today) == length
}

object Fatigue {
    private val MAIN = listOf("squat", "bench_press", "deadlift", "overhead_press")

    fun assess(history: List<Workout>, nowEpochMs: Long): FatigueReport {
        val stalled = MAIN.filter { id ->
            val pts = WorkoutStats.exerciseProgress(id, history).takeLast(4)
            pts.size >= 4 && pts.drop(1).all { it.bestOneRepMax <= pts.first().bestOneRepMax }
        }
        val week = history.filter { it.isFinished && nowEpochMs - it.startedAtEpochMs in 0..(7 * 86_400_000L) }
        val rpes = week.flatMap { w -> w.exercises.flatMap { it.completedSets } }.filter { it.type != SetType.WARMUP }.mapNotNull { it.rpe }
        val ratings = history.filter { it.isFinished && nowEpochMs - it.startedAtEpochMs in 0..(14 * 86_400_000L) }.mapNotNull { it.rating }
        return FatigueReport(
            stalledLifts = stalled,
            avgRpe = rpes.takeIf { it.isNotEmpty() }?.average(),
            avgEnergy = ratings.takeIf { it.isNotEmpty() }?.map { it.energy }?.average(),
            avgPain = ratings.takeIf { it.isNotEmpty() }?.map { it.pain }?.average(),
        )
    }

    /**
     * Versión de descarga de una rutina: 60 % del peso y la mitad de series (mínimo 1). [label]: la palabra que se
     * añade al nombre, en el idioma de la app («descarga», «Deload»…).
     */
    fun deload(routine: Routine, label: String = "deload"): Routine = routine.copy(
        // Descargar dos veces no repite el sufijo
        name = " · $label".let { suffix -> if (routine.name.endsWith(suffix)) routine.name else routine.name + suffix },
        exercises = routine.exercises.map { re ->
            // Solo calentamientos: se descargan ellos (si no, el ejercicio se quedaría sin series)
            val work = re.sets.filter { it.type != SetType.WARMUP }.ifEmpty { re.sets }
            val keep = (work.size + 1) / 2
            re.copy(sets = work.take(keep.coerceAtLeast(1)).map { it.copy(weightKg = Periodization.round(it.weightKg * 0.6)) })
        },
    )
}

// --- Preparación para una fecha (pico) --------------------------------------------------------

data class PeakWeek(val weeksOut: Int, val focusEs: String, val focusEn: String, val sets: Int, val reps: Int, val percent: Double) {
    /** En el idioma de la app ([lang]: código ISO); si no hay traducción, español o inglés. */
    fun focus(lang: String): String =
        PEAK_FOCUS[focusEs]?.getOrNull(PEAK_LANGS.indexOf(lang)) ?: if (lang in setOf("es", "ca", "gl", "eu")) focusEs else focusEn
}

private val PEAK_LANGS = listOf("ca", "gl", "pt", "fr", "de")

/** Fases del plan de pico en el resto de idiomas de la app (por su nombre en español). */
private val PEAK_FOCUS: Map<String, List<String>> = mapOf(
    "Volumen" to listOf("Volum", "Volume", "Volume", "Volume", "Volumen"),
    "Fuerza" to listOf("Força", "Forza", "Força", "Force", "Kraft"),
    "Fuerza pesada" to listOf("Força pesada", "Forza pesada", "Força pesada", "Force lourde", "Schwere Kraft"),
    "Intensidad" to listOf("Intensitat", "Intensidade", "Intensidade", "Intensité", "Intensität"),
    "Pico: sencillos" to listOf("Pic: singles", "Pico: sinxelos", "Pico: singles", "Pic : singles", "Peak: Singles"),
    "Abridores" to listOf("Obertures", "Abridores", "Aberturas", "Ouvertures", "Eröffnungsversuche"),
    "Descarga y competición" to listOf("Descàrrega i competició", "Descarga e competición", "Descarga e competição", "Affûtage et compétition", "Tapering und Wettkampf"),
)

object Peaking {
    /**
     * Plan hacia una competición o test de 1RM: volumen → fuerza → intensidad → pico → descarga.
     * Devuelve una semana por cada semana que falta (máximo 12), de la más lejana a la última;
     * vacío si la competición ya pasó.
     */
    fun plan(today: LocalDate, meetDate: LocalDate): List<PeakWeek> {
        if (meetDate.isBefore(today)) return emptyList()
        // Redondeado hacia arriba: con 13 días quedan 2 semanas (aproximación + descarga), no 1
        val weeks = ((ChronoUnit.DAYS.between(today, meetDate) + 6) / 7).toInt().coerceIn(1, 12)
        return (weeks downTo 1).map { out ->
            when {
                out >= 9 -> PeakWeek(out, "Volumen", "Volume", 5, 5, 0.75)
                out >= 7 -> PeakWeek(out, "Fuerza", "Strength", 4, 4, 0.80)
                out >= 5 -> PeakWeek(out, "Fuerza pesada", "Heavy strength", 4, 3, 0.85)
                out >= 4 -> PeakWeek(out, "Intensidad", "Intensity", 3, 2, 0.90)
                out == 3 -> PeakWeek(out, "Pico: sencillos", "Peak: singles", 3, 1, 0.93)
                out == 2 -> PeakWeek(out, "Abridores", "Openers", 2, 1, 0.90)
                else -> PeakWeek(out, "Descarga y competición", "Taper and meet", 2, 2, 0.70)
            }
        }
    }

    /** Peso de una semana del plan a partir del 1RM (redondeado a 2,5 kg). */
    fun weight(week: PeakWeek, oneRepMaxKg: Double): Double = Periodization.round(oneRepMaxKg * week.percent)
}

// --- Generador de rutinas ----------------------------------------------------------------------

/** Objetivo de la persona: cambia el reparto de días, las series × reps, los descansos y si hay remate de cardio. */
enum class Goal { STRENGTH, HYPERTROPHY, FAT_LOSS, ENDURANCE, GENERAL }

/** Experiencia: cambia cuántas series se hacen (menos al empezar, más con años de gimnasio). */
enum class Level(val extraSets: Int) { BEGINNER(-1), INTERMEDIATE(0), ADVANCED(1) }

/** Series, reps y descanso de un ejercicio. */
data class Prescription(val sets: Int, val reps: Int, val restSeconds: Int)

object RoutineGenerator {
    /** Huecos de cada tipo de día: el primer ejercicio disponible de cada lista entra en la rutina. */
    private val slots: Map<String, List<List<String>>> = mapOf(
        "full" to listOf(
            listOf("squat", "goblet_squat", "leg_press", "air_squat"),
            listOf("bench_press", "db_bench_press", "machine_chest_press", "push_up"),
            listOf("barbell_row", "db_row", "seated_cable_row", "pull_up", "band_pull_up"),
            listOf("romanian_deadlift", "hip_thrust", "kb_swing", "glute_kickback", "lunge"),
            listOf("overhead_press", "db_shoulder_press", "pike_push_up", "lateral_raise", "diamond_push_up"),
            listOf("plank", "hanging_leg_raise", "crunch"),
        ),
        "upper" to listOf(
            listOf("bench_press", "db_bench_press", "machine_chest_press", "push_up"),
            listOf("barbell_row", "db_row", "seated_cable_row", "band_pull_up"),
            listOf("overhead_press", "db_shoulder_press", "diamond_push_up"),
            listOf("pull_up", "lat_pulldown", "chin_up", "negative_pull_up"),
            listOf("lateral_raise", "cable_lateral_raise", "face_pull"),
            listOf("barbell_curl", "db_curl", "hammer_curl"),
            listOf("triceps_pushdown", "skull_crusher", "dips", "diamond_push_up"),
        ),
        "lower" to listOf(
            listOf("squat", "hack_squat", "leg_press", "goblet_squat", "air_squat"),
            listOf("romanian_deadlift", "deadlift", "hip_thrust", "kb_swing"),
            listOf("bulgarian_split_squat", "lunge", "shrimp_squat"),
            listOf("leg_curl", "seated_leg_curl", "glute_kickback"),
            listOf("standing_calf_raise", "seated_calf_raise"),
            listOf("hanging_leg_raise", "plank", "hollow_hold"),
        ),
        "push" to listOf(
            listOf("bench_press", "db_bench_press", "push_up"),
            listOf("overhead_press", "db_shoulder_press", "pike_push_up"),
            listOf("incline_bench_press", "db_incline_press", "incline_push_up"),
            listOf("lateral_raise", "cable_lateral_raise", "archer_push_up"),
            listOf("triceps_pushdown", "overhead_triceps_ext", "dips", "diamond_push_up"),
        ),
        "pull" to listOf(
            listOf("deadlift", "romanian_deadlift", "kb_swing", "hyperextension"),
            listOf("pull_up", "lat_pulldown", "band_pull_up"),
            listOf("barbell_row", "db_row", "seated_cable_row", "negative_pull_up"),
            listOf("face_pull", "rear_delt_fly", "scapular_pull_up"),
            listOf("barbell_curl", "db_curl", "hammer_curl", "chin_up"),
        ),
        "legs" to listOf(
            listOf("squat", "hack_squat", "leg_press", "goblet_squat", "air_squat"),
            listOf("romanian_deadlift", "hip_thrust"),
            listOf("bulgarian_split_squat", "lunge", "pistol_squat"),
            listOf("leg_extension", "wall_sit"),
            listOf("leg_curl", "seated_leg_curl", "hyperextension"),
            listOf("standing_calf_raise", "seated_calf_raise"),
        ),
    )

    private val dayNames = mapOf(
        "full" to ("Cuerpo completo" to "Full body"),
        "upper" to ("Torso" to "Upper"),
        "lower" to ("Tren inferior" to "Lower"),
        "push" to ("Empuje" to "Push"),
        "pull" to ("Tirón" to "Pull"),
        "legs" to ("Pierna" to "Legs"),
    )

    /**
     * Qué toca cada día según los días y el objetivo: cuerpo completo con pocos días; con más, divisiones que dejan
     * descansar cada músculo (para ganar músculo, empuje/tirón/pierna antes; para fuerza, torso/pierna).
     */
    fun split(days: Int, goal: Goal = Goal.HYPERTROPHY, limitedEquipment: Boolean = false): List<String> =
        // Sin pesas no hay ejercicios para llenar un día de tirón o de empuje: se reparte como para salud
        if (limitedEquipment && goal != Goal.GENERAL) split(days, Goal.GENERAL) else splitFor(days, goal)

    private fun splitFor(days: Int, goal: Goal): List<String> = when (days.coerceIn(1, 6)) {
        1 -> listOf("full")
        2 -> listOf("full", "full")
        3 -> if (goal == Goal.HYPERTROPHY) listOf("push", "pull", "legs") else listOf("full", "full", "full")
        4 -> listOf("upper", "lower", "upper", "lower")
        5 -> when (goal) {
            Goal.HYPERTROPHY, Goal.STRENGTH -> listOf("push", "pull", "legs", "upper", "lower")
            else -> listOf("upper", "lower", "full", "upper", "lower")
        }
        else -> when (goal) {
            Goal.HYPERTROPHY, Goal.STRENGTH -> listOf("push", "pull", "legs", "push", "pull", "legs")
            else -> listOf("upper", "lower", "full", "upper", "lower", "full")
        }
    }

    /** Series, reps y descanso según el objetivo (los ejercicios principales de cada día). */
    fun prescription(goal: Goal): Triple<Int, Int, Int> = main(goal).let { Triple(it.sets, it.reps, it.restSeconds) }

    /** Los dos primeros ejercicios del día (los básicos). */
    fun main(goal: Goal): Prescription = when (goal) {
        Goal.STRENGTH -> Prescription(5, 5, 180)
        Goal.HYPERTROPHY -> Prescription(4, 8, 120)
        Goal.FAT_LOSS -> Prescription(3, 12, 60)
        Goal.ENDURANCE -> Prescription(3, 15, 45)
        Goal.GENERAL -> Prescription(3, 10, 90)
    }

    /** El resto (accesorios). */
    fun accessory(goal: Goal): Prescription = when (goal) {
        Goal.STRENGTH -> Prescription(3, 8, 120)
        Goal.HYPERTROPHY -> Prescription(3, 12, 75)
        Goal.FAT_LOSS -> Prescription(3, 15, 45)
        Goal.ENDURANCE -> Prescription(3, 20, 30)
        Goal.GENERAL -> Prescription(3, 12, 60)
    }

    /** Series según el nivel (siempre al menos 2). */
    private fun Prescription.withLevel(level: Level) = copy(sets = (sets + level.extraSets).coerceAtLeast(2))

    /** Para perder grasa o ganar resistencia, el día acaba con un remate metabólico (si no hay cardio que hacer). */
    private fun finisher(goal: Goal): String? = if (goal == Goal.FAT_LOSS || goal == Goal.ENDURANCE) "burpee" else null

    /**
     * Minutos de cardio al final de la sesión: para perder grasa o ganar resistencia, una cuarta parte de la sesión
     * (entre 10 y 20 min; 25 en resistencia), a múltiplos de 5. 0 con otros objetivos.
     */
    fun cardioMinutes(goal: Goal, minutesPerSession: Int): Int {
        val max = when (goal) {
            Goal.FAT_LOSS -> CARDIO_MAX_FAT_LOSS
            Goal.ENDURANCE -> CARDIO_MAX_ENDURANCE
            else -> return 0
        }
        return ((minutesPerSession / 4 + 2) / 5 * 5).coerceIn(CARDIO_MIN, max)
    }

    /** Cardio del día: máquinas si las hay (cinta, elíptica, bici…), si no caminar, correr o comba; varía por día. */
    private fun cardioFor(dayIndex: Int, equipment: Set<Equipment>, allowed: (Exercise) -> Boolean): Exercise? {
        val ids = if (Equipment.MACHINE in equipment) CARDIO_MACHINES else CARDIO_FREE
        val options = ids.mapNotNull(ExerciseLibrary::byId).filter(allowed)
        return options.getOrNull(dayIndex % options.size.coerceAtLeast(1))
    }

    /** Final del día: cardio de verdad (cinta, bici, caminar…) y, solo si no hay ninguno que se pueda hacer, burpees. */
    private fun dayEnd(goal: Goal, cardioMin: Int, dayIndex: Int, equipment: Set<Equipment>, allowed: (Exercise) -> Boolean, usable: (Exercise) -> Boolean): Pair<Exercise?, Exercise?> {
        val cardio = if (cardioMin > 0) cardioFor(dayIndex, equipment, allowed) else null
        return cardio to (if (cardio == null) finisher(goal)?.let(ExerciseLibrary::byId)?.takeIf(usable) else null)
    }

    private val CARDIO_MACHINES = listOf("treadmill", "elliptical", "stationary_bike", "rowing_machine", "stair_climber")
    private val CARDIO_FREE = listOf("walking", "running", "jump_rope")
    private const val CARDIO_MIN = 10
    private const val CARDIO_MAX_FAT_LOSS = 20
    private const val CARDIO_MAX_ENDURANCE = 25

    /**
     * Rutinas para [daysPerWeek] días con el material disponible y el objetivo. Cuantos más
     * minutos por sesión, más ejercicios. Perder grasa y resistencia emparejan los accesorios en superseries.
     */
    fun generate(
        daysPerWeek: Int,
        equipment: Set<Equipment>,
        goal: Goal,
        minutesPerSession: Int,
        lang: String = "es",
        /** Ejercicios que se pueden usar (p. ej. los seguros con las lesiones apuntadas). */
        allowed: (Exercise) -> Boolean = { true },
        /** Nombres de cada tipo de día en el idioma de la app (si no, español o inglés). */
        dayLabels: Map<String, String> = emptyMap(),
        level: Level = Level.INTERMEDIATE,
    ): List<Routine> {
        val main = main(goal).withLevel(level)
        val acc = accessory(goal).withLevel(level)
        // Los dos primeros son básicos (los que más tardan) y el resto accesorios: con la media, 30 min daban 45
        val mainCost = main.sets * (40 + main.restSeconds)
        val accCost = acc.sets * (40 + acc.restSeconds)
        // El cardio del final (perder grasa, resistencia) sale del tiempo de la sesión
        val cardioMin = cardioMinutes(goal, minutesPerSession)
        val maxExercises = (2 + ((minutesPerSession - cardioMin) * 60 - 2 * mainCost) / accCost).coerceIn(3, 8)
        val usable: (Exercise) -> Boolean = { (it.equipment in equipment || it.equipment == Equipment.BODYWEIGHT) && allowed(it) }
        val limited = equipment.none { it == Equipment.BARBELL || it == Equipment.DUMBBELL || it == Equipment.MACHINE || it == Equipment.CABLE }
        val days = split(daysPerWeek, goal, limited)
        return days.mapIndexed { dayIndex, type ->
            val (cardio, finisherEx) = dayEnd(goal, cardioMin, dayIndex, equipment, allowed, usable)
            // Segundo/tercer día del mismo tipo (A/B/C): otra opción de cada hueco para variar
            val variant = days.take(dayIndex).count { it == type }
            // El remate cuenta dentro del tiempo de la sesión
            val room = if (finisherEx != null) maxExercises - 1 else maxExercises
            val picks = picksFor(type, variant, usable, finisherEx, room)
            val label = dayLabels[type] ?: dayNames.getValue(type).let { (es, en) -> com.bestrong.core.i18n.ContentText.pick(es, en, lang) }
            val letter = if (days.count { it == type } > 1) " " + ('A' + variant) else ""
            // Superseries solo entre accesorios; el remate va suelto al final
            val exercises = pairAccessories(picks.mapIndexed { i, ex -> routineExercise(ex, if (i < 2) main else acc) }, goal)
            val cardioBlock = cardio?.let { RoutineExercise(exercise = it, sets = listOf(RoutineSet(reps = 0, durationSec = cardioMin * 60)), restSeconds = 0) }
            Routine(name = label + letter, exercises = exercises + listOfNotNull(finisherEx?.let { routineExercise(it, acc) }, cardioBlock))
        }
    }

    /** Una opción de cada hueco del tipo de día (la [variant]-ésima de las que se pueden hacer). */
    private fun picksFor(type: String, variant: Int, usable: (Exercise) -> Boolean, finisher: Exercise?, room: Int): List<Exercise> {
        val base = slots.getValue(type).mapNotNull { options ->
            val available = options.mapNotNull { ExerciseLibrary.byId(it) }.filter(usable)
            if (available.isEmpty()) null else available[variant % available.size]
        }.distinctBy { it.id }.filter { it.id != finisher?.id }.take(room)
        // Con más tiempo, más ejercicios del mismo tipo de día (otras opciones de sus huecos) hasta llenar la sesión
        val same = slots.getValue(type).flatten().distinct().mapNotNull(ExerciseLibrary::byId)
            .filter { usable(it) && it.id != finisher?.id && base.none { b -> b.id == it.id } }
        val filled = base + same.take((room - base.size).coerceAtLeast(0))
        // Con lesiones o poco material un día puede quedarse con 0-1 ejercicios: se completa con lo que se
        // pueda hacer de los demás tipos de día (mejor un día algo distinto que uno vacío)
        return if (filled.size >= MIN_DAY_EXERCISES) filled else topUp(filled, usable, finisher, minOf(room, MIN_DAY_EXERCISES))
    }

    private fun topUp(base: List<Exercise>, usable: (Exercise) -> Boolean, finisher: Exercise?, target: Int): List<Exercise> {
        val used = base.mapTo(HashSet()) { it.id } + listOfNotNull(finisher?.id)
        val extra = slots.values.flatten().flatten().distinct().mapNotNull { ExerciseLibrary.byId(it) }
            .filter { it.id !in used && usable(it) }
        return base + extra.take((target - base.size).coerceAtLeast(0))
    }

    private fun routineExercise(ex: Exercise, p: Prescription): RoutineExercise {
        val timed = ex.tracking == TrackingType.DURATION
        return RoutineExercise(
            exercise = ex,
            sets = List(p.sets) { RoutineSet(reps = if (timed) 0 else p.reps, durationSec = if (timed) (p.reps * 3).coerceIn(20, 60) else 0) },
            restSeconds = p.restSeconds,
        )
    }

    /** Superseries de dos en dos con los accesorios (más trabajo en menos tiempo) para perder grasa y resistencia. */
    private fun pairAccessories(exercises: List<RoutineExercise>, goal: Goal): List<RoutineExercise> {
        if (goal != Goal.FAT_LOSS && goal != Goal.ENDURANCE) return exercises
        val out = exercises.toMutableList()
        var i = 2
        while (i + 1 < out.size) {
            val group = com.bestrong.core.util.newId()
            out[i] = out[i].copy(supersetId = group)
            out[i + 1] = out[i + 1].copy(supersetId = group)
            i += 2
        }
        return out
    }
}

/** Programa generado: nombre, explicación y sus rutinas (una por día de entreno). */
data class GeneratedProgram(val name: String, val description: String, val routines: List<Routine>)

/** Programa a medida a partir del objetivo, los días, el material y el tiempo. */
object ProgramGenerator {
    /**
     * Días de la semana (1 = lunes) para [days] sesiones, repartidos para descansar entre medias: 3 → L X V,
     * 4 → L M J V…
     */
    fun defaultWeekdays(days: Int): List<Int> = when (days.coerceIn(1, 7)) {
        1 -> listOf(1)
        2 -> listOf(1, 4)
        3 -> listOf(1, 3, 5)
        4 -> listOf(1, 2, 4, 5)
        5 -> listOf(1, 2, 3, 4, 5)
        6 -> listOf(1, 2, 3, 4, 5, 6)
        else -> (1..7).toList()
    }

    /**
     * Pone las rutinas (por orden, A, B, C…) en los días elegidos de [weeks] semanas desde [firstMonday], empezando por
     * [from] (los días ya pasados de esta semana no se planifican: saldrían como sesiones «saltadas») y sin tocar los
     * días que ya tengan algo (salvo que sea una rutina que ya no existe: [exists] = false, ese día está libre).
     */
    fun schedule(
        plans: Map<Long, WeekPlan>,
        routineIds: List<String>,
        weekdays: List<Int>,
        firstMonday: LocalDate,
        weeks: Int = 12,
        from: LocalDate = firstMonday,
        exists: (String) -> Boolean = { true },
    ): Map<Long, WeekPlan> {
        if (routineIds.isEmpty() || weekdays.isEmpty()) return plans
        val out = plans.toMutableMap()
        var next = 0
        repeat(weeks) { w ->
            val monday = firstMonday.plusWeeks(w.toLong())
            val key = monday.toEpochDay()
            var plan = out[key] ?: WeekPlan()
            weekdays.sorted().forEach { d ->
                val day = DayOfWeek.of(d)
                if (!monday.plusDays(d - 1L).isBefore(from) && plan.routineFor(day)?.let(exists) != true) {
                    plan = plan.assign(day, routineIds[next % routineIds.size])
                    next++
                }
            }
            out[key] = plan
        }
        return out
    }

    private val goalNames = mapOf(
        Goal.STRENGTH to ("Fuerza" to "Strength"),
        Goal.HYPERTROPHY to ("Músculo" to "Muscle"),
        Goal.FAT_LOSS to ("Definición" to "Fat loss"),
        Goal.ENDURANCE to ("Resistencia" to "Endurance"),
        Goal.GENERAL to ("Forma física" to "Fitness"),
    )

    /** Nombre y descripción a medida (para los idiomas que no son español ni inglés): días, básicos y accesorios. */
    data class Texts(
        val name: String,
        val describe: (split: String, main: Prescription, accessory: Prescription) -> String,
        val dayLabels: Map<String, String>,
        /** « + 15 min de cardio al final» en el idioma de la app (perder grasa, resistencia). */
        val cardio: (minutes: Int) -> String = { "" },
    )

    fun build(
        daysPerWeek: Int,
        goal: Goal,
        equipment: Set<Equipment>,
        minutesPerSession: Int,
        lang: String = "es",
        allowed: (Exercise) -> Boolean = { true },
        texts: Texts? = null,
        level: Level = Level.INTERMEDIATE,
    ): GeneratedProgram {
        // Con 7 días salen 6 rutinas que se repiten a lo largo de la semana
        val days = daysPerWeek.coerceIn(1, 7)
        val routines = RoutineGenerator.generate(days, equipment, goal, minutesPerSession, lang, allowed, texts?.dayLabels.orEmpty(), level)
        val (es, en) = goalNames.getValue(goal)
        val name = texts?.name ?: if (lang == "en") "$en · $days days" else "$es · $days días"
        val m = RoutineGenerator.main(goal).let { it.copy(sets = (it.sets + level.extraSets).coerceAtLeast(2)) }
        val a = RoutineGenerator.accessory(goal).let { it.copy(sets = (it.sets + level.extraSets).coerceAtLeast(2)) }
        val split = routines.joinToString(" / ") { it.name }
        val description = texts?.describe?.invoke(split, m, a) ?: if (lang == "en") {
            "$split. Main lifts ${m.sets}×${m.reps} (rest ${m.restSeconds}s), accessories ${a.sets}×${a.reps} (rest ${a.restSeconds}s)."
        } else {
            "$split. Básicos ${m.sets}×${m.reps} (descanso ${m.restSeconds} s), accesorios ${a.sets}×${a.reps} (descanso ${a.restSeconds} s)."
        } + RoutineGenerator.cardioMinutes(goal, minutesPerSession).takeIf { it > 0 }?.let { min ->
            texts?.cardio?.invoke(min) ?: if (lang == "en") " + $min min of cardio at the end." else " + $min min de cardio al final."
        }.orEmpty()
        // Todas en una carpeta con el nombre del programa, para tenerlas juntas en Inicio
        return GeneratedProgram(name, description, routines.map { it.copy(folder = name) })
    }
}

// --- Calendario semanal y tiempo estimado ------------------------------------------------------

/** Semana planificada: qué rutina toca cada día (null = descanso). */
@Serializable
data class WeekPlan(val days: Map<Int, String> = emptyMap()) {
    fun routineFor(day: DayOfWeek): String? = days[day.value]
    fun assign(day: DayOfWeek, routineId: String?): WeekPlan = copy(days = if (routineId == null) days - day.value else days + (day.value to routineId))

    /** Mueve la rutina de un día a otro (arrastrar en el calendario); si el destino tenía una, se intercambian. */
    fun move(from: DayOfWeek, to: DayOfWeek): WeekPlan {
        val a = days[from.value]
        val b = days[to.value]
        var d = days - from.value - to.value
        if (a != null) d = d + (to.value to a)
        if (b != null) d = d + (from.value to b)
        return copy(days = d)
    }
}

object RoutineTime {
    /** Minutos estimados: ~40 s por serie + su descanso (salvo tras la última) + 5 min de calentamiento. */
    fun estimateMinutes(routine: Routine): Int {
        val perSet = routine.exercises.flatMap { re -> re.sets.map { set -> (if (set.durationSec > 0) set.durationSec else 40) + re.restSeconds } }
        val seconds = perSet.sum() - (routine.exercises.lastOrNull()?.restSeconds ?: 0) + 300
        return (seconds / 60.0).toInt().coerceAtLeast(1)
    }

    /** Aviso si la rutina no cabe en el tiempo disponible: devuelve los minutos que sobran (0 si cabe). */
    fun overBy(routine: Routine, availableMinutes: Int): Int = (estimateMinutes(routine) - availableMinutes).coerceAtLeast(0)
}

// --- Calistenia e isométricos -------------------------------------------------------------------

object Calisthenics {
    /** Cadenas de progresión, de más fácil a más difícil. */
    val chains: Map<String, List<String>> = mapOf(
        "push" to listOf("wall_push_up", "incline_push_up", "knee_push_up", "push_up", "diamond_push_up", "archer_push_up", "one_arm_push_up"),
        "pull" to listOf("dead_hang", "scapular_pull_up", "negative_pull_up", "band_pull_up", "pull_up", "chest_to_bar", "muscle_up"),
        "squat" to listOf("assisted_squat", "air_squat", "bulgarian_split_squat", "shrimp_squat", "pistol_squat"),
        "core" to listOf("plank", "side_plank", "hollow_hold", "l_sit"),
    )

    /** Reps (o segundos en isométricos) para pasar al siguiente nivel. */
    const val REPS_TO_ADVANCE = 12
    const val SECONDS_TO_ADVANCE = 60

    fun chainOf(exerciseId: String): List<String>? = chains.values.firstOrNull { exerciseId in it }

    /**
     * Siguiente ejercicio de la cadena si en la última sesión todas las series de trabajo
     * llegaron al objetivo; si no, el mismo. Null si no es de una cadena.
     */
    fun next(exerciseId: String, history: List<Workout>): String? {
        val chain = chainOf(exerciseId) ?: return null
        val ex = ExerciseLibrary.byId(exerciseId) ?: return null
        // Con ayuda de máquina (peso negativo) aún no toca el siguiente paso
        val last = WorkoutStats.previousSets(exerciseId, history).filter { it.type != SetType.WARMUP }
        if (last.isEmpty() || last.any { it.weightKg < 0 }) return exerciseId
        val ready = if (ex.tracking == TrackingType.DURATION) last.all { it.durationSec >= SECONDS_TO_ADVANCE } else last.all { it.reps >= REPS_TO_ADVANCE }
        val i = chain.indexOf(exerciseId)
        return if (ready && i < chain.lastIndex) chain[i + 1] else exerciseId
    }
}

// --- Temporizadores de intervalos y sesiones guiadas ------------------------------------------

/** Fase de un temporizador: trabajo o descanso con su duración. */
data class TimerPhase(val work: Boolean, val seconds: Int, val round: Int, val station: Int = 0)

sealed interface IntervalPlan {
    /** HIIT / Tabata: [rounds] × ([workSec] trabajo + [restSec] descanso). */
    data class Hiit(val workSec: Int, val restSec: Int, val rounds: Int) : IntervalPlan

    /** EMOM: cada minuto, durante [minutes] minutos. */
    data class Emom(val minutes: Int) : IntervalPlan

    /** AMRAP: tantas rondas como puedas en [minutes] minutos. */
    data class Amrap(val minutes: Int) : IntervalPlan

    /** Circuito (modo clase): cada estación [workSec] con [restSec] de cambio, [rounds] vueltas. */
    data class Circuit(val stations: List<String>, val workSec: Int, val restSec: Int, val rounds: Int) : IntervalPlan
}

object IntervalTimer {
    val TABATA = IntervalPlan.Hiit(20, 10, 8)

    fun phases(plan: IntervalPlan): List<TimerPhase> = when (plan) {
        is IntervalPlan.Hiit -> (1..plan.rounds).flatMap { r ->
            listOfNotNull(TimerPhase(true, plan.workSec, r), TimerPhase(false, plan.restSec, r).takeIf { r < plan.rounds && plan.restSec > 0 })
        }
        is IntervalPlan.Emom -> (1..plan.minutes).map { TimerPhase(true, 60, it) }
        is IntervalPlan.Amrap -> listOf(TimerPhase(true, plan.minutes * 60, 1))
        is IntervalPlan.Circuit -> (1..plan.rounds).flatMap { r ->
            plan.stations.indices.flatMap { i ->
                val last = r == plan.rounds && i == plan.stations.lastIndex
                listOfNotNull(TimerPhase(true, plan.workSec, r, i), TimerPhase(false, plan.restSec, r, i).takeIf { !last && plan.restSec > 0 })
            }
        }
    }

    fun totalSeconds(plan: IntervalPlan): Int = phases(plan).sumOf { it.seconds }

    /** Fase en curso a los [elapsedSec] segundos (null = terminado) y segundos que le quedan. */
    fun at(plan: IntervalPlan, elapsedSec: Int): Pair<TimerPhase, Int>? {
        var t = elapsedSec
        phases(plan).forEach { p ->
            if (t < p.seconds) return p to (p.seconds - t)
            t -= p.seconds
        }
        return null
    }
}

/** Paso de una sesión guiada (calentamiento o estiramientos). */
data class GuidedStep(val nameEs: String, val nameEn: String, val seconds: Int) {
    /** En el idioma de la app ([lang]: código ISO, p. ej. «ca»); si no hay traducción, español o inglés. */
    fun name(lang: String): String {
        val i = GUIDED_LANGS.indexOf(lang)
        return GUIDED_NAMES[nameEs]?.getOrNull(i) ?: if (lang in setOf("es", "ca", "gl", "eu")) nameEs else nameEn
    }
}

/** Idiomas de [GUIDED_NAMES], en el orden de cada lista. */
private val GUIDED_LANGS = listOf("ca", "gl", "pt", "fr", "de")

/** Nombres de los pasos guiados en el resto de idiomas de la app, por su nombre en español. */
private val GUIDED_NAMES: Map<String, List<String>> = mapOf(
    "Saltos suaves o bici" to listOf("Salts suaus o bici", "Saltos suaves ou bici", "Saltos suaves ou bicicleta", "Sauts légers ou vélo", "Lockere Hampelmänner oder Rad"),
    "Círculos de brazos" to listOf("Cercles de braços", "Círculos de brazos", "Círculos de braços", "Cercles de bras", "Armkreisen"),
    "Rotaciones de hombro con banda" to listOf("Rotacions d'espatlla amb banda", "Rotacións de ombro con banda", "Rotações de ombro com elástico", "Rotations d'épaules avec élastique", "Schulterkreisen mit Band"),
    "Flexiones lentas" to listOf("Flexions lentes", "Flexións lentas", "Flexões lentas", "Pompes lentes", "Langsame Liegestütze"),
    "Remo escapular" to listOf("Rem escapular", "Remo escapular", "Remada escapular", "Rowing scapulaire", "Skapula-Rudern"),
    "Balanceos de pierna" to listOf("Balancejos de cama", "Balanceos de perna", "Balanços de perna", "Balancements de jambe", "Beinschwingen"),
    "Sentadilla profunda con pausa" to listOf("Esquat profund amb pausa", "Agachamento profundo con pausa", "Agachamento profundo com pausa", "Squat profond tenu", "Tiefe Kniebeuge halten"),
    "Puente de glúteo" to listOf("Pont de glutis", "Ponte de glúteo", "Ponte de glúteo", "Pont fessier", "Glute Bridge"),
    "Gusano con flexión" to listOf("Cuc amb flexió", "Verme con flexión", "Minhoca com flexão", "Inchworm avec pompe", "Inchworm mit Liegestütz"),
    "Sentadilla sin peso" to listOf("Esquat sense pes", "Agachamento sen peso", "Agachamento sem peso", "Squat au poids du corps", "Kniebeugen ohne Gewicht"),
    "Zancada con giro" to listOf("Gambada amb gir", "Zancada con xiro", "Afundo com rotação", "Fente avec rotation", "Ausfallschritt mit Drehung"),
    "Pectoral en marco de puerta" to listOf("Pectoral al marc de la porta", "Pectoral no marco da porta", "Peitoral no batente da porta", "Étirement des pectoraux dans l'encadrement", "Brustdehnung im Türrahmen"),
    "Dorsal colgado o en banco" to listOf("Dorsal penjat o al banc", "Dorsal colgado ou no banco", "Dorsal suspenso ou no banco", "Étirement du dos", "Latissimus-Dehnung"),
    "Tríceps por encima de la cabeza" to listOf("Tríceps per sobre del cap", "Tríceps por riba da cabeza", "Tríceps por cima da cabeça", "Triceps au-dessus de la tête", "Trizepsdehnung über Kopf"),
    "Hombro cruzado" to listOf("Espatlla creuada", "Ombro cruzado", "Ombro cruzado", "Épaule croisée", "Schulter quer"),
    "Cuádriceps de pie" to listOf("Quàdriceps dempeus", "Cuádriceps de pé", "Quadríceps em pé", "Quadriceps debout", "Quadrizeps im Stehen"),
    "Isquios sentado" to listOf("Isquiotibials assegut", "Isquiotibiais sentado", "Isquiotibiais sentado", "Ischios assis", "Beinbeuger im Sitzen"),
    "Figura 4 (glúteo)" to listOf("Figura 4 (glutis)", "Figura 4 (glúteo)", "Figura 4 (glúteo)", "Figure 4 (fessiers)", "Figur 4 (Gesäß)"),
    "Gemelo en pared" to listOf("Bessó a la paret", "Xemelgo na parede", "Panturrilha na parede", "Mollet contre le mur", "Wadendehnung an der Wand"),
    "Postura del niño" to listOf("Postura del nen", "Postura do neno", "Postura da criança", "Posture de l'enfant", "Kindhaltung"),
    "Cobra" to listOf("Cobra", "Cobra", "Cobra", "Cobra", "Kobra"),
)

enum class BodyFocus { UPPER, LOWER, FULL }

object GuidedRoutines {
    fun warmup(focus: BodyFocus): List<GuidedStep> = listOf(
        GuidedStep("Saltos suaves o bici", "Easy jumping jacks or bike", 120),
        GuidedStep("Círculos de brazos", "Arm circles", 30),
    ) + when (focus) {
        BodyFocus.UPPER -> listOf(
            GuidedStep("Rotaciones de hombro con banda", "Band shoulder dislocates", 45),
            GuidedStep("Flexiones lentas", "Slow push-ups", 45),
            GuidedStep("Remo escapular", "Scapular rows", 45),
        )
        BodyFocus.LOWER -> listOf(
            GuidedStep("Balanceos de pierna", "Leg swings", 45),
            GuidedStep("Sentadilla profunda con pausa", "Deep squat hold", 45),
            GuidedStep("Puente de glúteo", "Glute bridge", 45),
        )
        BodyFocus.FULL -> listOf(
            GuidedStep("Gusano con flexión", "Inchworm with push-up", 45),
            GuidedStep("Sentadilla sin peso", "Air squats", 45),
            GuidedStep("Zancada con giro", "Lunge with twist", 45),
        )
    }

    fun stretching(focus: BodyFocus): List<GuidedStep> = when (focus) {
        BodyFocus.UPPER -> listOf(
            GuidedStep("Pectoral en marco de puerta", "Doorway chest stretch", 40),
            GuidedStep("Dorsal colgado o en banco", "Lat stretch", 40),
            GuidedStep("Tríceps por encima de la cabeza", "Overhead triceps stretch", 30),
            GuidedStep("Hombro cruzado", "Cross-body shoulder", 30),
        )
        BodyFocus.LOWER -> listOf(
            GuidedStep("Cuádriceps de pie", "Standing quad stretch", 40),
            GuidedStep("Isquios sentado", "Seated hamstring stretch", 40),
            GuidedStep("Figura 4 (glúteo)", "Figure-4 glute stretch", 40),
            GuidedStep("Gemelo en pared", "Wall calf stretch", 30),
        )
        BodyFocus.FULL -> listOf(
            GuidedStep("Postura del niño", "Child's pose", 45),
            GuidedStep("Cobra", "Cobra", 30),
            GuidedStep("Isquios sentado", "Seated hamstring stretch", 40),
            GuidedStep("Pectoral en marco de puerta", "Doorway chest stretch", 40),
        )
    }

    /** Foco del entreno según los músculos principales de sus ejercicios. */
    fun focusOf(exercises: List<Exercise>): BodyFocus {
        val lower = setOf(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
        val l = exercises.count { it.primaryMuscle in lower }
        val u = exercises.size - l
        return when {
            l == 0 -> BodyFocus.UPPER
            u <= 1 -> BodyFocus.LOWER
            else -> BodyFocus.FULL
        }
    }
}

// --- Lesiones y límite de progresión ------------------------------------------------------------

/** Zona lesionada o a cuidar. */
enum class InjuryZone { SHOULDER, ELBOW, WRIST, LOWER_BACK, KNEE, HIP, NECK }

object Injuries {
    private val byMuscle: Map<InjuryZone, Set<MuscleGroup>> = mapOf(
        InjuryZone.SHOULDER to setOf(MuscleGroup.SHOULDERS, MuscleGroup.CHEST),
        InjuryZone.ELBOW to setOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS),
        InjuryZone.WRIST to setOf(MuscleGroup.FOREARMS),
        InjuryZone.LOWER_BACK to emptySet(),
        InjuryZone.KNEE to setOf(MuscleGroup.QUADRICEPS),
        InjuryZone.HIP to setOf(MuscleGroup.GLUTES),
        InjuryZone.NECK to setOf(MuscleGroup.TRAPS),
    )
    private val byExercise: Map<InjuryZone, Set<String>> = mapOf(
        InjuryZone.LOWER_BACK to setOf("deadlift", "romanian_deadlift", "barbell_row", "pendlay_row", "good_morning", "squat", "hyperextension", "kb_swing", "clean_and_press", "t_bar_row"),
        InjuryZone.SHOULDER to setOf("dips", "upright_row", "muscle_up", "overhead_press", "pull_up", "chin_up"),
        InjuryZone.WRIST to setOf("front_squat", "push_up", "diamond_push_up", "l_sit", "barbell_curl", "close_grip_bench"),
        InjuryZone.KNEE to setOf("lunge", "bulgarian_split_squat", "pistol_squat", "shrimp_squat", "leg_extension", "hack_squat", "leg_press"),
        InjuryZone.ELBOW to setOf("skull_crusher", "overhead_triceps_ext", "close_grip_bench", "chin_up"),
        InjuryZone.HIP to setOf("hip_thrust", "hip_abduction", "pistol_squat"),
        InjuryZone.NECK to setOf("shrug", "upright_row"),
    )

    /** Zonas que carga el ejercicio (de las que el usuario ha marcado). */
    fun conflicts(exercise: Exercise, zones: Set<InjuryZone>): Set<InjuryZone> = zones.filter { z ->
        exercise.id in byExercise[z].orEmpty() || exercise.primaryMuscle in byMuscle[z].orEmpty()
    }.toSet()

    fun isSafe(exercise: Exercise, zones: Set<InjuryZone>): Boolean = conflicts(exercise, zones).isEmpty()
}

object ProgressionCap {
    /**
     * Mejor peso de [exerciseId] en la semana natural anterior (lunes a domingo): la referencia del tope. Con una
     * ventana de 7 días hacia atrás, dos sesiones en la semana se sumaban (+5 % y otro +5 %) y una sesión semanal a
     * una hora algo más tarde quedaba fuera y se quitaba el tope.
     */
    fun lastWeekBest(exerciseId: String, history: List<com.bestrong.core.model.Workout>, nowEpochMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Double? {
        val monday = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        fun ms(d: java.time.LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return com.bestrong.core.stats.WorkoutStats.bestWeightBetween(exerciseId, history, ms(monday.minusWeeks(1)), ms(monday))
    }

    /**
     * Limita la subida de peso semanal a [maxWeeklyIncrease] (5 % por defecto) sobre el mejor
     * peso de la semana anterior; nunca baja de ese peso. Siempre deja subir al menos un escalón [minStepKg]: con
     * pesos ligeros (40 kg al 2,5 % = 41 → 40 al redondear) el tope, si no, bloquearía toda subida para siempre.
     */
    fun limit(suggestedKg: Double, lastWeekBestKg: Double?, maxWeeklyIncrease: Double = 0.05, minStepKg: Double = 0.0): Double {
        if (lastWeekBestKg == null || lastWeekBestKg <= 0) return suggestedKg
        val cap = maxOf(Periodization.round(lastWeekBestKg * (1 + maxWeeklyIncrease)), lastWeekBestKg + minStepKg)
        return suggestedKg.coerceAtMost(cap)
    }

    /**
     * [limit] con un peso que se pueda montar en [unit]: en libras el tope (redondeado en kg) se baja al escalón
     * [stepKg] de la unidad, sin bajar del mejor de la semana anterior.
     */
    fun limitIn(unit: com.bestrong.core.util.WeightUnit, suggestedKg: Double, lastWeekBestKg: Double?, maxWeeklyIncrease: Double, stepKg: Double): Double {
        val limited = limit(suggestedKg, lastWeekBestKg, maxWeeklyIncrease, stepKg)
        val step = unit.fromKg(stepKg)
        // En kg, sin tope aplicado o sin escalón, el tope ya vale tal cual
        if (unit == com.bestrong.core.util.WeightUnit.KG || limited >= suggestedKg - 1e-9 || step <= 0) return limited
        val floored = unit.toKg(kotlin.math.floor(unit.fromKg(limited) / step + 1e-6) * step)
        return maxOf(floored, lastWeekBestKg ?: 0.0)
    }
}

/**
 * Sesiones planificadas de la semana que empieza en [weekStart] cuyo día ya pasó sin entrenar ese día ni
 * hacer esa rutina después (día de la semana, id de rutina), la más reciente primero.
 * [routineDone] dice si la rutina se hizo entre una fecha y hoy (moverla de día también cuenta).
 */
fun WeekPlan.missedSessions(
    weekStart: java.time.LocalDate,
    today: java.time.LocalDate,
    trainedDays: Set<java.time.LocalDate>,
    routineDone: (routineId: String, since: java.time.LocalDate) -> Boolean = { _, _ -> false },
): List<Pair<java.time.DayOfWeek, String>> =
    days.mapNotNull { (d, id) ->
        val date = weekStart.plusDays(d - 1L)
        if (date.isBefore(today) && date !in trainedDays && !routineDone(id, date)) java.time.DayOfWeek.of(d) to id else null
    }.sortedByDescending { it.first }

private const val MIN_DAY_EXERCISES = 3
