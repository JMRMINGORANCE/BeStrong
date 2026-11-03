package com.bestrong.wear

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.core.sensors.RepConfidence
import com.bestrong.wear.ui.BeStrongWearTheme
import com.bestrong.wear.ui.CountedRepsLine
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Repeticiones contadas por el acelerómetro: confianza visible y corrección de un toque antes de guardar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w240dp-h240dp-round-watch-xhdpi")
class RepCountLineTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun counted_reps_line_corrects_count() {
        compose.activity.setContent {
            BeStrongWearTheme {
                var reps by remember { mutableIntStateOf(8) }
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CountedRepsLine(reps, RepConfidence.HIGH) { reps += it }
                        CountedRepsLine(5, RepConfidence.MEDIUM) {}
                        CountedRepsLine(3, RepConfidence.LOW) {}
                    }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithText("Contadas: 8 · fiable").assertExists()
        compose.onAllNodesWithContentDescription("Una repetición más")[0].performClick()
        compose.onNodeWithText("Contadas: 9 · fiable").assertExists()
        compose.onAllNodesWithContentDescription("Una repetición menos")[0].performClick()
        compose.onAllNodesWithContentDescription("Una repetición menos")[0].performClick()
        compose.onNodeWithText("Contadas: 7 · fiable").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/wear_counted_reps.png")
    }
}
