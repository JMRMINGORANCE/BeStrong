package com.bestrong.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MuscleGroup(val label: String) {
    CHEST("Pecho"),
    BACK("Espalda"),
    SHOULDERS("Hombros"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    FOREARMS("Antebrazos"),
    QUADRICEPS("Cuádriceps"),
    HAMSTRINGS("Isquios"),
    GLUTES("Glúteos"),
    CALVES("Gemelos"),
    ABS("Abdominales"),
    TRAPS("Trapecio"),
    FULL_BODY("Cuerpo completo"),
    CARDIO("Cardio"),
}

@Serializable
enum class Equipment(val label: String) {
    BARBELL("Barra"),
    DUMBBELL("Mancuernas"),
    MACHINE("Máquina"),
    CABLE("Polea"),
    BODYWEIGHT("Peso corporal"),
    KETTLEBELL("Kettlebell"),
    BAND("Banda elástica"),
    SMITH("Multipower"),
    OTHER("Otro"),
}

/** Qué se registra en cada serie. */
@Serializable
enum class TrackingType { WEIGHT_REPS, REPS_ONLY, DURATION }

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment = Equipment.BARBELL,
    val tracking: TrackingType = TrackingType.WEIGHT_REPS,
    val isCustom: Boolean = false,
)
