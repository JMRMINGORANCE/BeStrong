package com.bestrong.core.training

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.RoutineExercise

/** Zona con molestias: el programa a medida evita los ejercicios que más la cargan (no es un diagnóstico). */
enum class CareZone { SHOULDER, ELBOW, WRIST, NECK, LOWER_BACK, HIP, KNEE, ANKLE }

/** Punto débil que se quiere reforzar: más series y un ejercicio extra en los días que lo entrenan. */
enum class FocusArea(val muscles: Set<MuscleGroup>) {
    CHEST(setOf(MuscleGroup.CHEST)),
    BACK(setOf(MuscleGroup.BACK, MuscleGroup.TRAPS)),
    SHOULDERS(setOf(MuscleGroup.SHOULDERS)),
    ARMS(setOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS)),
    GLUTES(setOf(MuscleGroup.GLUTES)),
    LEGS(setOf(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS)),
    CALVES(setOf(MuscleGroup.CALVES)),
    CORE(setOf(MuscleGroup.ABS)),
}

/**
 * Zonas a cuidar y a reforzar del programa a medida. Las exclusiones quitan el ejercicio que más carga la
 * articulación y dejan una alternativa más controlada del mismo músculo (máquina, polea o mancuerna). El refuerzo
 * sigue la evidencia sobre especialización: pocas zonas a la vez (como mucho [MAX_FOCUS]), +1 serie y un accesorio
 * extra de 10-15 reps, sin pasar de [MAX_SETS] series por ejercicio ni de [MAX_STRENGTH] ejercicios por sesión.
 */
object BodyFocus {
    const val MAX_FOCUS = 2
    const val MAX_SETS = 5
    const val MAX_STRENGTH = 8

    /** Series, reps y descanso del ejercicio extra. */
    val extraPrescription = Prescription(sets = 3, reps = 12, restSeconds = 75)

    private val excluded: Map<CareZone, Set<String>> = mapOf(
        // Por encima de la cabeza con barra, rotación interna con carga y estiramiento cargado
        CareZone.SHOULDER to setOf("overhead_press", "arnold_press", "upright_row", "dips", "db_fly", "pike_push_up", "muscle_up", "clean_and_press"),
        // Tendones del codo al final del recorrido o con barra recta en supinación
        CareZone.ELBOW to setOf(
            "skull_crusher", "overhead_triceps_ext", "close_grip_bench", "barbell_curl", "preacher_curl",
            "dips", "diamond_push_up", "chin_up", "muscle_up",
        ),
        // Muñeca muy extendida con carga (posición de rack, apoyos con todo el peso) o barra recta
        CareZone.WRIST to setOf(
            "wrist_curl", "front_squat", "barbell_curl", "preacher_curl", "clean_and_press", "diamond_push_up",
            "archer_push_up", "one_arm_push_up", "pike_push_up", "l_sit", "burpee", "muscle_up",
        ),
        // Trapecio superior y cervicales cargados, o tirar del cuello al flexionarlo
        CareZone.NECK to setOf("shrug", "upright_row", "farmers_walk", "crunch", "overhead_press", "clean_and_press", "muscle_up"),
        // Bisagras con carga, remos sin apoyo, carga axial pesada y extensión o rotación lumbar
        CareZone.LOWER_BACK to setOf(
            "deadlift", "romanian_deadlift", "barbell_row", "pendlay_row", "t_bar_row", "squat", "hyperextension",
            "kb_swing", "clean_and_press", "overhead_press", "ab_wheel", "russian_twist", "hanging_leg_raise", "burpee",
        ),
        // Flexión profunda de cadera con carga, apoyo a una pierna e impacto
        CareZone.HIP to setOf(
            "squat", "front_squat", "lunge", "bulgarian_split_squat", "pistol_squat", "shrimp_squat",
            "hanging_leg_raise", "l_sit", "running", "burpee",
        ),
        // Rodilla muy doblada con carga, cadena abierta cerca de la extensión e impacto
        CareZone.KNEE to setOf(
            "squat", "front_squat", "hack_squat", "lunge", "bulgarian_split_squat", "pistol_squat", "shrimp_squat",
            "leg_extension", "running", "jump_rope", "burpee", "stair_climber",
        ),
        // Impacto, mucha carga en el Aquiles y equilibrio a una pierna
        CareZone.ANKLE to setOf(
            "running", "jump_rope", "burpee", "stair_climber", "standing_calf_raise", "pistol_squat", "shrimp_squat",
            "lunge", "bulgarian_split_squat",
        ),
    )

    /** Ejercicios extra de cada zona, por preferencia (de varios materiales). */
    private val extras: Map<FocusArea, List<String>> = mapOf(
        FocusArea.CHEST to listOf("pec_deck", "cable_crossover", "machine_chest_press", "db_fly", "db_incline_press", "push_up", "incline_push_up", "knee_push_up"),
        FocusArea.BACK to listOf("seated_cable_row", "lat_pulldown", "straight_arm_pulldown", "db_row", "pull_up", "band_pull_up", "negative_pull_up", "shrug"),
        FocusArea.SHOULDERS to listOf("cable_lateral_raise", "lateral_raise", "rear_delt_fly", "face_pull", "db_shoulder_press", "pike_push_up"),
        FocusArea.ARMS to listOf("triceps_pushdown", "cable_curl", "overhead_triceps_ext", "hammer_curl", "incline_db_curl", "db_curl", "diamond_push_up", "chin_up"),
        FocusArea.GLUTES to listOf("hip_thrust", "hip_abduction", "glute_kickback", "bulgarian_split_squat", "lunge", "hyperextension", "kb_swing"),
        FocusArea.LEGS to listOf("leg_press", "seated_leg_curl", "leg_extension", "leg_curl", "goblet_squat", "bulgarian_split_squat", "romanian_deadlift", "air_squat"),
        FocusArea.CALVES to listOf("seated_calf_raise", "standing_calf_raise"),
        FocusArea.CORE to listOf("cable_crunch", "hanging_leg_raise", "ab_wheel", "side_plank", "plank", "hollow_hold", "crunch"),
    )

    /** Ids que se evitan con esa zona. */
    fun excludedIds(zone: CareZone): Set<String> = excluded.getValue(zone)

    /** Si el ejercicio se puede usar con esas zonas a cuidar. */
    fun isSafe(exercise: Exercise, zones: Set<CareZone>): Boolean = zones.none { exercise.id in excluded.getValue(it) }

    /**
     * Refuerza [focus] en los días de fuerza ([days], sin remate ni cardio): +1 serie a cada ejercicio de esa zona
     * (hasta [MAX_SETS]) y, si [addExtras] (no a principiantes), un ejercicio extra en cada día que ya la entrena,
     * de un músculo que ese día trabaja. Si ningún día la entrena (core, gemelos), el extra va a los dos días con
     * menos ejercicios, separados. [build] crea el ejercicio extra con sus series.
     */
    fun reinforce(
        days: List<List<RoutineExercise>>,
        focus: Set<FocusArea>,
        addExtras: Boolean,
        usable: (Exercise) -> Boolean,
        build: (Exercise) -> RoutineExercise,
    ): List<List<RoutineExercise>> {
        val areas = focus.sortedBy { it.ordinal }.take(MAX_FOCUS)
        if (areas.isEmpty()) return days
        val muscles = areas.flatMapTo(HashSet()) { it.muscles }
        var out = days.map { day ->
            day.map { re ->
                val more = re.exercise.primaryMuscle in muscles && re.sets.isNotEmpty() && re.sets.size < MAX_SETS
                if (more) re.copy(sets = re.sets + re.sets.last()) else re
            }
        }
        if (addExtras) {
            areas.forEach { area ->
                val trained = out.indices.filter { i -> out[i].any { it.exercise.primaryMuscle in area.muscles } }
                val targets = trained.ifEmpty { lightestDays(out) }
                out = out.mapIndexed { i, day ->
                    // En un día que ya la entrena, un músculo de ese día (tríceps el de empuje, bíceps el de tirón)
                    val wanted = if (trained.isEmpty()) area.muscles else area.muscles.filterTo(HashSet()) { m -> day.any { it.exercise.primaryMuscle == m } }
                    if (i in targets) withExtra(day, area, wanted, usable, build) else day
                }
            }
        }
        return out
    }

    private fun withExtra(
        day: List<RoutineExercise>,
        area: FocusArea,
        wanted: Set<MuscleGroup>,
        usable: (Exercise) -> Boolean,
        build: (Exercise) -> RoutineExercise,
    ): List<RoutineExercise> {
        val pick = extras.getValue(area).mapNotNull(ExerciseLibrary::byId)
            .firstOrNull { ex -> ex.primaryMuscle in wanted && usable(ex) && day.none { it.exercise.id == ex.id } }
            ?.takeIf { day.size < MAX_STRENGTH } ?: return day
        // Al final de la parte de fuerza, antes del core (salvo que el extra sea de core)
        val coreFrom = day.indexOfLast { it.exercise.primaryMuscle != MuscleGroup.ABS } + 1
        val at = if (pick.primaryMuscle == MuscleGroup.ABS) day.size else coreFrom.coerceAtLeast(minOf(2, day.size))
        return day.toMutableList().apply { add(at, build(pick)) }
    }

    /** Los (hasta) dos días con menos ejercicios, a ser posible no seguidos. */
    private fun lightestDays(days: List<List<RoutineExercise>>): List<Int> {
        if (days.isEmpty()) return emptyList()
        val byLoad = days.indices.sortedBy { days[it].size }
        val first = byLoad.first()
        val second = byLoad.drop(1).firstOrNull { kotlin.math.abs(it - first) >= 2 } ?: byLoad.getOrNull(1)
        return listOfNotNull(first, second)
    }
}
