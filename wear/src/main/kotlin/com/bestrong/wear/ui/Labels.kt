package com.bestrong.wear.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.wear.R
import com.bestrong.wear.session.CardioType
import java.time.Instant
import java.time.ZoneId

@Composable @ReadOnlyComposable
fun MuscleGroup.label(): String = stringResource(
    when (this) {
        MuscleGroup.CHEST -> R.string.muscle_chest
        MuscleGroup.BACK -> R.string.muscle_back
        MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
        MuscleGroup.BICEPS -> R.string.muscle_biceps
        MuscleGroup.TRICEPS -> R.string.muscle_triceps
        MuscleGroup.FOREARMS -> R.string.muscle_forearms
        MuscleGroup.QUADRICEPS -> R.string.muscle_quadriceps
        MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
        MuscleGroup.GLUTES -> R.string.muscle_glutes
        MuscleGroup.CALVES -> R.string.muscle_calves
        MuscleGroup.ABS -> R.string.muscle_abs
        MuscleGroup.TRAPS -> R.string.muscle_traps
        MuscleGroup.FULL_BODY -> R.string.muscle_full_body
        MuscleGroup.CARDIO -> R.string.muscle_cardio
    },
)

@Composable @ReadOnlyComposable
fun Equipment.label(): String = stringResource(
    when (this) {
        Equipment.BARBELL -> R.string.equip_barbell
        Equipment.DUMBBELL -> R.string.equip_dumbbell
        Equipment.MACHINE -> R.string.equip_machine
        Equipment.CABLE -> R.string.equip_cable
        Equipment.BODYWEIGHT -> R.string.equip_bodyweight
        Equipment.KETTLEBELL -> R.string.equip_kettlebell
        Equipment.BAND -> R.string.equip_band
        Equipment.SMITH -> R.string.equip_smith
        Equipment.OTHER -> R.string.equip_other
    },
)

/** Nombre del ejercicio en el idioma del reloj. */
@Composable @ReadOnlyComposable
fun Exercise.displayName(): String = ExerciseLibrary.displayName(this, LocalConfiguration.current.locales[0].language)

/** Nombre por defecto según la hora del día. */
fun defaultWorkoutName(context: Context, nowEpochMs: Long): String {
    val hour = Instant.ofEpochMilli(nowEpochMs).atZone(ZoneId.systemDefault()).hour
    return context.getString(
        when (hour) {
            in 5..11 -> R.string.workout_morning
            in 12..15 -> R.string.workout_midday
            in 16..20 -> R.string.workout_afternoon
            else -> R.string.workout_night
        },
    )
}

/** Nombre de la sesión de cardio (también se usa fuera de Compose: servicio y guardado). */
fun cardioName(context: Context, type: CardioType): String = context.getString(
    when (type) {
        CardioType.RUNNING -> R.string.cardio_running
        CardioType.WALKING -> R.string.cardio_walking
        CardioType.CYCLING -> R.string.cardio_cycling
    },
)
