package com.bestrong.mobile.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bestrong.mobile.AppIntents
import com.bestrong.mobile.net.AppsScript
import com.bestrong.mobile.shared.R
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Un aviso del equipo de BeStrong (texto plano, sin enlaces ni acciones). */
data class Announcement(val id: Int, val title: String, val text: String)

/**
 * Avisos del equipo: cada pocas horas (con conexión) se pregunta al Apps Script si hay alguno nuevo
 * y se enseña como notificación una sola vez. No se envía nada del usuario: solo se pide la lista.
 * Los avisos salen del archivo avisos.json del repositorio privado de propuestas, que solo edita el equipo.
 */
class AnnouncementsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Sin conexión o sin respuesta del script: se prueba en la siguiente vuelta
        val list = runCatching { fetch() }.getOrNull().orEmpty()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        pick(list, prefs.getInt(KEY_SEEN, 0))?.let { next ->
            notify(applicationContext, next)
            prefs.edit().putInt(KEY_SEEN, list.maxOf { it.id }).apply()
        }
        return Result.success()
    }

    /** Solo texto, en un canal propio (se puede silenciar sin perder nada más); al tocarlo se abre la app. */
    @Suppress("MissingPermission")
    private fun notify(context: Context, announcement: Announcement) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel_announcements), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            AppIntents.main(context).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dumbbell)
            .setContentTitle(announcement.title.ifEmpty { context.getString(R.string.notif_announcement_title) })
            .setContentText(announcement.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(announcement.text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    private fun fetch(): List<Announcement>? {
        val json = JSONObject(AppsScript.request(URL("${AppsScript.ENDPOINT}?key=${AppsScript.APP_KEY}&action=avisos"), null))
        return if (json.optBoolean("ok")) parse(json, Locale.getDefault().language) else null
    }

    companion object {
        private const val NAME = "announcements"
        private const val PREFS = "announcements"
        private const val KEY_SEEN = "seen"
        private const val KEY_LAST_CHECK = "last_check"
        private const val NAME_NOW = "announcements-now"
        private const val MIN_GAP_MS = 30 * 60 * 1000L
        private const val CHANNEL = "announcements"
        private const val NOTIFICATION_ID = 27
        private const val EVERY_HOURS = 6L
        private const val MAX_TITLE = 80
        private const val MAX_TEXT = 500

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AnnouncementsWorker>(EVERY_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Al abrir la app: comprobación inmediata (con conexión), para no esperar a la periódica, que Android
         * puede retrasar horas. Como mucho una cada 30 minutos.
         */
        fun checkNow(context: Context) {
            val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0)
            if (System.currentTimeMillis() - last < MIN_GAP_MS) return
            val request = OneTimeWorkRequestBuilder<AnnouncementsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME_NOW, ExistingWorkPolicy.KEEP, request)
        }

        /** El aviso más reciente que aún no se ha enseñado (uno solo, aunque haya varios nuevos). */
        fun pick(list: List<Announcement>, seen: Int): Announcement? = list.filter { it.id > seen }.maxByOrNull { it.id }

        /**
         * Respuesta del script: {ok, avisos:[{id, titulo:{es,en…}, texto:{es,en…}}]}. Se elige el idioma del
         * móvil (si no, español, si no, el primero). Se queda solo en texto plano y con longitud acotada.
         */
        fun parse(json: JSONObject, language: String): List<Announcement> {
            val array = json.optJSONArray("avisos") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val a = array.optJSONObject(i) ?: return@mapNotNull null
                val id = a.optInt("id")
                val title = localized(a.opt("titulo"), language).plain(MAX_TITLE)
                val text = localized(a.opt("texto"), language).plain(MAX_TEXT)
                if (id <= 0 || text.isEmpty()) null else Announcement(id, title, text)
            }
        }

        private fun localized(value: Any?, language: String): String = when (value) {
            is String -> value
            is JSONObject -> value.optString(language).ifEmpty { value.optString("es") }.ifEmpty {
                value.keys().asSequence().map { value.optString(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
            }
            else -> ""
        }

        /** Sin caracteres de control ni espacios de sobra, y cortado a [max]. */
        private fun String.plain(max: Int): String =
            replace(Regex("[\\p{Cntrl}&&[^\\n]]"), "").replace(Regex("\\n{3,}"), "\n\n").trim().take(max)
    }
}
