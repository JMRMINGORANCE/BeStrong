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
// Hilt: las dependencias de la sincronización con el reloj, una por almacén
@Suppress("LongParameterList")
@Singleton
class AppSync @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val repository: BeStrongRepository,
    private val settings: SettingsStore,
    private val planning: com.bestrong.mobile.data.PlanningStore,
    private val watchSync: WatchSync,
    private val restTimer: RestTimerController,
    private val watchMetrics: WatchMetricsStore,
    private val watchPush: WatchPush,
    private val media: ExerciseMediaStore,
) {
    /** Envío inmediato de rutinas (incluida la sesión de hoy) e historial al reloj. */
    suspend fun pushToWatchNow() = watchPush.pushToWatchNow()

    private suspend fun scheduleCardioNudge() =
        settings.settings.map { it.cardioNudgeDay }.distinctUntilChanged().collect { day ->
            runCatching { com.bestrong.mobile.notify.CardioNudgeWorker.schedule(context, day) }
        }

    /** Cómo va el objetivo de cardio de esta semana: [goal] identifica el objetivo puesto («km:20», «min:150»). */
    private data class CardioGoalState(val week: Long, val goal: String, val met: Boolean, val text: String)

    /**
     * Aviso al cumplir el objetivo semanal de cardio: solo cuando se cruza entrenando (no al arrancar la app, ni
     * al bajar el objetivo por debajo de lo hecho) y una vez por semana y objetivo (si se sube, vuelve a avisar).
     */
    private suspend fun notifyCardioGoal() {
        val prefs = context.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
        // El estado anterior se guarda: un entreno del reloj puede llegar con la app cerrada (proceso nuevo)
        var previous: Triple<Long, String, Boolean>? = prefs.getString("cardio_goal_prev", null)?.split('|')?.takeIf { it.size == 3 }?.let {
            Triple(it[0].toLongOrNull() ?: -1, it[1], it[2] == "1")
        }
        // La lista en memoria empieza vacía hasta que carga: ese arranque no cuenta como «antes»
        combine(repository.history.dropWhile { it.isEmpty() }, settings.settings) { history, s -> cardioGoalState(history, s) }
            .debounce(2_000).distinctUntilChanged().collect { cur ->
                val prev = previous
                previous = cur?.let { Triple(it.week, it.goal, it.met) }
                cur?.let { prefs.edit().putString("cardio_goal_prev", "${it.week}|${it.goal}|${if (it.met) 1 else 0}").apply() }
                // Cruzado entrenando: antes sin cumplir con el mismo objetivo (o la semana pasada: esta empezó en cero)
                val crossed = cur != null && cur.met && prev != null && prev.second == cur.goal && (prev.first != cur.week || !prev.third)
                val key = cur?.let { "${it.week}:${it.goal}" }
                if (crossed && prefs.getString("cardio_goal_notified", null) != key) {
                    prefs.edit().putString("cardio_goal_notified", key).apply()
                    com.bestrong.mobile.notify.Notifier.cardioGoal(context, cur?.text.orEmpty())
                }
            }
    }

    /** El objetivo de cardio de esta semana y lo hecho («150 de 150 min»); null si no hay objetivo. */
    private fun cardioGoalState(history: List<com.bestrong.core.model.Workout>, s: com.bestrong.mobile.data.UserSettings): CardioGoalState? {
        val goalKm = s.weeklyCardioKm
        val goalMin = s.weeklyCardioMin
        if (goalKm == null && goalMin == null) return null
        val zone = java.time.ZoneId.systemDefault()
        val monday = java.time.LocalDate.now(zone).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val (meters, seconds) = com.bestrong.core.stats.CardioTotals.since(history, monday.atStartOfDay(zone).toInstant().toEpochMilli())
        return if (goalMin != null) {
            val minutes = (seconds / 60).toInt()
            CardioGoalState(monday.toEpochDay(), "min:$goalMin", minutes >= goalMin, context.getString(R.string.cardio_week_goal_min, minutes, goalMin))
        } else {
            val km = meters / 1000
            val goal = goalKm ?: 0.0
            CardioGoalState(monday.toEpochDay(), "km:$goal", km >= goal, context.getString(R.string.cardio_week_goal, com.bestrong.core.util.formatKmLike(km, goal), com.bestrong.core.util.formatGoalKm(goal)))
        }
    }

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
            // La primera lista de la base de datos suele ser la misma que ya se leyó: no se reenvía
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
        combine(settings.settings, repository.activeProgram, notes, bests, planning.state.map { it.weeklyCap }.distinctUntilChanged()) { s, program, pinned, (best1rm, heaviest), cap ->
            SharedSettings(
                s.unit, s.progressionEnabled, s.restVibration,
                weeklyGoal = program?.weeklyGoal ?: s.weeklyGoal,
                exerciseIncrementKg = s.exerciseIncrementKg,
                exerciseRepRange = s.exerciseRepRange,
                gymBarKg = s.activeGym.loadedBarKg,
                exerciseBarKg = s.exerciseBarKg,
                activeGym = s.activeGym,
                multiGym = s.gyms.size > 1,
                weeklyCardioKm = s.weeklyCardioKm,
                weeklyCardioMin = s.weeklyCardioMin,
                weeklyCap = cap,
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
        // El widget muestra la semana y la rutina que toca: se redibuja si cambia el historial, las rutinas,
        // el mínimo de series por músculo, el objetivo de cardio o la planificación (la rutina de hoy)
        scope.launch {
            merge(repository.history, repository.routines, settings.settings.map { Triple(it.weeklySetsTarget, it.weeklyCardioKm, it.weeklyCardioMin) }.distinctUntilChanged(), planning.state).debounce(2_000).collect {
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
        // Empujón si el cardio de la semana va por detrás del objetivo, el día elegido (se reprograma si cambia)
        scope.launch { scheduleCardioNudge() }
        scope.launch { notifyCardioGoal() }
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
        scope.launch {
            settings.settings.map { it.measureReminder }.distinctUntilChanged()
                .collect { on -> runCatching { com.bestrong.mobile.notify.MeasureWorker.schedule(context, on) }.onFailure { Log.w("AppSync", "Medidas", it) } }
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
