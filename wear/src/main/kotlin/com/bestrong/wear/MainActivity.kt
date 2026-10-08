package com.bestrong.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.health.ExerciseService
import com.bestrong.wear.ui.WearApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var container: WearContainer

    /** Petición pendiente desde el móvil: "" = entrenamiento vacío, id = rutina. */
    private val startRequest = MutableStateFlow<String?>(null)

    /** Modo siempre activo (pantalla atenuada): la UI muestra una versión de bajo consumo. */
    private val ambient = MutableStateFlow(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient.value = true
        }
        override fun onExitAmbient() {
            ambient.value = false
        }
        override fun onUpdateAmbient() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En dispositivos sin la librería de Wear (tests, emuladores no-Wear) el modo ambiente no existe
        runCatching { lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback)) }

        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS))

        lifecycleScope.launch { container.phoneSync.pullFromPhone(onActive = container.session::applyRemote) }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            WearApp(
                container = container,
                startRequest = startRequest,
                ambient = ambient,
                onStartRequestHandled = { startRequest.value = null },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Entreno empezado en el móvil con la app del reloj cerrada: arrancamos la medición ahora
        if (container.session.workout.value != null) ExerciseService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == SyncContract.DEEP_LINK_SCHEME && data.host == SyncContract.DEEP_LINK_HOST) {
            startRequest.value = data.getQueryParameter(SyncContract.DEEP_LINK_PARAM_ROUTINE).orEmpty()
        }
    }
}
