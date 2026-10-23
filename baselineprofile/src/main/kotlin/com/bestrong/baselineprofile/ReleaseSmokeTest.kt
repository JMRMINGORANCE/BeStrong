package com.bestrong.baselineprofile

import androidx.benchmark.Outputs
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.io.File
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
        packageName = "com.copidev.bestrong",
        metrics = listOf(StartupTimingMetric()),
        iterations = 1,
        startupMode = StartupMode.COLD,
        // El diálogo de permiso de notificaciones (Android 13+) taparía la app: se concede antes
        setupBlock = { device.executeShellCommand("pm grant com.copidev.bestrong android.permission.POST_NOTIFICATIONS") },
    ) {
        pressHome()
        startActivityAndWait()
        // Por si aun así aparece algún diálogo de permisos
        device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow|permitir"))), 2_000)?.click()
        // Qué hay en pantalla (para saber si la app se cerró o si falta un botón)
        fun screen(): String {
            // Captura y árbol de accesibilidad (con estado de casillas) para la CI
            runCatching {
                val dir = Outputs.outputDirectory
                device.takeScreenshot(File(dir, "smoke-failure.png"))
                device.dumpWindowHierarchy(File(dir, "smoke-failure.xml"))
            }
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
        // El teclado de búsqueda puede abrirse y mover la lista justo al tocar: se reintenta hasta que se marca
        val addOne = By.desc(Pattern.compile("(Añadir|Add) 1"))
        repeat(3) {
            if (device.hasObject(addOne)) return@repeat
            device.waitForIdle()
            device.wait(Until.findObject(text("Press banca", "Bench Press")), 5_000)?.click()
            device.wait(Until.hasObject(addOne), 3_000)
        }
        tap(addOne, "Añadir 1")
        val ok = device.wait(Until.hasObject(text("Terminar", "Finish")), 10_000)
        assertTrue("La pantalla de entreno no apareció en la build minificada. ${screen()}", ok)
    }
}
