package com.bestrong.baselineprofile

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de humo sobre la build *minificada* (variante benchmarkRelease, con R8):
 * arranca en frío, crea un entreno y añade un ejercicio. Si R8 rompe la serialización
 * o Hilt, la app se cae aquí. También mide el tiempo de arranque.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun text(es: String, en: String) = By.text(Pattern.compile("$es|$en"))

    @Test
    fun coldStartAndWorkout() = rule.measureRepeated(
        packageName = "com.bestrong",
        metrics = listOf(StartupTimingMetric()),
        iterations = 1,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        fun tap(selector: androidx.test.uiautomator.BySelector) {
            repeat(3) {
                try {
                    device.wait(Until.findObject(selector), 10_000)?.click()
                    device.waitForIdle()
                    return
                } catch (_: StaleObjectException) {
                    device.waitForIdle()
                }
            }
        }
        tap(text("Entrenamiento vacío", "Empty workout"))
        tap(text("Añadir ejercicios", "Add exercises"))
        tap(text("Press banca", "Bench Press"))
        tap(By.text(Pattern.compile("(Añadir|Add) 1")))
        val ok = device.wait(Until.hasObject(text("Terminar", "Finish")), 10_000)
        assertTrue("La pantalla de entreno no apareció en la build minificada", ok)
    }
}
