package com.bestrong.wear

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Siguiente ejercicio» en el descanso (propuesta 09) y el resumen con anillos al terminar (propuesta 08). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w240dp-h240dp-round-watch-xhdpi")
class WearNextAndSummaryTest {
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

    /** En el menú (lista curva) el desplazamiento necesita que el reloj de la prueba avance solo. */
    private fun scrollTo(text: String) {
        compose.mainClock.autoAdvance = true
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun add(group: String, name: String, fromMenu: Boolean) {
        if (fromMenu) {
            compose.onNodeWithContentDescription("Más opciones").performClick()
            settle()
            scrollTo("Añadir ejercicio")
            compose.onNodeWithText("Añadir ejercicio").performClick()
        } else {
            compose.onNodeWithText("＋").performClick()
        }
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(group))
        compose.onNodeWithText(group).performClick()
        settle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        settle()
    }

    @Test
    fun next_exercise_and_summary() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Entrenamiento vacío").performClick()
        settle()
        add("Pecho", "Press banca", fromMenu = false)
        add("Espalda", "Remo con barra", fromMenu = true)
        // Las tres series del ejercicio que se ve: el descanso de la última lleva al otro
        repeat(3) { i ->
            compose.onNodeWithContentDescription("Completar serie").performClick()
            settle()
            if (i < 2) {
                compose.onNodeWithContentDescription("Saltar descanso").performClick()
                settle()
            }
        }
        shot("next_exercise")
        compose.onNodeWithContentDescription("Empezar ya").assertExists()
    }

    /** Los anillos y «La próxima vez» del resumen, con un entreno de ejemplo (terminar desde la UI no va en Robolectric). */
    @Test
    fun summary_rings() {
        compose.mainClock.autoAdvance = false
        settle()
        val start = 1_761_000_000_000L
        fun sets(kg: Double, done: Int, total: Int) = List(total) { i -> com.bestrong.core.model.WorkoutSet(weightKg = kg, reps = 8, completed = i < done, completedAtEpochMs = start + i * 60_000L) }
        val bench = com.bestrong.core.library.ExerciseLibrary.byId("bench_press")!!
        val row = com.bestrong.core.library.ExerciseLibrary.byId("barbell_row")!!
        val w = com.bestrong.core.model.Workout(
            name = "Torso A",
            startedAtEpochMs = start,
            finishedAtEpochMs = start + 52 * 60_000L,
            exercises = listOf(com.bestrong.core.model.WorkoutExercise(exercise = bench, sets = sets(82.5, 4, 4)), com.bestrong.core.model.WorkoutExercise(exercise = row, sets = sets(70.0, 3, 4))),
        )
        val next = listOf(com.bestrong.core.training.NextTimeSuggestion("bench_press", "Press banca", 85.0, 8, 1))
        val summary = com.bestrong.wear.session.Summary(w, emptyList(), emptyList(), next)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                com.bestrong.wear.ui.BeStrongWearTheme {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        com.bestrong.wear.ui.SummaryRings(summary)
                        androidx.wear.compose.material3.Text("▲ Press banca · 85 kg × 8", style = androidx.wear.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        settle()
        shot("summary_rings")
    }
}
