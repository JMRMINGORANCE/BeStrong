package com.bestrong.mobile.backup

import com.bestrong.core.io.Persistence
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SyncContract
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Copia completa en JSON (incluye la versión del formato para poder migrarla en el futuro). */
object BackupFile {
    fun encode(history: List<Workout>, routines: List<Routine>): String {
        val json = SyncContract.json
        return buildJsonObject {
            put("app", JsonPrimitive("BeStrong"))
            put("schemaVersion", JsonPrimitive(Persistence.CURRENT))
            put("history", json.encodeToJsonElement(ListSerializer(Workout.serializer()), history))
            put("routines", json.encodeToJsonElement(ListSerializer(Routine.serializer()), routines))
        }.toString()
    }
}
