package com.bestrong.mobile.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.bestrong.core.util.WeightUnit

/** Unidad de peso elegida en Ajustes; toda la UI la lee de aquí. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

/** Mancuernas apuntadas como total del par (Ajustes): lo leen las pantallas sin acceso a los ajustes. */
val LocalDumbbellTotal = staticCompositionLocalOf { false }

/** 2 con [LocalDumbbellTotal] en un ejercicio de la biblioteca con mancuernas; si no, 1 (se guarda por mancuerna). */
@androidx.compose.runtime.Composable
fun pairFactor(exerciseId: String): Int =
    if (LocalDumbbellTotal.current && com.bestrong.core.library.ExerciseLibrary.byId(exerciseId)?.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1

/** Como [pairFactor] con el ejercicio a mano (también los propios, que no están en la biblioteca). */
@androidx.compose.runtime.Composable
fun pairFactor(exercise: com.bestrong.core.model.Exercise): Int =
    if (LocalDumbbellTotal.current && exercise.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1

/** Unidad del peso corporal y las medidas (unidades mixtas: p. ej. cargas en lb y cuerpo en kg). */
val LocalBodyUnit = staticCompositionLocalOf { WeightUnit.KG }

/** Columnas visibles en la tabla de series. */
data class SetColumns(val previous: Boolean = true, val comparison: Boolean = true)

val LocalSetColumns = staticCompositionLocalOf { SetColumns() }
