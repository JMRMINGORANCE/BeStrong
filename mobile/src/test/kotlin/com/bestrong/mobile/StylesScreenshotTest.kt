package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasText
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

/** Los dos estilos (Ajustes → Apariencia): el selector, Inicio y Perfil con sus colores y su letra. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class StylesScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun openAppearance() {
        compose.onAllNodesWithText("Perfil")[0].performClick()
        settle()
        compose.onNodeWithText("Ajustes").performClick()
        settle()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Apariencia y accesibilidad").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Apariencia y accesibilidad").performClick()
        settle()
    }

    @Test
    fun styles() {
        compose.mainClock.autoAdvance = false
        settle()
        listOf("Titanio Blanco", "Carbono").forEachIndexed { i, name ->
            openAppearance()
            compose.mainClock.autoAdvance = true
            compose.onNode(hasText(name)).performScrollTo()
            compose.mainClock.autoAdvance = false
            compose.onNode(hasText(name)).performClick()
            settle()
            val key = "style_${i + 1}_" + name.lowercase().replace(" ", "_").replace("á", "a").replace("é", "e")
            captureScreenRoboImage("build/outputs/roborazzi/${key}_picker.png")
            compose.onNodeWithContentDescription("Volver").performClick()
            settle()
            compose.onAllNodesWithText("Entrenar")[0].performClick()
            settle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${key}_home.png")
            compose.onAllNodesWithText("Perfil")[0].performClick()
            settle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${key}_profile.png")
        }
    }
}
