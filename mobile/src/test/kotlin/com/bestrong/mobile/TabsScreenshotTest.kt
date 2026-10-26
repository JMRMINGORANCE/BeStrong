package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
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
        // Y la de comunidad (proponer mejoras)
        compose.onNodeWithText("Comunidad").performClick()
        settle()
        shot("dark_community")
        // El buzón de ideas, a pantalla completa y con fondo opaco
        compose.onNodeWithText("Proponer una mejora").performClick()
        settle()
        shot("dark_proposals")
    }
}
