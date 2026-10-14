package com.bestrong.core.sync

import com.bestrong.core.model.HeartRateSample
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.util.VibrationStyle
import com.bestrong.core.util.WeightUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Contrato compartido para la Wearable Data Layer API entre móvil y reloj.
 */
object SyncContract {
    /** DataItem con las rutinas (móvil → reloj). */
    const val PATH_ROUTINES = "/bestrong/routines"

    /** Prefijo de DataItems con entrenamientos terminados (reloj → móvil); se añade el id. */
    const val PATH_FINISHED_WORKOUT_PREFIX = "/bestrong/finished-workout/"

    /** DataItem con el historial reciente (móvil → reloj) para "anterior", tile y complicación. */
    const val PATH_RECENT_HISTORY = "/bestrong/recent-history"

    /** DataItem con el entrenamiento en curso, compartido en vivo entre móvil y reloj. */
    const val PATH_ACTIVE_WORKOUT = "/bestrong/active-workout"

    /** DataItem con los ajustes que el reloj necesita (unidad de peso). */
    const val PATH_SETTINGS = "/bestrong/settings"

    /** Prefijo de DataItems con rutinas creadas en el reloj (reloj → móvil); se añade el id. */
    const val PATH_WATCH_ROUTINE_PREFIX = "/bestrong/watch-routine/"

    /** Mensaje reloj → móvil con las métricas de salud en directo. */
    const val PATH_METRICS = "/bestrong/metrics"

    /** Datos de salud finales del reloj de un entreno terminado en el móvil (pulso y calorías exactos). */
    const val PATH_WORKOUT_HEALTH_PREFIX = "/bestrong/workout-health/"
    const val KEY_PAYLOAD = "payload"
    const val KEY_TIMESTAMP = "ts"

    /** Deep link que abre el reloj desde el móvil (RemoteActivityHelper). */
    const val DEEP_LINK_SCHEME = "bestrong"
    const val DEEP_LINK_HOST = "start"
    const val DEEP_LINK_PARAM_ROUTINE = "routine"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val workoutList = ListSerializer(Workout.serializer())
    private val routineList = ListSerializer(Routine.serializer())

    fun encodeWorkout(workout: Workout): ByteArray = json.encodeToString(Workout.serializer(), workout).encodeToByteArray()
    fun decodeWorkout(bytes: ByteArray): Workout = json.decodeFromString(Workout.serializer(), bytes.decodeToString())

    fun encodeWorkouts(workouts: List<Workout>): ByteArray = json.encodeToString(workoutList, workouts).encodeToByteArray()
    fun decodeWorkouts(bytes: ByteArray): List<Workout> = json.decodeFromString(workoutList, bytes.decodeToString())

    fun encodeRoutines(routines: List<Routine>): ByteArray = json.encodeToString(routineList, routines).encodeToByteArray()
    fun decodeRoutines(bytes: ByteArray): List<Routine> = json.decodeFromString(routineList, bytes.decodeToString())

    fun encodeMetrics(m: LiveMetrics): ByteArray = json.encodeToString(LiveMetrics.serializer(), m).encodeToByteArray()
    fun decodeMetrics(bytes: ByteArray): LiveMetrics = json.decodeFromString(LiveMetrics.serializer(), bytes.decodeToString())

    fun encodeHealth(h: WorkoutHealthData): ByteArray = json.encodeToString(WorkoutHealthData.serializer(), h).encodeToByteArray()
    fun decodeHealth(bytes: ByteArray): WorkoutHealthData = json.decodeFromString(WorkoutHealthData.serializer(), bytes.decodeToString())

    fun encodeSettings(s: SharedSettings): ByteArray = json.encodeToString(SharedSettings.serializer(), s).encodeToByteArray()

    /**
     * Ajustes para el reloj sin pasar de [maxBytes]: si no caben (miles de ejercicios con marca o de
     * notas), primero se quitan las marcas y luego las notas; lo básico (unidad, objetivo…) llega siempre.
     */
    fun encodeSettingsWithin(s: SharedSettings, maxBytes: Int = MAX_HISTORY_BYTES): ByteArray =
        sequenceOf(s, s.copy(bestOneRepMaxKg = emptyMap(), heaviestKg = emptyMap()), s.copy(bestOneRepMaxKg = emptyMap(), heaviestKg = emptyMap(), pinnedNotes = emptyMap()))
            .map(::encodeSettings).firstOrNull { it.size <= maxBytes }
            ?: encodeSettings(s.copy(bestOneRepMaxKg = emptyMap(), heaviestKg = emptyMap(), pinnedNotes = emptyMap(), exerciseIncrementKg = emptyMap(), exerciseRepRange = emptyMap()))
    fun decodeSettings(bytes: ByteArray): SharedSettings = json.decodeFromString(SharedSettings.serializer(), bytes.decodeToString())

    /**
     * Versión ligera del historial para el reloj: solo las series completadas de los
     * últimos [limit] entrenamientos, para no superar el límite de 100 KB de un DataItem.
     */
    fun compactHistory(history: List<Workout>, limit: Int = 30): List<Workout> =
        history.filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }.take(limit)
            .map { w -> w.copy(notes = "", heartRateSamples = emptyList(), exercises = w.exercises.map { it.copy(notes = "", sets = it.completedSets) }) }

    /**
     * Rutinas para el reloj sin pasar de [maxBytes]: sin notas ni músculos secundarios (no los usa)
     * y, si aun así no caben, las primeras (la sesión de hoy va la primera).
     */
    fun encodeRoutinesWithin(routines: List<Routine>, maxBytes: Int = MAX_HISTORY_BYTES): ByteArray {
        // Si caben tal cual, completas (los entrenos del reloj salen de estas rutinas)
        encodeRoutines(routines).let { if (it.size <= maxBytes) return it }
        var list = routines.map { r ->
            r.copy(notes = "", exercises = r.exercises.map { it.copy(notes = "", exercise = it.exercise.copy(secondaryMuscles = emptyList())) })
        }
        var bytes = encodeRoutines(list)
        while (bytes.size > maxBytes && list.isNotEmpty()) {
            list = list.take((list.size.toLong() * maxBytes / bytes.size).toInt().coerceIn(0, list.size - 1))
            bytes = encodeRoutines(list)
        }
        return bytes
    }

    /** Tamaño máximo del historial para el reloj (un DataItem admite ~100 KB; se deja margen). */
    const val MAX_HISTORY_BYTES = 90_000

    /**
     * Historial reciente codificado sin pasar de [maxBytes]: un entreno típico ocupa ~8 KB, así que
     * con historiales grandes se quitan los más antiguos hasta que cabe (si no, el envío falla en silencio).
     */
    fun encodeRecentHistory(history: List<Workout>, maxBytes: Int = MAX_HISTORY_BYTES): ByteArray {
        var recent = compactHistory(history)
        var bytes = encodeWorkouts(recent)
        while (bytes.size > maxBytes && recent.isNotEmpty()) {
            // Recorte proporcional al exceso (mínimo uno) para no recodificar 30 veces
            val keep = (recent.size.toLong() * maxBytes / bytes.size).toInt().coerceIn(0, recent.size - 1)
            recent = recent.take(keep)
            bytes = encodeWorkouts(recent)
        }
        return bytes
    }
}

/** Métricas del reloj durante un entreno (para mostrarlas en el móvil). */
@Serializable
data class LiveMetrics(
    val workoutId: String,
    val heartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    val timestampEpochMs: Long,
)

@Serializable
data class SharedSettings(
    val unit: WeightUnit = WeightUnit.KG,
    val progressionEnabled: Boolean = true,
    /** Vibración al acabar el descanso, elegida en el móvil y usada también en el reloj. */
    val restVibration: VibrationStyle = VibrationStyle.TRIPLE,
    /**
     * Entrenos por semana que se buscan (el del programa activo o el elegido en el móvil). Por
     * defecto 4, lo que mostraba el reloj antes de recibirlo, para ajustes guardados sin este campo.
     */
    val weeklyGoal: Int = 4,
    /** Incremento propio por ejercicio en kg (elegido en el móvil): bisel y progresión en el reloj. */
    val exerciseIncrementKg: Map<String, Double> = emptyMap(),
    /** Nota fija de cada ejercicio (altura del asiento, agarre…), recortada para el reloj. */
    val pinnedNotes: Map<String, String> = emptyMap(),
    /** Mejores marcas de todo el historial (el reloj solo tiene el reciente): 1RM estimado y peso máximo. */
    val bestOneRepMaxKg: Map<String, Double> = emptyMap(),
    val heaviestKg: Map<String, Double> = emptyMap(),
    /** Descanso máximo tras una serie de calentamiento (s). */
    val warmupRestSeconds: Int = 60,
    /** Rango de repeticiones por ejercicio («8-12»): la misma doble progresión que en el móvil. */
    val exerciseRepRange: Map<String, String> = emptyMap(),
    /** Barra del gimnasio activo y las propias de cada ejercicio (kg), para el calentamiento del reloj. */
    val gymBarKg: Double = 20.0,
    val exerciseBarKg: Map<String, Double> = emptyMap(),
) {
    fun barKgFor(exerciseId: String): Double = exerciseBarKg[exerciseId] ?: gymBarKg
    fun stepKgFor(exerciseId: String): Double = exerciseIncrementKg[exerciseId] ?: unit.stepKg()
    fun repRangeFor(exerciseId: String): IntRange? = com.bestrong.core.training.Progression.parseRepRange(exerciseRepRange[exerciseId])
}

/**
 * Lo que midió el reloj durante un entreno que se terminó en el móvil: el móvil solo tenía las
 * métricas en vivo (cada 5–30 s), así que al acabar el reloj manda los valores finales y la serie.
 */
@Serializable
data class WorkoutHealthData(
    val workoutId: String,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    val heartRateSamples: List<HeartRateSample> = emptyList(),
)
