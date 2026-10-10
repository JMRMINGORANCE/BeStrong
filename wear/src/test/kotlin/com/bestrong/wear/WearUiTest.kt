package com.bestrong.wear

import android.os.Looper
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de la app del reloj en una esfera redonda del tamaño del Galaxy Watch Ultra (480 × 480 px).
 * Se publican en la rama "capturas" junto a las del móvil.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w240dp-h240dp-round-watch-xhdpi")
class WearUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/wear_$name.png")

    @Test
    fun flow() {
        compose.mainClock.autoAdvance = false
        settle()
        shot("home")

        compose.onNodeWithText("Entrenamiento vacío").performClick()
        settle()
        shot("workout_empty")

        compose.onNodeWithText("＋").performClick()
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pecho"))
        compose.onNodeWithText("Pecho").performClick()
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Press banca"))
        compose.onNodeWithText("Press banca").performClick()
        settle()
        shot("workout_set")

        // El botón ✓ se anuncia a TalkBack como «Completar serie»
        compose.onNodeWithContentDescription("Completar serie").performClick()
        settle()
        shot("rest")
    }

    @Test
    fun menu_and_info() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Entrenamiento vacío").performClick()
        settle()
        compose.onNodeWithText("＋").performClick()
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pecho"))
        compose.onNodeWithText("Pecho").performClick()
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Press banca"))
        compose.onNodeWithText("Press banca").performClick()
        settle()
        compose.onNodeWithContentDescription("Más opciones").performClick()
        settle()
        shot("menu")
        // En la lista curva (ScalingLazyColumn) se desplaza por posición: cabecera, resumen, «Ejercicios», 1 ejercicio, «Progreso y discos»
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        settle()
        compose.onNodeWithText("Progreso y discos").performClick()
        settle()
        shot("info")
    }

    @Test
    fun cardio_and_new_routine() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Cardio con GPS"))
        compose.onNodeWithText("Cardio con GPS").performClick()
        settle()
        compose.onNodeWithText("Carrera").assertExists()
        shot("cardio_pick")
    }

    @Test
    fun create_routine_on_watch() {
        compose.mainClock.autoAdvance = false
        settle()
        // «Crear rutina» está al final: se desliza hacia arriba hasta verlo (con límite, nunca se cuelga)
        repeat(12) {
            if (compose.onAllNodesWithText("Crear rutina").fetchSemanticsNodes().isNotEmpty()) return@repeat
            compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
            settle()
        }
        compose.onNodeWithText("Crear rutina").performClick()
        settle()
        compose.onNodeWithText("Añadir ejercicio").assertExists()
        shot("routine_new")
    }
}
