package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las cinco propuestas de interfaz (modo foco, Inicio con la semana, mapa corporal, resumen de celebración y
 * centro de mando del Fold) en tema oscuro, con capturas para revisarlas.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-night-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ProposalsScreenshotTest {
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

    private fun startWithBench() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Entrenamiento vacío").performClick()
        settle()
        compose.onNodeWithText("Añadir ejercicios").performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("press banca")
        settle()
        compose.onNodeWithText("Press banca").performClick()
        settle(3)
        compose.onNode(hasText("Añadir 1"), useUnmergedTree = true).performClick()
        settle()
    }

    private fun scrollTo(text: String) {
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text))
        compose.mainClock.autoAdvance = false
        settle()
    }

    /** Propuestas 1, 4, 2 y 3: modo foco, resumen, Inicio con la semana y mapa corporal. */
    @Test
    fun proposals_folded() {
        startWithBench()
        compose.onNodeWithContentDescription("Modo foco").performClick()
        settle()
        shot("p1_focus_mode")
        compose.onNodeWithContentDescription("Más kg").performClick()
        compose.onNodeWithText("Serie hecha").performClick()
        settle()
        shot("p1_focus_mode_next")
        compose.onAllNodesWithText("Terminar")[0].performClick()
        settle()
        compose.onAllNodesWithText("Terminar").fetchSemanticsNodes().takeIf { it.size > 1 }?.let { compose.onAllNodesWithText("Terminar")[1].performClick(); settle() }
        settle(30)
        captureScreenRoboImage("build/outputs/roborazzi/p4_summary.png")
        compose.onNodeWithText("Listo").performClick()
        settle()
        // Al terminar se abre el historial: de vuelta a Inicio
        compose.onAllNodesWithText("Entrenar")[0].performClick()
        settle()
        shot("p2_home")
        scrollTo("Músculos esta semana")
        shot("p2_home_week")
        compose.onAllNodesWithText("Perfil")[0].performClick()
        settle()
        scrollTo("Mapa muscular de la semana")
        assertNotNull(compose.onNodeWithText("Mapa muscular de la semana").fetchSemanticsNode())
        shot("p3_body_map")
    }

    /** Propuesta 5: el entreno en el Fold desplegado con el panel de la derecha. */
    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-night-xhdpi")
    fun proposal_command_center() {
        startWithBench()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("60")
        settle()
        compose.onAllNodesWithContentDescription("Completar serie", substring = true)[0].performClick()
        settle()
        shot("p5_command_center")
    }
}
