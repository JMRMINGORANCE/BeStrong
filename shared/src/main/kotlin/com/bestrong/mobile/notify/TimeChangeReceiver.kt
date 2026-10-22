package com.bestrong.mobile.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Cambio de huso horario (un viaje) o de hora a mano: los avisos guardan un retraso absoluto, así que se
 * reprograman todos para que las 18:00 sigan siendo las 18:00 de donde estés. El huso forma parte de la
 * clave de [ExactWork], de modo que se sustituyen los pendientes.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return
        val app = context.applicationContext
        val s = EntryPointAccessors.fromApplication(app, WorkerEntryPoint::class.java).settings().settings.value
        // Sin forzar: cada aviso guarda su instante y WorkManager ya reprograma al cambiar la hora. Forzar con un
        // ajuste automático de la hora tiraba el aviso retenido por el ahorro de batería y lo pasaba a la semana siguiente
        val force = false
        runCatching {
            ReminderWorker.schedule(app, s.reminderDays, s.reminderHour, s.reminderMinute, force)
            WeighInWorker.schedule(app, s.weighInDay, force)
            MeasureWorker.schedule(app, s.measureReminder, force)
            CardioNudgeWorker.schedule(app, s.cardioNudgeDay, force)
            WeeklySummaryWorker.schedule(app, force)
        }.onFailure { Log.w("TimeChangeReceiver", "No se pudieron reprogramar los avisos", it) }
    }
}
