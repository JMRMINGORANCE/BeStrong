package com.bestrong.mobile.data

import android.content.Context
import androidx.core.content.edit
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.training.InjuryZone
import com.bestrong.core.training.Mesocycle
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.core.training.WeekPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Todo lo de planificación avanzada (fase 5), guardado como JSON en preferencias. */
@Serializable
data class PlanningState(
    val customPrograms: List<PeriodizedProgram> = emptyList(),
    /** Semana planificada por lunes (epochDay del lunes → plan). */
    val weekPlans: Map<Long, WeekPlan> = emptyMap(),
    val mesocycle: Mesocycle? = null,
    val injuries: Set<InjuryZone> = emptySet(),
    /** Límite de subida de peso por semana (null = sin límite). */
    val weeklyCap: Double? = null,
    /** Minutos disponibles por sesión (para avisar si una rutina no cabe). */
    val minutesPerSession: Int = 60,
    /** Fecha de la competición o test de 1RM (epochDay). */
    val meetEpochDay: Long? = null,
) {
    fun planFor(monday: LocalDate): WeekPlan = weekPlans[monday.toEpochDay()] ?: WeekPlan()
}

@Singleton
class PlanningStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("planning", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<PlanningState> = _state

    private fun read(): PlanningState = prefs.getString(KEY, null)
        ?.let { runCatching { SyncContract.json.decodeFromString(PlanningState.serializer(), it) }.getOrNull() }
        ?: PlanningState()

    @Synchronized
    fun update(block: (PlanningState) -> PlanningState) {
        val s = block(_state.value)
        prefs.edit { putString(KEY, SyncContract.json.encodeToString(PlanningState.serializer(), s)) }
        _state.value = s
    }

    fun saveProgram(p: PeriodizedProgram) = update { s -> s.copy(customPrograms = s.customPrograms.filterNot { it.id == p.id } + p.copy(custom = true)) }
    fun deleteProgram(id: String) = update { s -> s.copy(customPrograms = s.customPrograms.filterNot { it.id == id }) }

    fun backupJson(): JsonObject = SyncContract.json.encodeToJsonElement(PlanningState.serializer(), state.value).jsonObject

    /** Restaura sin borrar: añade programas y semanas que falten y rellena lo que no esté puesto. */
    fun restoreBackup(json: JsonObject) {
        val b = runCatching { SyncContract.json.decodeFromJsonElement(PlanningState.serializer(), json) }.getOrNull() ?: return
        update { s ->
            val ids = s.customPrograms.map { it.id }.toSet()
            s.copy(
                customPrograms = s.customPrograms + b.customPrograms.filter { it.id !in ids },
                weekPlans = b.weekPlans + s.weekPlans,
                mesocycle = s.mesocycle ?: b.mesocycle,
                injuries = s.injuries + b.injuries,
                weeklyCap = s.weeklyCap ?: b.weeklyCap,
                minutesPerSession = if (s.minutesPerSession == PlanningState().minutesPerSession) b.minutesPerSession else s.minutesPerSession,
                meetEpochDay = s.meetEpochDay ?: b.meetEpochDay,
            )
        }
    }

    companion object {
        private const val KEY = "state"

        fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Para exportar/importar programas propios. */
        val programList = ListSerializer(PeriodizedProgram.serializer())
    }
}
