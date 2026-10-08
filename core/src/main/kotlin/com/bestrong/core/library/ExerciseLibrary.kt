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

    fun byId(id: String): Exercise? = byId[id]

    /** Búsqueda sin tildes ni mayúsculas, filtrando por músculo y equipo. */
    fun search(
        exercises: List<Exercise>,
        query: String,
        muscle: MuscleGroup? = null,
        equipment: Equipment? = null,
    ): List<Exercise> {
        val q = query.normalized()
        return exercises
            .filter { muscle == null || it.primaryMuscle == muscle || muscle in it.secondaryMuscles }
            .filter { equipment == null || it.equipment == equipment }
            .filter { q.isBlank() || q.split(' ').all { token -> it.name.normalized().contains(token) } }
            .sortedWith(compareBy({ !it.name.normalized().startsWith(q) }, { it.name }))
    }

    internal fun String.normalized(): String =
        Normalizer.normalize(lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
