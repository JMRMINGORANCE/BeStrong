package com.bestrong.mobile.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** «Copiar ahora» del aviso de copia fallida: lo intenta de nuevo al momento y quita el aviso. */
class BackupNowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        // Si vuelve a fallar se avisa otra vez (se pidió a mano: hay que saber cómo acaba)
        dagger.hilt.android.EntryPointAccessors.fromApplication(context, com.bestrong.mobile.di.WorkerEntryPoint::class.java)
            .settings().update { it.copy(backupFailNotified = false) }
        AutoBackupWorker.runNow(context)
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
