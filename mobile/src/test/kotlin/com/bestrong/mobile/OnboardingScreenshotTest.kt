package com.bestrong.mobile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.mobile.data.Appearance
import com.bestrong.mobile.data.ThemeMode
import com.bestrong.mobile.ui.help.TutorialDialog
import com.bestrong.mobile.ui.theme.BeStrongTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La bienvenida a pantalla completa: primera pantalla y la de configuración, con capturas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class OnboardingScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onboarding_pages() {
        var done = false
        compose.setContent {
            BeStrongTheme(Appearance(mode = ThemeMode.DARK)) { TutorialDialog(onDone = { done = true }) }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/onboarding_1.png")
        compose.onNodeWithText("Siguiente").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/onboarding_2.png")
        compose.onNodeWithText("Saltar").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        captureScreenRoboImage("build/outputs/roborazzi/onboarding_setup.png")
        compose.onNodeWithText("Empezar").performClick()
        compose.waitForIdle()
        assert(done)
    }
}
