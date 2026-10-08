package com.bestrong.core.library

import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Equipment.BARBELL
import com.bestrong.core.model.Equipment.BODYWEIGHT
import com.bestrong.core.model.Equipment.CABLE
import com.bestrong.core.model.Equipment.DUMBBELL
import com.bestrong.core.model.Equipment.KETTLEBELL
import com.bestrong.core.model.Equipment.MACHINE
import com.bestrong.core.model.Equipment.SMITH
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.MuscleGroup.ABS
import com.bestrong.core.model.MuscleGroup.BACK
import com.bestrong.core.model.MuscleGroup.BICEPS
import com.bestrong.core.model.MuscleGroup.CALVES
import com.bestrong.core.model.MuscleGroup.CHEST
import com.bestrong.core.model.MuscleGroup.FOREARMS
import com.bestrong.core.model.MuscleGroup.FULL_BODY
import com.bestrong.core.model.MuscleGroup.GLUTES
import com.bestrong.core.model.MuscleGroup.HAMSTRINGS
import com.bestrong.core.model.MuscleGroup.QUADRICEPS
import com.bestrong.core.model.MuscleGroup.SHOULDERS
import com.bestrong.core.model.MuscleGroup.TRAPS
import com.bestrong.core.model.MuscleGroup.TRICEPS
import com.bestrong.core.model.TrackingType
import java.text.Normalizer

/** Biblioteca integrada de ejercicios. Los ids son estables: no cambiarlos. */
object ExerciseLibrary {

    private fun ex(
        id: String,
        name: String,
        muscle: MuscleGroup,
        equipment: Equipment,
        vararg secondary: MuscleGroup,
        tracking: TrackingType = TrackingType.WEIGHT_REPS,
    ) = Exercise(id, name, muscle, secondary.toList(), equipment, tracking)

    val all: List<Exercise> = listOf(
        // Pecho
        ex("bench_press", "Press banca", CHEST, BARBELL, TRICEPS, SHOULDERS),
        ex("incline_bench_press", "Press banca inclinado", CHEST, BARBELL, SHOULDERS, TRICEPS),
        ex("decline_bench_press", "Press banca declinado", CHEST, BARBELL, TRICEPS),
        ex("db_bench_press", "Press banca con mancuernas", CHEST, DUMBBELL, TRICEPS, SHOULDERS),
        ex("db_incline_press", "Press inclinado con mancuernas", CHEST, DUMBBELL, SHOULDERS),
        ex("db_fly", "Aperturas con mancuernas", CHEST, DUMBBELL),
        ex("cable_crossover", "Cruce de poleas", CHEST, CABLE),
        ex("pec_deck", "Contractor de pecho (pec deck)", CHEST, MACHINE),
        ex("machine_chest_press", "Press de pecho en máquina", CHEST, MACHINE, TRICEPS),
        ex("push_up", "Flexiones", CHEST, BODYWEIGHT, TRICEPS, SHOULDERS, tracking = TrackingType.REPS_ONLY),
        ex("dips", "Fondos en paralelas", CHEST, BODYWEIGHT, TRICEPS, tracking = TrackingType.REPS_ONLY),
        // Espalda
        ex("deadlift", "Peso muerto", BACK, BARBELL, HAMSTRINGS, GLUTES, TRAPS),
        ex("barbell_row", "Remo con barra", BACK, BARBELL, BICEPS),
        ex("db_row", "Remo con mancuerna", BACK, DUMBBELL, BICEPS),
        ex("pendlay_row", "Remo Pendlay", BACK, BARBELL, BICEPS),
        ex("t_bar_row", "Remo en T", BACK, BARBELL, BICEPS),
        ex("seated_cable_row", "Remo sentado en polea", BACK, CABLE, BICEPS),
        ex("lat_pulldown", "Jalón al pecho", BACK, CABLE, BICEPS),
        ex("pull_up", "Dominadas", BACK, BODYWEIGHT, BICEPS, tracking = TrackingType.REPS_ONLY),
        ex("chin_up", "Dominadas supinas", BACK, BODYWEIGHT, BICEPS, tracking = TrackingType.REPS_ONLY),
        ex("straight_arm_pulldown", "Pullover en polea", BACK, CABLE),
        ex("hyperextension", "Hiperextensiones", BACK, BODYWEIGHT, GLUTES, HAMSTRINGS),
        // Hombros
        ex("overhead_press", "Press militar", SHOULDERS, BARBELL, TRICEPS),
        ex("db_shoulder_press", "Press de hombro con mancuernas", SHOULDERS, DUMBBELL, TRICEPS),
        ex("arnold_press", "Press Arnold", SHOULDERS, DUMBBELL, TRICEPS),
        ex("lateral_raise", "Elevaciones laterales", SHOULDERS, DUMBBELL),
        ex("cable_lateral_raise", "Elevaciones laterales en polea", SHOULDERS, CABLE),
        ex("front_raise", "Elevaciones frontales", SHOULDERS, DUMBBELL),
        ex("rear_delt_fly", "Pájaros (deltoides posterior)", SHOULDERS, DUMBBELL, BACK),
        ex("face_pull", "Face pull", SHOULDERS, CABLE, TRAPS),
        ex("upright_row", "Remo al mentón", SHOULDERS, BARBELL, TRAPS),
        ex("shrug", "Encogimientos", TRAPS, DUMBBELL),
        // Brazos
        ex("barbell_curl", "Curl con barra", BICEPS, BARBELL, FOREARMS),
        ex("db_curl", "Curl con mancuernas", BICEPS, DUMBBELL, FOREARMS),
        ex("hammer_curl", "Curl martillo", BICEPS, DUMBBELL, FOREARMS),
        ex("preacher_curl", "Curl predicador", BICEPS, BARBELL),
        ex("cable_curl", "Curl en polea", BICEPS, CABLE),
        ex("incline_db_curl", "Curl inclinado con mancuernas", BICEPS, DUMBBELL),
        ex("close_grip_bench", "Press banca agarre cerrado", TRICEPS, BARBELL, CHEST),
        ex("skull_crusher", "Press francés", TRICEPS, BARBELL),
        ex("triceps_pushdown", "Extensión de tríceps en polea", TRICEPS, CABLE),
        ex("overhead_triceps_ext", "Extensión de tríceps sobre la cabeza", TRICEPS, DUMBBELL),
        ex("wrist_curl", "Curl de muñeca", FOREARMS, DUMBBELL),
        // Piernas
        ex("squat", "Sentadilla", QUADRICEPS, BARBELL, GLUTES, HAMSTRINGS),
        ex("front_squat", "Sentadilla frontal", QUADRICEPS, BARBELL, GLUTES, ABS),
        ex("smith_squat", "Sentadilla en multipower", QUADRICEPS, SMITH, GLUTES),
        ex("goblet_squat", "Sentadilla goblet", QUADRICEPS, DUMBBELL, GLUTES),
        ex("hack_squat", "Sentadilla hack", QUADRICEPS, MACHINE, GLUTES),
        ex("leg_press", "Prensa de piernas", QUADRICEPS, MACHINE, GLUTES),
        ex("bulgarian_split_squat", "Sentadilla búlgara", QUADRICEPS, DUMBBELL, GLUTES),
        ex("lunge", "Zancadas", QUADRICEPS, DUMBBELL, GLUTES),
        ex("leg_extension", "Extensión de cuádriceps", QUADRICEPS, MACHINE),
        ex("romanian_deadlift", "Peso muerto rumano", HAMSTRINGS, BARBELL, GLUTES, BACK),
        ex("leg_curl", "Curl femoral tumbado", HAMSTRINGS, MACHINE),
        ex("seated_leg_curl", "Curl femoral sentado", HAMSTRINGS, MACHINE),
        ex("hip_thrust", "Hip thrust", GLUTES, BARBELL, HAMSTRINGS),
        ex("glute_kickback", "Patada de glúteo en polea", GLUTES, CABLE),
        ex("hip_abduction", "Abducción de cadera", GLUTES, MACHINE),
        ex("standing_calf_raise", "Elevación de gemelos de pie", CALVES, MACHINE),
        ex("seated_calf_raise", "Elevación de gemelos sentado", CALVES, MACHINE),
        // Core
        ex("plank", "Plancha", ABS, BODYWEIGHT, tracking = TrackingType.DURATION),
        ex("crunch", "Crunch abdominal", ABS, BODYWEIGHT, tracking = TrackingType.REPS_ONLY),
        ex("hanging_leg_raise", "Elevación de piernas colgado", ABS, BODYWEIGHT, tracking = TrackingType.REPS_ONLY),
        ex("cable_crunch", "Crunch en polea", ABS, CABLE),
        ex("ab_wheel", "Rueda abdominal", ABS, BODYWEIGHT, tracking = TrackingType.REPS_ONLY),
        ex("russian_twist", "Giros rusos", ABS, BODYWEIGHT, tracking = TrackingType.REPS_ONLY),
        // Cuerpo completo
        ex("kb_swing", "Swing con kettlebell", FULL_BODY, KETTLEBELL, GLUTES, HAMSTRINGS),
        ex("clean_and_press", "Cargada y press", FULL_BODY, BARBELL, SHOULDERS),
        ex("farmers_walk", "Paseo del granjero", FULL_BODY, DUMBBELL, FOREARMS, TRAPS, tracking = TrackingType.DURATION),
        ex("burpee", "Burpees", FULL_BODY, BODYWEIGHT, tracking = TrackingType.REPS_ONLY),
    )

    private val byId = all.associateBy { it.id }

    /** Nombres en inglés (Hevy/Strong) → id de la biblioteca, para importar CSV. */
    val englishAliases: Map<String, String> = mapOf(
        "Bench Press" to "bench_press", "Bench Press (Barbell)" to "bench_press",
        "Incline Bench Press" to "incline_bench_press", "Incline Bench Press (Barbell)" to "incline_bench_press",
        "Decline Bench Press" to "decline_bench_press", "Decline Bench Press (Barbell)" to "decline_bench_press",
        "Bench Press (Dumbbell)" to "db_bench_press", "Incline Bench Press (Dumbbell)" to "db_incline_press",
        "Chest Fly (Dumbbell)" to "db_fly", "Dumbbell Fly" to "db_fly", "Cable Crossover" to "cable_crossover", "Cable Fly Crossovers" to "cable_crossover",
        "Butterfly (Pec Deck)" to "pec_deck", "Pec Deck" to "pec_deck", "Chest Press (Machine)" to "machine_chest_press",
        "Push Up" to "push_up", "Push Ups" to "push_up", "Chest Dip" to "dips", "Dip" to "dips", "Triceps Dip" to "dips",
        "Deadlift" to "deadlift", "Deadlift (Barbell)" to "deadlift", "Bent Over Row" to "barbell_row", "Bent Over Row (Barbell)" to "barbell_row",
        "Dumbbell Row" to "db_row", "Bent Over One Arm Row (Dumbbell)" to "db_row", "Pendlay Row (Barbell)" to "pendlay_row",
        "T Bar Row" to "t_bar_row", "Seated Cable Row" to "seated_cable_row", "Seated Row (Cable)" to "seated_cable_row",
        "Lat Pulldown" to "lat_pulldown", "Lat Pulldown (Cable)" to "lat_pulldown", "Pull Up" to "pull_up", "Chin Up" to "chin_up",
        "Straight Arm Pulldown" to "straight_arm_pulldown", "Back Extension" to "hyperextension", "Hyperextension" to "hyperextension",
        "Overhead Press" to "overhead_press", "Overhead Press (Barbell)" to "overhead_press", "Strict Military Press (Barbell)" to "overhead_press",
        "Shoulder Press (Dumbbell)" to "db_shoulder_press", "Seated Overhead Press (Dumbbell)" to "db_shoulder_press", "Arnold Press (Dumbbell)" to "arnold_press",
        "Lateral Raise" to "lateral_raise", "Lateral Raise (Dumbbell)" to "lateral_raise", "Lateral Raise (Cable)" to "cable_lateral_raise",
        "Front Raise (Dumbbell)" to "front_raise", "Rear Delt Reverse Fly (Dumbbell)" to "rear_delt_fly", "Reverse Fly (Dumbbell)" to "rear_delt_fly",
        "Face Pull" to "face_pull", "Face Pull (Cable)" to "face_pull", "Upright Row (Barbell)" to "upright_row",
        "Shrug (Dumbbell)" to "shrug", "Shrug (Barbell)" to "shrug",
        "Bicep Curl (Barbell)" to "barbell_curl", "Curl (Barbell)" to "barbell_curl", "Bicep Curl (Dumbbell)" to "db_curl", "Curl (Dumbbell)" to "db_curl",
        "Hammer Curl (Dumbbell)" to "hammer_curl", "Preacher Curl (Barbell)" to "preacher_curl", "Bicep Curl (Cable)" to "cable_curl",
        "Incline Curl (Dumbbell)" to "incline_db_curl", "Bench Press - Close Grip (Barbell)" to "close_grip_bench", "Close Grip Bench Press" to "close_grip_bench",
        "Skullcrusher (Barbell)" to "skull_crusher", "Lying Triceps Extension (Barbell)" to "skull_crusher",
        "Triceps Pushdown" to "triceps_pushdown", "Triceps Pushdown (Cable - Straight Bar)" to "triceps_pushdown", "Triceps Rope Pushdown" to "triceps_pushdown",
        "Overhead Triceps Extension (Dumbbell)" to "overhead_triceps_ext", "Triceps Extension (Dumbbell)" to "overhead_triceps_ext", "Wrist Curl (Dumbbell)" to "wrist_curl",
        "Squat" to "squat", "Squat (Barbell)" to "squat", "Full Squat" to "squat", "Front Squat" to "front_squat", "Front Squat (Barbell)" to "front_squat",
        "Squat (Smith Machine)" to "smith_squat", "Goblet Squat" to "goblet_squat", "Goblet Squat (Kettlebell)" to "goblet_squat",
        "Hack Squat" to "hack_squat", "Hack Squat (Machine)" to "hack_squat", "Leg Press" to "leg_press", "Leg Press (Machine)" to "leg_press",
        "Bulgarian Split Squat" to "bulgarian_split_squat", "Lunge (Dumbbell)" to "lunge", "Lunge" to "lunge", "Walking Lunge (Dumbbell)" to "lunge",
        "Leg Extension (Machine)" to "leg_extension", "Leg Extension" to "leg_extension",
        "Romanian Deadlift" to "romanian_deadlift", "Romanian Deadlift (Barbell)" to "romanian_deadlift",
        "Lying Leg Curl (Machine)" to "leg_curl", "Leg Curl" to "leg_curl", "Seated Leg Curl (Machine)" to "seated_leg_curl",
        "Hip Thrust" to "hip_thrust", "Hip Thrust (Barbell)" to "hip_thrust", "Glute Kickback (Cable)" to "glute_kickback",
        "Hip Abduction (Machine)" to "hip_abduction", "Standing Calf Raise" to "standing_calf_raise", "Standing Calf Raise (Machine)" to "standing_calf_raise",
        "Seated Calf Raise" to "seated_calf_raise", "Seated Calf Raise (Machine)" to "seated_calf_raise",
        "Plank" to "plank", "Crunch" to "crunch", "Hanging Leg Raise" to "hanging_leg_raise", "Cable Crunch" to "cable_crunch",
        "Ab Wheel" to "ab_wheel", "Russian Twist" to "russian_twist", "Kettlebell Swing" to "kb_swing",
        "Clean and Press" to "clean_and_press", "Farmers Walk" to "farmers_walk", "Burpee" to "burpee",
    )

    fun byId(id: String): Exercise? = byId[id]

    /** Nombre para mostrar según el idioma ("en" o cualquier otro = español). Los personalizados no se traducen. */
    fun displayName(exercise: Exercise, language: String): String =
        if (language == "en" && !exercise.isCustom) englishNames[exercise.id] ?: exercise.name else exercise.name

    /** Búsqueda sin tildes ni mayúsculas, filtrando por músculo y equipo. */
    fun search(
        exercises: List<Exercise>,
        query: String,
        muscle: MuscleGroup? = null,
        equipment: Equipment? = null,
        nameOf: (Exercise) -> String = { it.name },
    ): List<Exercise> {
        val q = query.normalized()
        return exercises
            .filter { muscle == null || it.primaryMuscle == muscle || muscle in it.secondaryMuscles }
            .filter { equipment == null || it.equipment == equipment }
            .filter { q.isBlank() || q.split(' ').all { token -> nameOf(it).normalized().contains(token) } }
            .sortedWith(compareBy({ !nameOf(it).normalized().startsWith(q) }, { nameOf(it) }))
    }

    internal fun String.normalized(): String =
        Normalizer.normalize(lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
