package com.bestrong.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.ui.WearApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Petición pendiente desde el móvil: "" = entrenamiento vacío, id = rutina. */
    private val startRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BeStrongWearApp).container

        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS))

        lifecycleScope.launch { container.phoneSync.pullFromPhone() }
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            WearApp(container = container, startRequest = startRequest, onStartRequestHandled = { startRequest.value = null })
        }
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
