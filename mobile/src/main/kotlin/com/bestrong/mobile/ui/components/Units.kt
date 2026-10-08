package com.bestrong.mobile.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.bestrong.core.util.WeightUnit

/** Unidad de peso elegida en Ajustes; toda la UI la lee de aquí. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }
