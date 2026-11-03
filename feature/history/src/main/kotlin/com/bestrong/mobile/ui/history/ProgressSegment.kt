package com.bestrong.mobile.ui.history

import androidx.compose.runtime.Composable

/** Otra vista de Progreso (Cuerpo, Fuerza) que se elige en los segmentos de la cabecera, junto al historial. */
class ProgressSegment(val label: String, val content: @Composable () -> Unit)
