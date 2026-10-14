package com.bestrong.mobile

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SharedSettings
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.backup.ImagePrefetchWorker
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.ExerciseMediaStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.di.ApplicationScope
import com.bestrong.mobile.notify.ReminderWorker
import com.bestrong.mobile.notify.WeeklySummaryWorker
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.sync.WatchSync
import com.bestrong.mobile.widget.BeStrongWidget
import com.bestrong.mobile.workout.RestTimerController
import com.bestrong.mobile.workout.WorkoutForegroundService
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private const val MAX_WATCH_NOTE = 140

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
    private val watchPush: WatchPush,
    private val media: ExerciseMediaStore,
) {
    /** Envío inmediato de rutinas (incluida la sesión de hoy) e historial al reloj. */
    suspend fun pushToWatchNow() = watchPush.pushToWatchNow()

    /**
     * Ajustes compartidos con el reloj. Objetivo semanal: el del programa activo si lo hay, como en
     * la pantalla de inicio. Notas fijas: se releen cuando cambia alguna (cortas, para el reloj).
     */
    private suspend fun syncSettings() {
        val notes = media.version.map { media.allNotes().mapValues { (_, n) -> n.trim().take(MAX_WATCH_NOTE) } }

        // Mejores marcas por ejercicio, para que el reloj sepa si una serie es récord
        fun bestsOf(h: List<com.bestrong.core.model.Workout>): Pair<Map<String, Double>, Map<String, Double>> {
            val records = WorkoutStats.personalRecords(h)
            // Solo las que tienen peso (el cardio o los isométricos no pesan nada en el mensaje)
            return records.mapValues { (_, r) -> r[RecordType.BEST_ONE_REP_MAX]?.value ?: 0.0 }.filterValues { it > 0 } to
                records.mapValues { (_, r) -> r[RecordType.HEAVIEST_WEIGHT]?.value ?: 0.0 }.filterValues { it > 0 }
        }
        // La primera, ya leída de la base de datos (la lista en memoria empieza vacía hasta que carga)
        val bests = flow {
            emit(bestsOf(repository.historyOnce()))
            // La lista en memoria empieza vacía hasta que carga: se salta solo ese arranque
            emitAll(repository.history.dropWhile { it.isEmpty() }.map(::bestsOf))
        }.flowOn(Dispatchers.Default)
        combine(settings.settings, repository.activeProgram, notes, bests) { s, program, pinned, (best1rm, heaviest) ->
            SharedSettings(
                s.unit, s.progressionEnabled, s.restVibration,
                weeklyGoal = program?.weeklyGoal ?: s.weeklyGoal,
                exerciseIncrementKg = s.exerciseIncrementKg,
                exerciseRepRange = s.exerciseRepRange,
                pinnedNotes = pinned,
                warmupRestSeconds = s.warmupRestSeconds,
                bestOneRepMaxKg = best1rm,
                heaviestKg = heaviest,
            )
        }.distinctUntilChanged()
            .collect { watchSync.pushSettings(it) }
    }

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            // La sesión de hoy del programa activo va la primera, con sus cargas calculadas
            combine(repository.routines, repository.activeProgram, repository.history) { routines, program, history ->
                watchPush.routinesForWatch(routines, program, history)
            }.debounce(1_000).distinctUntilChanged().collect { watchSync.pushRoutines(it) }
        }
        scope.launch {
            repository.history.debounce(2_000).collect { watchSync.pushHistory(it) }
        }
        // El widget muestra la semana y la rutina que toca: se redibuja si cambia el historial, las rutinas
        // o el mínimo de series por músculo
        scope.launch {
            merge(repository.history, repository.routines, settings.settings.map { it.weeklySetsTarget }.distinctUntilChanged()).debounce(2_000).collect {
                BeStrongWidget.refresh(context)
                com.bestrong.mobile.widget.MuscleWidget.refresh(context)
            }
        }
        scope.launch { syncSettings() }
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
            repository.routines.map { list -> list.filter { it.exercises.isNotEmpty() && !it.archived }.take(3) }.distinctUntilChanged().collect { list ->
                runCatching {
                    val shortcuts = list.map { r ->
                        ShortcutInfoCompat.Builder(context, "routine_" + r.id)
                            .setShortLabel(r.name.take(24))
                            .setLongLabel(r.name)
                            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_dumbbell))
                            .setIntent(
                                AppIntents.main(context)
                                    .setAction(AppIntents.ACTION_START_ROUTINE)
                                    .putExtra(AppIntents.EXTRA_ROUTINE_ID, r.id),
                            )
                            .build()
                    }
                    ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
                }
            }
        }
        // Recordatorios y copia de seguridad semanal (WorkManager)
        scheduleReminders()
        scope.launch {
            settings.settings.map { it.backupFolderUri != null }.distinctUntilChanged()
                .collect { on -> runCatching { AutoBackupWorker.schedule(context, on) }.onFailure { Log.w("AppSync", "Copia de seguridad", it) } }
        }
    }

    /** Recordatorio de entreno y de pesarse: se reprograman cuando cambian sus ajustes. */
    private fun scheduleReminders() {
        scope.launch {
            settings.settings.map { Triple(it.reminderDays, it.reminderHour, it.reminderMinute) }.distinctUntilChanged()
                .collect { (days, h, m) -> runCatching { ReminderWorker.schedule(context, days, h, m) }.onFailure { Log.w("AppSync", "Recordatorios", it) } }
        }
        scope.launch {
            settings.settings.map { it.weighInDay }.distinctUntilChanged()
                .collect { day -> runCatching { com.bestrong.mobile.notify.WeighInWorker.schedule(context, day) }.onFailure { Log.w("AppSync", "Pesarse", it) } }
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
