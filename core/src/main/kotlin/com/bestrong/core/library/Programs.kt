package com.bestrong.core.library

import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.util.newId

/** Ejercicio de una plantilla: id de la biblioteca, series × reps y descanso. */
data class TemplateItem(val exerciseId: String, val sets: Int, val reps: Int, val restSeconds: Int = 120, val warmup: Boolean = false)

data class RoutineTemplate(val nameEs: String, val nameEn: String, val items: List<TemplateItem>, val supersetPairs: List<Pair<Int, Int>> = emptyList())

/** Programa de entrenamiento predefinido: crea varias rutinas de una vez. */
data class TrainingProgram(
    val id: String,
    val nameEs: String,
    val nameEn: String,
    val descriptionEs: String,
    val descriptionEn: String,
    val daysPerWeek: Int,
    val routines: List<RoutineTemplate>,
) {
    fun name(language: String) = if (language == "en") nameEn else nameEs
    fun description(language: String) = if (language == "en") descriptionEn else descriptionEs

    /** Crea las rutinas (sin pesos: se rellenan con la última sesión o la progresión). */
    fun toRoutines(language: String): List<Routine> = routines.map { t ->
        val exercises = t.items.map { item ->
            val ex = ExerciseLibrary.byId(item.exerciseId) ?: error("Ejercicio desconocido: ${item.exerciseId}")
            val warmups = if (item.warmup) listOf(RoutineSet(reps = 10, type = SetType.WARMUP)) else emptyList()
            RoutineExercise(
                exercise = ex,
                sets = warmups + List(item.sets) { RoutineSet(reps = item.reps, durationSec = if (ex.tracking == com.bestrong.core.model.TrackingType.DURATION) item.reps else 0) },
                restSeconds = item.restSeconds,
            )
        }.toMutableList()
        t.supersetPairs.forEach { (a, b) ->
            val group = newId()
            exercises[a] = exercises[a].copy(supersetId = group)
            exercises[b] = exercises[b].copy(supersetId = group)
        }
        Routine(name = "${name(language)} · ${if (language == "en") t.nameEn else t.nameEs}", exercises = exercises)
    }
}

object Programs {
    private fun i(id: String, sets: Int, reps: Int, rest: Int = 120, warmup: Boolean = false) = TemplateItem(id, sets, reps, rest, warmup)

    val all: List<TrainingProgram> = listOf(
        TrainingProgram(
            "full_body_3", "Full Body 3 días", "Full Body 3 days",
            "Cuerpo completo tres veces por semana. Ideal para empezar o con poco tiempo.",
            "Whole body three times a week. Great for beginners or busy schedules.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 3, 8, 180, true), i("bench_press", 3, 8, 150, true), i("barbell_row", 3, 10), i("lateral_raise", 3, 12, 60), i("plank", 3, 45, 60))),
                RoutineTemplate("B", "B", listOf(i("deadlift", 3, 5, 180, true), i("overhead_press", 3, 8, 150, true), i("lat_pulldown", 3, 10), i("db_curl", 3, 12, 60), i("triceps_pushdown", 3, 12, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("C", "C", listOf(i("leg_press", 3, 10, 150), i("db_incline_press", 3, 10), i("seated_cable_row", 3, 10), i("romanian_deadlift", 3, 10), i("hanging_leg_raise", 3, 12, 60))),
            ),
        ),
        TrainingProgram(
            "ppl_6", "Push / Pull / Legs", "Push / Pull / Legs",
            "Empuje, tirón y pierna. Repite el ciclo dos veces por semana para máxima hipertrofia.",
            "Push, pull and legs. Run the cycle twice a week for maximum hypertrophy.",
            6,
            listOf(
                RoutineTemplate("Empuje", "Push", listOf(i("bench_press", 4, 6, 180, true), i("overhead_press", 3, 8, 150), i("db_incline_press", 3, 10), i("lateral_raise", 4, 12, 60), i("triceps_pushdown", 3, 12, 60), i("overhead_triceps_ext", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Tirón", "Pull", listOf(i("deadlift", 3, 5, 180, true), i("pull_up", 3, 8, 150), i("barbell_row", 3, 10), i("face_pull", 3, 15, 60), i("barbell_curl", 3, 10, 60), i("hammer_curl", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna", "Legs", listOf(i("squat", 4, 6, 180, true), i("romanian_deadlift", 3, 10, 150), i("leg_press", 3, 12), i("leg_curl", 3, 12, 60), i("standing_calf_raise", 4, 12, 60))),
            ),
        ),
        TrainingProgram(
            "upper_lower_4", "Torso / Pierna 4 días", "Upper / Lower 4 days",
            "Dos días de torso y dos de pierna, alternando fuerza e hipertrofia.",
            "Two upper and two lower days, alternating strength and hypertrophy.",
            4,
            listOf(
                RoutineTemplate("Torso fuerza", "Upper strength", listOf(i("bench_press", 4, 5, 180, true), i("barbell_row", 4, 6, 150), i("overhead_press", 3, 6, 150), i("pull_up", 3, 6, 150))),
                RoutineTemplate("Pierna fuerza", "Lower strength", listOf(i("squat", 4, 5, 180, true), i("romanian_deadlift", 3, 6, 150), i("leg_press", 3, 8), i("standing_calf_raise", 3, 10, 60))),
                RoutineTemplate("Torso hipertrofia", "Upper hypertrophy", listOf(i("db_incline_press", 3, 10), i("seated_cable_row", 3, 12), i("lateral_raise", 4, 15, 60), i("cable_crossover", 3, 12, 60), i("db_curl", 3, 12, 60), i("triceps_pushdown", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna hipertrofia", "Lower hypertrophy", listOf(i("front_squat", 3, 8, 150), i("bulgarian_split_squat", 3, 10), i("leg_extension", 3, 12, 60), i("leg_curl", 3, 12, 60), i("hip_thrust", 3, 10)), supersetPairs = listOf(2 to 3)),
            ),
        ),
        TrainingProgram(
            "stronglifts_5x5", "5×5 Fuerza", "5×5 Strength",
            "Dos rutinas alternas con básicos a 5×5 (peso muerto 1×5). Sube peso cada sesión.",
            "Two alternating workouts of compound lifts at 5×5 (deadlift 1×5). Add weight every session.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 5, 5, 180, true), i("bench_press", 5, 5, 180, true), i("barbell_row", 5, 5, 180))),
                RoutineTemplate("B", "B", listOf(i("squat", 5, 5, 180, true), i("overhead_press", 5, 5, 180, true), i("deadlift", 1, 5, 180, true))),
            ),
        ),
        TrainingProgram(
            "glutes_legs", "Glúteo y pierna", "Glutes & legs",
            "Dos días centrados en glúteos y piernas más uno de torso.",
            "Two glute- and leg-focused days plus one upper-body day.",
            3,
            listOf(
                RoutineTemplate("Glúteo", "Glutes", listOf(i("hip_thrust", 4, 10, 150, true), i("romanian_deadlift", 3, 10, 150), i("bulgarian_split_squat", 3, 10), i("glute_kickback", 3, 15, 60), i("hip_abduction", 3, 15, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Pierna", "Legs", listOf(i("squat", 4, 8, 180, true), i("leg_press", 3, 12), i("leg_curl", 3, 12, 60), i("leg_extension", 3, 12, 60), i("standing_calf_raise", 3, 15, 60)), supersetPairs = listOf(2 to 3)),
                RoutineTemplate("Torso", "Upper", listOf(i("db_bench_press", 3, 10), i("lat_pulldown", 3, 10), i("db_shoulder_press", 3, 10), i("seated_cable_row", 3, 12), i("plank", 3, 45, 60))),
            ),
        ),
    )
}
