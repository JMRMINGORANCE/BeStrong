package com.bestrong.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bestrong.mobile.ui.BeStrongApp
import com.bestrong.mobile.ui.theme.BeStrongTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openWorkout = savedInstanceState == null && intent?.action == ACTION_OPEN_WORKOUT
        setContent {
            BeStrongTheme { BeStrongApp(startWorkoutFromIntent = openWorkout) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "com.bestrong.OPEN_WORKOUT"
    }
}
