package com.bestrong.mobile

import android.os.Looper
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.util.WeightUnit
import com.bestrong.mobile.share.WorkoutShareCard
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
@OptIn(ExperimentalRoborazziApi::class)
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

    /**
     * Como [settle], pero hasta que aparezca [text] (o pasen [timeoutMs]): tras una escritura en Room el flujo
     * puede tardar más que unas rondas fijas si la máquina va cargada.
     */
    private fun settleUntil(text: String, timeoutMs: Long = 10_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() && System.currentTimeMillis() < end) settle(1)
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
    fun programs_folded() {
        compose.mainClock.autoAdvance = false
        settle()
        openPrograms()
        settle()
        // Con un diálogo hay dos raíces: se captura la pantalla entera
        captureScreenRoboImage("build/outputs/roborazzi/programs_folded.png")
        // Con más programas el elegido queda abajo: el desplazamiento necesita que avance el reloj
        compose.mainClock.autoAdvance = true
        programsList().performScrollToNode(hasText("Full Body 3 días"))
        compose.onNodeWithText("Full Body 3 días").performClick()
        compose.mainClock.autoAdvance = false
        settleUntil("Full Body 3 días · A")
        // Aparece en «Te toca» y en la lista de rutinas
        assertTrue(compose.onAllNodesWithText("Full Body 3 días · A").fetchSemanticsNodes().isNotEmpty())
        shot("programs_added_folded")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun pause_substitute_undo() {
        startEmptyWorkoutWithBench()
        // Pausa: el cronómetro se detiene y aparece el aviso
        compose.onNodeWithContentDescription("Pausar entreno").performClick()
        settle()
        assertNotNull(compose.onNodeWithText("Entreno en pausa").fetchSemanticsNode())
        shot("workout_paused_folded")
        compose.onNodeWithText("Entreno en pausa").performClick()
        settle()
        // Cambiar de ejercicio por una alternativa del mismo músculo
        compose.onNodeWithContentDescription("Opciones de Press banca").performClick()
        settle()
        compose.onNodeWithText("Cambiar por otro ejercicio").performClick()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/substitute_folded.png")
        compose.onNodeWithText("Press banca con mancuernas").performClick()
        settle()
        assertTrue(compose.onAllNodesWithText("Press banca con mancuernas").fetchSemanticsNodes().isNotEmpty())
        // Quitar el ejercicio y deshacer
        compose.onNodeWithContentDescription("Opciones de Press banca con mancuernas").performClick()
        settle()
        compose.onNodeWithText("Quitar ejercicio").performClick()
        settle()
        compose.onNodeWithText("Deshacer").performClick()
        settle(20)
        assertTrue(compose.onAllNodesWithText("Press banca con mancuernas").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun step_bar_autofills_following_sets() {
        startEmptyWorkoutWithBench()
        // Al tocar el peso aparece la barra ±; sumar en la primera serie rellena las siguientes
        compose.onAllNodes(hasContentDescription("Peso en kg"))[0].performClick()
        settle()
        compose.onNodeWithContentDescription("Sumar 2,5 kg").performClick()
        settle()
        compose.onNodeWithContentDescription("Sumar 2,5 kg").performClick()
        settle()
        shot("step_bar_folded")
        assertEquals(3, compose.onAllNodes(hasContentDescription("Peso en kg") and hasText("5")).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "es-w884dp-h1104dp-xhdpi")
    fun planning_unfolded() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Planificar"))
        compose.onNodeWithText("Planificar").performClick()
        settle()
        assertNotNull(compose.onNodeWithText("Calendario de la semana").fetchSemanticsNode())
        compose.onNodeWithText("Generar").performClick()
        settle()
        shot("planning_unfolded")
    }

    /** Sin rutinas el botón de programas queda bajo la ilustración: se desplaza con el reloj en marcha. */
    private fun openPrograms() {
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Crear mi programa").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Crear mi programa").performClick()
    }

    /** Lista del diálogo de programas (la rejilla de Inicio también es desplazable). */
    private fun programsList() = compose.onNode(hasTestTag("programs_list"))

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun generated_program_folded() {
        compose.mainClock.autoAdvance = false
        settle()
        openPrograms()
        settle()
        // Objetivo y días: el programa a medida cambia al momento
        compose.onNodeWithText("Perder grasa").performClick()
        compose.mainClock.autoAdvance = true
        // Tres días por defecto (L X V): añadir el jueves deja el programa en 4
        programsList().performScrollToNode(hasText("J"))
        compose.onNodeWithText("J").performClick()
        compose.mainClock.autoAdvance = false
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/builder_folded.png")
        compose.onNodeWithText("Crear programa").performClick()
        settle(25)
        // Sus cuatro rutinas, juntas en la carpeta del programa
        assertTrue(compose.onAllNodesWithText("Perder grasa · 4 días", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("generated_added_folded")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun periodized_program_today() {
        compose.mainClock.autoAdvance = false
        settle()
        openPrograms()
        settle()
        compose.mainClock.autoAdvance = true
        programsList().performScrollToNode(hasText("5/3/1 Boring But Big"))
        compose.onNodeWithText("5/3/1 Boring But Big").performClick()
        compose.mainClock.autoAdvance = false
        settle()
        // Sin TM en todos los básicos no se puede empezar
        compose.onNodeWithText("Empezar programa").assertIsNotEnabled()
        val tmFields = compose.onAllNodes(hasSetTextAction() and hasText("TM ", substring = true))
        repeat(tmFields.fetchSemanticsNodes().size) { tmFields[it].performTextReplacement("100") }
        // TM del press militar
        compose.onNode(hasSetTextAction() and hasText("TM Press militar", substring = true)).performTextReplacement("60")
        settle()
        shot("program_setup_folded")
        compose.onNodeWithText("Empezar programa").performClick()
        settleUntil("40×5  45×5  50×5 kg")
        assertNotNull(compose.onNodeWithText("Hoy toca").fetchSemanticsNode())
        // Semana 1 del 5/3/1 con TM 60: 65/75/85 % → 40 / 45 / 50 kg
        assertNotNull(compose.onNodeWithText("40×5  45×5  50×5 kg").fetchSemanticsNode())
        shot("today_folded")
        compose.onNodeWithText("Empezar sesión").performClick()
        settle()
        assertTrue(compose.onAllNodesWithText("Press militar", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("today_workout_folded")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun share_card() {
        compose.mainClock.autoAdvance = false
        settle()
        val bench = ExerciseLibrary.byId("bench_press")!!
        val squat = ExerciseLibrary.byId("squat")!!
        val start = 1_760_000_000_000L
        val w = Workout(
            name = "Empuje",
            startedAtEpochMs = start,
            finishedAtEpochMs = start + 62 * 60_000,
            exercises = listOf(
                WorkoutExercise(exercise = bench, sets = List(4) { WorkoutSet(weightKg = 80.0, reps = 8, completed = true) }),
                WorkoutExercise(exercise = squat, sets = List(3) { WorkoutSet(weightKg = 100.0, reps = 5, completed = true) }),
            ),
            avgHeartRate = 128,
            calories = 410,
        )
        val bmp = WorkoutShareCard.render(compose.activity, w, emptyList(), WeightUnit.KG)
        bmp.captureRoboImage("build/outputs/roborazzi/share_card.png")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-xxhdpi")
    fun monthly_report() {
        compose.mainClock.autoAdvance = false
        settle()
        val month = java.time.YearMonth.of(2026, 9)
        val summary = com.bestrong.core.stats.PeriodSummary(
            workouts = 13,
            volume = 84_250.0,
            sets = 260,
            durationSec = 13 * 3_900L,
            records = emptyList(),
            volumeChange = 0.12,
            topExercises = listOf("squat" to 21_000.0, "bench_press" to 15_500.0, "deadlift" to 12_000.0),
        )
        val days = setOf(1, 3, 5, 8, 10, 12, 15, 17, 19, 22, 24, 26, 29).map { month.atDay(it) }.toSet()
        com.bestrong.mobile.share.MonthlyReport.render(compose.activity, month, summary, days, WeightUnit.KG)
            .captureRoboImage("build/outputs/roborazzi/monthly_report.png")
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

/** Modo oscuro: capturas de referencia de Inicio y Perfil con el tema oscuro del sistema. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UiDarkTest {
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
    @Config(qualifiers = "es-w411dp-h891dp-night-xxhdpi")
    fun home_folded_dark() {
        compose.mainClock.autoAdvance = false
        settle()
        assertNotNull(compose.onNodeWithText("Entrenamiento vacío").fetchSemanticsNode())
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_folded_dark.png")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-night-xxhdpi")
    fun profile_folded_dark() {
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithText("Perfil").performClick()
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/profile_folded_dark.png")
    }
}
