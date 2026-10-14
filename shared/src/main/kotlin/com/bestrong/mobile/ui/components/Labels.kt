package com.bestrong.mobile.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.RecordType
import com.bestrong.core.training.ProgressionSuggestion
import com.bestrong.core.training.SuggestionKind
import com.bestrong.mobile.shared.R
import java.time.Instant
import java.time.ZoneId

/* Textos de los modelos de `core` (que están en español) traducidos con los recursos de Android. */

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
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
}

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BARBELL -> R.string.equip_barbell
    Equipment.DUMBBELL -> R.string.equip_dumbbell
    Equipment.MACHINE -> R.string.equip_machine
    Equipment.CABLE -> R.string.equip_cable
    Equipment.BODYWEIGHT -> R.string.equip_bodyweight
    Equipment.KETTLEBELL -> R.string.equip_kettlebell
    Equipment.BAND -> R.string.equip_band
    Equipment.SMITH -> R.string.equip_smith
    Equipment.OTHER -> R.string.equip_other
}

@StringRes
fun RecordType.labelRes(): Int = when (this) {
    RecordType.HEAVIEST_WEIGHT -> R.string.record_heaviest
    RecordType.BEST_ONE_REP_MAX -> R.string.record_1rm
    RecordType.BEST_SET_VOLUME -> R.string.record_set_volume
    RecordType.MOST_REPS -> R.string.record_most_reps
}

@Composable @ReadOnlyComposable
fun MuscleGroup.label(): String = stringResource(labelRes())

@Composable @ReadOnlyComposable
fun Equipment.label(): String = stringResource(labelRes())

@Composable @ReadOnlyComposable
fun RecordType.label(): String = stringResource(labelRes())

/**
 * Idioma de los contenidos de la biblioteca ("en" o "es"): los ejercicios, programas e instrucciones
 * solo están en esos dos, así que catalán y gallego usan español y el resto inglés.
 */
fun contentLanguage(language: String): String = if (language in setOf("es", "ca", "gl", "eu")) "es" else "en"

@Composable @ReadOnlyComposable
fun uiLanguage(): String = contentLanguage(LocalConfiguration.current.locales[0].language)

fun Context.uiLanguage(): String = contentLanguage(resources.configuration.locales[0].language)

/** Textos que los programas escriben en cada sesión, en el idioma de la app. */
fun Context.programTexts(): com.bestrong.core.training.ProgramTexts =
    com.bestrong.core.training.ProgramTexts(getString(com.bestrong.mobile.shared.R.string.program_amrap_note), getString(com.bestrong.mobile.shared.R.string.program_week_prefix))

/** Nombre del ejercicio en el idioma de la app (los personalizados se muestran tal cual). */
@Composable @ReadOnlyComposable
fun Exercise.displayName(): String = ExerciseLibrary.displayName(this, uiLanguage())

fun Exercise.displayName(context: Context): String = ExerciseLibrary.displayName(this, context.uiLanguage())

/** Por qué se propone esto; con « · limitado por el tope semanal» si Planificación recortó la subida. */
@Composable @ReadOnlyComposable
fun ProgressionSuggestion.reasonText(): String = kindText() + if (cappedByWeek) stringResource(R.string.prog_capped_suffix) else ""

@Composable @ReadOnlyComposable
private fun ProgressionSuggestion.kindText(): String = when (kind) {
    SuggestionKind.INCREASE_WEIGHT -> stringResource(R.string.prog_increase)
    SuggestionKind.ADD_REPS -> stringResource(R.string.prog_add_reps, targetReps)
    SuggestionKind.ADD_ONE_REP -> stringResource(R.string.prog_add_one_rep)
    SuggestionKind.RANGE_ADD_REP -> stringResource(R.string.prog_range_add_rep, targetReps)
    SuggestionKind.ADD_TIME -> stringResource(R.string.prog_add_time)
    SuggestionKind.REPEAT -> stringResource(R.string.prog_repeat)
    SuggestionKind.DELOAD -> stringResource(R.string.prog_deload, stallSessions)
}

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
