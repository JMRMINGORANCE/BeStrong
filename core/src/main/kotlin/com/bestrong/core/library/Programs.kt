package com.bestrong.core.library

import com.bestrong.core.model.Routine
import com.bestrong.core.model.RoutineExercise
import com.bestrong.core.model.RoutineSet
import com.bestrong.core.model.SetType
import com.bestrong.core.util.newId

/** Ejercicio de una plantilla: id de la biblioteca, series × reps y descanso (en isométricos y cardio, reps = segundos). */
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
    // En el idioma de la app (con traducción a los que no son español ni inglés)
    fun name(language: String) = com.bestrong.core.i18n.ContentText.pick(nameEs, nameEn, language)
    fun description(language: String) = com.bestrong.core.i18n.ContentText.pick(descriptionEs, descriptionEn, language)

    /** Crea las rutinas (sin pesos: se rellenan con la última sesión o la progresión). */
    fun toRoutines(language: String): List<Routine> = routines.map { t ->
        val exercises = t.items.map { item ->
            val ex = ExerciseLibrary.byId(item.exerciseId) ?: error("Ejercicio desconocido: ${item.exerciseId}")
            val warmups = if (item.warmup) listOf(RoutineSet(reps = 10, type = SetType.WARMUP)) else emptyList()
            RoutineExercise(
                exercise = ex,
                // Isométricos y cardio: [TemplateItem.reps] son segundos (el cardio no lleva reps)
                sets = warmups + List(item.sets) {
                    val cardio = ex.tracking == com.bestrong.core.model.TrackingType.CARDIO
                    val timed = cardio || ex.tracking == com.bestrong.core.model.TrackingType.DURATION
                    RoutineSet(reps = if (cardio) 0 else item.reps, durationSec = if (timed) item.reps else 0)
                },
                restSeconds = item.restSeconds,
            )
        }.toMutableList()
        t.supersetPairs.forEach { (a, b) ->
            val group = newId()
            exercises[a] = exercises[a].copy(supersetId = group)
            exercises[b] = exercises[b].copy(supersetId = group)
        }
        Routine(name = "${name(language)} · ${com.bestrong.core.i18n.ContentText.pick(t.nameEs, t.nameEn, language)}", exercises = exercises)
    }
}

object Programs {
    private fun i(id: String, sets: Int, reps: Int, rest: Int = 120, warmup: Boolean = false) = TemplateItem(id, sets, reps, rest, warmup)

    /** Bloque de cardio al final: una serie de [minutes] minutos. */
    private fun cardio(id: String, minutes: Int) = TemplateItem(id, 1, minutes * 60, 0)

    val all: List<TrainingProgram> = listOf(
        // --- Principiantes / cuerpo completo ---
        TrainingProgram(
            "full_body_3", "Full Body 3 días", "Full Body 3 days",
            "Para principiantes o quien entrena 3 días: cuerpo completo A/B/C con los básicos primero (3 series de 8-12). Sube peso cuando completes todas las reps.",
            "For beginners or anyone training 3 days a week: full body A/B/C with the main lifts first (3 sets of 8-12). Add weight once you hit all the reps.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 3, 8, 150, true), i("bench_press", 3, 8, 120, true), i("barbell_row", 3, 10, 90), i("leg_curl", 3, 12, 60), i("lateral_raise", 3, 12, 60), i("plank", 3, 45, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("B", "B", listOf(i("romanian_deadlift", 3, 8, 150, true), i("overhead_press", 3, 8, 120, true), i("lat_pulldown", 3, 10, 90), i("leg_press", 3, 12, 90), i("db_curl", 2, 12, 60), i("triceps_pushdown", 2, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("C", "C", listOf(i("bulgarian_split_squat", 3, 10, 90), i("db_incline_press", 3, 10, 90), i("seated_cable_row", 3, 12, 90), i("hip_thrust", 3, 10, 90), i("face_pull", 3, 15, 60), i("cable_crunch", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
            ),
        ),
        TrainingProgram(
            "full_body_2", "Full Body 2 días", "Full Body 2 days",
            "Para principiantes o agendas muy ocupadas: dos sesiones completas por semana con los básicos. Suficiente para ganar fuerza y salud con poco tiempo.",
            "For beginners or very busy schedules: two full-body sessions a week built on the main lifts. Enough to gain strength and health on little time.",
            2,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 3, 8, 150, true), i("bench_press", 3, 8, 120, true), i("lat_pulldown", 3, 10, 90), i("romanian_deadlift", 3, 10, 120), i("lateral_raise", 2, 15, 60), i("plank", 3, 30, 60))),
                RoutineTemplate("B", "B", listOf(i("deadlift", 3, 5, 180, true), i("db_incline_press", 3, 10, 90), i("seated_cable_row", 3, 10, 90), i("leg_press", 3, 12, 90), i("db_shoulder_press", 2, 10, 90), i("crunch", 3, 15, 60))),
            ),
        ),
        // --- Fuerza ---
        TrainingProgram(
            "stronglifts_5x5", "5×5 Fuerza", "5×5 Strength",
            "Para principiantes que quieren fuerza: dos rutinas alternas con básicos a 5×5 (peso muerto 1×5). Sube 2,5 kg cada sesión mientras puedas.",
            "For beginners chasing strength: two alternating workouts of compound lifts at 5×5 (deadlift 1×5). Add 2.5 kg every session while you can.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 5, 5, 180, true), i("bench_press", 5, 5, 180, true), i("barbell_row", 5, 5, 180))),
                RoutineTemplate("B", "B", listOf(i("squat", 5, 5, 180, true), i("overhead_press", 5, 5, 180, true), i("deadlift", 1, 5, 180, true))),
            ),
        ),
        TrainingProgram(
            "powerlifting_4", "Powerlifting 4 días", "Powerlifting 4 days",
            "Intermedio-avanzado que compite o quiere su máximo en sentadilla, banca y peso muerto: un día pesado por básico (5×3) más banca de volumen. Combínalo con el pico de Planificación antes de competir.",
            "Intermediate to advanced lifters who compete or want their best squat, bench and deadlift: one heavy day per lift (5×3) plus a bench volume day. Combine it with the peaking plan in Planning before a meet.",
            4,
            listOf(
                RoutineTemplate("Sentadilla", "Squat", listOf(i("squat", 5, 3, 240, true), i("front_squat", 3, 5, 180), i("leg_curl", 3, 10, 90), i("ab_wheel", 3, 10, 60))),
                RoutineTemplate("Banca", "Bench", listOf(i("bench_press", 5, 3, 240, true), i("close_grip_bench", 3, 6, 180), i("pendlay_row", 4, 6, 120), i("face_pull", 3, 15, 60), i("triceps_pushdown", 3, 12, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Peso muerto", "Deadlift", listOf(i("deadlift", 4, 3, 240, true), i("squat", 3, 6, 180), i("pull_up", 3, 8, 120), i("hyperextension", 3, 12, 60))),
                RoutineTemplate("Banca volumen", "Bench volume", listOf(i("bench_press", 4, 8, 150, true), i("overhead_press", 3, 6, 150), i("db_row", 3, 10, 90), i("dips", 3, 10, 90), i("hammer_curl", 3, 12, 60))),
            ),
        ),
        // --- Hipertrofia (intermedio / avanzado) ---
        TrainingProgram(
            "upper_lower_4", "Torso / Pierna 4 días", "Upper / Lower 4 days",
            "Intermedio, 4 días: dos de torso y dos de pierna, uno de fuerza (5-6 reps) y otro de hipertrofia (10-15 reps) por mitad. Unas 12-16 series por músculo a la semana.",
            "Intermediate, 4 days: two upper and two lower days, one strength (5-6 reps) and one hypertrophy (10-15 reps) day for each half. About 12-16 sets per muscle per week.",
            4,
            listOf(
                RoutineTemplate("Torso fuerza", "Upper strength", listOf(i("bench_press", 4, 5, 180, true), i("barbell_row", 4, 6, 150), i("overhead_press", 3, 6, 150), i("pull_up", 3, 6, 150), i("skull_crusher", 2, 10, 60), i("barbell_curl", 2, 10, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna fuerza", "Lower strength", listOf(i("squat", 4, 5, 180, true), i("romanian_deadlift", 3, 6, 150), i("leg_press", 3, 10, 120), i("leg_curl", 3, 10, 60), i("standing_calf_raise", 4, 10, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Torso hipertrofia", "Upper hypertrophy", listOf(i("db_incline_press", 3, 10, 90), i("seated_cable_row", 3, 12, 90), i("lat_pulldown", 3, 12, 90), i("cable_crossover", 3, 15, 60), i("lateral_raise", 4, 15, 60), i("db_curl", 3, 12, 60), i("triceps_pushdown", 3, 12, 60)), supersetPairs = listOf(5 to 6)),
                RoutineTemplate("Pierna hipertrofia", "Lower hypertrophy", listOf(i("hack_squat", 3, 10, 120, true), i("hip_thrust", 3, 10, 90), i("bulgarian_split_squat", 3, 10, 90), i("leg_extension", 3, 15, 60), i("seated_leg_curl", 3, 12, 60), i("seated_calf_raise", 3, 15, 60)), supersetPairs = listOf(3 to 4)),
            ),
        ),
        TrainingProgram(
            "phul_4", "PHUL", "PHUL",
            "Intermedio-avanzado: dos días de potencia (3-5 reps en los básicos) y dos de hipertrofia (8-15), torso y pierna. Fuerza y músculo a la vez en 4 días.",
            "Intermediate to advanced: two power days (3-5 reps on the main lifts) and two hypertrophy days (8-15), upper and lower. Strength and size together in 4 days.",
            4,
            listOf(
                RoutineTemplate("Torso potencia", "Upper power", listOf(i("bench_press", 4, 4, 180, true), i("barbell_row", 4, 4, 180), i("incline_bench_press", 3, 8, 120), i("lat_pulldown", 3, 8, 90), i("overhead_press", 3, 6, 120), i("barbell_curl", 3, 8, 60), i("skull_crusher", 3, 8, 60)), supersetPairs = listOf(5 to 6)),
                RoutineTemplate("Pierna potencia", "Lower power", listOf(i("squat", 4, 4, 180, true), i("deadlift", 3, 4, 180, true), i("leg_press", 3, 10, 120), i("leg_curl", 3, 8, 60), i("standing_calf_raise", 4, 8, 60))),
                RoutineTemplate("Torso hipertrofia", "Upper hypertrophy", listOf(i("db_incline_press", 4, 10, 90), i("seated_cable_row", 4, 10, 90), i("db_fly", 3, 12, 60), i("db_row", 3, 12, 60), i("lateral_raise", 3, 15, 60), i("incline_db_curl", 3, 12, 60), i("triceps_pushdown", 3, 12, 60)), supersetPairs = listOf(5 to 6)),
                RoutineTemplate("Pierna hipertrofia", "Lower hypertrophy", listOf(i("front_squat", 3, 10, 150, true), i("lunge", 3, 12, 90), i("leg_extension", 3, 15, 60), i("seated_leg_curl", 3, 15, 60), i("seated_calf_raise", 4, 15, 60)), supersetPairs = listOf(2 to 3)),
            ),
        ),
        TrainingProgram(
            "ppl_ul_5", "Torso / Pierna + PPL 5 días", "Upper / Lower + PPL 5 days",
            "Intermedio, 5 días para ganar músculo: torso y pierna de fuerza más empuje, tirón y pierna de hipertrofia. Cada músculo dos veces por semana.",
            "Intermediate, 5 days for muscle gain: strength upper and lower days plus hypertrophy push, pull and legs. Every muscle twice a week.",
            5,
            listOf(
                RoutineTemplate("Torso fuerza", "Upper strength", listOf(i("bench_press", 4, 6, 150, true), i("barbell_row", 4, 6, 150), i("overhead_press", 3, 8, 120), i("lat_pulldown", 3, 10, 90), i("db_curl", 2, 12, 60), i("triceps_pushdown", 2, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna fuerza", "Lower strength", listOf(i("squat", 4, 6, 180, true), i("romanian_deadlift", 3, 8, 150), i("leg_press", 3, 10, 120), i("leg_curl", 3, 12, 60), i("standing_calf_raise", 4, 12, 60))),
                RoutineTemplate("Empuje", "Push", listOf(i("db_incline_press", 4, 10, 90), i("db_shoulder_press", 3, 10, 90), i("machine_chest_press", 3, 12, 90), i("cable_crossover", 3, 15, 60), i("lateral_raise", 4, 15, 60), i("overhead_triceps_ext", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Tirón", "Pull", listOf(i("pull_up", 4, 8, 120), i("seated_cable_row", 3, 12, 90), i("db_row", 3, 10, 90), i("face_pull", 3, 15, 60), i("incline_db_curl", 3, 12, 60), i("hammer_curl", 2, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna hipertrofia", "Lower hypertrophy", listOf(i("hack_squat", 3, 10, 120, true), i("hip_thrust", 3, 10, 90), i("bulgarian_split_squat", 3, 10, 90), i("leg_extension", 3, 15, 60), i("seated_leg_curl", 3, 15, 60), i("seated_calf_raise", 3, 15, 60)), supersetPairs = listOf(3 to 4)),
            ),
        ),
        TrainingProgram(
            "ppl_6", "Push / Pull / Legs", "Push / Pull / Legs",
            "Intermedio-avanzado, 6 días: empuje, tirón y pierna dos veces por semana, el bloque A más pesado (6-10 reps) y el B con más volumen (10-15). Máxima hipertrofia; necesita buena recuperación.",
            "Intermediate to advanced, 6 days: push, pull and legs twice a week, block A heavier (6-10 reps) and block B higher volume (10-15). Maximum hypertrophy; needs good recovery.",
            6,
            listOf(
                RoutineTemplate("Empuje A", "Push A", listOf(i("bench_press", 4, 6, 180, true), i("overhead_press", 3, 8, 150), i("db_incline_press", 3, 10, 90), i("lateral_raise", 4, 15, 60), i("triceps_pushdown", 3, 12, 60), i("overhead_triceps_ext", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Tirón A", "Pull A", listOf(i("deadlift", 3, 5, 180, true), i("pull_up", 4, 8, 120), i("barbell_row", 3, 10, 90), i("face_pull", 3, 15, 60), i("barbell_curl", 3, 10, 60), i("hammer_curl", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna A", "Legs A", listOf(i("squat", 4, 6, 180, true), i("romanian_deadlift", 3, 10, 150), i("leg_press", 3, 12, 120), i("leg_curl", 3, 12, 60), i("standing_calf_raise", 4, 12, 60), i("hanging_leg_raise", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Empuje B", "Push B", listOf(i("incline_bench_press", 4, 8, 150, true), i("db_shoulder_press", 3, 10, 90), i("machine_chest_press", 3, 12, 90), i("cable_crossover", 3, 15, 60), i("cable_lateral_raise", 4, 15, 60), i("skull_crusher", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Tirón B", "Pull B", listOf(i("t_bar_row", 4, 8, 120, true), i("lat_pulldown", 3, 10, 90), i("seated_cable_row", 3, 12, 90), i("rear_delt_fly", 3, 15, 60), i("incline_db_curl", 3, 12, 60), i("cable_curl", 3, 15, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Pierna B", "Legs B", listOf(i("hack_squat", 3, 10, 120, true), i("hip_thrust", 3, 10, 90), i("bulgarian_split_squat", 3, 10, 90), i("leg_extension", 3, 15, 60), i("seated_leg_curl", 3, 12, 60), i("seated_calf_raise", 4, 15, 60)), supersetPairs = listOf(3 to 4)),
            ),
        ),
        TrainingProgram(
            "phat_5", "PHAT", "PHAT",
            "Avanzado, 5 días: dos días de potencia y tres de hipertrofia por grupos, con series rápidas de velocidad (6×3 al 65-70 %). Mucho volumen; solo con experiencia.",
            "Advanced, 5 days: two power days and three hypertrophy days by body part, with speed sets (6×3 at 65-70%). High volume; for experienced lifters only.",
            5,
            listOf(
                RoutineTemplate("Torso potencia", "Upper power", listOf(i("pendlay_row", 3, 5, 180, true), i("pull_up", 2, 8, 150), i("bench_press", 3, 5, 180, true), i("dips", 2, 8, 120), i("db_shoulder_press", 3, 8, 90), i("barbell_curl", 2, 8, 60), i("skull_crusher", 2, 8, 60)), supersetPairs = listOf(5 to 6)),
                RoutineTemplate("Pierna potencia", "Lower power", listOf(i("squat", 3, 5, 180, true), i("hack_squat", 2, 8, 150), i("romanian_deadlift", 3, 6, 150), i("leg_extension", 2, 10, 60), i("leg_curl", 2, 8, 60), i("standing_calf_raise", 3, 8, 60)), supersetPairs = listOf(3 to 4)),
                // Velocidad: 6×3 rápidas al 65-70 % con descanso corto
                RoutineTemplate("Espalda y hombro", "Back & shoulders", listOf(i("barbell_row", 6, 3, 90, true), i("lat_pulldown", 3, 10, 90), i("seated_cable_row", 3, 12, 90), i("db_shoulder_press", 3, 10, 90), i("lateral_raise", 3, 15, 60), i("shrug", 2, 15, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna hipertrofia", "Lower hypertrophy", listOf(i("squat", 6, 3, 90, true), i("hack_squat", 3, 10, 120), i("leg_press", 2, 15, 120), i("leg_extension", 3, 15, 60), i("leg_curl", 3, 15, 60), i("seated_calf_raise", 4, 12, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Pecho y brazos", "Chest & arms", listOf(i("db_bench_press", 6, 3, 90, true), i("db_incline_press", 3, 10, 90), i("cable_crossover", 2, 15, 60), i("preacher_curl", 3, 10, 60), i("overhead_triceps_ext", 3, 10, 60), i("hammer_curl", 2, 12, 60), i("triceps_pushdown", 2, 15, 60)), supersetPairs = listOf(3 to 4, 5 to 6)),
            ),
        ),
        // --- Casos especiales ---
        TrainingProgram(
            "glutes_legs", "Glúteo y pierna", "Glutes & legs",
            "Para priorizar glúteos y piernas: un día de glúteo (hip thrust, peso muerto rumano), uno de pierna y uno de torso para mantener el equilibrio. Todos los niveles.",
            "To prioritise glutes and legs: a glute day (hip thrust, Romanian deadlift), a leg day and an upper-body day to stay balanced. All levels.",
            3,
            listOf(
                RoutineTemplate("Glúteo", "Glutes", listOf(i("hip_thrust", 4, 10, 150, true), i("romanian_deadlift", 3, 10, 150), i("bulgarian_split_squat", 3, 10, 90), i("glute_kickback", 3, 15, 60), i("hip_abduction", 3, 20, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Pierna", "Legs", listOf(i("squat", 4, 8, 180, true), i("leg_press", 3, 12, 120), i("lunge", 3, 12, 90), i("leg_curl", 3, 12, 60), i("leg_extension", 3, 15, 60), i("standing_calf_raise", 3, 15, 60)), supersetPairs = listOf(3 to 4)),
                RoutineTemplate("Torso", "Upper", listOf(i("db_bench_press", 3, 10, 90), i("lat_pulldown", 3, 10, 90), i("db_shoulder_press", 3, 10, 90), i("seated_cable_row", 3, 12, 90), i("lateral_raise", 3, 15, 60), i("plank", 3, 45, 60)), supersetPairs = listOf(4 to 5)),
            ),
        ),
        TrainingProgram(
            "low_impact_2", "Vuelta al entreno / +60 (bajo impacto)", "Back to training / 60+ (low impact)",
            "Para mayores de 60 o para volver tras un parón o una lesión: máquinas y ejercicios de bajo impacto, 2 series controladas lejos del fallo y cardio suave. Tras 4-6 semanas, pasa a 3 series o a 3 días.",
            "For adults over 60 or coming back after a break or injury: machines and low-impact exercises, 2 controlled sets well short of failure and easy cardio. After 4-6 weeks move to 3 sets or 3 days.",
            2,
            listOf(
                RoutineTemplate("A", "A", listOf(i("leg_press", 2, 12, 90), i("machine_chest_press", 2, 12, 90), i("seated_cable_row", 2, 12, 90), i("seated_leg_curl", 2, 12, 75), i("plank", 2, 20, 60), cardio("stationary_bike", 15))),
                RoutineTemplate("B", "B", listOf(i("goblet_squat", 2, 10, 90), i("lat_pulldown", 2, 12, 90), i("db_shoulder_press", 2, 10, 90), i("hip_abduction", 2, 15, 60), i("standing_calf_raise", 2, 15, 60), cardio("elliptical", 15))),
            ),
        ),
        TrainingProgram(
            "express_30_3", "Exprés 30 min (3 días)", "Express 30 min (3 days)",
            "Para quien tiene unos 30 minutos: un básico pesado y después superseries de músculos opuestos. Cuerpo completo 3 días, ideal para mantener o progresar con poco tiempo.",
            "For when you only have about 30 minutes: one heavy main lift then supersets of opposing muscles. 3-day full body, great to maintain or progress on little time.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 3, 8, 120, true), i("bench_press", 3, 8, 60), i("barbell_row", 3, 8, 60), i("leg_curl", 2, 12, 45), i("lateral_raise", 2, 15, 45)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("B", "B", listOf(i("romanian_deadlift", 3, 8, 120, true), i("db_shoulder_press", 3, 10, 60), i("lat_pulldown", 3, 10, 60), i("leg_press", 2, 12, 45), i("plank", 2, 45, 45)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("C", "C", listOf(i("hip_thrust", 3, 10, 90, true), i("db_incline_press", 3, 10, 60), i("seated_cable_row", 3, 10, 60), i("goblet_squat", 2, 12, 45), i("face_pull", 2, 15, 45)), supersetPairs = listOf(1 to 2, 3 to 4)),
            ),
        ),
        // --- Pérdida de grasa: fuerza para conservar músculo, superseries y cardio al final ---
        TrainingProgram(
            "fat_loss_3", "Pérdida de grasa 3 días", "Fat loss 3 days",
            "Para perder grasa manteniendo el músculo, de principiante a intermedio: cuerpo completo con superseries y descansos cortos, y 15-20 min de cardio al final (cinta inclinada, intervalos en bici, elíptica). Acompáñalo de un ligero déficit calórico.",
            "To lose fat while keeping muscle, beginner to intermediate: full body with supersets and short rests, then 15-20 min of cardio (incline treadmill walk, bike intervals, elliptical). Pair it with a slight calorie deficit.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("squat", 3, 10, 90, true), i("db_bench_press", 3, 12, 60), i("seated_cable_row", 3, 12, 60), i("romanian_deadlift", 3, 12, 60), i("db_shoulder_press", 3, 12, 60), cardio("treadmill", 20)), supersetPairs = listOf(1 to 2, 3 to 4)),
                // Bici: intervalos de 30 s fuertes y 90 s suaves
                RoutineTemplate("B", "B", listOf(i("leg_press", 3, 12, 90), i("lat_pulldown", 3, 12, 60), i("db_incline_press", 3, 12, 60), i("hip_thrust", 3, 12, 60), i("plank", 3, 40, 45), cardio("stationary_bike", 15)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("C", "C", listOf(i("goblet_squat", 3, 12, 60), i("db_row", 3, 12, 60), i("machine_chest_press", 3, 12, 60), i("leg_curl", 3, 12, 60), i("burpee", 3, 10, 45), cardio("elliptical", 20)), supersetPairs = listOf(0 to 1, 2 to 3)),
            ),
        ),
        TrainingProgram(
            "fat_loss_4", "Pérdida de grasa 4 días", "Fat loss 4 days",
            "Para perder grasa, intermedio: torso/pierna 4 días con los básicos pesados para conservar fuerza, accesorios en superseries y cardio al final alternando intervalos (remo, bici) y cardio suave (cinta, escaladora).",
            "Fat loss for intermediates: 4-day upper/lower with heavy main lifts to keep strength, accessories in supersets and cardio at the end alternating intervals (rower, bike) and steady state (treadmill, stair climber).",
            4,
            listOf(
                RoutineTemplate("Torso A", "Upper A", listOf(i("bench_press", 3, 8, 120, true), i("barbell_row", 3, 10, 90), i("db_shoulder_press", 3, 12, 60), i("lat_pulldown", 3, 12, 60), i("lateral_raise", 3, 15, 45), i("triceps_pushdown", 3, 15, 45), cardio("rowing_machine", 15)), supersetPairs = listOf(2 to 3, 4 to 5)),
                RoutineTemplate("Pierna A", "Legs A", listOf(i("squat", 3, 8, 150, true), i("romanian_deadlift", 3, 10, 90), i("leg_press", 3, 15, 60), i("leg_curl", 3, 12, 60), i("plank", 3, 45, 45), cardio("treadmill", 20)), supersetPairs = listOf(2 to 3)),
                RoutineTemplate("Torso B", "Upper B", listOf(i("pull_up", 3, 8, 90), i("db_incline_press", 3, 10, 75), i("db_row", 3, 12, 60), i("push_up", 3, 15, 60), i("face_pull", 3, 15, 45), i("db_curl", 3, 12, 45), cardio("stationary_bike", 15)), supersetPairs = listOf(2 to 3, 4 to 5)),
                RoutineTemplate("Pierna B", "Legs B", listOf(i("hip_thrust", 3, 10, 90, true), i("bulgarian_split_squat", 3, 10, 60), i("seated_leg_curl", 3, 12, 60), i("leg_extension", 3, 15, 45), i("hanging_leg_raise", 3, 12, 45), cardio("stair_climber", 15)), supersetPairs = listOf(1 to 2, 3 to 4)),
            ),
        ),
        // --- En casa con mancuernas (y banco) ---
        TrainingProgram(
            "dumbbell_full_body_3", "Mancuernas: Full Body 3 días", "Dumbbells: Full Body 3 days",
            "Para entrenar en casa con mancuernas y un banco, principiante o intermedio: cuerpo completo 3 días para ganar músculo y fuerza (3 series de 10-15).",
            "For training at home with dumbbells and a bench, beginner or intermediate: 3-day full body to build muscle and strength (3 sets of 10-15).",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("goblet_squat", 3, 10, 120), i("db_bench_press", 3, 10, 90), i("db_row", 3, 10, 90), i("hyperextension", 3, 12, 60), i("lateral_raise", 3, 15, 60), i("plank", 3, 45, 60))),
                RoutineTemplate("B", "B", listOf(i("bulgarian_split_squat", 3, 10, 90), i("db_shoulder_press", 3, 10, 90), i("db_row", 3, 12, 90), i("db_incline_press", 3, 12, 90), i("hammer_curl", 3, 12, 60), i("overhead_triceps_ext", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("C", "C", listOf(i("lunge", 3, 12, 90), i("db_bench_press", 3, 12, 90), i("db_row", 3, 15, 90), i("hyperextension", 3, 15, 60), i("arnold_press", 3, 10, 75), i("rear_delt_fly", 3, 15, 60)), supersetPairs = listOf(4 to 5)),
            ),
        ),
        TrainingProgram(
            "dumbbell_upper_lower_4", "Mancuernas: Torso / Pierna 4 días", "Dumbbells: Upper / Lower 4 days",
            "Intermedio con mancuernas y banco en casa: torso/pierna 4 días, con más volumen por músculo. Usa mancuernas ajustables para poder progresar.",
            "Intermediate with dumbbells and a bench at home: 4-day upper/lower with more volume per muscle. Adjustable dumbbells let you keep progressing.",
            4,
            listOf(
                RoutineTemplate("Torso A", "Upper A", listOf(i("db_bench_press", 4, 8, 120), i("db_row", 4, 10, 90), i("db_shoulder_press", 3, 10, 90), i("db_fly", 3, 12, 60), i("lateral_raise", 3, 15, 60), i("overhead_triceps_ext", 3, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna A", "Legs A", listOf(i("goblet_squat", 4, 10, 120), i("bulgarian_split_squat", 3, 10, 90), i("hyperextension", 3, 12, 75), i("wall_sit", 3, 45, 60), i("russian_twist", 3, 20, 45))),
                RoutineTemplate("Torso B", "Upper B", listOf(i("db_incline_press", 4, 10, 90), i("db_row", 3, 12, 90), i("arnold_press", 3, 10, 90), i("push_up", 3, 15, 60), i("rear_delt_fly", 3, 15, 60), i("hammer_curl", 3, 12, 60), i("db_curl", 2, 12, 60)), supersetPairs = listOf(4 to 5)),
                RoutineTemplate("Pierna B", "Legs B", listOf(i("bulgarian_split_squat", 4, 8, 90), i("goblet_squat", 3, 15, 75), i("lunge", 3, 12, 75), i("hyperextension", 3, 15, 60), i("plank", 3, 45, 45))),
            ),
        ),
        TrainingProgram(
            "fat_loss_home_3", "Pérdida de grasa en casa (mancuernas)", "Fat loss at home (dumbbells)",
            "Para perder grasa en casa con un par de mancuernas (y un banco o silla): superseries con descanso corto y cardio sin máquinas al final (comba, caminar o correr).",
            "Fat loss at home with a pair of dumbbells (and a bench or chair): supersets with short rests and machine-free cardio at the end (jump rope, walking or running).",
            3,
            listOf(
                // Comba: 30 s saltando y 30 s de descanso
                RoutineTemplate("A", "A", listOf(i("goblet_squat", 3, 12, 75), i("db_bench_press", 3, 12, 60), i("db_row", 3, 12, 60), i("lunge", 3, 10, 60), i("db_shoulder_press", 3, 12, 60), cardio("jump_rope", 10)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("B", "B", listOf(i("bulgarian_split_squat", 3, 10, 75), i("push_up", 3, 12, 60), i("rear_delt_fly", 3, 15, 60), i("hyperextension", 3, 15, 60), i("arnold_press", 3, 12, 60), i("plank", 3, 40, 45), cardio("walking", 25)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("C", "C", listOf(i("goblet_squat", 3, 15, 60), i("db_shoulder_press", 3, 12, 60), i("db_row", 3, 12, 60), i("push_up", 3, 12, 60), i("burpee", 3, 10, 45), cardio("running", 20)), supersetPairs = listOf(0 to 1, 2 to 3)),
            ),
        ),
        // --- Peso corporal (sin material) ---
        TrainingProgram(
            "calisthenics_3", "Calistenia 3 días", "Calisthenics 3 days",
            "Solo peso corporal. Empieza por el nivel que puedas y avanza con las progresiones de Planificación.",
            "Bodyweight only. Start at your level and move up with the progressions in Planning.",
            3,
            listOf(
                RoutineTemplate("Empuje y pierna", "Push & legs", listOf(i("push_up", 4, 10, 90), i("dips", 3, 8, 120), i("pike_push_up", 3, 8, 90), i("air_squat", 4, 20, 60), i("wall_sit", 3, 45, 60), i("hollow_hold", 3, 30, 60))),
                RoutineTemplate("Tirón y core", "Pull & core", listOf(i("negative_pull_up", 4, 5, 120), i("chin_up", 3, 6, 120), i("scapular_pull_up", 3, 10, 60), i("hyperextension", 3, 12, 60), i("hanging_leg_raise", 3, 10, 60), i("side_plank", 3, 30, 45))),
                RoutineTemplate("Cuerpo completo", "Full body", listOf(i("pull_up", 3, 6, 120), i("diamond_push_up", 3, 10, 90), i("shrimp_squat", 3, 6, 90), i("burpee", 3, 10, 60), i("plank", 3, 60, 60))),
            ),
        ),
        TrainingProgram(
            "bodyweight_conditioning_3", "Sin material: quema y resistencia", "No equipment: burn & conditioning",
            "Sin ningún material, en casa o de viaje: circuitos de peso corporal para perder grasa y ganar resistencia, terminando con cardio (carrera, caminata rápida o comba). Apto para principiantes.",
            "No equipment at all, at home or travelling: bodyweight circuits to lose fat and build endurance, finishing with cardio (running, brisk walking or jump rope). Beginner-friendly.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("air_squat", 3, 20, 45), i("push_up", 3, 12, 45), i("hyperextension", 3, 15, 45), i("plank", 3, 40, 30), i("burpee", 3, 10, 60), cardio("running", 20)), supersetPairs = listOf(0 to 1, 2 to 3)),
                RoutineTemplate("B", "B", listOf(i("wall_sit", 3, 45, 45), i("incline_push_up", 3, 15, 45), i("hyperextension", 3, 15, 45), i("side_plank", 3, 30, 30), i("russian_twist", 3, 20, 45), cardio("walking", 30)), supersetPairs = listOf(0 to 1, 2 to 3)),
                RoutineTemplate("C", "C", listOf(i("air_squat", 3, 25, 45), i("pike_push_up", 3, 10, 45), i("hollow_hold", 3, 30, 30), i("knee_push_up", 3, 15, 45), i("burpee", 4, 8, 60), cardio("jump_rope", 15)), supersetPairs = listOf(0 to 1, 2 to 3)),
            ),
        ),
        // --- Resistencia muscular ---
        TrainingProgram(
            "endurance_circuit_3", "Resistencia muscular en circuito", "Muscular endurance circuit",
            "Para mejorar la resistencia muscular (deportes, pruebas físicas, trabajos exigentes): 15-20 reps, descansos de 30-45 s en superseries y 10 min de cardio al final.",
            "To build muscular endurance (sports, fitness tests, physical jobs): 15-20 reps, 30-45 s rests in supersets and 10 min of cardio at the end.",
            3,
            listOf(
                RoutineTemplate("A", "A", listOf(i("leg_press", 3, 20, 45), i("machine_chest_press", 3, 15, 30), i("seated_cable_row", 3, 15, 30), i("lunge", 3, 20, 30), i("db_shoulder_press", 3, 15, 30), i("plank", 3, 60, 30), cardio("rowing_machine", 10)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("B", "B", listOf(i("goblet_squat", 3, 20, 45), i("push_up", 3, 20, 30), i("lat_pulldown", 3, 15, 30), i("hip_thrust", 3, 20, 30), i("lateral_raise", 3, 20, 30), i("crunch", 3, 25, 30), cardio("stationary_bike", 10)), supersetPairs = listOf(1 to 2, 3 to 4)),
                RoutineTemplate("C", "C", listOf(i("bulgarian_split_squat", 3, 15, 45), i("db_incline_press", 3, 15, 30), i("db_row", 3, 15, 30), i("leg_curl", 3, 20, 30), i("face_pull", 3, 20, 30), i("kb_swing", 3, 20, 45), cardio("elliptical", 10)), supersetPairs = listOf(1 to 2, 3 to 4)),
            ),
        ),
    )
}
