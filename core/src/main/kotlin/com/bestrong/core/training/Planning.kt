package com.bestrong.core.training

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.stats.WorkoutStats

// --- Generador de rutinas ----------------------------------------------------------------------

/** Objetivo de la persona: cambia el reparto de días, las series × reps, los descansos y si hay remate de cardio. */
enum class Goal { STRENGTH, HYPERTROPHY, FAT_LOSS, ENDURANCE, GENERAL }

/** Experiencia: cambia cuántas series se hacen (menos al empezar, más con años de gimnasio). */
enum class Level(val extraSets: Int) { BEGINNER(-1), INTERMEDIATE(0), ADVANCED(1) }

/** Series, reps y descanso de un ejercicio. */
data class Prescription(val sets: Int, val reps: Int, val restSeconds: Int)

object RoutineGenerator {
    /**
     * Huecos de cada tipo de día, de más a menos importante (básicos primero: si falta tiempo se recortan los del
     * final). En cada hueco, opciones por preferencia: la primera que se pueda hacer entra en la rutina, las demás
     * sirven para los días B/C y para quien entrena con mancuernas o solo con su peso.
     */
    private val slots: Map<String, List<List<String>>> = mapOf(
        "full" to listOf(
            // Rodilla, empuje y tirón horizontales, cadera, empuje y tirón verticales y core
            listOf("squat", "leg_press", "hack_squat", "goblet_squat", "bulgarian_split_squat", "air_squat"),
            listOf("bench_press", "db_bench_press", "machine_chest_press", "push_up"),
            listOf("barbell_row", "seated_cable_row", "db_row", "chin_up", "band_pull_up"),
            listOf("romanian_deadlift", "hip_thrust", "deadlift", "kb_swing", "hyperextension"),
            listOf("overhead_press", "db_shoulder_press", "arnold_press", "pike_push_up"),
            listOf("lat_pulldown", "pull_up", "negative_pull_up", "band_pull_up"),
            listOf("plank", "hanging_leg_raise", "ab_wheel", "side_plank", "hollow_hold"),
        ),
        "upper" to listOf(
            // Tanto tirón como empuje (salud del hombro)
            listOf("bench_press", "db_bench_press", "machine_chest_press", "push_up"),
            listOf("barbell_row", "seated_cable_row", "db_row", "t_bar_row", "chin_up", "band_pull_up"),
            listOf("overhead_press", "db_shoulder_press", "arnold_press", "pike_push_up"),
            listOf("lat_pulldown", "pull_up", "negative_pull_up", "chin_up"),
            listOf("db_incline_press", "incline_bench_press", "cable_crossover", "db_fly", "dips"),
            listOf("lateral_raise", "face_pull", "cable_lateral_raise", "rear_delt_fly", "scapular_pull_up"),
            listOf("db_curl", "barbell_curl", "hammer_curl", "cable_curl", "incline_db_curl"),
            listOf("triceps_pushdown", "overhead_triceps_ext", "skull_crusher", "diamond_push_up"),
        ),
        "lower" to listOf(
            listOf("squat", "hack_squat", "leg_press", "front_squat", "goblet_squat", "air_squat"),
            listOf("romanian_deadlift", "deadlift", "hip_thrust", "kb_swing", "hyperextension"),
            listOf("bulgarian_split_squat", "lunge", "shrimp_squat", "pistol_squat"),
            listOf("leg_curl", "seated_leg_curl", "glute_kickback", "hyperextension"),
            listOf("leg_extension", "wall_sit"),
            listOf("standing_calf_raise", "seated_calf_raise"),
            listOf("hanging_leg_raise", "plank", "ab_wheel", "hollow_hold", "side_plank"),
        ),
        "push" to listOf(
            listOf("bench_press", "db_bench_press", "machine_chest_press", "push_up"),
            listOf("overhead_press", "db_shoulder_press", "arnold_press", "pike_push_up"),
            listOf("incline_bench_press", "db_incline_press", "machine_chest_press", "dips"),
            listOf("cable_crossover", "pec_deck", "db_fly", "archer_push_up"),
            listOf("lateral_raise", "cable_lateral_raise"),
            listOf("triceps_pushdown", "overhead_triceps_ext", "skull_crusher", "diamond_push_up"),
        ),
        "pull" to listOf(
            // Remo y jalón primero; la bisagra pesada va en el día de pierna
            listOf("barbell_row", "t_bar_row", "db_row", "pendlay_row", "chin_up"),
            listOf("pull_up", "lat_pulldown", "chin_up", "negative_pull_up", "band_pull_up"),
            listOf("seated_cable_row", "db_row", "straight_arm_pulldown", "negative_pull_up"),
            listOf("face_pull", "rear_delt_fly", "scapular_pull_up"),
            listOf("barbell_curl", "db_curl", "incline_db_curl", "cable_curl", "preacher_curl"),
            listOf("hammer_curl", "cable_curl", "shrug"),
            listOf("hyperextension", "dead_hang"),
        ),
        "legs" to listOf(
            listOf("squat", "hack_squat", "front_squat", "leg_press", "goblet_squat", "air_squat"),
            listOf("romanian_deadlift", "hip_thrust", "deadlift", "kb_swing", "hyperextension"),
            listOf("leg_press", "bulgarian_split_squat", "lunge", "shrimp_squat", "pistol_squat"),
            listOf("leg_extension", "wall_sit"),
            listOf("leg_curl", "seated_leg_curl", "glute_kickback", "hyperextension"),
            listOf("standing_calf_raise", "seated_calf_raise"),
            listOf("hanging_leg_raise", "ab_wheel", "cable_crunch", "plank"),
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
     * descansar cada músculo y lo entrenan dos veces por semana (para ganar músculo, torso/pierna/completo con 3 días
     * y empuje/tirón/pierna con 5-6; para fuerza, torso/pierna y cuerpo completo para repetir los básicos).
     */
    fun split(days: Int, goal: Goal = Goal.HYPERTROPHY, limitedEquipment: Boolean = false): List<String> =
        // Sin pesas no hay ejercicios para llenar un día de tirón o de empuje: se reparte como para salud
        if (limitedEquipment && goal != Goal.GENERAL) split(days, Goal.GENERAL) else splitFor(days, goal)

    private fun splitFor(days: Int, goal: Goal): List<String> = when (days.coerceIn(1, 6)) {
        1 -> listOf("full")
        2 -> listOf("full", "full")
        // Empuje/tirón/pierna en 3 días entrena cada músculo una sola vez: mejor dos veces
        3 -> if (goal == Goal.HYPERTROPHY) listOf("upper", "lower", "full") else listOf("full", "full", "full")
        4 -> listOf("upper", "lower", "upper", "lower")
        5 -> when (goal) {
            Goal.HYPERTROPHY -> listOf("upper", "lower", "push", "pull", "legs")
            else -> listOf("upper", "lower", "full", "upper", "lower")
        }
        else -> when (goal) {
            Goal.HYPERTROPHY -> listOf("push", "pull", "legs", "push", "pull", "legs")
            else -> listOf("upper", "lower", "full", "upper", "lower", "full")
        }
    }

    /** Series, reps y descanso según el objetivo (los ejercicios principales de cada día). */
    fun prescription(goal: Goal): Triple<Int, Int, Int> = main(goal).let { Triple(it.sets, it.reps, it.restSeconds) }

    /** Los dos primeros ejercicios del día (los básicos). */
    fun main(goal: Goal): Prescription = when (goal) {
        Goal.STRENGTH -> Prescription(5, 5, 180)
        Goal.HYPERTROPHY -> Prescription(4, 8, 120)
        // Al perder grasa, cargas algo más altas en los básicos para no perder músculo
        Goal.FAT_LOSS -> Prescription(3, 10, 60)
        Goal.ENDURANCE -> Prescription(3, 15, 45)
        Goal.GENERAL -> Prescription(3, 10, 90)
    }

    /** El resto (accesorios). */
    fun accessory(goal: Goal): Prescription = when (goal) {
        Goal.STRENGTH -> Prescription(3, 8, 90)
        Goal.HYPERTROPHY -> Prescription(3, 12, 75)
        Goal.FAT_LOSS -> Prescription(3, 12, 45)
        Goal.ENDURANCE -> Prescription(3, 20, 30)
        Goal.GENERAL -> Prescription(3, 12, 60)
    }

    /** Series según el nivel (siempre al menos 2). */
    private fun Prescription.withLevel(level: Level) = copy(sets = (sets + level.extraSets).coerceAtLeast(2))

    /** Para perder grasa o ganar resistencia, el día acaba con un remate metabólico (si no hay cardio que hacer). */
    private fun finisher(goal: Goal): String? = if (goal == Goal.FAT_LOSS || goal == Goal.ENDURANCE) "burpee" else null

    /**
     * Minutos de cardio al final de la sesión: para perder grasa o ganar resistencia, una cuarta parte de la sesión
     * (entre 10 y 20 min; 25 en resistencia), a múltiplos de 5. Para salud, 10 min si la sesión da para ello
     * (hacia los 150 min semanales de actividad aeróbica). 0 con otros objetivos.
     */
    fun cardioMinutes(goal: Goal, minutesPerSession: Int): Int {
        val scaled = ((minutesPerSession / 4 + 2) / 5 * 5)
        return when (goal) {
            Goal.FAT_LOSS -> scaled.coerceIn(CARDIO_MIN, CARDIO_MAX_FAT_LOSS)
            Goal.ENDURANCE -> scaled.coerceIn(CARDIO_MIN, CARDIO_MAX_ENDURANCE)
            Goal.GENERAL -> if (minutesPerSession >= CARDIO_GENERAL_FROM) CARDIO_MIN else 0
            else -> 0
        }
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

    // Alternando impacto y no impacto entre días (cinta, bici, elíptica, remo, escaladora)
    private val CARDIO_MACHINES = listOf("treadmill", "stationary_bike", "elliptical", "rowing_machine", "stair_climber")

    // Sin máquinas: caminar primero (lo puede hacer cualquiera), luego comba y carrera
    private val CARDIO_FREE = listOf("walking", "jump_rope", "running")
    private const val CARDIO_MIN = 10
    private const val CARDIO_GENERAL_FROM = 45
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
        // Con más tiempo, más ejercicios del mismo tipo de día: otra opción de cada hueco por turnos (no tres
        // sentadillas seguidas) hasta llenar la sesión
        val options = slots.getValue(type)
        val same = (0 until options.maxOf { it.size }).flatMap { k -> options.mapNotNull { it.getOrNull(k) } }.distinct()
            .mapNotNull(ExerciseLibrary::byId)
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

private const val MIN_DAY_EXERCISES = 3
