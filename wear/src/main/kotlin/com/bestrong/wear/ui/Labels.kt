package com.bestrong.wear.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.repeatOnLifecycle
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
fun Exercise.displayName(): String = ExerciseLibrary.displayName(this, LocalConfiguration.current.locales[0].language.let { if (it in setOf("es", "ca", "gl", "eu")) "es" else "en" })

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

/** Una serie como se lee en la muñeca, según cómo se mide: «80×8», «45 s», «20 min», «40×30 m», «12 reps». */
fun setText(
    set: com.bestrong.core.model.WorkoutSet,
    tracking: com.bestrong.core.model.TrackingType,
    unit: com.bestrong.core.util.WeightUnit,
    // En el idioma del reloj («Wdh.», «rép.»…): R.string.unit_reps
    repsLabel: String = "reps",
): String = when (tracking) {
    com.bestrong.core.model.TrackingType.DURATION -> "${set.durationSec} s"
    com.bestrong.core.model.TrackingType.CARDIO -> "${set.durationSec / 60} min" + if (set.distanceM > 0) " · ${"%.1f".format(set.distanceM / 1000)} km" else ""
    com.bestrong.core.model.TrackingType.DISTANCE -> (if (set.weightKg != 0.0) "${unit.format(set.weightKg)}×" else "") + "${set.distanceM.toInt()} m"
    else -> if (set.weightKg == 0.0) "${set.reps} $repsLabel" else "${unit.format(set.weightKg)}×${set.reps}"
}

/**
 * La hora actual cada [intervalMs] solo mientras la pantalla está visible: con la pantalla apagada (y el
 * descanso manteniendo la CPU despierta) no tiene sentido redibujar un reloj que nadie ve.
 */
@androidx.compose.runtime.Composable
fun rememberVisibleNow(intervalMs: Long): Long {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val now = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(lifecycle, intervalMs) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                now.longValue = System.currentTimeMillis()
                kotlinx.coroutines.delay(intervalMs)
            }
        }
    }
    return now.longValue
}
