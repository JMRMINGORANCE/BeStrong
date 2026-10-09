package com.bestrong.mobile

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bestrong.core.io.RoutineLink
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.ui.BeStrongApp
import com.bestrong.mobile.ui.theme.BeStrongTheme
import com.bestrong.mobile.ui.workout.RestPip
import com.bestrong.mobile.workout.RestTimerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Acción pedida desde fuera de la app (notificación, widget). */
sealed interface LaunchRequest {
    data object OpenWorkout : LaunchRequest
    data object StartEmpty : LaunchRequest
    data class StartRoutine(val routineId: String) : LaunchRequest

    /** Rutina compartida por enlace o QR. */
    data class ImportRoutine(val link: String) : LaunchRequest
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: BeStrongRepository

    @Inject lateinit var settings: SettingsStore

    @Inject lateinit var restTimer: RestTimerController

    @Inject lateinit var actions: WorkoutActions

    private val launchRequest = MutableStateFlow<LaunchRequest?>(null)

    /** En picture-in-picture solo se muestra el descanso / la siguiente serie. */
    private val inPip = MutableStateFlow(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        // La notificación del entreno (descanso en la pantalla de bloqueo) necesita este permiso
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Con un entreno en marcha, al salir de la app el descanso sigue en una ventana flotante (PiP)
        lifecycleScope.launch {
            repository.activeWorkout.map { it != null }.distinctUntilChanged().collect { active -> updatePipParams(active) }
        }
        setContent {
            BeStrongTheme {
                val pip by inPip.collectAsStateWithLifecycle()
                if (pip) {
                    RestPip(repository, restTimer)
                } else {
                    BeStrongApp(launchRequest = launchRequest, onLaunchHandled = { launchRequest.value = null })
                }
            }
        }
    }

    private fun updatePipParams(active: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(active)
                    .setSeamlessResizeEnabled(true)
                    .build(),
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    /**
     * Botones de volumen durante el entreno (si se activan en Perfil):
     * subir = completar la siguiente serie, bajar = saltar el descanso.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val handled = settings.settings.value.volumeKeys && repository.activeWorkout.value != null &&
            (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)
        if (!handled) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                lifecycleScope.launch { actions.completeNextSet() }
            } else {
                restTimer.skip()
            }
        }
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data?.scheme == RoutineLink.SCHEME && data.host == RoutineLink.HOST) {
            launchRequest.value = LaunchRequest.ImportRoutine(data.toString())
            return
        }
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
