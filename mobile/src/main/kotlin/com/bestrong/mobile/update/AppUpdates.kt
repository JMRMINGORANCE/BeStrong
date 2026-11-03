package com.bestrong.mobile.update

import android.app.Activity
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
import com.bestrong.mobile.notify.AnnouncementsWorker
import com.bestrong.mobile.shared.R
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.Job
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
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result -> onFlowResult(result.resultCode) }
    private val _prompt = MutableStateFlow<UpdatePrompt>(UpdatePrompt.None)
    val prompt: StateFlow<UpdatePrompt> = _prompt
    private var info: AppUpdateInfo? = null

    /** Versión que el usuario aceptó y que Play está descargando: no se vuelve a ofrecer mientras tanto. */
    private var inFlightVersion: Int? = null
    private var checkJob: Job? = null

    private val listener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> _prompt.value = UpdatePrompt.Downloaded
            // Falló o se canceló la descarga: se podrá volver a ofrecer en la próxima comprobación
            InstallStatus.FAILED, InstallStatus.CANCELED -> inFlightVersion = null
            InstallStatus.INSTALLED -> {
                inFlightVersion = null
                _prompt.value = UpdatePrompt.None
            }
            else -> Unit
        }
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
        // Una sola comprobación a la vez: volver a la app varias veces seguidas no apila diálogos
        checkJob?.cancel()
        checkJob = activity.lifecycleScope.launch { check() }
        // Y los avisos del equipo, sin esperar a la comprobación periódica
        runCatching { AnnouncementsWorker.checkNow(activity.applicationContext) }
    }

    private suspend fun check() {
        val current = runCatching { manager?.appUpdateInfo?.await() }.getOrNull() ?: return
        info = current
        _prompt.value = decide(
            availability = current.updateAvailability(),
            installStatus = current.installStatus(),
            flexibleAllowed = current.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            available = current.availableVersionCode(),
            inFlightVersion = inFlightVersion,
            dismissedVersion = prefs.getInt(KEY_DISMISSED_VERSION, 0),
            dismissedAt = prefs.getLong(KEY_DISMISSED_AT, 0),
            now = System.currentTimeMillis(),
        )
    }

    /** Vuelta de la pantalla de Play: aceptada (empieza la descarga), cancelada o fallida. */
    private fun onFlowResult(resultCode: Int) {
        val version = inFlightVersion ?: return
        when (resultCode) {
            Activity.RESULT_OK -> Unit
            // «No, gracias» en Play: como «Ahora no» (no se vuelve a preguntar en unos días)
            Activity.RESULT_CANCELED -> {
                inFlightVersion = null
                later(version)
            }
            // Falló al empezar: se reintenta en la próxima comprobación
            else -> inFlightVersion = null
        }
    }

    /** «Actualizar»: Play enseña su pantalla de confirmación y descarga en segundo plano. */
    fun update() {
        val current = info ?: return
        _prompt.value = UpdatePrompt.None
        inFlightVersion = current.availableVersionCode()
        // Un AppUpdateInfo solo sirve para un intento: se descarta y, si Play no abre su pantalla, se vuelve a preguntar
        info = null
        val started = runCatching { manager?.startUpdateFlowForResult(current, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()) == true }
            .getOrDefault(false)
        if (!started) {
            inFlightVersion = null
            checkJob?.cancel()
            checkJob = activity.lifecycleScope.launch { check() }
        }
    }

    /** «Ahora no»: no se vuelve a preguntar por esta versión hasta dentro de unos días. */
    fun later(versionCode: Int) {
        prefs.edit().putInt(KEY_DISMISSED_VERSION, versionCode).putLong(KEY_DISMISSED_AT, System.currentTimeMillis()).apply()
        _prompt.value = UpdatePrompt.None
    }

    /** «Reiniciar»: instala la versión descargada y vuelve a abrir la app. */
    fun restart() {
        _prompt.value = UpdatePrompt.None
        // Si Play ya no la tiene lista (se borró la descarga), se vuelve a comprobar en vez de quedarse sin nada
        checkJob?.cancel()
        checkJob = activity.lifecycleScope.launch {
            val ok = runCatching { manager?.completeUpdate()?.await() }.isSuccess && manager != null
            if (!ok) check()
        }
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

        /**
         * Qué enseñar con lo que dice Play: «Reiniciar» si ya está descargada; nada mientras se descarga o instala (si no,
         * al volver de la pantalla de Play salía otra vez «Actualizar»); «Actualizar» si hay una nueva y no se rechazó hace poco.
         */
        @Suppress("LongParameterList")
        fun decide(
            availability: Int,
            installStatus: Int,
            flexibleAllowed: Boolean,
            available: Int,
            inFlightVersion: Int?,
            dismissedVersion: Int,
            dismissedAt: Long,
            now: Long,
        ): UpdatePrompt = when {
            installStatus == InstallStatus.DOWNLOADED -> UpdatePrompt.Downloaded
            installStatus in IN_PROGRESS -> UpdatePrompt.None
            availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> UpdatePrompt.None
            inFlightVersion == available -> UpdatePrompt.None
            availability == UpdateAvailability.UPDATE_AVAILABLE && flexibleAllowed && shouldAsk(available, dismissedVersion, dismissedAt, now) ->
                UpdatePrompt.Available(available)
            else -> UpdatePrompt.None
        }

        private val IN_PROGRESS = setOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING)
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
