package com.bestrong.mobile.net

import java.net.HttpURLConnection
import java.net.URL

/**
 * El Google Apps Script de BeStrong (tools/propuestas/Code.gs): buzón de propuestas y avisos.
 * La app no lleva ninguna clave; [APP_KEY] no es secreta, solo evita el ruido de bots genéricos.
 */
object AppsScript {
    const val ENDPOINT = "https://script.google.com/macros/s/AKfycbymPpeaVYJgZWpKv98aTi71Y84DxffJudFzdTCaJFeWfiHQK0e5_NYnIqVgYv2G_XBG/exec"
    const val APP_KEY = "bs-propuestas-v1"
    private const val TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 3

    /** Apps Script responde con una redirección: se sigue a mano para no perder la respuesta. */
    fun request(url: URL, body: String?): String {
        var target = url
        var payload = body
        repeat(MAX_REDIRECTS) {
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
