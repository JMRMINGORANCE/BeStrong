package com.bestrong.mobile

import android.content.Context
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Periodization
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.sync.WatchSync
import com.bestrong.mobile.ui.components.uiLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Qué rutinas van al reloj (la sesión de hoy del programa activo primero) y su envío inmediato. */
@Singleton
class WatchPush @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BeStrongRepository,
    private val watchSync: WatchSync,
    private val planning: PlanningStore,
) {
    fun routinesForWatch(routines: List<Routine>, program: ActiveProgram?, history: List<Workout>): List<Routine> {
        val today = program?.let { Periodization.nextSession(it, history, context.uiLanguage(), planning.state.value.customPrograms)?.routine }
        return listOfNotNull(today) + routines
    }

    /** Envío inmediato de rutinas (incluida la sesión de hoy) e historial al reloj. */
    suspend fun pushToWatchNow() {
        watchSync.pushRoutines(routinesForWatch(repository.routines.value, repository.activeProgram.value, repository.history.value))
        watchSync.pushHistory(repository.history.value)
    }
}
