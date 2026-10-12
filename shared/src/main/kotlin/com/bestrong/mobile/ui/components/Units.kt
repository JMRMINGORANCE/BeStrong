package com.bestrong.mobile.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.bestrong.core.util.WeightUnit

/** Unidad de peso elegida en Ajustes; toda la UI la lee de aquí. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

/** Unidad del peso corporal y las medidas (unidades mixtas: p. ej. cargas en lb y cuerpo en kg). */
val LocalBodyUnit = staticCompositionLocalOf { WeightUnit.KG }

/** Columnas visibles en la tabla de series. */
data class SetColumns(val previous: Boolean = true, val comparison: Boolean = true)

val LocalSetColumns = staticCompositionLocalOf { SetColumns() }
