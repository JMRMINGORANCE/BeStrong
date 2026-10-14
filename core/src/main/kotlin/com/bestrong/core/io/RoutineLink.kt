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
    private const val MAX_LINK_CHARS = 16_000
    private const val MAX_JSON_BYTES = 256 * 1024
    private const val MAX_EXERCISES = 50
    private const val MAX_SETS = 30

    const val SCHEME = "bestrong"
    const val HOST = "routine"
    const val PARAM = "d"

    fun encode(routine: Routine): String {
        val json = SyncContract.json.encodeToString(Routine.serializer(), routine.copy(id = "shared", position = 0, archived = false))
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
        // Enlaces de cualquier web: se limitan tamaño y descompresión (evita «bombas zip»)
        require(data.length <= MAX_LINK_CHARS)
        val bytes = Base64.getUrlDecoder().decode(data)
        val inflater = Inflater()
        val out = ByteArrayOutputStream()
        try {
            inflater.setInput(bytes)
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
                require(out.size() <= MAX_JSON_BYTES)
            }
        } finally {
            // La memoria nativa del Inflater se libera siempre, también si el enlace es inválido
            inflater.end()
        }
        SyncContract.json.decodeFromString(Routine.serializer(), out.toByteArray().decodeToString())
            .also { require(it.exercises.size <= MAX_EXERCISES && it.exercises.all { e -> e.sets.size <= MAX_SETS }) }
            .copy(id = newId(), archived = false)
    }.getOrNull()
}
