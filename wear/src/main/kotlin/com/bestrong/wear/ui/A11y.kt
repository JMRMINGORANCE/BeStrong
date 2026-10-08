package com.bestrong.wear.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Descripción para TalkBack de los botones que solo muestran un símbolo (⋯, ＋, ▶, ⏭…). */
fun Modifier.describedAs(description: String): Modifier = semantics { contentDescription = description }
