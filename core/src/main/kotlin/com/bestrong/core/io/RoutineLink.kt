package com.bestrong.core.io

import com.bestrong.core.model.Routine
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.util.newId
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Compartir una rutina como enlace o QR: JSON comprimido (Deflate) en Base64 URL-safe
 * dentro de «bestrong://routine?d=…». Al abrirlo, la rutina se importa con un id nuevo.
 */
object RoutineLink {
    const val SCHEME = "bestrong"
    const val HOST = "routine"
    const val PARAM = "d"

    fun encode(routine: Routine): String {
        val json = SyncContract.json.encodeToString(Routine.serializer(), routine.copy(id = "shared", position = 0))
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(json.encodeToByteArray())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return "$SCHEME://$HOST?$PARAM=" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Lee el parámetro «d» (o el enlace entero) y devuelve la rutina con un id nuevo. */
    fun decode(linkOrData: String): Routine? = runCatching {
        val data = linkOrData.substringAfter("$PARAM=", linkOrData).substringBefore('&')
        val bytes = Base64.getUrlDecoder().decode(data)
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        SyncContract.json.decodeFromString(Routine.serializer(), out.toByteArray().decodeToString()).copy(id = newId())
    }.getOrNull()
}
