package com.bestrong.wear.data

import android.content.Context
import androidx.core.content.edit
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.sync.SyncContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Almacenamiento local del reloj (SharedPreferences + JSON): rutinas e historial que llegan
 * del móvil, entrenamientos hechos en el reloj y el entrenamiento activo.
 */
class WearStore(context: Context) {
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

    var activeWorkout: Workout?
        get() = prefs.getString(KEY_ACTIVE, null)?.let { runCatching { SyncContract.decodeWorkout(it.encodeToByteArray()) }.getOrNull() }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, SyncContract.encodeWorkout(value).decodeToString())
        }

    private companion object {
        const val KEY_ROUTINES = "routines"
        const val KEY_PHONE_HISTORY = "phone_history"
        const val KEY_LOCAL_HISTORY = "local_history"
        const val KEY_ACTIVE = "active"
        const val MAX_LOCAL = 50
    }
}
