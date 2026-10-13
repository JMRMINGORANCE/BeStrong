package com.bestrong.core.io

import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SyncContract
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Formato versionado de lo que se guarda en disco (Room en el móvil, preferencias en el reloj).
 *
 * Cada JSON guardado lleva `schemaVersion`. Al leer, se aplican las migraciones necesarias
 * para llevarlo a la versión actual antes de convertirlo en modelo, de modo que un cambio
 * futuro del modelo no rompa historiales antiguos.
 *
 * Para añadir una versión: subir [CURRENT] y añadir un caso en [migrateWorkout]/[migrateRoutine].
 */
object Persistence {
    const val CURRENT = 1
    private const val KEY = "schemaVersion"
    private val json = SyncContract.json

    fun encodeWorkout(w: Workout): String = stamp(json.encodeToJsonElement(Workout.serializer(), w))
    fun encodeRoutine(r: Routine): String = stamp(json.encodeToJsonElement(Routine.serializer(), r))

    fun decodeWorkout(text: String): Workout {
        // Ya en la versión actual: se decodifica directo, sin árbol JSON intermedio (el doble de rápido)
        if (isCurrent(text)) return json.decodeFromString(Workout.serializer(), text)
        val (obj, version) = read(text)
        return json.decodeFromJsonElement(Workout.serializer(), migrateWorkout(obj, version))
    }

    fun decodeRoutine(text: String): Routine {
        if (isCurrent(text)) return json.decodeFromString(Routine.serializer(), text)
        val (obj, version) = read(text)
        return json.decodeFromJsonElement(Routine.serializer(), migrateRoutine(obj, version))
    }

    /** [stamp] añade la versión al final del objeto. */
    private fun isCurrent(text: String): Boolean = text.endsWith("\"$KEY\":$CURRENT}")

    private fun stamp(e: JsonElement): String =
        JsonObject(e.jsonObject + (KEY to JsonPrimitive(CURRENT))).toString()

    private fun read(text: String): Pair<JsonObject, Int> {
        val obj = json.parseToJsonElement(text).jsonObject
        return obj to (obj[KEY]?.jsonPrimitive?.intOrNull ?: 0)
    }

    /** v0 (sin versión, antes de la sincronización en vivo) → v1: añade `updatedAtEpochMs`. */
    internal fun migrateWorkout(obj: JsonObject, from: Int): JsonObject {
        var o = obj
        if (from < 1 && "updatedAtEpochMs" !in o) {
            val updated = o["finishedAtEpochMs"]?.jsonPrimitive?.longOrNull ?: o["startedAtEpochMs"]?.jsonPrimitive?.longOrNull ?: 0L
            o = buildJsonObject { o.forEach { (k, v) -> put(k, v) }; put("updatedAtEpochMs", JsonPrimitive(updated)) }
        }
        return JsonObject(o - KEY)
    }

    internal fun migrateRoutine(obj: JsonObject, @Suppress("UNUSED_PARAMETER") from: Int): JsonObject = JsonObject(obj - KEY)
}
