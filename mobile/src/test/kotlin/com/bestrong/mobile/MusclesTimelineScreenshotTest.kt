package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.mobile.di.WorkerEntryPoint
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Historia del ejercicio (hitos), equilibrio de patrones y la tarjeta única de músculos, con un historial de
 * ejemplo de varias semanas.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-night-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class MusclesTimelineScreenshotTest {
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

    private fun scrollTo(text: String, substring: Boolean = false) {
        compose.mainClock.autoAdvance = true
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text, substring = substring))
        compose.mainClock.autoAdvance = false
        settle()
    }

    /** Diez semanas de press banca subiendo (con un cambio de gimnasio y un parón), sentadilla y remo. */
    private fun seed() = runBlocking {
        val repo = EntryPointAccessors.fromApplication(compose.activity.application, WorkerEntryPoint::class.java).repository()
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val bench = ExerciseLibrary.byId("bench_press")!!
        val squat = ExerciseLibrary.byId("squat")!!
        val row = ExerciseLibrary.byId("barbell_row")!!
        val weights = listOf(55.0, 57.5, 60.0, 62.5, 65.0, 50.0, 67.5, 70.0, 72.5, 80.0)
        val starts = listOf(84, 77, 70, 63, 56, 49, 42, 35, 14, 0)
        weights.forEachIndexed { i, w ->
            // La última, hoy (dos horas antes): cuenta en la semana actual de Inicio
            val start = now - starts[i] * day - 7_200_000L
            fun sets(kg: Double, n: Int, reps: Int) = List(n) { WorkoutSet(weightKg = kg, reps = reps, completed = true, completedAtEpochMs = start + 60_000L * (it + 1)) }
            repo.saveWorkout(
                Workout(
                    name = "Torso A",
                    startedAtEpochMs = start,
                    finishedAtEpochMs = start + 3_600_000L,
                    gymName = if (i < 6) "Gimnasio Centro" else "Gimnasio Norte",
                    exercises = listOf(
                        WorkoutExercise(exercise = bench, sets = sets(w, 4, 6)),
                        WorkoutExercise(exercise = squat, sets = sets(w * 1.3, 3, 5)),
                        WorkoutExercise(exercise = row, sets = sets(w * 0.8, 2, 8)),
                    ),
                ),
            )
        }
    }

    @Test
    fun profile_muscles_and_patterns() {
        compose.mainClock.autoAdvance = false
        seed()
        settle(30)
        compose.onAllNodesWithText("Perfil")[0].performClick()
        settle(30)
        scrollTo("Músculos")
        shot("m_profile_muscles_week")
        compose.onNodeWithText("Tendencia").performClick()
        settle()
        shot("m_profile_muscles_trend")
        // Hasta la proporción del final: el radar queda entero a la vista
        scrollTo("Bisagra : sentadilla")
        assertNotNull(compose.onNodeWithText("Equilibrio de patrones").fetchSemanticsNode())
        shot("m_profile_patterns")
    }

    @Test
    fun exercise_history_timeline() {
        compose.mainClock.autoAdvance = false
        seed()
        settle(30)
        compose.onAllNodesWithText("Ejercicios")[0].performClick()
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("press banca")
        settle()
        compose.onNodeWithText("Press banca").performClick()
        settle(30)
        // Hasta el final de la línea de tiempo («Ver todos»): queda entera a la vista
        scrollTo("Ver todos", substring = true)
        shot("m_exercise_history")
        compose.onAllNodesWithText("Récord de peso")[0].performClick()
        settle()
        shot("m_exercise_history_open")
    }

    @Test
    fun home_single_week_card() {
        // La semana de Inicio en una sola tarjeta: días, racha, volumen (los músculos están en Perfil)
        compose.mainClock.autoAdvance = false
        seed()
        settle(30)
        scrollTo("Volumen semanal")
        shot("m_home_week")
    }
}
