package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        listOf("Historial" to "dark_history", "Ejercicios" to "dark_exercises", "Perfil" to "dark_profile").forEach { (tab, name) ->
            compose.onAllNodesWithText(tab)[0].performClick()
            settle()
            shot(name)
        }
        // La pestaña de ajustes de Perfil
        compose.onNodeWithText("Ajustes").performClick()
        settle()
        shot("dark_settings")
        // Las pantallas de Ajustes (otra ventana: captura de toda la pantalla) y vuelta al menú
        listOf("Permisos y conexiones" to "dark_permissions", "Durante el entreno" to "dark_during", "Apariencia y accesibilidad" to "dark_appearance").forEach { (row, name) ->
            // El desplazamiento necesita que el reloj avance solo (con el reloj parado no termina nunca)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(row).performScrollTo()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(row).performClick()
            settle()
            captureScreenRoboImage("build/outputs/roborazzi/$name.png")
            compose.onNodeWithContentDescription("Volver").performClick()
            settle()
        }
        // Y la de comunidad (proponer mejoras)
        // «Comunidad» sale también en el menú de Ajustes: la primera es la pestaña (arriba, hay que volver a ella)
        compose.mainClock.autoAdvance = true
        compose.onAllNodesWithText("Comunidad")[0].performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithText("Comunidad")[0].performClick()
        settle()
        shot("dark_community")
        // El buzón de ideas, a pantalla completa y con fondo opaco (es otra ventana: captura de toda la pantalla)
        compose.onNodeWithText("Proponer una mejora").performClick()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/dark_proposals.png")
    }
}
