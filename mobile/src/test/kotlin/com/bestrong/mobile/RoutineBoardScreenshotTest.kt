package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Editor de rutinas en tablero (Fold desplegado), con capturas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-night-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class RoutineBoardScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun scrollTo(text: String) {
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text))
        compose.mainClock.autoAdvance = false
        settle()
    }

    /** Editor en tablero: biblioteca a la izquierda, rutina a la derecha con sus cifras. */
    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-night-xhdpi")
    fun board_editor_unfolded() {
        compose.mainClock.autoAdvance = false
        settle()
        scrollTo("+ Nueva rutina")
        compose.onNodeWithText("+ Nueva rutina").performClick()
        settle()
        shot("p10_board_empty")
        compose.onAllNodesWithContentDescription("Añadir", substring = true)[0].performClick()
        settle()
        compose.onAllNodesWithContentDescription("Añadir", substring = true)[3].performClick()
        settle()
        compose.onAllNodesWithContentDescription("Añadir", substring = true)[6].performClick()
        settle()
        shot("p10_board_routine")
    }
}
