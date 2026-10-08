package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pruebas de interfaz en JVM (Robolectric) con capturas (Roborazzi) en los tamaños del
 * Galaxy Z Fold: pantalla exterior (plegado), interior (desplegado) y modo mesa.
 * Las capturas quedan en mobile/build/outputs/roborazzi y la CI las sube como artefacto.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UiFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val publisher = WindowLayoutInfoPublisherRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(publisher).around(compose)

    /** Deja avanzar la UI, Room (hilos reales) y el looper principal. */
    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

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
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun home_folded() {
        compose.mainClock.autoAdvance = false
        settle()
        assertNotNull(compose.onNodeWithText("Entrenamiento vacío").fetchSemanticsNode())
        shot("home_folded")
    }

    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-xhdpi")
    fun home_unfolded() {
        compose.mainClock.autoAdvance = false
        settle()
        shot("home_unfolded")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun workout_flow_folded() {
        startEmptyWorkoutWithBench()
        assertNotNull(compose.onNodeWithText("Press banca").fetchSemanticsNode())
        shot("workout_folded")
        compose.onNodeWithText("Ejercicios").assertDoesNotExistCompat()
    }

    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-xhdpi")
    fun workout_unfolded() {
        startEmptyWorkoutWithBench()
        shot("workout_unfolded")
    }

    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-xhdpi")
    fun workout_tabletop() {
        startEmptyWorkoutWithBench()
        val fold = FoldingFeature(
            activity = compose.activity,
            state = FoldingFeature.State.HALF_OPENED,
            orientation = FoldingFeature.Orientation.HORIZONTAL,
        )
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(fold)))
        settle()
        shot("workout_tabletop")
    }

    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-xhdpi")
    fun exercises_unfolded() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Ejercicios").performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("sentadilla")
        settle()
        compose.onNodeWithText("Sentadilla").performClick()
        settle()
        shot("exercises_unfolded")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun profile_folded() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Perfil").performClick()
        settle()
        shot("profile_folded")
    }
}

/** Misma app en inglés: comprueba que la traducción se aplica. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UiEnglishTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun workout_folded_en() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Empty workout").performClick()
        settle()
        compose.onNodeWithText("Add exercises").performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("bench")
        settle()
        compose.onNodeWithText("Bench Press").performClick()
        settle(3)
        compose.onNode(hasText("Add 1"), useUnmergedTree = true).performClick()
        settle()
        assertNotNull(compose.onNodeWithText("Bench Press").fetchSemanticsNode())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/workout_folded_en.png")
    }

    @Test
    @Config(qualifiers = "en-w884dp-h1104dp-xhdpi")
    fun profile_unfolded_en() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Profile").performClick()
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/profile_unfolded_en.png")
    }
}

/** La barra de navegación se oculta durante el entreno. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
