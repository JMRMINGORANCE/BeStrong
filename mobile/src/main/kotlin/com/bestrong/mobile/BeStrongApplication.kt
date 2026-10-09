package com.bestrong.mobile

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.LiveMetrics
import com.bestrong.core.sync.SharedSettings
import com.bestrong.core.training.ActiveProgram
import com.bestrong.core.training.Periodization
import com.bestrong.core.training.Progression
import com.bestrong.core.training.ProgressionCap
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.backup.ImagePrefetchWorker
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.PlanningStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.notify.ReminderWorker
import com.bestrong.mobile.notify.WeeklySummaryWorker
import com.bestrong.mobile.sync.WatchSync
import com.bestrong.mobile.ui.components.uiLanguage
import com.bestrong.mobile.widget.BeStrongWidget
import com.bestrong.mobile.workout.RestTimerController
import com.bestrong.mobile.workout.WorkoutForegroundService
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Últimas métricas recibidas del reloj durante el entreno. */
@Singleton
class WatchMetricsStore @Inject constructor() {
    val metrics = MutableStateFlow<LiveMetrics?>(null)
}

/** Lógica compartida al empezar y terminar entrenamientos. */
@Singleton
class WorkoutActions @Inject constructor(
    private val repository: BeStrongRepository,
    private val settings: SettingsStore,
    private val healthConnect: HealthConnectExporter,
    private val restTimer: RestTimerController,
    private val planning: PlanningStore,
) {
    /**
     * Completa la siguiente serie pendiente y arranca su descanso (botón de la notificación,
     * botones de volumen). Devuelve false si no queda ninguna.
     */
    suspend fun completeNextSet(): Boolean {
        val w = repository.activeWorkout.value ?: return false
        val (index, set) = WorkoutEditor.nextPendingSet(w) ?: return false
        val we = w.exercises[index]
        val updated = WorkoutEditor.toggleSetCompleted(w, we.id, set.id)
        repository.updateActive { WorkoutEditor.toggleSetCompleted(it, we.id, set.id) }
        val (_, rest) = WorkoutEditor.afterSetCompleted(updated, index)
        if (rest > 0) restTimer.start(rest) else restTimer.skip()
        return true
    }

    /**
     * Series con las que rellenar un ejercicio al empezar: sugerencia de sobrecarga progresiva
     * si está activada; si no, lo que se hizo la última vez.
     */
    fun prefillLookup(): (String) -> List<WorkoutSet> {
        val history = repository.history.value
        val s = settings.settings.value
        val gym = s.activeGym
        val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
        return { id ->
            val ex = repository.exercise(id)
            val sets = (if (s.progressionEnabled && ex != null) Progression.suggest(ex, history, s.unit.stepKg())?.sets else null)
                ?: WorkoutStats.previousSets(id, history)
            // Límite de subida semanal (Planificación): no más de un x % sobre lo mejor de la semana pasada
            val cap = planning.state.value.weeklyCap
            val capped = if (cap == null) {
                sets
            } else {
                val lastWeekBest = WorkoutStats.bestWeightBetween(id, history, weekAgo, Long.MAX_VALUE)
                sets.map { it.copy(weightKg = ProgressionCap.limit(it.weightKg, lastWeekBest, cap)) }
            }
            // Solo pesos que se pueden montar con la barra, discos y mancuernas del gimnasio activo
            if (ex == null || s.unit != com.bestrong.core.util.WeightUnit.KG) capped else capped.map { it.copy(weightKg = if (it.weightKg > 0) gym.nearestLoad(it.weightKg, ex.equipment) else 0.0) }
        }
    }

    suspend fun exportToHealthConnectIfEnabled(workout: Workout) {
        if (!settings.settings.value.healthConnectExport) return
        runCatching { healthConnect.export(workout) }.onFailure { Log.w("HealthConnect", "Exportación fallida", it) }
    }
}

/** Replica rutinas, historial, ajustes y el entreno en curso al reloj; mantiene el widget al día. */
@Singleton
class AppSync @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val repository: BeStrongRepository,
    private val settings: SettingsStore,
    private val watchSync: WatchSync,
    private val restTimer: RestTimerController,
    private val watchMetrics: WatchMetricsStore,
    private val planning: PlanningStore,
) {
    private fun routinesForWatch(routines: List<Routine>, program: ActiveProgram?, history: List<Workout>): List<Routine> {
        val today = program?.let { Periodization.nextSession(it, history, context.uiLanguage(), planning.state.value.customPrograms)?.routine }
        return listOfNotNull(today) + routines
    }

    /** Envío inmediato de rutinas (incluida la sesión de hoy) e historial al reloj. */
    suspend fun pushToWatchNow() {
        watchSync.pushRoutines(routinesForWatch(repository.routines.value, repository.activeProgram.value, repository.history.value))
        watchSync.pushHistory(repository.history.value)
    }

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            // La sesión de hoy del programa activo va la primera, con sus cargas calculadas
            combine(repository.routines, repository.activeProgram, repository.history) { routines, program, history ->
                routinesForWatch(routines, program, history)
            }.debounce(1_000).distinctUntilChanged().collect { watchSync.pushRoutines(it) }
        }
        scope.launch {
            repository.history.debounce(2_000).collect {
                watchSync.pushHistory(it)
                BeStrongWidget.refresh(context)
            }
        }
        scope.launch {
            settings.settings.map { SharedSettings(it.unit, it.progressionEnabled) }.distinctUntilChanged()
                .collect { watchSync.pushSettings(it) }
        }
        // Entreno en curso compartido en vivo con el reloj + servicio en primer plano
        scope.launch {
            repository.activeWorkout.drop(1).debounce(300).collect { w ->
                watchSync.pushActive(w)
                if (w != null) {
                    WorkoutForegroundService.start(context)
                } else {
                    restTimer.skip()
                    watchMetrics.metrics.value = null
                }
            }
        }
        // Animaciones de ejercicios en caché para el gimnasio sin cobertura (solo con Wi-Fi)
        runCatching { ImagePrefetchWorker.schedule(context) }
        // Resumen de la semana el domingo por la tarde
        runCatching { WeeklySummaryWorker.schedule(context) }
        // Atajos dinámicos con las tres primeras rutinas (icono de la app, Bixby Routines)
        scope.launch {
            repository.routines.map { list -> list.filter { it.exercises.isNotEmpty() }.take(3) }.distinctUntilChanged().collect { list ->
                runCatching {
                    val shortcuts = list.map { r ->
                        ShortcutInfoCompat.Builder(context, "routine_" + r.id)
                            .setShortLabel(r.name.take(24))
                            .setLongLabel(r.name)
                            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_dumbbell))
                            .setIntent(
                                Intent(context, MainActivity::class.java)
                                    .setAction(MainActivity.ACTION_START_ROUTINE)
                                    .putExtra(MainActivity.EXTRA_ROUTINE_ID, r.id),
                            )
                            .build()
                    }
                    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
                }
            }
        }
        // Recordatorios y copia de seguridad semanal (WorkManager)
        scope.launch {
            settings.settings.map { Triple(it.reminderDays, it.reminderHour, it.reminderMinute) }.distinctUntilChanged()
                .collect { (days, h, m) -> runCatching { ReminderWorker.schedule(context, days, h, m) }.onFailure { Log.w("AppSync", "Recordatorios", it) } }
        }
        scope.launch {
            settings.settings.map { it.backupFolderUri != null }.distinctUntilChanged()
                .collect { on -> runCatching { AutoBackupWorker.schedule(context, on) }.onFailure { Log.w("AppSync", "Copia de seguridad", it) } }
        }
    }
}

@HiltAndroidApp
class BeStrongApplication :
    Application(),
    ImageLoaderFactory {
    /** Coil con soporte de GIF animados (animaciones de ExerciseDB). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(ImageDecoderDecoder.Factory()) }
        .crossfade(true)
        .build()

    @Inject lateinit var appSync: AppSync

    override fun onCreate() {
        super.onCreate()
        appSync.start()
    }
}
