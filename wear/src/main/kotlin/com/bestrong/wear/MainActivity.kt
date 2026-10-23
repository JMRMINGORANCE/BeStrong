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
            com.bestrong.wear.ui.AmbientBurnIn.required = ambientDetails.burnInProtectionRequired
            ambient.value = true
        }
        override fun onExitAmbient() {
            ambient.value = false
        }
        override fun onUpdateAmbient() = com.bestrong.wear.ui.AmbientBurnIn.onUpdate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En dispositivos sin la librería de Wear (tests, emuladores no-Wear) el modo ambiente no existe
        runCatching { lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback)) }

        // El servicio de medición solo se arranca con el permiso concedido (si no, Android cierra la app)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startMeasuringIfActive() }
            .launch(arrayOf(ExerciseService.HEART_RATE_PERMISSION, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS))

        lifecycleScope.launch {
            container.phoneSync.pullFromPhone(onActive = container.session::applyRemote)
            container.phoneSync.resendPendingWorkouts()
        }
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
        // Entreno empezado en el móvil con la app del reloj cerrada (o retomado tras un reinicio
        // del proceso): arrancamos la medición ahora, si ya hay permiso
        startMeasuringIfActive()
    }

    private fun startMeasuringIfActive() {
        if (container.session.workout.value != null || container.cardio.isActive) ExerciseService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Desde Recientes, Android repite el intent con el que se abrió: no se vuelve a empezar esa rutina
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val data = intent.data
        // Desde el tile «Hoy toca»: empezar esa rutina; o desde el enlace de la complicación o del móvil
        val routine = intent.getStringExtra(EXTRA_ROUTINE)
            ?: data?.takeIf { it.scheme == SyncContract.DEEP_LINK_SCHEME && it.host == SyncContract.DEEP_LINK_HOST }
                ?.let { it.getQueryParameter(SyncContract.DEEP_LINK_PARAM_ROUTINE).orEmpty() }
        if (routine != null) startRequest.value = routine
    }

    companion object {
        const val EXTRA_ROUTINE = "com.bestrong.wear.ROUTINE"
    }
}
