package com.bestrong.mobile.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bestrong.core.io.Backup
import com.bestrong.core.io.CoachReport
import com.bestrong.core.io.EncryptedBackup
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.io.Xlsx
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.stats.Achievement
import com.bestrong.core.stats.Balance
import com.bestrong.core.stats.PeriodSummary
import com.bestrong.core.stats.PersonalRecord
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.Strength
import com.bestrong.core.stats.StrengthStanding
import com.bestrong.core.stats.TrainingLoad
import com.bestrong.core.stats.WeekBucket
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.mobile.WatchPush
import com.bestrong.mobile.backup.AutoBackupWorker
import com.bestrong.mobile.backup.FullBackup
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.ProgressPhoto
import com.bestrong.mobile.data.ProgressPhotoStore
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.health.HealthConnectExporter
import com.bestrong.mobile.health.HealthConnectReader
import com.bestrong.mobile.share.MonthlyReport
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileStats(
    /** Ya calculadas (hasta entonces no se enseña «aún no hay entrenos», que sería un parpadeo). */
    val loaded: Boolean = false,
    val totalWorkouts: Int = 0,
    val totalVolume: Double = 0.0,
    val totalDurationSec: Long = 0,
    val weeklyStreak: Int = 0,
    val weeks: List<WeekBucket> = emptyList(),
    val muscles30d: Map<MuscleGroup, Double> = emptyMap(),
    val topOneRepMax: List<PersonalRecord> = emptyList(),
    /** Series de esta semana por músculo (para el mapa con el objetivo de 10–20). */
    val weeklyMuscles: Map<MuscleGroup, Double> = emptyMap(),
    val totalSets: Int = 0,
    /** Series por músculo de las últimas 8 semanas (el más trabajado primero). */
    val muscleTrend: Map<MuscleGroup, List<Pair<java.time.LocalDate, Double>>> = emptyMap(),
    /** Tu año (el actual; en enero, el anterior, que es el que tiene sentido resumir). */
    val year: com.bestrong.core.stats.YearSummary? = null,
)

/** Estadísticas de la fase 3 (niveles, equilibrio, logros, recuperación…). */
data class AdvancedStats(
    /** Rendimiento por franja del día (mañana, tarde, noche), la mejor primero. */
    val dayParts: List<com.bestrong.core.stats.DayPartStat> = emptyList(),
    /** Rendimiento por día de la semana (el mejor primero). */
    val weekdays: List<Triple<java.time.DayOfWeek, Int, Double>> = emptyList(),
    val bodyWeightKg: Double? = null,
    val standings: List<StrengthStanding> = emptyList(),
    val balance: Balance = Balance(0.0, 0.0, 0.0, 0.0),
    val week: PeriodSummary? = null,
    val weekEffectiveSets: Double = 0.0,
    val achievements: List<Achievement> = emptyList(),
    val timeline: List<PersonalRecord> = emptyList(),
    val daysSince: Map<MuscleGroup, Int?> = emptyMap(),
    val soreness: Map<MuscleGroup, Double> = emptyMap(),
    /** Reto con fecha estimada: (ejercicio, 1RM objetivo, fecha). */
    val challenge: Triple<String, Double, Long>? = null,
    /** Carga semanal (RPE × minutos) y relación aguda:crónica. */
    val load: TrainingLoad.Weekly? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BeStrongRepository,
    private val settingsStore: SettingsStore,
    private val healthConnect: HealthConnectExporter,
    private val watchPush: WatchPush,
    private val photoStore: ProgressPhotoStore,
    private val healthReader: HealthConnectReader,
    private val fullBackup: FullBackup,
) : ViewModel() {

    val healthReadPermissions: Set<String> = healthReader.permissions

    /** Trae los pesos de Health Connect (Samsung Health, básculas) a Medidas, sin duplicar días. */
    fun importHealthWeights() = viewModelScope.launch {
        val snap = runCatching { healthReader.read() }.getOrNull() ?: return@launch
        val zone = ZoneId.systemDefault()
        fun day(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        // De la base de datos (con la app recién abierta la lista en memoria aún está vacía)
        val current = repo.measurementsOnce()
        // Las traídas antes de marcar el origen: misma hora y mismo peso que en Health Connect
        current.filter { m -> !m.weightFromHealth && snap.weights.any { (t, kg) -> t == m.dateEpochMs && kotlin.math.abs(kg - (m.weightKg ?: -1.0)) < 0.01 } }
            .forEach { repo.saveMeasurement(it.copy(weightFromHealth = true)) }
        val ignored = repo.ignoredHealthWeightDays()
        val byDay = current.groupBy { day(it.dateEpochMs) }
        val fresh = snap.weights.filter { (t, _) -> day(t).toEpochDay() !in ignored }.groupBy { (t, _) -> day(t) }
            // Un día que ya tiene peso no se toca; si tiene otra medida (la cintura), el peso va en ella
            .filterKeys { d -> byDay[d].orEmpty().none { it.weightKg != null } }
            .map { (d, list) -> d to list.maxBy { it.first } }
        fresh.forEach { (d, w) ->
            val (t, kg) = w
            val sameDay = byDay[d]?.firstOrNull()
            repo.saveMeasurement(sameDay?.copy(weightKg = kg, weightFromHealth = true) ?: BodyMeasurement(dateEpochMs = t, weightKg = kg, weightFromHealth = true))
        }
        _messages.tryEmit(context.getString(R.string.hc_import_weight_done, fresh.size))
    }

    val stats: StateFlow<ProfileStats> = repo.history.map { history ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        ProfileStats(
            loaded = true,
            totalWorkouts = history.size,
            totalVolume = history.sumOf { it.totalVolume },
            totalDurationSec = history.sumOf { it.durationSeconds() },
            totalSets = history.sumOf { it.completedSetCount },
            weeklyStreak = WorkoutStats.weeklyStreak(history, today, zone),
            weeks = WorkoutStats.weeklySummary(history, 12, today, zone),
            muscles30d = WorkoutStats.setsPerMuscle(history, System.currentTimeMillis() - 30L * 24 * 3600 * 1000),
            topOneRepMax = WorkoutStats.personalRecords(history).values
                .mapNotNull { it[RecordType.BEST_ONE_REP_MAX] }
                .sortedByDescending { it.value }
                .take(8),
            weeklyMuscles = WorkoutStats.weeklySetsPerMuscle(history, today, zone),
            muscleTrend = WorkoutStats.muscleTrend(history, today, zone),
            // En enero se resume el año que acaba de terminar (si entrenaste); si no, el actual
            year = (if (today.monthValue == 1) Strength.yearSummary(history, today.year - 1, zone) else null)
                ?: Strength.yearSummary(history, today.year, zone),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    // Solo el sexo de los ajustes: cambiar el tema o las unidades no rehace estos cálculos (son caros con mucho historial)
    val advanced: StateFlow<AdvancedStats> = combine(repo.history, repo.measurements, settingsStore.settings.map { it.sex }.distinctUntilChanged()) { history, measures, sex ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val bw = measures.sortedByDescending { it.dateEpochMs }.firstNotNullOfOrNull { it.weightKg }
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        AdvancedStats(
            bodyWeightKg = bw,
            standings = bw?.let { Strength.standings(history, it, sex) }.orEmpty(),
            balance = Strength.balance(history, now),
            week = Strength.weekSummary(history, today, zone),
            weekEffectiveSets = history.filter { it.isFinished && it.startedAtEpochMs >= weekStart }.sumOf { Strength.effectiveSets(it) },
            achievements = Strength.achievements(history, today, zone, bw),
            timeline = Strength.recordTimeline(history, setOf(RecordType.BEST_ONE_REP_MAX)).takeLast(12).reversed(),
            daysSince = Strength.daysSinceTrained(history, today, zone),
            soreness = Strength.soreness(history, now),
            challenge = challenge(history),
            load = TrainingLoad.weekly(history, today, zone),
            dayParts = com.bestrong.core.stats.TimeOfDay.stats(history, zone, System.currentTimeMillis()),
            weekdays = com.bestrong.core.stats.TimeOfDay.byWeekday(history, zone, System.currentTimeMillis()),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdvancedStats())

    /** El básico con más sesiones: próximo múltiplo de 10 kg de 1RM y cuándo se alcanzará al ritmo actual. */
    private fun challenge(history: List<com.bestrong.core.model.Workout>): Triple<String, Double, Long>? {
        val lift = Strength.STANDARD_LIFTS.maxByOrNull { id -> history.count { w -> w.exercises.any { it.exercise.id == id } } } ?: return null
        val points = WorkoutStats.exerciseProgress(lift, history).filter { it.bestOneRepMax > 0 }.map { it.dateEpochMs to it.bestOneRepMax }
        val best = points.maxOfOrNull { it.second } ?: return null
        val target = (kotlin.math.floor(best / 10.0) + 1) * 10.0
        val eta = Strength.predictDate(points, target) ?: return null
        return Triple(lift, target, eta)
    }

    val measurements: StateFlow<List<BodyMeasurement>> = repo.measurements

    // --- Fotos de progreso y fuerza relativa -------------------------------------------------

    val photos: StateFlow<List<ProgressPhoto>> = photoStore.photos
    fun addPhoto(uri: Uri) = viewModelScope.launch { runCatching { photoStore.add(uri) } }
    fun deletePhoto(photo: ProgressPhoto) = viewModelScope.launch { photoStore.delete(photo) }

    /** Fuerza relativa (1RM / peso corporal) del básico con más datos: (ejercicio, [(fecha, peso, 1RM)]). */
    val relativeStrength: StateFlow<Pair<String, List<Triple<Long, Double, Double>>>?> = combine(repo.history, repo.measurements) { history, measures ->
        val weights = measures.mapNotNull { m -> m.weightKg?.takeIf { it > 0 }?.let { m.dateEpochMs to it } }
        if (weights.isEmpty()) return@combine null
        Strength.STANDARD_LIFTS.map { id -> id to Strength.bodyWeightVsStrength(WorkoutStats.exerciseProgress(id, history), weights) }
            .filter { it.second.size >= 2 }
            .maxByOrNull { it.second.size }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Informe del mes ([monthsAgo] = 0 el actual) como imagen o PDF. */
    fun shareMonthlyReport(monthsAgo: Int, pdf: Boolean) = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now(zone).minusMonths(monthsAgo.toLong())
        val history = repo.historyOnce()
        val summary = withContext(Dispatchers.Default) { Strength.monthSummary(history, month, zone) }
        val days = WorkoutStats.trainingDays(history, zone).filter { YearMonth.from(it) == month }.toSet()
        runCatching { MonthlyReport.share(context, month, summary, days, settings.value.unit, pdf) }
            .onFailure { _messages.tryEmit(context.getString(R.string.export_error, it.message.orEmpty())) }
    }
    val settings: StateFlow<UserSettings> = settingsStore.settings

    val healthConnectAvailable = healthConnect.isAvailable()
    val healthConnectPermissions = healthConnect.permissions
    val healthConnectRequired = healthConnect.sessionPermission
    val healthConnectGranted = MutableStateFlow(false)

    /** Todos los permisos de exportación (los nuevos, como el peso, se piden aunque ya exportes). */
    val healthConnectComplete = MutableStateFlow(true)

    fun refreshHealthConnect() = viewModelScope.launch {
        healthConnectGranted.value = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
        healthConnectComplete.value = runCatching { healthConnect.hasAllPermissions() }.getOrDefault(false)
    }

    fun updateSettings(block: (UserSettings) -> UserSettings) = settingsStore.update(block)

    fun saveMeasurement(m: BodyMeasurement) = viewModelScope.launch { saveMeasurementNow(m, force = false) }

    /**
     * Guarda la medida; a Health Connect solo si cambian el peso o la grasa ([force]: siempre, al deshacer un
     * borrado).
     */
    private suspend fun saveMeasurementNow(m: BodyMeasurement, force: Boolean, known: List<BodyMeasurement>? = null) {
        // [known]: las medidas ya leídas (importando muchas no se relee la tabla en cada una)
        val before = (known ?: repo.measurementsOnce()).firstOrNull { it.id == m.id }
        // Un peso de la báscula corregido a mano sigue sin exportarse: allí ya está el de la báscula (saldría doble)
        val saved = m
        repo.saveMeasurement(saved)
        val healthChanged = force || before == null || before.weightKg != saved.weightKg || before.bodyFatPct != saved.bodyFatPct
        if (!healthChanged || !settingsStore.settings.value.healthConnectExport) return
        // Solo se borra lo que se quitó; lo demás se sustituye al exportar (mismo id de cliente)
        if (before != null) runCatching { healthConnect.deleteMeasurement(m.id, weight = saved.weightKg == null, fat = saved.bodyFatPct == null) }
        runCatching { healthConnect.exportMeasurement(saved) }.onFailure { android.util.Log.w("Profile", "Medida no exportada", it) }
    }

    /** Todas las medidas en CSV en [uri]; [onDone] dice si salió bien. */
    fun exportMeasurementsCsv(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val csv = withContext(Dispatchers.Default) { com.bestrong.core.io.MeasurementCsv.export(repo.measurementsOnce()) }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("no stream") }
        }.isSuccess
        onDone(ok)
    }

    /**
     * Medidas desde un CSV ([uri]): el día que ya tiene medida solo se le rellena lo que le falta; [onDone] recibe
     * cuántas se añadieron, cuántas se completaron y cuántas filas se saltaron (null si no se pudo leer o no se
     * entendió ninguna fila).
     */
    fun importMeasurementsCsv(uri: Uri, onDone: (Triple<Int, Int, Int>?) -> Unit) = viewModelScope.launch {
        val read = runCatching {
            // Con el mismo límite que las demás importaciones (un archivo enorme no tumba la app)
            val text = withContext(Dispatchers.IO) { readLimited(uri) }
            withContext(Dispatchers.Default) { com.bestrong.core.io.MeasurementCsv.read(text) }
        }.getOrNull()
        if (read == null || read.rows.isEmpty()) {
            onDone(null)
            return@launch
        }
        // Aunque se salga de la pantalla, la importación termina (no se queda a medias)
        val (added, completed) = withContext(kotlinx.coroutines.NonCancellable) { saveImported(read.rows) }
        onDone(Triple(added, completed, read.skipped))
    }

    /** Guarda las filas importadas juntando las del mismo día; devuelve (nuevas, completadas). */
    private suspend fun saveImported(rows: List<BodyMeasurement>): Pair<Int, Int> {
        val zone = ZoneId.systemDefault()
        fun day(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val known = repo.measurementsOnce()
        // Se actualiza al ir guardando: dos filas del mismo día se juntan en una medida
        val byDay = known.associateBy { day(it.dateEpochMs) }.toMutableMap()
        var added = 0
        var completed = 0
        rows.forEach { row ->
            val old = byDay[day(row.dateEpochMs)]
            val merged = old?.let { fillMissing(it, row) } ?: row
            if (merged != old) {
                // Como las demás: con la exportación activada, el peso y la grasa también a Health Connect
                saveMeasurementNow(merged, force = false, known = listOfNotNull(old))
                byDay[day(merged.dateEpochMs)] = merged
                if (old == null) added++ else completed++
            }
        }
        return added to completed
    }

    /** [old] con lo que le falta tomado de [row] (lo que ya tenía no se toca). */
    private fun fillMissing(old: BodyMeasurement, row: BodyMeasurement) = old.copy(
        weightKg = old.weightKg ?: row.weightKg,
        bodyFatPct = old.bodyFatPct ?: row.bodyFatPct,
        waistCm = old.waistCm ?: row.waistCm,
        chestCm = old.chestCm ?: row.chestCm,
        armCm = old.armCm ?: row.armCm,
        thighCm = old.thighCm ?: row.thighCm,
    )

    /** Los pesos de la báscula borrados aquí vuelven a traerse en la próxima importación. */
    fun restoreIgnoredHealthWeights() = viewModelScope.launch {
        // Sin ninguno borrado no hay nada que recuperar (ni se pide importar en vano)
        if (repo.ignoredHealthWeightDays().isEmpty()) {
            _messages.tryEmit(context.getString(R.string.hc_restore_none))
            return@launch
        }
        repo.clearIgnoredHealthWeights()
        importHealthWeights()
    }

    /** Última medida borrada, para «Deshacer». */
    private val _deletedMeasurement = MutableStateFlow<BodyMeasurement?>(null)
    val deletedMeasurement: StateFlow<BodyMeasurement?> = _deletedMeasurement

    /** Borrado en curso: «Deshacer» espera a que acabe (si no, el borrado en Health Connect llegaría después). */
    private var deleteMeasurementJob: kotlinx.coroutines.Job? = null

    fun deleteMeasurement(id: String) {
        val m = repo.measurements.value.firstOrNull { it.id == id }
        // El aviso sale ya (y en orden si se borran varias seguidas); el borrado sigue después
        _deletedMeasurement.value = m
        deleteMeasurementJob = viewModelScope.launch {
            repo.deleteMeasurement(id)
            // Una de la báscula no vuelve al importar de nuevo
            // (por día: si se unió a otra medida del día, su hora no es la de la báscula)
            if (m?.weightFromHealth == true) {
                repo.ignoreHealthWeightDay(java.time.Instant.ofEpochMilli(m.dateEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())
            }
            runCatching { healthConnect.deleteMeasurement(id) }
        }
    }

    fun undoDeleteMeasurement() {
        val m = _deletedMeasurement.value ?: return
        _deletedMeasurement.value = null
        viewModelScope.launch {
            deleteMeasurementJob?.join()
            saveMeasurementNow(m, force = true)
        }
    }

    fun clearDeletedMeasurement() {
        _deletedMeasurement.value = null
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Periodo de las exportaciones a CSV y Excel. */
    val exportRange = MutableStateFlow(ExportRange.ALL)

    /** Entrenos de los últimos 7 días en texto, para el entrenador (null y aviso si no hay ninguno). */
    fun coachWeekText(): String? {
        val from = System.currentTimeMillis() - 7 * 86_400_000L
        val text = settingsStore.settings.value.let { prefs ->
            CoachReport.text(repo.history.value, from, prefs.unit, exerciseName = { it.displayName(context) }, dumbbellTotal = prefs.dumbbellTotal)
        }
        if (text.isBlank()) _messages.tryEmit(context.getString(R.string.share_week_empty))
        return text.takeIf { it.isNotBlank() }?.let { context.getString(R.string.share_week_title) + "\n\n" + it }
    }

    /** Inicio del periodo elegido para exportar (0 = todo). */
    private fun rangeStart(): Long {
        val now = java.time.LocalDate.now()
        return when (exportRange.value) {
            ExportRange.ALL -> return 0
            ExportRange.YEAR -> now.withDayOfYear(1)
            ExportRange.DAYS_90 -> now.minusDays(90)
            ExportRange.DAYS_30 -> now.minusDays(30)
        }.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun inRange(history: List<com.bestrong.core.model.Workout>): List<com.bestrong.core.model.Workout> =
        rangeStart().let { from -> history.filter { it.startedAtEpochMs >= from } }

    fun exportXlsx(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val bytes = withContext(Dispatchers.Default) { Xlsx.workouts(inRange(repo.historyOnce()), measurements = rangeStart().let { from -> repo.measurements.value.filter { it.dateEpochMs >= from } }) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error(context.getString(R.string.file_open_error))
            }
        }
        _messages.tryEmit(result.fold({ context.getString(R.string.export_done) }, { context.getString(R.string.export_error, it.message.orEmpty()) }))
    }

    /** Copia completa cifrada con contraseña (AES-256-GCM). */
    private var exportPassword: CharArray? = null

    /** Contraseña elegida para la copia cifrada, a la espera de que se elija dónde guardarla. */
    fun setExportPassword(password: CharArray) {
        exportPassword?.fill(' ')
        exportPassword = password
    }

    /** Guarda la copia cifrada con la contraseña pendiente; sin ella (p. ej. tras cerrarse la app) no se exporta en claro. */
    fun exportEncrypted(uri: Uri?) {
        val password = exportPassword
        exportPassword = null
        when {
            uri == null -> password?.fill(' ')
            password == null || password.size < MIN_EXPORT_PASSWORD -> {
                password?.fill(' ')
                _messages.tryEmit(context.getString(R.string.export_error, context.getString(R.string.password_hint)))
            }
            else -> writeEncrypted(uri, password)
        }
    }

    private fun writeEncrypted(uri: Uri, password: CharArray) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.Default) { EncryptedBackup.encrypt(Backup.encode(fullBackup.data()), password) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error(context.getString(R.string.file_open_error))
            }
        }
        password.fill(' ')
        _messages.tryEmit(result.fold({ context.getString(R.string.export_done) }, { context.getString(R.string.export_error, it.message.orEmpty()) }))
    }

    /** Copia cifrada pendiente de contraseña al restaurar. */
    val pendingEncrypted = MutableStateFlow<String?>(null)

    fun restoreEncrypted(password: CharArray) = viewModelScope.launch {
        val text = pendingEncrypted.value ?: return@launch
        val result = runCatching { withContext(Dispatchers.Default) { EncryptedBackup.decrypt(text, password) } }
        password.fill(' ')
        val plain = result.getOrElse { e ->
            // Con contraseña equivocada se puede reintentar; un archivo dañado no se arregla repitiéndola
            if (e is com.bestrong.core.io.DamagedBackupException) pendingEncrypted.value = null
            _messages.tryEmit(context.getString(if (e is com.bestrong.core.io.DamagedBackupException) R.string.backup_damaged else R.string.wrong_password))
            return@launch
        }
        pendingEncrypted.value = null
        restoreText(plain)
    }

    private suspend fun restoreText(text: String) {
        runCatching { withContext(Dispatchers.Default) { Backup.decode(text) } }
            .onSuccess { showRestorePreview(it) }
            .onFailure { _messages.tryEmit(context.getString(R.string.restore_error, it.message.orEmpty())) }
    }

    private suspend fun showRestorePreview(data: com.bestrong.core.io.BackupData) {
        // Como al restaurar: con los de la pareja y sin contar los ya importados con otro id (misma hora y nombre)
        val local = repo.historyOnce(includePartner = true)
        val ids = local.map { it.id }.toSet()
        val keys = local.map { it.startedAtEpochMs to it.name }.toSet()
        val latestLocal = local.maxOfOrNull { it.startedAtEpochMs }
        val latestBackup = data.history.maxOfOrNull { it.startedAtEpochMs }
        restoreInfo.value = RestoreInfo(
            newWorkouts = data.history.count { it.id !in ids && (it.startedAtEpochMs to it.name) !in keys },
            olderThanLocal = latestLocal != null && latestBackup != null && latestBackup < latestLocal,
        )
        restoreWithSettings.value = true
        pendingRestore.value = data
    }

    /** Lo que aporta la copia frente a lo que ya hay: entrenos nuevos y si es más antigua que tu último entreno. */
    data class RestoreInfo(val newWorkouts: Int = 0, val olderThanLocal: Boolean = false)

    val restoreInfo = MutableStateFlow(RestoreInfo())

    /** Copia leída y pendiente de confirmar (se enseña qué trae antes de restaurar). */
    val pendingRestore = MutableStateFlow<com.bestrong.core.io.BackupData?>(null)

    /** Si restaurar también los ajustes: aquí y no en la pantalla, para que sobreviva a girar el móvil. */
    val restoreWithSettings = MutableStateFlow(true)

    /**
     * Restaura la copia pendiente: añade lo que falte sin borrar nada. Con [includeSettings] en false se
     * quedan tus ajustes y tu planificación actuales (útil al traer entrenos de otro móvil ya configurado).
     */
    fun confirmRestore(includeSettings: Boolean) = viewModelScope.launch {
        val data = pendingRestore.value ?: return@launch
        pendingRestore.value = null
        // Sin ajustes: tampoco el programa (sus training max y su objetivo semanal son configuración)
        val result = runCatching { fullBackup.restore(if (includeSettings) data else data.copy(settings = null, planning = null, activeProgram = null)) }
        _messages.tryEmit(
            result.fold(
                { added -> context.getString(R.string.restore_done, data.history.size, data.routines.size, added) },
                { context.getString(R.string.restore_error, it.message.orEmpty()) },
            ),
        )
    }

    /** Lee un archivo elegido por el usuario, rechazando los enormes (un fichero equivocado de GB agotaría la memoria). */
    private fun readLimited(uri: android.net.Uri): String =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= MAX_IMPORT_BYTES) { context.getString(R.string.file_too_big) }
            }
            out.toByteArray().decodeToString()
        } ?: error(context.getString(R.string.file_open_error))

    /** Borrado selectivo: entrenos antiguos, medidas, fotos o todo el historial. */
    /** Lo exportado a Health Connect de los entrenos que se borran (si se exportaba). */
    private suspend fun deleteFromHealth(before: java.time.Instant?) {
        if (settingsStore.settings.value.healthConnectExport) runCatching { healthConnect.deleteWorkoutsBefore(before) }
    }

    fun deleteData(kind: DeleteKind) = viewModelScope.launch {
        val count = when (kind) {
            DeleteKind.OLD_HISTORY -> {
                val limit = System.currentTimeMillis() - 365L * 86_400_000L
                deleteFromHealth(java.time.Instant.ofEpochMilli(limit))
                repo.historyOnce(includePartner = true).filter { it.startedAtEpochMs < limit }.map { it.id }.also { repo.deleteWorkouts(it) }.size
            }
            DeleteKind.ALL_HISTORY -> {
                deleteFromHealth(null)
                repo.historyOnce(includePartner = true).map { it.id }.also { repo.deleteWorkouts(it) }.size
            }
            DeleteKind.MEASUREMENTS -> repo.measurements.value.onEach {
                repo.deleteMeasurement(it.id)
                runCatching { healthConnect.deleteMeasurement(it.id) }
            }.size
            DeleteKind.PHOTOS -> photoStore.photos.value.onEach { photoStore.delete(it) }.size
        }
        _messages.tryEmit(context.getString(R.string.delete_done, count))
    }

    /** Exporta todo el historial al archivo elegido (SAF). */
    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val history = repo.historyOnce()
            val csv = withContext(Dispatchers.Default) { WorkoutCsv.export(inRange(history)) }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error(context.getString(R.string.file_open_error))
            }
        }
        _messages.tryEmit(result.fold({ context.getString(R.string.export_done) }, { context.getString(R.string.export_error, it.message.orEmpty()) }))
    }

    /** Importa un CSV de BeStrong, Hevy o Strong. */
    fun importCsv(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                readLimited(uri)
            }
            val parsed = withContext(Dispatchers.Default) {
                WorkoutCsv.import(text, repo.exercises.value, strongUnit = settings.value.unit)
            }
            parsed to repo.importWorkouts(parsed.workouts, parsed.newExercises)
        }
        _messages.tryEmit(
            result.fold(
                { (r, added) ->
                    context.getString(R.string.import_done, r.format.label, added) +
                        (if (r.workouts.size > added) context.getString(R.string.import_existing, r.workouts.size - added) else "") +
                        (if (r.newExercises.isNotEmpty()) context.getString(R.string.import_new_exercises, r.newExercises.size) else "") +
                        (if (r.skippedRows > 0) context.getString(R.string.import_skipped, r.skippedRows) else "")
                },
                { context.getString(R.string.import_error, it.message.orEmpty()) },
            ),
        )
    }

    /** Una copia automática para elegir: su fecha, cuántos entrenos trae (null si no se pudo leer) y el documento. */
    data class AutoBackupItem(val date: String, val workouts: Int?, val uri: Uri)

    /** Copias automáticas de la carpeta para elegir cuál restaurar; null = sin abrir. */
    val autoBackups = MutableStateFlow<List<AutoBackupItem>?>(null)

    /** Lee las copias automáticas de la carpeta elegida (la más reciente primero), sin buscar el archivo a mano. */
    fun listAutoBackups() = viewModelScope.launch {
        val tree = settings.value.backupFolderUri?.let(Uri::parse) ?: return@launch
        val list = runCatching { withContext(Dispatchers.IO) { AutoBackupWorker.listJson(context, tree) } }
        // Sin permiso sobre la carpeta no es que no haya copias: la carpeta ya no está disponible
        val found = list.getOrElse { e ->
            _messages.tryEmit(context.getString(if (e is SecurityException) R.string.auto_backup_lost else R.string.restore_latest_none))
            return@launch
        }
        if (found.isEmpty()) {
            _messages.tryEmit(context.getString(R.string.restore_latest_none))
            return@launch
        }
        // Cuántos entrenos trae cada una (leerlas lleva un momento: fuera del hilo principal)
        autoBackups.value = withContext(Dispatchers.IO) {
            found.map { (date, uri) -> AutoBackupItem(date, runCatching { Backup.decode(readLimited(uri)).history.size }.getOrNull(), uri) }
        }
    }

    /** Lee una copia JSON de BeStrong y la deja pendiente de confirmar (añade lo que falte, no borra nada). */
    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                readLimited(uri)
            }
            // Copia cifrada: se pide la contraseña y se sigue en restoreEncrypted
            if (EncryptedBackup.isEncrypted(text)) {
                pendingEncrypted.value = text
                return@launch
            }
            withContext(Dispatchers.Default) { Backup.decode(text) }
        }
        // Se confirma en el diálogo con lo que trae (confirmRestore)
        result.onSuccess { showRestorePreview(it) }
            .onFailure { _messages.tryEmit(context.getString(R.string.restore_error, it.message.orEmpty())) }
    }

    /** Copia ahora en la carpeta elegida y dice cómo ha ido (con reintentos, puede tardar un poco). */
    fun backupNow() {
        _messages.tryEmit(context.getString(R.string.backup_started))
        viewModelScope.launch {
            val message = when (AutoBackupWorker.runNowAndWait(context)) {
                true -> R.string.backup_now_done
                false -> R.string.backup_now_failed
                null -> R.string.auto_backup_lost
            }
            _messages.tryEmit(context.getString(message))
        }
    }

    /**
     * Guarda un perfil de gimnasio (nuevo o editado). Si cambia de nombre, también en los entrenos hechos allí:
     * el historial, su filtro y el relleno «en este gimnasio» van por el nombre.
     */
    fun saveGym(updated: com.bestrong.core.training.GymProfile): Boolean {
        // Dos con el mismo nombre mezclarían sus entrenos (el historial va por el nombre)
        if (settings.value.gyms.any { it.id != updated.id && it.name.trim().equals(updated.name.trim(), ignoreCase = true) }) {
            _messages.tryEmit(context.getString(R.string.gym_name_taken, updated.name))
            return false
        }
        val oldName = settings.value.gyms.firstOrNull { it.id == updated.id }?.name
        settingsStore.update { s ->
            val exists = s.gyms.any { it.id == updated.id }
            s.copy(gyms = if (exists) s.gyms.map { if (it.id == updated.id) updated else it } else s.gyms + updated)
        }
        if (oldName != null && oldName != updated.name) {
            // De una vez (una transacción) y aunque se salga de la pantalla
            repo.rewriteHistory({ it.gymName == oldName }) { it.copy(gymName = updated.name) }
        }
        return true
    }

    /**
     * Cambia el nombre de la pareja y, con él, el de sus entrenos (pasados y el que está en curso): su historial
     * va por el nombre, y sin esto dejaría de usarse para sus pesos y marcas.
     */
    fun setPartnerName(name: String) {
        // El nombre de su historial queda fijado (si no lo estaba) hasta confirmar el nuevo en commitPartnerName
        settingsStore.update { it.copy(partnerName = name.trim(), partnerHistoryName = it.partnerAthlete) }
    }

    /** El último renombrado de historial en marcha: se espera antes de comprobar si un nombre está cogido. */
    private var lastRewrite: kotlinx.coroutines.Job? = null

    init {
        // Un nombre escrito y no confirmado (la app se cerró a medias): se confirma al volver
        commitPartnerName()
    }

    /**
     * Al terminar de escribir (Hecho o al salir del campo): sus entrenos pasan al nombre nuevo. El nombre se guarda
     * con cada letra (si la app se cierra no se pierde); el historial, solo aquí. Un nombre que ya tiene otra
     * persona en el historial se rechaza: mezclaría los dos para siempre.
     */
    fun commitPartnerName() {
        val s = settings.value
        val old = s.partnerHistoryName
        val new = s.partnerName
        // Sin edición pendiente, o vacío (se queda el de su historial hasta que se escriba uno): nada que confirmar
        if (old == null || new.isBlank()) return
        if (new == old) {
            settingsStore.update { it.copy(partnerHistoryName = null) }
        } else {
            renamePartnerHistory(old, new)
        }
    }

    private fun renamePartnerHistory(old: String, new: String) {
        viewModelScope.launch {
            lastRewrite?.join()
            if (old.isNotBlank() && repo.historyOnce(includePartner = true).any { it.athlete == new }) {
                settingsStore.update { it.copy(partnerName = old, partnerHistoryName = null) }
                _messages.tryEmit(context.getString(R.string.partner_name_taken, new))
                return@launch
            }
            if (old.isNotBlank()) lastRewrite = repo.rewriteHistory({ it.athlete == old }) { it.copy(athlete = new) }
            // Su entreno en curso, también si se empezó sin nombre
            repo.updatePartnerActive { w -> if (w.athlete == old || w.athlete.isNullOrBlank()) w.copy(athlete = new) else w }
            settingsStore.update { if (it.partnerName == new) it.copy(partnerHistoryName = null) else it }
        }
    }

    /** Entrenos borrados en los últimos 30 días. */
    val trash: StateFlow<List<com.bestrong.core.model.TrashedWorkout>> = repo.trash

    fun restoreFromTrash(id: String) = viewModelScope.launch {
        if (restoreOne(id)) _messages.tryEmit(context.getString(R.string.trash_restored))
    }

    /** Todos los de la papelera de vuelta al historial. */
    fun restoreAllFromTrash() = viewModelScope.launch {
        val restored = repo.trash.value.map { it.workout.id }.count { restoreOne(it) }
        if (restored > 0) _messages.tryEmit(context.getString(R.string.trash_restored_n, restored))
    }

    private suspend fun restoreOne(id: String): Boolean {
        val workout = repo.restoreFromTrash(id) ?: return false
        if (workout.athlete == null && settingsStore.settings.value.healthConnectExport) {
            runCatching { healthConnect.export(workout) }.onFailure { android.util.Log.w("Profile", "Entreno no exportado", it) }
        }
        return true
    }

    /** «Borrar para siempre» uno de la papelera, o vaciarla entera (con [id] null). */
    fun purgeTrash(id: String?) = viewModelScope.launch { repo.purgeTrash(id?.let { setOf(it) }) }

    /** Borra un perfil de gimnasio y lo quita de las rutinas que lo tenían como suyo. */
    fun deleteGym(id: String) {
        settingsStore.update { s -> s.copy(gyms = s.gyms.filterNot { it.id == id }) }
        viewModelScope.launch {
            repo.routines.value.filter { it.gymId == id }.forEach { repo.saveRoutine(it.copy(gymId = null), keepPosition = true) }
        }
    }

    /** Carpeta elegida para la copia semanal: guardamos el permiso y hacemos una copia ya. */
    fun setBackupFolder(uri: Uri?) {
        val old = settings.value.backupFolderUri
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        // Primero el ajuste y luego el permiso: una copia en marcha no lo toma por una carpeta perdida
        settingsStore.update {
            it.copy(backupFolderUri = uri?.toString(), backupFolderLost = false, backupFailNotified = false, backupFolderSetEpochMs = if (uri != null) System.currentTimeMillis() else 0)
        }
        // La carpeta anterior (al quitarla o al cambiar a otra) ya no hace falta: los permisos guardados tienen tope
        if (old != null && old != uri?.toString()) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        if (uri != null) {
            AutoBackupWorker.runNow(context)
            _messages.tryEmit(context.getString(R.string.backup_started))
        }
    }

    fun syncWatchNow() = viewModelScope.launch {
        watchPush.pushToWatchNow()
    }
}

enum class ExportRange { ALL, YEAR, DAYS_90, DAYS_30 }

enum class DeleteKind { OLD_HISTORY, MEASUREMENTS, PHOTOS, ALL_HISTORY }

private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024

/** Igual que el mínimo que pide el diálogo de la copia cifrada. */
private const val MIN_EXPORT_PASSWORD = 10
