package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accesibilidad: la app con el texto al 200 % (máximo de Android 14+) en la pantalla
 * exterior del Fold, y comprobación de que todo lo pulsable tiene etiqueta para TalkBack.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
class UiAccessibilityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        compose.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
    }

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    /** Todo nodo pulsable debe tener texto o descripción (si no, TalkBack dice solo «botón»). */
    private fun assertClickablesLabelled() {
        val unlabeled = compose.onAllNodes(
            hasClickAction() and
                !SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
                !SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and
                !SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText),
        ).fetchSemanticsNodes()
        assertTrue("Elementos pulsables sin etiqueta: ${unlabeled.map { it.config }}", unlabeled.isEmpty())
    }

    @Test
    fun home_large_font() {
        launch(2.0f)
        shot("a11y_home_font200")
        assertClickablesLabelled()
    }

    @Test
    fun workout_large_font() {
        launch(2.0f)
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
        shot("a11y_workout_font200")
        assertClickablesLabelled()
    }

    @Test
    fun profile_large_font() {
        launch(2.0f)
        compose.onNodeWithText("Perfil").performClick()
        settle()
        shot("a11y_profile_font200")
        assertClickablesLabelled()
    }
}
