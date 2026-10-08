package com.bestrong.mobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.bestrong.mobile.ui.BeStrongApp
import com.bestrong.mobile.ui.theme.BeStrongTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/** Acción pedida desde fuera de la app (notificación, widget). */
sealed interface LaunchRequest {
    data object OpenWorkout : LaunchRequest
    data object StartEmpty : LaunchRequest
    data class StartRoutine(val routineId: String) : LaunchRequest
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val launchRequest = MutableStateFlow<LaunchRequest?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        // La notificación del entreno (descanso en la pantalla de bloqueo) necesita este permiso
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            BeStrongTheme {
                BeStrongApp(launchRequest = launchRequest, onLaunchHandled = { launchRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        launchRequest.value = when (intent?.action) {
            ACTION_OPEN_WORKOUT -> LaunchRequest.OpenWorkout
            ACTION_START_EMPTY -> LaunchRequest.StartEmpty
            ACTION_START_ROUTINE -> intent.getStringExtra(EXTRA_ROUTINE_ID)?.let { LaunchRequest.StartRoutine(it) }
            else -> null
        } ?: launchRequest.value
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "com.bestrong.OPEN_WORKOUT"
        const val ACTION_START_EMPTY = "com.bestrong.START_EMPTY"
        const val ACTION_START_ROUTINE = "com.bestrong.START_ROUTINE"
        const val EXTRA_ROUTINE_ID = "routine_id"
    }
}
