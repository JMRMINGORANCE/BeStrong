package com.bestrong.wear.data

import android.content.Context
import androidx.core.content.edit
import com.bestrong.core.io.Persistence
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.session.CardioState
import com.bestrong.wear.session.CardioType
import com.bestrong.wear.session.Rest
import com.bestrong.wear.session.StemAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Almacenamiento local del reloj (SharedPreferences + JSON): rutinas e historial que llegan
 * del móvil, entrenamientos hechos en el reloj y el entrenamiento activo.
 */
@Singleton
class WearStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("bestrong_wear", Context.MODE_PRIVATE)

    /**
     * Lo que cambia en cada serie o giro de corona (entreno activo, descanso, cardio) va en un archivo
     * aparte y pequeño: SharedPreferences reescribe el archivo entero, y el principal guarda historiales de ~1 MB.
     */
    private val live = context.getSharedPreferences("bestrong_wear_live", Context.MODE_PRIVATE).also { live ->
        // Una sola vez: se trae lo que hubiera en el archivo grande de versiones anteriores
        if (LIVE_KEYS.any { it in prefs.all }) {
            live.edit {
                prefs.all.filterKeys { it in LIVE_KEYS }.forEach { (k, v) ->
                    when (v) {
                        is String -> putString(k, v)
                        is Int -> putInt(k, v)
                    }
                }
            }
            prefs.edit { LIVE_KEYS.forEach { remove(it) } }
        }
    }
    private val json = SyncContract.json

    private val _routines = MutableStateFlow(readRoutines())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _phoneHistory = MutableStateFlow(readWorkouts(KEY_PHONE_HISTORY))
    private val _localHistory = MutableStateFlow(readWorkouts(KEY_LOCAL_HISTORY))
    private val _history = MutableStateFlow(merge())

    /** Historial combinado (móvil + reloj), más reciente primero. */
    val history: StateFlow<List<Workout>> = _history.asStateFlow()

    private fun readRoutines(): List<Routine> =
        prefs.getString(KEY_ROUTINES, null)?.let { runCatching { SyncContract.decodeRoutines(it.encodeToByteArray()) }.getOrNull() }.orEmpty()

    private fun readWorkouts(key: String): List<Workout> =
        prefs.getString(key, null)?.let { runCatching { SyncContract.decodeWorkouts(it.encodeToByteArray()) }.getOrNull() }.orEmpty()

    private fun merge(): List<Workout> =
        (_localHistory.value + _phoneHistory.value).distinctBy { it.id }.sortedByDescending { it.startedAtEpochMs }

    fun setRoutines(routines: List<Routine>) {
        prefs.edit { putString(KEY_ROUTINES, SyncContract.encodeRoutines(routines).decodeToString()) }
        _routines.value = routines
    }

    /** Rutinas creadas aquí que el móvil aún no ha devuelto: una lista suya sin ellas no las borra. */
    private fun pendingRoutineIds(): Set<String> = prefs.getStringSet(KEY_PENDING_ROUTINES, emptySet()).orEmpty()

    /** El móvil ya guardó (y borró el aviso de) esta rutina creada aquí: su lista vuelve a mandar. */
    fun routineDelivered(id: String) {
        val pending = pendingRoutineIds()
        if (id in pending) prefs.edit { putStringSet(KEY_PENDING_ROUTINES, pending - id) }
    }

    /** Lista de rutinas del móvil: manda, salvo las creadas aquí que todavía no conoce. */
    fun setPhoneRoutines(routines: List<Routine>) {
        val phoneIds = routines.map { it.id }.toSet()
        val pending = pendingRoutineIds()
        val keep = _routines.value.filter { it.id in pending && it.id !in phoneIds }
        val stillPending = pending.filterNot { it in phoneIds }.toSet()
        if (stillPending != pending) prefs.edit { putStringSet(KEY_PENDING_ROUTINES, stillPending) }
        setRoutines(routines + keep)
    }

    fun setPhoneHistory(history: List<Workout>) {
        prefs.edit { putString(KEY_PHONE_HISTORY, SyncContract.encodeWorkouts(history).decodeToString()) }
        _phoneHistory.value = history
        // Lo que ya está en el móvil no hace falta guardarlo también como local
        val phoneIds = history.map { it.id }.toSet()
        saveLocal(_localHistory.value.filterNot { it.id in phoneIds })
        _history.value = merge()
    }

    fun addLocalWorkout(workout: Workout) {
        saveLocal((listOf(workout) + _localHistory.value).take(MAX_LOCAL))
        _history.value = merge()
    }

    private fun saveLocal(list: List<Workout>) {
        prefs.edit { putString(KEY_LOCAL_HISTORY, SyncContract.encodeWorkouts(list).decodeToString()) }
        _localHistory.value = list
    }

    private val _settings = MutableStateFlow(
        prefs.getString(KEY_SETTINGS, null)?.let { runCatching { SyncContract.decodeSettings(it.encodeToByteArray()) }.getOrNull() } ?: SharedSettings(),
    )

    /** Unidad de peso y progresión, elegidas en el móvil. */
    val settings: StateFlow<SharedSettings> = _settings.asStateFlow()

    fun setSettings(s: SharedSettings) {
        prefs.edit { putString(KEY_SETTINGS, SyncContract.encodeSettings(s).decodeToString()) }
        _settings.value = s
    }

    /** Qué hace el botón rápido del Galaxy Watch Ultra. */
    var stemAction: StemAction
        get() = runCatching { StemAction.valueOf(prefs.getString(KEY_STEM, null) ?: "") }.getOrDefault(StemAction.COMPLETE_SET)
        set(value) = prefs.edit { putString(KEY_STEM, value.name) }

    var repCounterEnabled: Boolean
        get() = prefs.getBoolean(KEY_REP_COUNTER, false)
        set(value) = prefs.edit { putBoolean(KEY_REP_COUNTER, value) }

    /** Modo bajo consumo: menos envíos al móvil y sin acelerómetro. */
    var lowPower: Boolean
        get() = prefs.getBoolean(KEY_LOW_POWER, false)
        set(value) = prefs.edit { putBoolean(KEY_LOW_POWER, value) }

    /** Pulso máximo para zonas y recuperación (por defecto 190; se ajusta en el menú). */
    var maxHeartRate: Int
        get() = prefs.getInt(KEY_MAX_HR, 190)
        set(value) = prefs.edit { putInt(KEY_MAX_HR, value) }

    /**
     * Último entreno que el móvil ha tenido (llegó desde él o lo devolvió): si el móvil lo terminó o lo descartó
     * sin que el reloj se enterase, no se le vuelve a mandar.
     */
    var phoneKnownWorkoutId: String?
        get() = prefs.getString(KEY_PHONE_KNOWN, null)
        set(value) = prefs.edit { putString(KEY_PHONE_KNOWN, value) }

    /** Último entreno terminado o descartado en el reloj: un mensaje atrasado del móvil no lo vuelve a abrir. */
    var lastEndedWorkoutId: String?
        get() = prefs.getString(KEY_LAST_ENDED, null)
        set(value) {
            prefs.edit { putString(KEY_LAST_ENDED, value) }
            value?.let(::markEnded)
        }

    /**
     * Los últimos entrenos terminados o cerrados aquí (no solo el último): un eco atrasado del móvil de uno
     * de ellos no lo reabre aunque después se hiciera otro.
     */
    private fun endedIds(): List<String> = prefs.getString(KEY_ENDED_IDS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    fun markEnded(id: String) = prefs.edit { putString(KEY_ENDED_IDS, (listOf(id) + endedIds().filterNot { it == id }).take(MAX_ENDED).joinToString(",")) }

    fun wasEnded(id: String): Boolean = id in endedIds() || _history.value.any { it.id == id }

    /** Terminados en el reloj que el móvil aún no tiene en su historial (para reenviarlos si se perdió el aviso). */
    val pendingForPhone: List<Workout> get() = _localHistory.value

    /** Umbral calibrado del contador de repeticiones (m/s²). */
    var repThreshold: Float
        get() = prefs.getFloat(KEY_REP_THRESHOLD, 1.2f)
        set(value) = prefs.edit { putFloat(KEY_REP_THRESHOLD, value) }

    /**
     * «Hoy toca»: la sesión del programa activo (si no se ha hecho ya hoy en el reloj), la planificada para hoy en el
     * móvil o la rutina que más tiempo lleva sin hacerse; lo mismo que Inicio y el widget del móvil.
     */
    fun todayRoutine(): Routine? {
        val (program, routines) = _routines.value.filter { it.exercises.isNotEmpty() }
            .partition { it.id.startsWith(com.bestrong.core.training.Periodization.ROUTINE_PREFIX) }
        val today = java.time.LocalDate.now()
        val startOfToday = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val doneToday = _history.value.filter { it.startedAtEpochMs >= startOfToday }.mapNotNull { it.routineId }.toSet()
        program.firstOrNull { it.id !in doneToday }?.let { return it }
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay()
        val planned = _settings.value.weekPlans[monday]?.get(today.dayOfWeek.value)
        return com.bestrong.core.training.QuickRoutines.pick(routines, _history.value, planned, startOfToday, 1).firstOrNull()
    }

    /** Rutina creada en el reloj: se guarda aquí y se envía al móvil. */
    fun addRoutine(routine: Routine) {
        prefs.edit { putStringSet(KEY_PENDING_ROUTINES, pendingRoutineIds() + routine.id) }
        setRoutines(_routines.value.filterNot { it.id == routine.id } + routine)
    }

    /**
     * Calorías de los tramos de Health Services ya cerrados en el entreno en curso (pesas → cinta →
     * pesas…): cada tramo empieza de cero y aquí se acumulan. Sobrevive a un reinicio del proceso.
     */
    var closedSegmentsKcal: Int
        get() = live.getInt(KEY_SEGMENTS_KCAL, 0)
        set(value) = live.edit { putInt(KEY_SEGMENTS_KCAL, value) }

    var activeWorkout: Workout?
        get() = live.getString(KEY_ACTIVE, null)?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() }
        set(value) = live.edit {
            if (value == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, Persistence.encodeWorkout(value))
        }

    /** Descanso en marcha: si el proceso muere, al volver sigue contando hasta la misma hora. */
    var activeRest: Rest?
        get() = runCatching {
            live.getString(KEY_REST, null)?.split(';')?.let { (ends, total) -> Rest(ends.toLong(), total.toInt()) }
        }.getOrNull()
        set(value) = live.edit {
            if (value == null) remove(KEY_REST) else putString(KEY_REST, "${value.endsAtEpochMs};${value.totalSeconds}")
        }

    /** Cardio libre en curso (sobrevive a un reinicio del proceso). */
    var activeCardio: CardioState?
        get() = runCatching {
            live.getString(KEY_CARDIO, null)?.let { text ->
                val o = JSONObject(text)
                CardioState(
                    type = CardioType.valueOf(o.getString("type")),
                    startedAtEpochMs = o.getLong("startedAt"),
                    distanceM = o.optDouble("distanceM", 0.0),
                    avgHeartRate = o.optInt("avgHr").takeIf { it > 0 },
                    maxHeartRate = o.optInt("maxHr").takeIf { it > 0 },
                    calories = o.optInt("kcal", -1).takeIf { it >= 0 },
                )
            }
        }.getOrNull()
        set(value) = live.edit {
            if (value == null) {
                remove(KEY_CARDIO)
            } else {
                val o = JSONObject()
                    .put("type", value.type.name)
                    .put("startedAt", value.startedAtEpochMs)
                    .put("distanceM", value.distanceM)
                value.avgHeartRate?.let { o.put("avgHr", it) }
                value.maxHeartRate?.let { o.put("maxHr", it) }
                value.calories?.let { o.put("kcal", it) }
                putString(KEY_CARDIO, o.toString())
            }
        }

    private companion object {
        val LIVE_KEYS = setOf("active", "active_rest", "active_cardio", "segments_kcal")
        const val KEY_ROUTINES = "routines"
        const val KEY_PHONE_HISTORY = "phone_history"
        const val KEY_LOCAL_HISTORY = "local_history"
        const val KEY_ACTIVE = "active"
        const val KEY_SETTINGS = "settings"
        const val KEY_REP_COUNTER = "rep_counter"
        const val KEY_STEM = "stem_action"
        const val KEY_LOW_POWER = "low_power"
        const val KEY_MAX_HR = "max_hr"
        const val KEY_SEGMENTS_KCAL = "segments_kcal"
        const val KEY_REP_THRESHOLD = "rep_threshold"
        const val KEY_LAST_ENDED = "last_ended_workout"
        const val KEY_ENDED_IDS = "ended_workout_ids"
        const val KEY_PENDING_ROUTINES = "pending_watch_routines"
        const val MAX_ENDED = 20
        const val KEY_PHONE_KNOWN = "phone_known_workout"
        const val KEY_REST = "active_rest"
        const val KEY_CARDIO = "active_cardio"
        const val MAX_LOCAL = 50
    }
}
