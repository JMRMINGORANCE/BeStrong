package com.bestrong.mobile.update

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bestrong.mobile.shared.R
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Qué se le enseña al usuario sobre una versión nueva de Google Play. */
sealed interface UpdatePrompt {
    data object None : UpdatePrompt

    /** Hay versión nueva: «Actualizar» la descarga en segundo plano. */
    data class Available(val versionCode: Int) : UpdatePrompt

    /** Ya descargada: «Reiniciar» la instala. */
    data object Downloaded : UpdatePrompt
}

/**
 * Actualizaciones dentro de la app (Google Play In-App Updates, modo flexible): al volver a la app se pregunta
 * a Play si hay una versión nueva y, si la hay, se ofrece con un botón. Fuera de Play (depuración, tests) no hace nada.
 * Se crea en la inicialización de la actividad (registra el lanzador antes de onStart); el resto, perezoso.
 */
class AppUpdates(private val activity: ComponentActivity) : DefaultLifecycleObserver {
    // Perezosos: al crearse (en el constructor de la actividad) aún no hay contexto
    private val manager: AppUpdateManager? by lazy { runCatching { AppUpdateManagerFactory.create(activity.applicationContext) }.getOrNull() }
    private val prefs by lazy { activity.getSharedPreferences(UpdateCheckWorker.PREFS, Context.MODE_PRIVATE) }
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    private val _prompt = MutableStateFlow<UpdatePrompt>(UpdatePrompt.None)
    val prompt: StateFlow<UpdatePrompt> = _prompt
    private var info: AppUpdateInfo? = null

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) _prompt.value = UpdatePrompt.Downloaded
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        // Comprobación diaria para avisar con una notificación si la app lleva días sin abrirse
        runCatching { UpdateCheckWorker.schedule(activity.applicationContext) }
    }

    override fun onStart(owner: LifecycleOwner) {
        runCatching { manager?.registerListener(listener) }
    }

    override fun onStop(owner: LifecycleOwner) {
        runCatching { manager?.unregisterListener(listener) }
    }

    override fun onResume(owner: LifecycleOwner) {
        // La app está abierta: la notificación de versión nueva ya no hace falta
        prefs.edit().putLong(UpdateCheckWorker.KEY_LAST_OPEN, System.currentTimeMillis()).apply()
        activity.lifecycleScope.launch { check() }
    }

    private suspend fun check() {
        val current = runCatching { manager?.appUpdateInfo?.await() }.getOrNull() ?: return
        info = current
        val now = System.currentTimeMillis()
        _prompt.value = when {
            current.installStatus() == InstallStatus.DOWNLOADED -> UpdatePrompt.Downloaded
            current.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                current.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                shouldAsk(current.availableVersionCode(), prefs.getInt(KEY_DISMISSED_VERSION, 0), prefs.getLong(KEY_DISMISSED_AT, 0), now) ->
                UpdatePrompt.Available(current.availableVersionCode())
            else -> UpdatePrompt.None
        }
    }

    /** «Actualizar»: Play enseña su pantalla de confirmación y descarga en segundo plano. */
    fun update() {
        val current = info ?: return
        _prompt.value = UpdatePrompt.None
        runCatching { manager?.startUpdateFlowForResult(current, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()) }
    }

    /** «Ahora no»: no se vuelve a preguntar por esta versión hasta dentro de unos días. */
    fun later(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION, versionCode).putLong(KEY_DISMISSED_AT, System.currentTimeMillis()).apply()
        _prompt.value = UpdatePrompt.None
    }

    /** «Reiniciar»: instala la versión descargada y vuelve a abrir la app. */
    fun restart() {
        _prompt.value = UpdatePrompt.None
        runCatching { manager?.completeUpdate() }
    }

    /** Tras «Más tarde» con la descarga lista: se vuelve a ofrecer la próxima vez que se abra la app. */
    fun dismiss() {
        _prompt.value = UpdatePrompt.None
    }

    companion object {
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_DISMISSED_AT = "dismissed_at"
        private const val ASK_AGAIN_MS = 3 * 86_400_000L

        /** Se pregunta salvo que esta misma versión se haya rechazado hace menos de 3 días. */
        fun shouldAsk(available: Int, dismissedVersion: Int, dismissedAt: Long, now: Long): Boolean =
            available != dismissedVersion || now - dismissedAt >= ASK_AGAIN_MS
    }
}

/** Diálogo de versión nueva (o descargada); nada si no hay ninguna. */
@Composable
fun UpdateDialog(prompt: UpdatePrompt, updates: AppUpdates) {
    when (prompt) {
        UpdatePrompt.None -> Unit
        is UpdatePrompt.Available -> AlertDialog(
            onDismissRequest = { updates.later(prompt.versionCode) },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_available_text)) },
            confirmButton = { TextButton(onClick = updates::update) { Text(stringResource(R.string.update_now)) } },
            dismissButton = { TextButton(onClick = { updates.later(prompt.versionCode) }) { Text(stringResource(R.string.update_later)) } },
        )
        UpdatePrompt.Downloaded -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_ready_title)) },
            text = { Text(stringResource(R.string.update_ready_text)) },
            confirmButton = { TextButton(onClick = updates::restart) { Text(stringResource(R.string.update_restart)) } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.update_later)) } },
        )
    }
}
