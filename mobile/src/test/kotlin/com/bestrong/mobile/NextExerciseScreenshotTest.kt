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
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Siguiente ejercicio» en el descanso (propuesta 09) y «Vídeos de técnica» en la ficha (propuesta 04). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-night-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class NextExerciseScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun settle(rounds: Int = 15) {
        repeat(rounds) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(30)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun add(query: String, name: String) {
        compose.onNodeWithText("Añadir ejercicios").performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput(query)
        settle()
        compose.onNodeWithText(name).performClick()
        settle(3)
        compose.onNode(hasText("Añadir 1"), useUnmergedTree = true).performClick()
        settle()
    }

    @Test
    fun next_exercise() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Entrenamiento vacío").performClick()
        settle()
        add("press banca", "Press banca")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("80")
        settle()
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("Añadir ejercicios"))
        compose.mainClock.autoAdvance = false
        add("remo con barra", "Remo con barra")
        // Las tres series del press: tras la última, el descanso lleva al remo
        repeat(3) {
            compose.onAllNodesWithContentDescription("Completar serie", substring = true)[it].performClick()
            settle()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/p09_next_exercise.png")
        assertNotNull(compose.onNodeWithText("Empezar ya").fetchSemanticsNode())
    }

    @Test
    fun technique_videos() {
        // Sección «Técnica»: dos vídeos ya grabados del press banca (con sus datos) junto a la nota y lo propio
        val ctx = compose.activity
        val dir = File(ctx.filesDir, "media/bench_press/sets").apply { mkdirs() }
        listOf("a" to (1_761_000_000_000L to 85.0), "b" to (1_758_000_000_000L to 80.0)).forEachIndexed { i, (id, data) ->
            File(dir, "$id.mp4").writeBytes(ByteArray(64) { 1 })
            File(dir, "$id.json").writeText("""{"recordedAtEpochMs":${data.first},"weightKg":${data.second},"reps":6,"rpe":8.5,"setNumber":${i + 2}}""")
        }
        compose.mainClock.autoAdvance = false
        settle()
        compose.onAllNodesWithText("Ejercicios")[0].performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("press banca")
        settle()
        compose.onNodeWithText("Press banca").performClick()
        settle()
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText("85 kg", substring = true))
        compose.mainClock.autoAdvance = false
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/p04_technique_videos.png")
    }
}
