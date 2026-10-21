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

    // coerceInputValues: un valor de enum nuevo (de una versión más reciente del otro lado) usa el de por defecto en vez de romper
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

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
    /** Gimnasio activo (discos, mancuernas, cierres): el reloj redondea la sugerencia a lo que se puede montar. */
    val activeGym: com.bestrong.core.training.GymProfile? = null,
    /** Hay más de un gimnasio: el reloj rellena con lo hecho en el activo (con uno solo no hace falta). */
    val multiGym: Boolean = false,
    /** Objetivo semanal de cardio elegido en el móvil (km o minutos), para la complicación del reloj. */
    val weeklyCardioKm: Double? = null,
    val weeklyCardioMin: Int? = null,
    /** Subida máxima semanal de Planificación (0,05 = 5 %); null = sin tope. */
    val weeklyCap: Double? = null,
    /** Mancuernas apuntadas como total del par (elegido en el móvil): el reloj muestra y entiende el mismo peso. */
    val dumbbellTotal: Boolean = false,
) {
    /** 2 con [dumbbellTotal] en un ejercicio con mancuernas; si no, 1 (el peso se guarda siempre por mancuerna). */
    fun weightFactor(exercise: com.bestrong.core.model.Exercise): Int =
        if (dumbbellTotal && exercise.equipment == com.bestrong.core.model.Equipment.DUMBBELL) 2 else 1

    /** El gimnasio activo con la barra propia del ejercicio, si la tiene (como en el móvil). */
    fun gymFor(exerciseId: String): com.bestrong.core.training.GymProfile? =
        activeGym?.let { g -> exerciseBarKg[exerciseId]?.let { g.copy(barKg = it) } ?: g }

    fun barKgFor(exerciseId: String): Double = gymFor(exerciseId)?.loadedBarKg ?: exerciseBarKg[exerciseId] ?: gymBarKg

    /** Barra (con cierres) del ejercicio en la unidad, como en el móvil: la de 20 kg es la de 45 lb. */
    fun barInUnit(exerciseId: String): Double = gymFor(exerciseId)
        ?.let { com.bestrong.core.training.GymProfile.barIn(unit, it.barKg) + unit.fromKg(it.collarKg) }
        ?: com.bestrong.core.training.GymProfile.barIn(unit, barKgFor(exerciseId))

    /** Discos por lado para [kg] con el gimnasio activo (o los estándar si el móvil aún no lo envía). */
    fun plateLoad(exerciseId: String, kg: Double): com.bestrong.core.training.PlateLoad =
        gymFor(exerciseId)?.plateLoad(kg, unit) ?: com.bestrong.core.training.PlateCalculator.calculate(unit.fromKg(kg), unit, barInUnit(exerciseId))

    /**
     * Series sugeridas ajustadas al material del gimnasio activo (solo en kg, como en el móvil): si tocaba
     * subir y el redondeo deja el peso de la última vez, el siguiente que se pueda montar.
     */
    fun loadable(
        exercise: com.bestrong.core.model.Exercise,
        suggestion: com.bestrong.core.training.ProgressionSuggestion,
        previous: List<com.bestrong.core.model.WorkoutSet>,
        /** Mejor peso de los últimos 7 días, para el tope semanal (el reloj tiene el historial reciente). */
        lastWeekBestKg: Double? = null,
    ): List<com.bestrong.core.model.WorkoutSet> {
        val cap = weeklyCap
        fun capped(kg: Double) = if (cap == null) kg else com.bestrong.core.training.ProgressionCap.limitIn(unit, kg, lastWeekBestKg, cap, stepKgFor(exercise.id))
        val gym = gymFor(exercise.id) ?: return suggestion.sets.map { it.copy(weightKg = capped(it.weightKg)) }
        val raise = suggestion.kind == com.bestrong.core.training.SuggestionKind.INCREASE_WEIGHT
        val before = previous.filter { it.type != com.bestrong.core.model.SetType.WARMUP }
        return suggestion.sets.mapIndexed { i, set ->
            val target = capped(set.weightKg)
            // En libras también, con los discos de libras (los mismos que enseña la calculadora)
            val rounded = if (target > 0) gym.nearestLoad(target, exercise.equipment, unit) else target
            val prev = before.getOrNull(i)?.weightKg
            val lost = raise && prev != null && prev > 0 && rounded <= prev + 1e-6
            // La subida que no se pierde al redondear, si el tope semanal la permite
            val above = if (lost) gym.loadAbove(prev!!, exercise.equipment, unit).takeIf { it <= capped(it) + 1e-6 } else null
            set.copy(weightKg = above ?: rounded)
        }
    }

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
