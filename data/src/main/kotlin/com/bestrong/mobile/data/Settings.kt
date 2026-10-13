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
    /** Sesiones por semana que el usuario se propone (si no sigue un programa). */
    val weeklyGoal: Int = 3,
    /** Botones principales abajo en la pantalla exterior del Fold (uso con una mano). */
    val oneHanded: Boolean = false,
    /** Perfiles de gimnasio con su material; el activo condiciona pesos, discos y alternativas. */
    val gyms: List<GymProfile> = listOf(GymProfile.gym(), GymProfile.home()),
    val activeGymId: String? = null,
    /** Ejercicios marcados como favoritos. */
    val favoriteExercises: Set<String> = emptySet(),
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
    /** Tema, acento, AMOLED, alto contraste, modo daltónico y tipo de letra. */
    val appearance: Appearance = Appearance(),
    /** Vibración al acabar el descanso (también en el reloj). */
    val restVibration: VibrationStyle = VibrationStyle.TRIPLE,
    /** Unidades mixtas: peso corporal en otra unidad que las cargas (null = la misma). */
    val bodyUnit: WeightUnit? = null,
    /** Columnas visibles en la tabla de series. */
    val showPreviousColumn: Boolean = true,
    val showComparison: Boolean = true,
    /** Tutorial inicial visto, última versión cuyas novedades se mostraron y consejos ya descartados. */
    val tutorialDone: Boolean = false,
    val lastSeenVersion: Int = 0,
    val dismissedTips: Set<String> = emptySet(),
    /** Incremento propio por ejercicio en kg (mancuernas de 2 kg, máquinas de 5 kg, microdiscos…). */
    val exerciseIncrementKg: Map<String, Double> = emptyMap(),
) {
    val activeGym: GymProfile get() = gyms.firstOrNull { it.id == activeGymId } ?: gyms.first()

    /** Salto de peso de ese ejercicio: el suyo propio o el de la unidad (2,5 kg / 5 lb). */
    fun stepKgFor(exerciseId: String): Double = exerciseIncrementKg[exerciseId] ?: unit.stepKg()
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Si algún valor guardado no se puede leer, mejor los ajustes por defecto que no arrancar
    private val _settings = MutableStateFlow(runCatching { read() }.getOrElse { UserSettings() })
    val settings: StateFlow<UserSettings> = _settings

    private fun read() = UserSettings(
        defaultRestSeconds = prefs.getInt("rest", 120),
        healthConnectExport = prefs.getBoolean("hc", false),
        vibrateOnRestEnd = prefs.getBoolean("vibrate", true),
        keepScreenOn = prefs.getBoolean("screen_on", true),
        unit = runCatching { WeightUnit.valueOf(prefs.getString("unit", null) ?: "KG") }.getOrDefault(WeightUnit.KG),
        progressionEnabled = prefs.getBoolean("progression", true),
        reminderDays = prefs.getStringSet("reminder_days", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        reminderHour = prefs.getInt("reminder_hour", 18),
        reminderMinute = prefs.getInt("reminder_minute", 0),
        backupFolderUri = prefs.getString("backup_folder", null),
        lastBackupEpochMs = prefs.getLong("backup_last", 0),
        weeklyGoal = prefs.getInt("weekly_goal", 3),
        oneHanded = prefs.getBoolean("one_handed", false),
        gyms = prefs.getString("gyms", null)
            ?.let { runCatching { SyncContract.json.decodeFromString(ListSerializer(GymProfile.serializer()), it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(GymProfile.gym(), GymProfile.home()),
        activeGymId = prefs.getString("active_gym", null),
        favoriteExercises = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet(),
        dumbbellTotal = prefs.getBoolean("dumbbell_total", false),
        lockProgressPhotos = prefs.getBoolean("lock_photos", true),
        volumeKeys = prefs.getBoolean("volume_keys", false),
        mediaControls = prefs.getBoolean("media_controls", true),
        voiceAnnouncements = prefs.getBoolean("voice_announcements", false),
        musicApp = prefs.getString("music_app", null),
        birthYear = prefs.getInt("birth_year", 0).takeIf { it > 0 },
        heightCm = prefs.getInt("height_cm", 0).takeIf { it > 0 },
        partnerName = prefs.getString("partner_name", "").orEmpty(),
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
        showPreviousColumn = prefs.getBoolean("col_previous", true),
        showComparison = prefs.getBoolean("col_comparison", true),
        tutorialDone = prefs.getBoolean("tutorial_done", false),
        lastSeenVersion = prefs.getInt("last_seen_version", 0),
        dismissedTips = prefs.getStringSet("dismissed_tips", emptySet()).orEmpty().toSet(),
        exerciseIncrementKg = prefs.getString("exercise_increments", null)
            ?.let { runCatching { SyncContract.json.decodeFromString(INCREMENTS, it) }.getOrNull() }
            .orEmpty(),
    )

    private inline fun <reified E : Enum<E>> enumOr(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

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
            putString("backup_folder", s.backupFolderUri)
            putLong("backup_last", s.lastBackupEpochMs)
            putInt("weekly_goal", s.weeklyGoal)
            putBoolean("one_handed", s.oneHanded)
            putString("gyms", SyncContract.json.encodeToString(ListSerializer(GymProfile.serializer()), s.gyms))
            putString("active_gym", s.activeGymId)
            putStringSet("favorites", s.favoriteExercises)
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
            putString("theme_mode", s.appearance.mode.name)
            putString("accent", s.appearance.accent.name)
            putBoolean("amoled", s.appearance.amoled)
            putBoolean("high_contrast", s.appearance.highContrast)
            putBoolean("colorblind", s.appearance.colorblind)
            putString("font", s.appearance.font.name)
            putString("rest_vibration", s.restVibration.name)
            putString("body_unit", s.bodyUnit?.name)
            putBoolean("col_previous", s.showPreviousColumn)
            putBoolean("col_comparison", s.showComparison)
            putBoolean("tutorial_done", s.tutorialDone)
            putInt("last_seen_version", s.lastSeenVersion)
            putStringSet("dismissed_tips", s.dismissedTips)
            putString("exercise_increments", SyncContract.json.encodeToString(INCREMENTS, s.exerciseIncrementKg))
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
        val current = prefs.all
        prefs.edit {
            json.filterKeys { it !in DEVICE_KEYS && it in current }.forEach { (k, v) -> putTyped(k, current[k], v) }
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

    private companion object {
        // Del móvil y no de la copia: la carpeta, y el bloqueo de fotos (quitarlo exige la huella)
        val DEVICE_KEYS = setOf("backup_folder", "backup_last", "lock_photos")
        val INCREMENTS = MapSerializer(String.serializer(), Double.serializer())
    }
}
