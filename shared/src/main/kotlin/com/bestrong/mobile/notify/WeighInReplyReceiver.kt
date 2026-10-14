package com.bestrong.mobile.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Respuesta directa a la notificación de pesarse: «80,4» se guarda como medida de hoy (y en Health Connect
 * si está activado) sin abrir la app. La notificación se sustituye por la confirmación o por el error.
 */
class WeighInReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_WEIGHT)?.toString() ?: return
        val app = context.applicationContext
        val deps = EntryPointAccessors.fromApplication(app, WorkerEntryPoint::class.java)
        val settings = deps.settings().settings.value
        val unit = settings.bodyUnit ?: settings.unit
        // Un peso corporal razonable (evita guardar un «8» por error al teclear)
        val kg = unit.parseToKg(text.trim())?.takeIf { it in MIN_KG..MAX_KG }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (kg != null) deps.actions().saveMeasurement(BodyMeasurement(dateEpochMs = System.currentTimeMillis(), weightKg = kg))
                Notifier.weighInResult(app, kg?.let { unit.format(it, withUnit = true) })
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val KEY_WEIGHT = "weight"
        private const val MIN_KG = 20.0
        private const val MAX_KG = 400.0
    }
}
