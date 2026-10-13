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
import com.bestrong.core.sync.SharedSettings
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.backup.ImagePrefetchWorker
import com.bestrong.mobile.data.BeStrongRepository
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

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
) {
    /** Envío inmediato de rutinas (incluida la sesión de hoy) e historial al reloj. */
    suspend fun pushToWatchNow() = watchPush.pushToWatchNow()

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
        // El widget muestra la semana y la rutina que toca: se redibuja si cambia el historial o las rutinas
        scope.launch {
            merge(repository.history, repository.routines).debounce(2_000).collect { BeStrongWidget.refresh(context) }
        }
        scope.launch {
            // Objetivo semanal: el del programa activo si lo hay, como en la pantalla de inicio
            combine(settings.settings, repository.activeProgram) { s, program ->
                SharedSettings(s.unit, s.progressionEnabled, s.restVibration, weeklyGoal = program?.weeklyGoal ?: s.weeklyGoal)
            }.distinctUntilChanged()
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
