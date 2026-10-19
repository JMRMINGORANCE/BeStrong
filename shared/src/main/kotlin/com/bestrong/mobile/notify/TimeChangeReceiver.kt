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
        // Con la hora cambiada a mano el huso es el mismo: se fuerza para no quedarse con el retraso viejo
        val force = intent.action == Intent.ACTION_TIME_CHANGED
        runCatching {
            ReminderWorker.schedule(app, s.reminderDays, s.reminderHour, s.reminderMinute, force)
            WeighInWorker.schedule(app, s.weighInDay, force)
            MeasureWorker.schedule(app, s.measureReminder, force)
            CardioNudgeWorker.schedule(app, s.cardioNudgeDay, force)
            WeeklySummaryWorker.schedule(app, force)
        }.onFailure { Log.w("TimeChangeReceiver", "No se pudieron reprogramar los avisos", it) }
    }
}
