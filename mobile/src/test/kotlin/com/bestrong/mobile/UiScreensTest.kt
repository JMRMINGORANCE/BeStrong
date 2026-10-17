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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capturas de las pantallas secundarias (editor de rutina, ficha, medidas y fin del entreno) para revisar el diseño. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class UiScreensTest {
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

    private fun pickBench() {
        compose.onNodeWithText("Añadir ejercicios").performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("press banca")
        settle()
        compose.onNodeWithText("Press banca").performClick()
        settle(3)
        compose.onNode(hasText("Añadir 1"), useUnmergedTree = true).performClick()
        settle()
    }

    @Test
    fun routine_editor() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Nueva").performClick()
        settle()
        compose.onNode(hasSetTextAction() and hasText("Nombre", substring = true)).performTextReplacement("Pecho y tríceps")
        pickBench()
        shot("routine_editor")
    }

    @Test
    fun exercise_detail() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onAllNodesWithText("Ejercicios")[0].performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("press banca")
        settle()
        compose.onNodeWithText("Press banca").performClick()
        settle()
        shot("exercise_detail")
    }

    @Test
    fun measurements() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onAllNodesWithText("Perfil")[0].performClick()
        settle()
        compose.mainClock.autoAdvance = true
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Medidas corporales"))
        compose.onNodeWithText("Medidas corporales").performClick()
        compose.mainClock.autoAdvance = false
        settle()
        shot("measurements")
    }

    @Test
    fun workout_summary() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Entrenamiento vacío").performClick()
        settle()
        pickBench()
        compose.onAllNodesWithContentDescription("Completar serie", substring = true)[0].performClick()
        settle()
        compose.onAllNodesWithText("Terminar")[0].performClick()
        settle()
        // Puede pedir confirmación (series sin hacer): se confirma
        compose.onAllNodesWithText("Terminar").fetchSemanticsNodes().takeIf { it.size > 1 }?.let { compose.onAllNodesWithText("Terminar")[1].performClick(); settle() }
        captureScreenRoboImage("build/outputs/roborazzi/workout_summary.png")
        assertTrue(true)
    }
}
