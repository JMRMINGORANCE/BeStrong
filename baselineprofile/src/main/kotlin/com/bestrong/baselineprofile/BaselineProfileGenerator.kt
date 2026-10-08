package com.bestrong.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
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

    @Test
    fun generate() = rule.collect(packageName = "com.bestrong", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        val empty = text("Entrenamiento vacío", "Empty workout")
        device.wait(Until.hasObject(empty), 10_000)

        // Entreno: vacío → añadir ejercicio
        device.findObject(empty)?.click()
        val addExercises = text("Añadir ejercicios", "Add exercises")
        device.wait(Until.hasObject(addExercises), 5_000)
        device.findObject(addExercises)?.click()
        val bench = text("Press banca", "Bench Press")
        device.wait(Until.hasObject(bench), 5_000)
        device.findObject(bench)?.click()
        device.findObject(By.text(Pattern.compile("(Añadir|Add) 1")))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // Pestañas principales
        listOf(
            text("Historial", "History"),
            text("Ejercicios", "Exercises"),
            text("Perfil", "Profile"),
            text("Entrenar", "Train"),
        ).forEach { tab ->
            device.findObject(tab)?.click()
            device.waitForIdle()
        }
        device.findObject(text("Ejercicios", "Exercises"))?.click()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}
