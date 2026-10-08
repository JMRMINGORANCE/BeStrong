package com.bestrong.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el Baseline Profile del móvil recorriendo los flujos críticos (arranque, entreno,
 * biblioteca, historial y perfil) para que se precompilen y la app arranque y haga scroll más fluido.
 *
 * ./gradlew :mobile:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.bestrong", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Entrenamiento vacío")), 10_000)

        // Entreno: vacío → añadir ejercicio
        device.findObject(By.text("Entrenamiento vacío"))?.click()
        device.wait(Until.hasObject(By.text("Añadir ejercicios")), 5_000)
        device.findObject(By.text("Añadir ejercicios"))?.click()
        device.wait(Until.hasObject(By.text("Press banca")), 5_000)
        device.findObject(By.text("Press banca"))?.click()
        device.findObject(By.textStartsWith("Añadir 1"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()

        // Pestañas principales
        listOf("Historial", "Ejercicios", "Perfil", "Entrenar").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
        device.findObject(By.text("Ejercicios"))?.click()
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(androidx.test.uiautomator.Direction.DOWN)
            device.waitForIdle()
        }
    }
}
