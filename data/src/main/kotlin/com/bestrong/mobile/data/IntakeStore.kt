package com.bestrong.mobile.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Proteína y agua de hoy (se reinician solas cada día). */
data class Intake(val proteinG: Int = 0, val waterMl: Int = 0)

@Singleton
class IntakeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("intake", Context.MODE_PRIVATE)
    private val _today = MutableStateFlow(read())
    val today: StateFlow<Intake> = _today

    private fun key() = LocalDate.now().toEpochDay()

    private fun read(): Intake {
        val k = key()
        return if (prefs.getLong("day", -1) == k) Intake(prefs.getInt("protein", 0), prefs.getInt("water", 0)) else Intake()
    }

    @Synchronized
    fun add(proteinG: Int = 0, waterMl: Int = 0) {
        val cur = read()
        val next = Intake((cur.proteinG + proteinG).coerceAtLeast(0), (cur.waterMl + waterMl).coerceAtLeast(0))
        prefs.edit {
            putLong("day", key())
            putInt("protein", next.proteinG)
            putInt("water", next.waterMl)
        }
        _today.value = next
    }

    /** Al volver a la app otro día. */
    fun refresh() {
        _today.value = read()
    }
}
