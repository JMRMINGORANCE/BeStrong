package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Novedades de la fase 9 en adelante: drop set y registro de entrenos pasados. (Los diálogos con
 * campo de texto no llegan a quedar inactivos en Robolectric, así que no se prueban aquí.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
class NewFeaturesUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun startEmptyWorkoutWithBench() {
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

    @Test
    fun dropSetAddsADropRow() {
        startEmptyWorkoutWithBench()
        val before = compose.onAllNodesWithText("D").fetchSemanticsNodes().size
        compose.onNodeWithContentDescription("Opciones de Press banca").performClick()
        settle(3)
        compose.onNodeWithText("Añadir drop set (−20 %)").performClick()
        settle()
        assertEquals(before + 1, compose.onAllNodesWithText("D").fetchSemanticsNodes().size)
    }

    @Test
    fun logPastWorkoutAsksForARoutineFirst() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Historial").performClick()
        settle()
        compose.onNodeWithContentDescription("Registrar entreno pasado").performClick()
        settle(3)
        assertNotNull(compose.onNodeWithText("Crea antes una rutina con los ejercicios que hiciste.").fetchSemanticsNode())
    }
}
