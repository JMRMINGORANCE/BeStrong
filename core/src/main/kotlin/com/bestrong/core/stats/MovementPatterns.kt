package com.bestrong.core.stats

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout

/** Patrón de movimiento de un ejercicio (para ver si el entrenamiento está equilibrado). */
enum class MovementPattern {
    PUSH_HORIZONTAL,
    PUSH_VERTICAL,
    PULL_HORIZONTAL,
    PULL_VERTICAL,
    SQUAT,
    HINGE,
    CORE,
}

/** Series de trabajo por patrón y las proporciones que importan (empuje:tirón y bisagra:sentadilla). */
data class PatternBalance(val sets: Map<MovementPattern, Double>) {
    val push: Double get() = (sets[MovementPattern.PUSH_HORIZONTAL] ?: 0.0) + (sets[MovementPattern.PUSH_VERTICAL] ?: 0.0)
    val pull: Double get() = (sets[MovementPattern.PULL_HORIZONTAL] ?: 0.0) + (sets[MovementPattern.PULL_VERTICAL] ?: 0.0)
    val hinge: Double get() = sets[MovementPattern.HINGE] ?: 0.0
    val squat: Double get() = sets[MovementPattern.SQUAT] ?: 0.0

    /** Empuje ÷ tirón (null si no hay tirón). Lo sano: entre 0,67 y 1,5 (mejor algo de tirón de más). */
    val pushPullRatio: Double? get() = if (pull > 0) push / pull else null

    /** Bisagra ÷ sentadilla (null si no hay sentadilla). */
    val hingeSquatRatio: Double? get() = if (squat > 0) hinge / squat else null

    val total: Double get() = sets.values.sum()

    /**
     * Patrones que van cortos: con algo de historial, los que no llegan a la mitad de la media de los patrones. Los
     * más atrasados primero.
     */
    fun lagging(): List<MovementPattern> {
        if (total < MIN_TOTAL_SETS) return emptyList()
        val avg = total / MovementPattern.entries.size
        return MovementPattern.entries.filter { (sets[it] ?: 0.0) < avg * LAGGING_FRACTION }.sortedBy { sets[it] ?: 0.0 }
    }

    companion object {
        const val MIN_TOTAL_SETS = 8.0
        const val LAGGING_FRACTION = 0.5
    }
}

object MovementPatterns {
    private val BY_ID: Map<String, MovementPattern> = buildMap {
        fun put(p: MovementPattern, vararg ids: String) = ids.forEach { put(it, p) }
        put(
            MovementPattern.PUSH_HORIZONTAL,
            "bench_press", "incline_bench_press", "decline_bench_press", "db_bench_press", "db_incline_press", "db_fly",
            "cable_crossover", "pec_deck", "machine_chest_press", "push_up", "dips", "close_grip_bench", "skull_crusher",
            "triceps_pushdown", "wall_push_up", "incline_push_up", "knee_push_up", "diamond_push_up", "archer_push_up", "one_arm_push_up",
        )
        put(
            MovementPattern.PUSH_VERTICAL,
            "overhead_press", "db_shoulder_press", "arnold_press", "lateral_raise", "cable_lateral_raise", "front_raise",
            "pike_push_up", "overhead_triceps_ext", "clean_and_press",
        )
        put(
            MovementPattern.PULL_HORIZONTAL,
            "barbell_row", "db_row", "pendlay_row", "t_bar_row", "seated_cable_row", "rear_delt_fly", "face_pull",
            "barbell_curl", "db_curl", "hammer_curl", "preacher_curl", "cable_curl", "incline_db_curl",
        )
        put(
            MovementPattern.PULL_VERTICAL,
            "lat_pulldown", "pull_up", "chin_up", "straight_arm_pulldown", "upright_row", "shrug", "dead_hang",
            "scapular_pull_up", "negative_pull_up", "band_pull_up", "chest_to_bar", "muscle_up",
        )
        put(
            MovementPattern.SQUAT,
            "squat", "front_squat", "smith_squat", "goblet_squat", "hack_squat", "leg_press", "bulgarian_split_squat",
            "lunge", "leg_extension", "assisted_squat", "air_squat", "shrimp_squat", "pistol_squat", "wall_sit",
            "standing_calf_raise", "seated_calf_raise",
        )
        put(
            MovementPattern.HINGE,
            "deadlift", "romanian_deadlift", "leg_curl", "seated_leg_curl", "hip_thrust", "glute_kickback", "hip_abduction",
            "hyperextension", "kb_swing",
        )
        put(
            MovementPattern.CORE,
            "plank", "crunch", "hanging_leg_raise", "cable_crunch", "ab_wheel", "russian_twist", "hollow_hold", "side_plank",
            "l_sit", "farmers_walk",
        )
    }

    /** Patrón de un ejercicio: el de la biblioteca o, si es propio, el de su músculo principal (null si no aplica). */
    fun of(exercise: Exercise): MovementPattern? = BY_ID[exercise.id] ?: byMuscle(exercise.primaryMuscle)

    fun byMuscle(m: MuscleGroup): MovementPattern? = when (m) {
        MuscleGroup.CHEST, MuscleGroup.TRICEPS -> MovementPattern.PUSH_HORIZONTAL
        MuscleGroup.SHOULDERS -> MovementPattern.PUSH_VERTICAL
        MuscleGroup.BACK, MuscleGroup.BICEPS -> MovementPattern.PULL_HORIZONTAL
        MuscleGroup.TRAPS -> MovementPattern.PULL_VERTICAL
        MuscleGroup.QUADRICEPS, MuscleGroup.CALVES -> MovementPattern.SQUAT
        MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> MovementPattern.HINGE
        MuscleGroup.ABS -> MovementPattern.CORE
        MuscleGroup.FOREARMS, MuscleGroup.FULL_BODY, MuscleGroup.CARDIO -> null
    }

    /** Series de trabajo hechas por patrón en los últimos [days] días. */
    fun balance(history: List<Workout>, nowEpochMs: Long, days: Int = 28): PatternBalance {
        val since = nowEpochMs - days * DAY_MS
        val sets = mutableMapOf<MovementPattern, Double>()
        history.filter { it.isFinished && it.startedAtEpochMs >= since }.forEach { w ->
            w.exercises.forEach { we ->
                val p = of(we.exercise) ?: return@forEach
                val n = we.completedSets.count { it.type != SetType.WARMUP }
                if (n > 0) sets[p] = (sets[p] ?: 0.0) + n
            }
        }
        return PatternBalance(sets)
    }

    /**
     * Ejercicios de la biblioteca para un patrón que va corto: primero los que ya has hecho alguna vez (los conoces),
     * luego el resto; sin los ocultos.
     */
    fun suggestions(pattern: MovementPattern, history: List<Workout>, hidden: Set<String> = emptySet(), limit: Int = 3): List<Exercise> {
        val done = history.flatMap { w -> w.exercises.map { it.exercise.id } }.groupingBy { it }.eachCount()
        return ExerciseLibrary.all
            .filter { BY_ID[it.id] == pattern && it.id !in hidden }
            .sortedByDescending { done[it.id] ?: 0 }
            .take(limit)
    }

    private const val DAY_MS = 86_400_000L
}
