package com.bestrong.mobile.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/** Postura del plegable (Galaxy Z Fold). */
enum class FoldPosture { FLAT, TABLETOP, BOOK }

@Composable
fun rememberFoldPosture(): FoldPosture {
    val activity = LocalContext.current as Activity
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            when {
                fold == null || fold.state == FoldingFeature.State.FLAT -> FoldPosture.FLAT
                fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.TABLETOP
                else -> FoldPosture.BOOK
            }
        }
    }
    val posture by flow.collectAsState(initial = FoldPosture.FLAT)
    return posture
}
