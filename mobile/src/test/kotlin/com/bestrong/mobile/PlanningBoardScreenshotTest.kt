package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Periodización visual (Planificar) y editor de rutinas en tablero (Fold desplegado), con capturas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-night-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class PlanningBoardScreenshotTest {
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

    /** Mesociclo de 4 + 1 empezado desde Planificar: la línea temporal sale arriba. */
    @Test
    fun cycle_mesocycle_folded() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Planificar").performClick()
        settle()
        scrollTo("Mesociclo de 4 + 1")
        compose.onNodeWithText("Mesociclo de 4 + 1").performClick()
        settle()
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Tu mesociclo"))
        compose.mainClock.autoAdvance = false
        settle()
        assertTrue(compose.onAllNodesWithText("Tu mesociclo").fetchSemanticsNodes().isNotEmpty())
        shot("p06_cycle_mesocycle")
    }

    /** 5/3/1 activo en el Fold desplegado: línea temporal y rejilla de intensidad por ejercicio. */
    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-night-xhdpi")
    fun cycle_program_unfolded() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Programas").performClick()
        settle()
        scrollTo("5/3/1 Boring But Big")
        compose.onNodeWithText("5/3/1 Boring But Big").performClick()
        settle()
        compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().indices.forEach { i ->
            compose.onAllNodes(hasSetTextAction())[i].performTextInput("100")
        }
        settle()
        compose.onNodeWithText("Empezar programa").performClick()
        settle()
        compose.onNodeWithText("Planificar").performClick()
        settle(25)
        assertTrue(compose.onAllNodesWithText("5/3/1 Boring But Big").fetchSemanticsNodes().isNotEmpty())
        shot("p06_cycle_program_unfolded")
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
