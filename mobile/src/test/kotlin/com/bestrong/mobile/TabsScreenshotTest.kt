package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las cuatro pestañas en tema oscuro (el de la bienvenida), con capturas para revisar el diseño. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-night-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class TabsScreenshotTest {
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

    @Test
    fun tabs_dark() {
        compose.mainClock.autoAdvance = false
        settle()
        shot("dark_train")
        listOf("PROGRESO" to "dark_history", "BIBLIOTECA" to "dark_exercises").forEach { (tab, name) ->
            compose.onAllNodesWithText(tab)[0].performClick()
            settle()
            shot(name)
        }
        run {
            val name = "dark_profile"
            compose.onAllNodesWithContentDescription("Tú")[0].performClick()
            settle()
            shot(name)
        }
        // La pestaña de ajustes de Perfil
        compose.onNodeWithText("Ajustes").performClick()
        settle()
        shot("dark_settings")
        // Las pantallas de Ajustes (otra ventana: captura de toda la pantalla) y vuelta al menú
        listOf(
            "Entrenamiento" to "dark_training",
            "Apariencia y accesibilidad" to "dark_appearance",
            "Datos y copias de seguridad" to "dark_data",
            "Permisos y conexiones" to "dark_permissions",
            "Avanzado" to "dark_advanced",
        ).forEach { (row, name) ->
            // El desplazamiento necesita que el reloj avance solo (con el reloj parado no termina nunca)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(row).performScrollTo()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(row).performClick()
            settle()
            captureScreenRoboImage("build/outputs/roborazzi/$name.png")
            compose.onAllNodesWithContentDescription("Volver").onLast().performClick()
            settle()
        }
        // Y la de comunidad (ideas y avisos): una fila de Tú
        while (compose.onAllNodesWithText("Comunidad").fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodesWithContentDescription("Volver").onLast().performClick()
            settle()
        }
        // La rejilla es perezosa: hasta que se desplaza, la fila no existe
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Comunidad"))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Comunidad").performClick()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/dark_community.png")
        // El buzón de ideas, a pantalla completa y con fondo opaco (es otra ventana: captura de toda la pantalla)
        compose.onNodeWithText("Proponer una mejora").performClick()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/dark_proposals.png")
    }
}
