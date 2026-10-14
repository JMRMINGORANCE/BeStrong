package com.bestrong.mobile.data

import android.content.Context
import androidx.core.content.edit
import com.bestrong.core.stats.Sex
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.training.GymProfile
import com.bestrong.core.util.VibrationStyle
import com.bestrong.core.util.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

data class UserSettings(
    val defaultRestSeconds: Int = 120,
    val healthConnectExport: Boolean = false,
    val vibrateOnRestEnd: Boolean = true,
    val keepScreenOn: Boolean = true,
    val unit: WeightUnit = WeightUnit.KG,
    /** Rellenar pesos con la sugerencia de sobrecarga progresiva al empezar. */
    val progressionEnabled: Boolean = true,
    /** Días de la semana con recordatorio (1 = lunes … 7 = domingo). */
    val reminderDays: Set<Int> = emptySet(),
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    /** Carpeta (SAF) para la copia de seguridad semanal; null = desactivada. */
    val backupFolderUri: String? = null,
    val lastBackupEpochMs: Long = 0,
    /** La carpeta de la copia automática dejó de estar disponible y se desactivó: se avisa hasta elegir otra. */
    val backupFolderLost: Boolean = false,
    /** Ya se avisó de que la copia lleva tiempo fallando (no se repite cada semana; se borra al salir bien). */
    val backupFailNotified: Boolean = false,
    /** Cuándo se eligió la carpeta de copias (para avisar si nunca llega a salir bien ninguna). */
    val backupFolderSetEpochMs: Long = 0,
    /** Sesiones por semana que el usuario se propone (si no sigue un programa). */
    val weeklyGoal: Int = 3,
    /** Botones principales abajo en la pantalla exterior del Fold (uso con una mano). */
    val oneHanded: Boolean = false,
    /** Perfiles de gimnasio con su material; el activo condiciona pesos, discos y alternativas. */
    val gyms: List<GymProfile> = listOf(GymProfile.gym(), GymProfile.home()),
    val activeGymId: String? = null,
    /** Ejercicios marcados como favoritos. */
    val favoriteExercises: Set<String> = emptySet(),
    /** Ejercicios que no se quieren ver en la biblioteca ni en el selector (se pueden volver a mostrar). */
    val hiddenExercises: Set<String> = emptySet(),
    /** En ejercicios con mancuernas se anota la suma de las dos (se guarda por mancuerna). */
    val dumbbellTotal: Boolean = false,
    /** Para niveles de fuerza y puntuaciones (DOTS, Wilks, IPF GL). */
    val sex: Sex = Sex.MALE,
    /** Pedir huella/PIN para ver las fotos de progreso. */
    val lockProgressPhotos: Boolean = true,
    /** Botones de volumen: subir completa serie, bajar salta el descanso. */
    val volumeKeys: Boolean = false,
    val mediaControls: Boolean = true,
    val voiceAnnouncements: Boolean = false,
    /** App de música que abre el botón del entreno (paquete; null = la primera instalada). */
    val musicApp: String? = null,
    /** Para calcular calorías (metabolismo basal e intensidad): año de nacimiento y altura. */
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    /** Modo pareja: nombre de la otra persona (vacío = desactivado). */
    val partnerName: String = "",
    /**
     * Nombre con el que están guardados los entrenos de la pareja (el de [partnerName] mientras se escribe aún no):
     * al confirmar un nombre nuevo se renombra su historial desde este. null = el mismo que [partnerName].
     */
    val partnerHistoryName: String? = null,
    /** Tema, acento, AMOLED, alto contraste, modo daltónico y tipo de letra. */
    val appearance: Appearance = Appearance(),
    /** Vibración al acabar el descanso (también en el reloj). */
    val restVibration: VibrationStyle = VibrationStyle.TRIPLE,
    /** Unidades mixtas: peso corporal en otra unidad que las cargas (null = la misma). */
    val bodyUnit: WeightUnit? = null,
    /** Peso corporal objetivo (kg); sirve para bajar o para subir. */
    val bodyWeightGoalKg: Double? = null,
    /** Peso que tenías al ponerte el objetivo: marca si es para bajar o para subir. */
    val bodyWeightGoalStartKg: Double? = null,
    /** Fecha para la que se quiere llegar al peso objetivo (opcional). */
    val bodyWeightGoalDateMs: Long? = null,
    /** Kilómetros de cardio por semana que se buscan (Inicio); null = sin objetivo. */
    val weeklyCardioKm: Double? = null,
    /** O minutos de cardio por semana (elíptica, bici sin distancia…; la OMS recomienda 150); solo uno de los dos. */
    val weeklyCardioMin: Int? = null,
    /** Cintura objetivo (cm); null = sin objetivo. */
    val waistGoalCm: Double? = null,
    /** Series semanales por músculo que se quiere alcanzar como mínimo (mapa, Inicio, widget). */
    val weeklySetsTarget: Int = 10,
    /** Día de la semana (1 = lunes … 7) para recordar pesarse; null = sin recordatorio. A las [WEIGH_IN_HOUR]. */
    val weighInDay: Int? = null,
    /** Recordatorio mensual (día 1) de tomarse las medidas con cinta. */
    val measureReminder: Boolean = false,
    /** Columnas visibles en la tabla de series. */
    val showPreviousColumn: Boolean = true,
    val showComparison: Boolean = true,
    /** Tutorial inicial visto, última versión cuyas novedades se mostraron y consejos ya descartados. */
    val tutorialDone: Boolean = false,
    val lastSeenVersion: Int = 0,
    val dismissedTips: Set<String> = emptySet(),
    /** Incremento propio por ejercicio en kg (mancuernas de 2 kg, máquinas de 5 kg, microdiscos…). */
    val exerciseIncrementKg: Map<String, Double> = emptyMap(),
    /** Barra propia de un ejercicio (EZ de 10 kg, hexagonal de 25…); si no, la del gimnasio. */
    val exerciseBarKg: Map<String, Double> = emptyMap(),
    /** Rango de repeticiones por ejercicio para la doble progresión («8-12»). */
    val exerciseRepRange: Map<String, String> = emptyMap(),
    /**
     * Objetivo que el usuario se pone en un ejercicio: 1RM estimado en kg («press banca a 100»), o
     * repeticiones / segundos en los ejercicios sin peso o de tiempo (ver ExerciseGoals).
     */
    val exerciseGoalsKg: Map<String, Double> = emptyMap(),
    /** Descanso tras una serie de calentamiento (no hace falta el de una serie efectiva). */
    val warmupRestSeconds: Int = 60,
) {
    val activeGym: GymProfile get() = gyms.firstOrNull { it.id == activeGymId } ?: gyms.first()

    /** El nombre de la pareja en su historial (el confirmado; mientras se escribe uno nuevo, el de antes). */
    val partnerAthlete: String get() = partnerHistoryName ?: partnerName

    /** Con el perfil de casa (sin barra) o el de gimnasio como activo, según dónde se entrena (configuración inicial). */
    fun trainingAt(home: Boolean): UserSettings {
        fun isHome(g: GymProfile) = com.bestrong.core.model.Equipment.BARBELL !in g.equipment
        // Si el activo ya es de ese tipo se respeta (repetir el tutorial no cambia tu «gimnasio del trabajo»)
        val match = gyms.firstOrNull { isHome(it) == home }?.takeIf { isHome(activeGym) != home }
        return if (match == null) this else copy(activeGymId = match.id)
    }

    /** Salto de peso de ese ejercicio: el suyo propio o el de la unidad (2,5 kg / 5 lb). */
    fun stepKgFor(exerciseId: String): Double = exerciseIncrementKg[exerciseId] ?: unit.stepKg()

    /** Rango de repeticiones de ese ejercicio, si tiene (para la progresión). */
    fun repRangeFor(exerciseId: String): IntRange? = com.bestrong.core.training.Progression.parseRepRange(exerciseRepRange[exerciseId])

    /** El gimnasio activo con la barra de ese ejercicio (para discos y calentamiento). */
    fun gymFor(exerciseId: String): GymProfile = exerciseBarKg[exerciseId]?.let { activeGym.copy(barKg = it) } ?: activeGym
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Si algún valor guardado no se puede leer, mejor los ajustes por defecto que no arrancar
    private val _settings = MutableStateFlow(runCatching { read() }.getOrElse { UserSettings() })

    val settings: StateFlow<UserSettings> = _settings

    init {
        // Carpeta de copias elegida antes de guardar cuándo: se cuenta desde hoy (si no, nunca avisaría)
        if (_settings.value.backupFolderUri != null && _settings.value.backupFolderSetEpochMs == 0L) {
            update { it.copy(backupFolderSetEpochMs = System.currentTimeMillis()) }
        }
    }

    /** Perfiles de gimnasio guardados; si no hay (o no se leen), el de gimnasio y el de casa por defecto. */
    private fun readGyms(): List<GymProfile> = prefs.getString("gyms", null)
        ?.let { runCatching { SyncContract.json.decodeFromString(ListSerializer(GymProfile.serializer()), it) }.getOrNull() }
        ?.takeIf { it.isNotEmpty() }
        ?: listOf(GymProfile.gym(), GymProfile.home())

    private fun read() = readBackupState(readBase())

    /** Estado de la copia automática (propio de este móvil), aparte para que la lectura no sea un bloque enorme. */
    private fun readBackupState(s: UserSettings) = s.copy(
        backupFolderUri = prefs.getString("backup_folder", null),
        lastBackupEpochMs = prefs.getLong("backup_last", 0),
        backupFolderLost = prefs.getBoolean("backup_folder_lost", false),
        backupFailNotified = prefs.getBoolean("backup_fail_notified", false),
        backupFolderSetEpochMs = prefs.getLong("backup_folder_set", 0),
    )

    private fun readBase() = UserSettings(
        defaultRestSeconds = prefs.getInt("rest", 120),
        healthConnectExport = prefs.getBoolean("hc", false),
        vibrateOnRestEnd = prefs.getBoolean("vibrate", true),
        keepScreenOn = prefs.getBoolean("screen_on", true),
        unit = runCatching { WeightUnit.valueOf(prefs.getString("unit", null) ?: "KG") }.getOrDefault(WeightUnit.KG),
        progressionEnabled = prefs.getBoolean("progression", true),
        reminderDays = prefs.getStringSet("reminder_days", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        reminderHour = prefs.getInt("reminder_hour", 18),
        reminderMinute = prefs.getInt("reminder_minute", 0),
        weeklyGoal = prefs.getInt("weekly_goal", 3),
        oneHanded = prefs.getBoolean("one_handed", false),
        gyms = readGyms(),
        activeGymId = prefs.getString("active_gym", null),
        favoriteExercises = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet(),
        hiddenExercises = prefs.getStringSet("hidden_exercises", emptySet()).orEmpty().toSet(),
        dumbbellTotal = prefs.getBoolean("dumbbell_total", false),
        lockProgressPhotos = prefs.getBoolean("lock_photos", true),
        volumeKeys = prefs.getBoolean("volume_keys", false),
        mediaControls = prefs.getBoolean("media_controls", true),
        voiceAnnouncements = prefs.getBoolean("voice_announcements", false),
        musicApp = prefs.getString("music_app", null),
        birthYear = prefs.getInt("birth_year", 0).takeIf { it > 0 },
        heightCm = prefs.getInt("height_cm", 0).takeIf { it > 0 },
        partnerName = prefs.getString("partner_name", "").orEmpty(),
        partnerHistoryName = prefs.getString("partner_history_name", null),
        sex = runCatching { Sex.valueOf(prefs.getString("sex", null) ?: "MALE") }.getOrDefault(Sex.MALE),
        appearance = Appearance(
            mode = enumOr("theme_mode", ThemeMode.SYSTEM),
            accent = enumOr("accent", Accent.ORANGE),
            amoled = prefs.getBoolean("amoled", false),
            highContrast = prefs.getBoolean("high_contrast", false),
            colorblind = prefs.getBoolean("colorblind", false),
            font = enumOr("font", AppFont.SYSTEM),
        ),
        restVibration = enumOr("rest_vibration", VibrationStyle.TRIPLE),
        bodyUnit = prefs.getString("body_unit", null)?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() },
        bodyWeightGoalKg = prefs.getString("body_weight_goal", null)?.toDoubleOrNull()?.takeIf { it > 0 },
        bodyWeightGoalStartKg = prefs.getString("body_weight_goal_start", null)?.toDoubleOrNull()?.takeIf { it > 0 },
        bodyWeightGoalDateMs = prefs.getString("body_weight_goal_date", null)?.toLongOrNull(),
        waistGoalCm = prefs.getString("waist_goal", null)?.toDoubleOrNull()?.takeIf { it > 0 },
        weeklyCardioKm = prefs.getString("weekly_cardio_km", null)?.toDoubleOrNull()?.takeIf { it > 0 },
        weeklyCardioMin = prefs.getString("weekly_cardio_min", null)?.toIntOrNull()?.takeIf { it > 0 },
        weeklySetsTarget = prefs.getInt("weekly_sets_target", 10).coerceIn(1, 30),
        weighInDay = prefs.getInt("weigh_in_day", 0).takeIf { it in 1..7 },
        measureReminder = prefs.getBoolean("measure_reminder", false),
        showPreviousColumn = prefs.getBoolean("col_previous", true),
        showComparison = prefs.getBoolean("col_comparison", true),
        tutorialDone = prefs.getBoolean("tutorial_done", false),
        lastSeenVersion = prefs.getInt("last_seen_version", 0),
        dismissedTips = prefs.getStringSet("dismissed_tips", emptySet()).orEmpty().toSet(),
        warmupRestSeconds = prefs.getInt("warmup_rest", 60),
        exerciseIncrementKg = prefs.decodeMap("exercise_increments", INCREMENTS),
        exerciseRepRange = prefs.decodeMap("exercise_rep_ranges", REP_RANGES),
        exerciseBarKg = prefs.decodeMap("exercise_bars", INCREMENTS),
        exerciseGoalsKg = prefs.decodeMap("exercise_goals", INCREMENTS),
    )

    private inline fun <reified E : Enum<E>> enumOr(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    /** Objetivos de peso, cintura y cardio (los vacíos no quedan guardados). */
    private fun android.content.SharedPreferences.Editor.putGoals(s: UserSettings) {
        putString("body_unit", s.bodyUnit?.name)
        putString("body_weight_goal", s.bodyWeightGoalKg?.toString())
        putString("body_weight_goal_start", s.bodyWeightGoalStartKg?.toString())
        putString("body_weight_goal_date", s.bodyWeightGoalDateMs?.toString())
        putString("waist_goal", s.waistGoalCm?.toString())
        putString("weekly_cardio_km", s.weeklyCardioKm?.toString())
        putString("weekly_cardio_min", s.weeklyCardioMin?.toString())
    }

    /** Estado de la copia automática (propio de este móvil: no viaja en las copias). */
    private fun android.content.SharedPreferences.Editor.putBackupState(s: UserSettings) {
        putString("backup_folder", s.backupFolderUri)
        putLong("backup_last", s.lastBackupEpochMs)
        putBoolean("backup_folder_lost", s.backupFolderLost)
        putBoolean("backup_fail_notified", s.backupFailNotified)
        putLong("backup_folder_set", s.backupFolderSetEpochMs)
    }

    @Synchronized
    fun update(block: (UserSettings) -> UserSettings) {
        val s = block(_settings.value)
        prefs.edit {
            putInt("rest", s.defaultRestSeconds)
            putBoolean("hc", s.healthConnectExport)
            putBoolean("vibrate", s.vibrateOnRestEnd)
            putBoolean("screen_on", s.keepScreenOn)
            putString("unit", s.unit.name)
            putBoolean("progression", s.progressionEnabled)
            putStringSet("reminder_days", s.reminderDays.map { it.toString() }.toSet())
            putInt("reminder_hour", s.reminderHour)
            putInt("reminder_minute", s.reminderMinute)
            putBackupState(s)
            putInt("weekly_goal", s.weeklyGoal)
            putBoolean("one_handed", s.oneHanded)
            putString("gyms", SyncContract.json.encodeToString(ListSerializer(GymProfile.serializer()), s.gyms))
            putString("active_gym", s.activeGymId)
            putStringSet("favorites", s.favoriteExercises)
            putStringSet("hidden_exercises", s.hiddenExercises)
            putBoolean("dumbbell_total", s.dumbbellTotal)
            putString("sex", s.sex.name)
            putBoolean("lock_photos", s.lockProgressPhotos)
            putBoolean("volume_keys", s.volumeKeys)
            putBoolean("media_controls", s.mediaControls)
            putBoolean("voice_announcements", s.voiceAnnouncements)
            putString("music_app", s.musicApp)
            putInt("birth_year", s.birthYear ?: 0)
            putInt("height_cm", s.heightCm ?: 0)
            putString("partner_name", s.partnerName)
            putString("partner_history_name", s.partnerHistoryName)
            putString("theme_mode", s.appearance.mode.name)
            putString("accent", s.appearance.accent.name)
            putBoolean("amoled", s.appearance.amoled)
            putBoolean("high_contrast", s.appearance.highContrast)
            putBoolean("colorblind", s.appearance.colorblind)
            putString("font", s.appearance.font.name)
            putString("rest_vibration", s.restVibration.name)
            putGoals(s)
            putInt("weekly_sets_target", s.weeklySetsTarget)
            putInt("weigh_in_day", s.weighInDay ?: 0)
            putBoolean("measure_reminder", s.measureReminder)
            putBoolean("col_previous", s.showPreviousColumn)
            putBoolean("col_comparison", s.showComparison)
            putBoolean("tutorial_done", s.tutorialDone)
            putInt("last_seen_version", s.lastSeenVersion)
            putStringSet("dismissed_tips", s.dismissedTips)
            putString("exercise_increments", SyncContract.json.encodeToString(INCREMENTS, s.exerciseIncrementKg))
            putString("exercise_goals", SyncContract.json.encodeToString(INCREMENTS, s.exerciseGoalsKg))
            putString("exercise_bars", SyncContract.json.encodeToString(INCREMENTS, s.exerciseBarKg))
            putString("exercise_rep_ranges", SyncContract.json.encodeToString(REP_RANGES, s.exerciseRepRange))
            putInt("warmup_rest", s.warmupRestSeconds)
        }
        _settings.value = s
    }

    /** Ajustes para la copia de seguridad (sin la carpeta de copias, que es de este móvil). */
    fun backupJson(): JsonObject = JsonObject(
        prefs.all.filterKeys { it !in DEVICE_KEYS }.mapNotNull { (k, v) ->
            when (v) {
                is Boolean -> k to JsonPrimitive(v)
                is Number -> k to JsonPrimitive(v)
                is String -> k to JsonPrimitive(v)
                is Set<*> -> k to JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> null
            }
        }.toMap(),
    )

    /**
     * Restaura los ajustes de una copia (los que falten se quedan como están). Solo claves conocidas y
     * con el tipo que espera la app: un valor de otro tipo (copia dañada o ajena) haría fallar cada arranque.
     */
    @Synchronized
    fun restoreBackup(json: JsonObject) {
        // Se escriben antes todos los ajustes actuales: así cada clave conocida tiene su tipo en prefs
        update { it }
        // Los textos opcionales vacíos no quedan guardados (putString null los borra): también son texto
        val current = NULLABLE_STRING_KEYS.associateWith { "" } + prefs.all
        prefs.edit {
            json.filterKeys { it !in DEVICE_KEYS && it in current }.forEach { (k, v) -> putTyped(k, current[k], v) }
            // El objetivo de cardio es uno (en km o en minutos): si la copia trae uno, el otro de ahora sobra
            if (CARDIO_GOAL_KEYS.any { it in json }) CARDIO_GOAL_KEYS.filterNot { it in json }.forEach { remove(it) }
        }
        _settings.value = runCatching { read() }.getOrDefault(_settings.value)
    }

    /** Escribe [v] solo si es del mismo tipo que el valor actual [expected]; si no, se ignora. */
    private fun android.content.SharedPreferences.Editor.putTyped(k: String, expected: Any?, v: kotlinx.serialization.json.JsonElement) {
        val p = v as? JsonPrimitive
        when {
            expected is Set<*> && v is JsonArray -> putStringSet(k, v.mapNotNull { (it as? JsonPrimitive)?.content }.toSet())
            p == null -> Unit
            p.isString -> if (expected is String) putString(k, p.content)
            expected is Boolean -> p.booleanOrNull?.let { putBoolean(k, it) }
            expected is Int -> p.longOrNull?.let { putInt(k, it.toInt()) }
            expected is Long -> p.longOrNull?.let { putLong(k, it) }
        }
    }

    /** Un mapa guardado como JSON (vacío si falta o está dañado). */
    private fun <V> android.content.SharedPreferences.decodeMap(key: String, serializer: kotlinx.serialization.KSerializer<Map<String, V>>): Map<String, V> =
        getString(key, null)?.let { runCatching { SyncContract.json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        // Del móvil y no de la copia: la carpeta, y el bloqueo de fotos (quitarlo exige la huella)
        val DEVICE_KEYS = setOf("backup_folder", "backup_last", "backup_folder_lost", "backup_fail_notified", "backup_folder_set", "lock_photos")
        val NULLABLE_STRING_KEYS = setOf("active_gym", "music_app", "body_unit", "exercise_increments", "exercise_goals", "exercise_bars", "exercise_rep_ranges", "body_weight_goal", "body_weight_goal_start", "body_weight_goal_date", "waist_goal", "weekly_cardio_km", "weekly_cardio_min", "partner_history_name")
        val CARDIO_GOAL_KEYS = setOf("weekly_cardio_km", "weekly_cardio_min")
        val INCREMENTS = MapSerializer(String.serializer(), Double.serializer())
        val REP_RANGES = MapSerializer(String.serializer(), String.serializer())
    }
}

/** Hora del recordatorio de pesarse (por la mañana, en ayunas). */
const val WEIGH_IN_HOUR = 8
