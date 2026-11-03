package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.performScrollTo
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

/** Búsqueda universal, comparador de sesiones y diario con etiquetas (propuestas 01, 02 y 07), con capturas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class SearchCompareDiaryScreenshotTest {
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

    private fun auto(block: () -> Unit) {
        compose.mainClock.autoAdvance = true
        block()
        compose.mainClock.autoAdvance = false
    }

    /** Un entreno de press banca con [kg] en la primera serie; con [tag], se etiqueta en el resumen. */
    private fun workout(kg: String, tag: String? = null, summaryShot: String? = null) {
        compose.onAllNodesWithText("Entrenar")[0].performClick()
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
        // El entreno se abre en modo gimnasio: la lista de series está en «Ver lista»
        compose.onNodeWithContentDescription("Ver lista").performClick()
        settle()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(kg)
        settle()
        compose.onAllNodesWithContentDescription("Completar serie", substring = true)[0].performClick()
        settle()
        compose.onAllNodesWithText("Terminar")[0].performClick()
        settle()
        compose.onAllNodesWithText("Terminar").fetchSemanticsNodes().takeIf { it.size > 1 }?.let { compose.onAllNodesWithText("Terminar")[1].performClick(); settle() }
        settle(30)
        if (tag != null) {
            auto { compose.onNode(hasText("Añadir etiqueta") and hasSetTextAction()).performScrollTo() }
            compose.onNode(hasText("Añadir etiqueta") and hasSetTextAction()).performTextInput(tag)
            settle()
            compose.onNode(hasContentDescription("Añadir etiqueta")).performClick()
            settle()
            summaryShot?.let { captureScreenRoboImage("build/outputs/roborazzi/$it.png") }
        }
        compose.onNodeWithText("Listo").performClick()
        settle()
    }

    @Test
    fun search_compare_diary() {
        compose.mainClock.autoAdvance = false
        settle()
        workout("60", tag = "dormí mal", summaryShot = "scd_summary_tags")
        workout("62,5")
        // Historial → Diario (bajo el calendario y «Tu año»), y filtrado por la etiqueta
        auto { compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Diario")) }
        compose.onNodeWithText("Diario").performClick()
        settle()
        auto { compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Sin nota")) }
        settle()
        shot("scd_diary")
        auto { compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("#dormí-mal · 1")) }
        compose.onNodeWithText("#dormí-mal · 1").performClick()
        settle()
        shot("scd_diary_tag")
        compose.onNodeWithText("#dormí-mal · 1").performClick()
        compose.onNodeWithText("Lista").performClick()
        settle()
        // El más reciente → Comparar (con el anterior de los mismos ejercicios)
        auto { compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Entrenamiento de ", substring = true)) }
        compose.onAllNodesWithText("Entrenamiento de ", substring = true)[0].performClick()
        settle()
        compose.onNodeWithContentDescription("Opciones").performClick()
        settle()
        compose.onNodeWithText("Comparar").performClick()
        settle(20)
        captureScreenRoboImage("build/outputs/roborazzi/scd_compare_folded.png")
        assertNotNull(compose.onNodeWithText("Esta sesión").fetchSemanticsNode())
        // Cierra el comparador (es un diálogo: con Atrás)
        androidx.test.espresso.Espresso.pressBack()
        settle()
        // Búsqueda universal desde Inicio
        compose.onAllNodesWithText("Entrenar")[0].performClick()
        settle()
        compose.onNodeWithContentDescription("Buscar en BeStrong").performClick()
        settle()
        shot("scd_search_empty")
        compose.onNode(hasSetTextAction()).performTextInput("press")
        settle()
        shot("scd_search_press")
        assertNotNull(compose.onAllNodesWithText("Press banca")[0].fetchSemanticsNode())
    }

    /** El comparador con el Fold desplegado: las dos sesiones en columnas. */
    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-xhdpi")
    fun compare_unfolded() {
        compose.mainClock.autoAdvance = false
        settle()
        workout("60")
        workout("62,5")
        auto { compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Entrenamiento de ", substring = true)) }
        compose.onAllNodesWithText("Entrenamiento de ", substring = true)[0].performClick()
        settle()
        compose.onNodeWithContentDescription("Opciones").performClick()
        settle()
        compose.onNodeWithText("Comparar").performClick()
        settle(20)
        captureScreenRoboImage("build/outputs/roborazzi/scd_compare_unfolded.png")
    }
}
