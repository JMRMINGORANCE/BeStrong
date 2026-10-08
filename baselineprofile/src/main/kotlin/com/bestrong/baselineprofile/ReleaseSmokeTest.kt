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
        // Qué hay en pantalla (para saber si la app se cerró o si falta un botón)
        fun screen(): String {
            val texts = runCatching { device.findObjects(By.text(Pattern.compile(".+"))).mapNotNull { it.text } }.getOrDefault(emptyList())
            return "paquete=${device.currentPackageName} textos=$texts"
        }
        fun tap(selector: androidx.test.uiautomator.BySelector, what: String) {
            repeat(3) {
                try {
                    val obj = device.wait(Until.findObject(selector), 10_000)
                        ?: throw AssertionError("No apareció «$what». ${screen()}")
                    obj.click()
                    device.waitForIdle()
                    return
                } catch (_: StaleObjectException) {
                    device.waitForIdle()
                }
            }
        }
        tap(text("Entrenamiento vacío", "Empty workout"), "Entrenamiento vacío")
        tap(text("Añadir ejercicios", "Add exercises"), "Añadir ejercicios")
        tap(text("Press banca", "Bench Press"), "Press banca")
        tap(By.text(Pattern.compile("(Añadir|Add) 1")), "Añadir 1")
        val ok = device.wait(Until.hasObject(text("Terminar", "Finish")), 10_000)
        assertTrue("La pantalla de entreno no apareció en la build minificada. ${screen()}", ok)
    }
}
