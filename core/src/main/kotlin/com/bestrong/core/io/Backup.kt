package com.bestrong.core.io

import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.training.ActiveProgram
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Contenido completo de una copia de seguridad de BeStrong. */
data class BackupData(
    val history: List<Workout> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val customExercises: List<Exercise> = emptyList(),
    val measurements: List<BodyMeasurement> = emptyList(),
    /** Opcionales (null en copias antiguas): programa activo con sus training max… */
    val activeProgram: ActiveProgram? = null,
    /** …planificación y ajustes, como JSON tal cual (sus tipos viven en la capa de datos)… */
    val planning: JsonObject? = null,
    val settings: JsonObject? = null,
    /** …y notas de técnica por ejercicio. */
    val techniqueNotes: Map<String, String>? = null,
)

/**
 * Copia completa en JSON. Lleva `schemaVersion` y, al restaurar, cada entreno y rutina pasa
 * por las migraciones de [Persistence], así que una copia antigua se puede restaurar en una
 * versión nueva de la app.
 */
object Backup {
    private val json = SyncContract.json

    fun encode(data: BackupData): String = buildJsonObject {
        put("app", JsonPrimitive("BeStrong"))
        put("schemaVersion", JsonPrimitive(Persistence.CURRENT))
        put("history", JsonArray(data.history.map { json.parseToJsonElement(Persistence.encodeWorkout(it)) }))
        put("routines", JsonArray(data.routines.map { json.parseToJsonElement(Persistence.encodeRoutine(it)) }))
        put("customExercises", json.encodeToJsonElement(ListSerializer(Exercise.serializer()), data.customExercises))
        put("measurements", json.encodeToJsonElement(ListSerializer(BodyMeasurement.serializer()), data.measurements))
        data.activeProgram?.let { put("activeProgram", json.encodeToJsonElement(ActiveProgram.serializer(), it)) }
        data.planning?.let { put("planning", it) }
        data.settings?.let { put("settings", it) }
        data.techniqueNotes?.let { put("techniqueNotes", json.encodeToJsonElement(notesSerializer, it)) }
    }.toString()

    private val notesSerializer = MapSerializer(String.serializer(), String.serializer())

    /** Lanza [IllegalArgumentException] si el archivo no es una copia de BeStrong o es de una versión futura. */
    fun decode(text: String): BackupData {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw IllegalArgumentException("El archivo no es JSON válido")
        require(root["app"]?.jsonPrimitive?.content == "BeStrong") { "No es una copia de BeStrong" }
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 0
        require(version <= Persistence.CURRENT) { "Copia de una versión más nueva de la app ($version)" }
        // Cada elemento hereda la versión del archivo si no lleva la suya
        fun stamped(e: kotlinx.serialization.json.JsonElement): String {
            val o = e.jsonObject
            return if ("schemaVersion" in o) o.toString() else kotlinx.serialization.json.JsonObject(o + ("schemaVersion" to JsonPrimitive(version))).toString()
        }
        return BackupData(
            history = root["history"]?.jsonArray?.mapNotNull { runCatching { Persistence.decodeWorkout(stamped(it)) }.getOrNull() }.orEmpty(),
            routines = root["routines"]?.jsonArray?.mapNotNull { runCatching { Persistence.decodeRoutine(stamped(it)) }.getOrNull() }.orEmpty(),
            customExercises = root["customExercises"]?.let { runCatching { json.decodeFromJsonElement(ListSerializer(Exercise.serializer()), it) }.getOrNull() }.orEmpty(),
            measurements = root["measurements"]?.let { runCatching { json.decodeFromJsonElement(ListSerializer(BodyMeasurement.serializer()), it) }.getOrNull() }.orEmpty(),
            activeProgram = root["activeProgram"]?.let { runCatching { json.decodeFromJsonElement(ActiveProgram.serializer(), it) }.getOrNull() },
            planning = root["planning"] as? JsonObject,
            settings = root["settings"] as? JsonObject,
            techniqueNotes = root["techniqueNotes"]?.let { runCatching { json.decodeFromJsonElement(notesSerializer, it) }.getOrNull() },
        )
    }
}

/** Lo que trae una copia, para enseñarlo antes de restaurar. */
data class BackupSummary(
    val workouts: Int,
    val routines: Int,
    val measurements: Int,
    val firstWorkoutEpochMs: Long?,
    val lastWorkoutEpochMs: Long?,
    val hasSettings: Boolean,
) {
    companion object {
        fun of(data: BackupData) = BackupSummary(
            workouts = data.history.size,
            routines = data.routines.size,
            measurements = data.measurements.size,
            firstWorkoutEpochMs = data.history.minOfOrNull { it.startedAtEpochMs },
            lastWorkoutEpochMs = data.history.maxOfOrNull { it.startedAtEpochMs },
            hasSettings = data.settings != null || data.planning != null || data.activeProgram != null,
        )
    }
}
