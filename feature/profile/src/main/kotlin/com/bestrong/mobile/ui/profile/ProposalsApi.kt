package com.bestrong.mobile.ui.profile

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Una propuesta de mejora enviada desde este dispositivo, con las preguntas y respuestas. */
data class Proposal(val number: Int, val title: String, val status: String, val messages: List<ProposalMessage>)

data class ProposalMessage(val fromUser: Boolean, val text: String)

/** Resultado de una petición: [error] es un código corto («limite», «red», «corta»…) si ha fallado. */
data class ProposalResult(val ok: Boolean, val error: String? = null)

/**
 * Buzón de propuestas: la app habla con un Google Apps Script que guarda cada propuesta como issue
 * en un repositorio privado de GitHub (la app no lleva ninguna clave). Solo se envía lo que el usuario
 * escribe, la versión de la app y un identificador aleatorio de este dispositivo para enseñarle sus respuestas.
 */
object ProposalsApi {
    private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbymPpeaVYJgZWpKv98aTi71Y84DxffJudFzdTCaJFeWfiHQK0e5_NYnIqVgYv2G_XBG/exec"
    private const val APP_KEY = "bs-propuestas-v1"
    private const val TIMEOUT_MS = 20_000

    /** Identificador aleatorio de esta instalación (no está ligado a la cuenta ni al dispositivo). */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("proposals", Context.MODE_PRIVATE)
        return prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
    }

    suspend fun send(context: Context, title: String, what: String, how: String): ProposalResult {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val body = JSONObject()
            .put("action", "create").put("key", APP_KEY).put("device", deviceId(context))
            .put("title", title.trim()).put("what", what.trim()).put("how", how.trim()).put("version", version)
        return result(post(body))
    }

    suspend fun reply(context: Context, number: Int, text: String): ProposalResult {
        val body = JSONObject()
            .put("action", "reply").put("key", APP_KEY).put("device", deviceId(context))
            .put("number", number).put("text", text.trim())
        return result(post(body))
    }

    /** Propuestas de este dispositivo; null si no hay conexión o el buzón no responde. */
    suspend fun list(context: Context): List<Proposal>? = withContext(Dispatchers.IO) {
        runCatching {
            val device = URLEncoder.encode(deviceId(context), "UTF-8")
            val json = JSONObject(request(URL("$ENDPOINT?key=$APP_KEY&device=$device"), null))
            if (!json.optBoolean("ok")) null else parseProposals(json.optJSONArray("proposals") ?: JSONArray())
        }.getOrNull()
    }

    private fun parseProposals(array: JSONArray): List<Proposal> = (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        val messages = p.optJSONArray("messages") ?: JSONArray()
        Proposal(
            number = p.optInt("number"),
            title = p.optString("title"),
            status = p.optString("status", "recibida"),
            messages = (0 until messages.length()).map { j ->
                val m = messages.getJSONObject(j)
                ProposalMessage(fromUser = m.optBoolean("fromUser"), text = m.optString("text"))
            },
        )
    }

    private suspend fun post(body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(request(URL(ENDPOINT), body.toString())) }.getOrNull()
    }

    private fun result(json: JSONObject?): ProposalResult = when {
        json == null -> ProposalResult(false, "red")
        json.optBoolean("ok") -> ProposalResult(true)
        else -> ProposalResult(false, json.optString("error", "servidor"))
    }

    /** Apps Script responde con una redirección: se sigue a mano para no perder la respuesta. */
    private fun request(url: URL, body: String?): String {
        var target = url
        var payload = body
        repeat(3) {
            val bytes = payload?.toByteArray()
            val conn = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                if (bytes != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(bytes) }
                }
            }
            try {
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                if (code in 300..399 && location != null) {
                    target = URL(location)
                    payload = null
                } else {
                    return conn.inputStream.bufferedReader().use { it.readText() }
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Demasiadas redirecciones")
    }
}
