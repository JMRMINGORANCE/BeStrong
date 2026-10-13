package com.bestrong.wear.data

import android.content.Context
import androidx.core.content.edit
import com.bestrong.core.io.Persistence
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.sync.SyncContract
import com.bestrong.wear.session.StemAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Almacenamiento local del reloj (SharedPreferences + JSON): rutinas e historial que llegan
 * del móvil, entrenamientos hechos en el reloj y el entrenamiento activo.
 */
@Singleton
class WearStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("bestrong_wear", Context.MODE_PRIVATE)
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

    /** Umbral calibrado del contador de repeticiones (m/s²). */
    var repThreshold: Float
        get() = prefs.getFloat(KEY_REP_THRESHOLD, 1.2f)
        set(value) = prefs.edit { putFloat(KEY_REP_THRESHOLD, value) }

    /** «Hoy toca»: la sesión del programa activo o la rutina que más tiempo lleva sin hacerse. */
    fun todayRoutine(): Routine? {
        val routines = _routines.value.filter { it.exercises.isNotEmpty() }
        routines.firstOrNull { it.id.startsWith(com.bestrong.core.training.Periodization.ROUTINE_PREFIX) }?.let { return it }
        val last = _history.value.groupBy { it.routineId }.mapValues { (_, ws) -> ws.maxOf { it.startedAtEpochMs } }
        return routines.minByOrNull { last[it.id] ?: 0L }
    }

    /** Rutina creada en el reloj: se guarda aquí y se envía al móvil. */
    fun addRoutine(routine: Routine) = setRoutines(_routines.value.filterNot { it.id == routine.id } + routine)

    /**
     * Calorías de los tramos de Health Services ya cerrados en el entreno en curso (pesas → cinta →
     * pesas…): cada tramo empieza de cero y aquí se acumulan. Sobrevive a un reinicio del proceso.
     */
    var closedSegmentsKcal: Int
        get() = prefs.getInt(KEY_SEGMENTS_KCAL, 0)
        set(value) = prefs.edit { putInt(KEY_SEGMENTS_KCAL, value) }

    var activeWorkout: Workout?
        get() = prefs.getString(KEY_ACTIVE, null)?.let { runCatching { Persistence.decodeWorkout(it) }.getOrNull() }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, Persistence.encodeWorkout(value))
        }

    private companion object {
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
        const val MAX_LOCAL = 50
    }
}
