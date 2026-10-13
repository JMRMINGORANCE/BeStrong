package com.bestrong.mobile.notify

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Avisos a hora exacta: un OneTimeWork con el retraso justo hasta la próxima vez, que el propio
 * Worker vuelve a programar al acabar. Un periódico deriva (flex, Doze) y no sigue los cambios de
 * hora ni de huso.
 */
internal object ExactWork {
    private const val PREFS = "exact_work"

    /**
     * Programa [name] para [next]. Si la configuración ([config] + huso horario) no ha cambiado y ya
     * hay uno pendiente se respeta (al abrir la app no se pisa un aviso que Doze está retrasando).
     * El Worker, al reprogramarse a sí mismo, pasa [force] (KEEP lo dejaría en su propia ejecución).
     */
    fun enqueue(context: Context, name: String, config: String, next: ZonedDateTime, worker: Class<out CoroutineWorker>, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "$config@${ZoneId.systemDefault().id}"
        val keep = !force && prefs.getString(name, null) == key
        val delay = Duration.between(ZonedDateTime.now(next.zone), next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequest.Builder(worker).setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        // KEEP solo respeta el que siga pendiente; si no queda ninguno, encola este
        WorkManager.getInstance(context).enqueueUniqueWork(name, if (keep) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
        prefs.edit { putString(name, key) }
    }

    fun cancel(context: Context, name: String) {
        WorkManager.getInstance(context).cancelUniqueWork(name)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(name) }
    }
}
