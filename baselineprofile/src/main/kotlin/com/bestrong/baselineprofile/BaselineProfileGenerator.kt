package com.bestrong.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el Baseline Profile del móvil recorriendo los flujos críticos (arranque, entreno,
 * biblioteca, historial y perfil) para que se precompilen y la app arranque y haga scroll más fluido.
 * Los textos se buscan en español o inglés, según el idioma del emulador.
 *
 * ./gradlew :mobile:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private fun text(es: String, en: String) = By.text(Pattern.compile("$es|$en"))

    /** Espera a que aparezca y pulsa; reintenta si la vista se recompone entre medias. */
    private fun MacrobenchmarkScope.tap(selector: BySelector, timeoutMs: Long = 5_000) {
        repeat(3) {
            try {
                device.wait(Until.findObject(selector), timeoutMs)?.click() ?: return
                device.waitForIdle()
                return
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
    }

    @Test
    fun generate() = rule.collect(packageName = "com.copidev.bestrong", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // Entreno: vacío → añadir ejercicio
        tap(text("Entrenamiento vacío", "Empty workout"), 10_000)
        tap(text("Añadir ejercicios", "Add exercises"))
        tap(text("Press banca", "Bench Press"))
        tap(By.desc(Pattern.compile("(Añadir|Add) 1")))
        device.pressBack()
        device.waitForIdle()

        // Pestañas principales
        tap(text("Historial", "History"))
        tap(text("Ejercicios", "Exercises"))
        repeat(2) {
            try {
                device.findObject(By.scrollable(true))?.let { list ->
                    list.setGestureMargin(device.displayWidth / 5)
                    list.fling(Direction.DOWN)
                }
                device.waitForIdle()
            } catch (_: StaleObjectException) {
                device.waitForIdle()
            }
        }
        tap(text("Perfil", "Profile"))
        tap(text("Entrenar", "Train"))
    }
}
