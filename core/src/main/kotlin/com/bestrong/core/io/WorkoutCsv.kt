package com.bestrong.core.io

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.library.ExerciseLibrary.normalized
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.Exercise
import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.util.WeightUnit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

enum class CsvFormat(val label: String) { BESTRONG("BeStrong"), HEVY("Hevy"), STRONG("Strong"), FITNOTES("FitNotes"), JEFIT("JEFIT") }

data class ImportResult(
    val format: CsvFormat,
    val workouts: List<Workout>,
    /** Ejercicios que no estaban en la biblioteca y se crean como personalizados. */
    val newExercises: List<Exercise>,
    val skippedRows: Int,
)

class CsvImportException(message: String) : Exception(message)

/**
 * Exportación del historial a CSV e importación desde BeStrong, Hevy y Strong.
 * Los ejercicios se emparejan por nombre (sin tildes/mayúsculas) con la biblioteca;
 * los desconocidos se crean como ejercicios personalizados.
 */
object WorkoutCsv {
    private val HEADER = listOf(
        "workout_id", "date", "end_date", "workout_name", "workout_notes", "exercise_id", "exercise_name",
        "superset_id", "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "duration_s", "rpe",
        "reps_right", "distance_m", "assisted", "set_note", "target_rpe", "tempo", "gym", "athlete",
    )
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** Los CSV guardados con Excel en español usan coma decimal («82,5»). */
    private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    /** Metros por unidad de distancia de FitNotes (km, mi, ft, yd; m por defecto). */
    private fun metresPer(unit: String): Double = when (unit.trim().lowercase()) {
        "km", "kms", "kilometres", "kilometers" -> 1000.0
        "mi", "mile", "miles" -> 1609.344
        "ft", "feet", "foot" -> 0.3048
        "yd", "yds", "yard", "yards" -> 0.9144
        else -> 1.0
    }

    // --- Exportar ----------------------------------------------------------------------------

    /** Solo un ejercicio (su historial completo, con el mismo formato que el CSV general). */
    fun exportExercise(workouts: List<Workout>, exerciseId: String, zone: ZoneId = ZoneId.systemDefault()): String =
        export(
            workouts.mapNotNull { w -> w.exercises.filter { it.exercise.id == exerciseId }.takeIf { it.isNotEmpty() }?.let { w.copy(exercises = it) } },
            zone,
        )

    fun export(workouts: List<Workout>, zone: ZoneId = ZoneId.systemDefault()): String {
        val rows = mutableListOf(HEADER)
        workouts.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.forEach { w ->
            val start = ISO.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(w.startedAtEpochMs), zone))
            val end = ISO.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(w.finishedAtEpochMs!!), zone))
            w.exercises.forEach { we ->
                // Solo lo hecho: al importar, cada fila se toma como serie completada
                we.completedSets.forEachIndexed { i, s ->
                    rows += listOf(
                        w.id, start, end, w.name, w.notes, we.exercise.id, we.exercise.name,
                        we.supersetId.orEmpty(), we.notes, i.toString(), s.type.name,
                        s.weightKg.toString(), s.reps.toString(), s.durationSec.toString(), s.rpe?.toString().orEmpty(),
                        s.repsRight?.toString().orEmpty(), s.distanceM.toString(), s.assisted.toString(), s.note,
                        // La prescripción del ejercicio (se repite en cada serie, como sus notas)
                        we.targetRpe?.toString().orEmpty(), we.tempo.orEmpty(),
                        w.gymName.orEmpty(), w.athlete.orEmpty(),
                    )
                }
            }
        }
        return Csv.write(rows)
    }

    // --- Importar ----------------------------------------------------------------------------

    /**
     * @param known ejercicios disponibles (biblioteca + personalizados) para emparejar por nombre.
     * @param strongUnit Strong no indica la unidad: se usa esta.
     */
    fun import(
        text: String,
        known: List<Exercise>,
        zone: ZoneId = ZoneId.systemDefault(),
        strongUnit: WeightUnit = WeightUnit.KG,
    ): ImportResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) throw CsvImportException("El archivo está vacío")
        // JEFIT exporta varias tablas; la de registros tiene las columnas «ename» y «logs»
        val jefitHeader = rows.indexOfFirst { r -> r.map { it.trim().lowercase() }.let { "ename" in it && "logs" in it } }
        if (jefitHeader >= 0) return importJefit(rows.drop(jefitHeader), known, zone, strongUnit)
        val header = rows.first().map { it.trim().lowercase() }
        val format = when {
            "workout_id" in header && "exercise_id" in header -> CsvFormat.BESTRONG
            "exercise_title" in header && "start_time" in header -> CsvFormat.HEVY
            "exercise name" in header && "workout name" in header -> CsvFormat.STRONG
            "exercise" in header && "category" in header && "date" in header -> CsvFormat.FITNOTES
            else -> throw CsvImportException("Formato no reconocido. Se admiten CSV de BeStrong, Hevy, Strong, FitNotes y JEFIT.")
        }
        val col = header.withIndex().associate { (i, h) -> h to i }
        fun List<String>.get(name: String): String = col[name]?.let { getOrNull(it) }?.trim().orEmpty().let(Csv::restoreFormula)

        val resolver = ExerciseResolver(known)
        var skipped = 0
        data class Row(
            val key: String, val name: String, val start: Long, val end: Long?, val notes: String, val exercise: Exercise,
            val exNotes: String, val superset: String?, val set: WorkoutSet,
            val targetRpe: Double? = null, val tempo: String? = null,
            val gym: String? = null, val athlete: String? = null,
            /** En nuestro formato, la serie 0 empieza una entrada nueva aunque repita ejercicio (press pesado y ligero). */
            val startsEntry: Boolean = false,
        )

        val parsed = rows.drop(1).mapNotNull { r ->
            try {
                when (format) {
                    CsvFormat.BESTRONG -> {
                        val ex = resolver.resolve(r.get("exercise_name"), r.get("exercise_id"))
                        Row(
                            startsEntry = r.get("set_index") == "0",
                            key = r.get("workout_id"), name = r.get("workout_name"),
                            start = parseIso(r.get("date"), zone), end = r.get("end_date").takeIf { it.isNotBlank() }?.let { parseIso(it, zone) },
                            notes = r.get("workout_notes"), exercise = ex, exNotes = r.get("exercise_notes"),
                            superset = r.get("superset_id").ifBlank { null },
                            set = WorkoutSet(
                                weightKg = num(r.get("weight_kg")) ?: 0.0,
                                reps = num(r.get("reps"))?.toInt() ?: 0,
                                durationSec = num(r.get("duration_s"))?.toInt() ?: 0,
                                type = runCatching { SetType.valueOf(r.get("set_type")) }.getOrDefault(SetType.NORMAL),
                                rpe = num(r.get("rpe")),
                                completed = true,
                                repsRight = num(r.get("reps_right"))?.toInt(),
                                distanceM = num(r.get("distance_m")) ?: 0.0,
                                assisted = r.get("assisted").equals("true", ignoreCase = true),
                                note = r.get("set_note"),
                            ),
                            // Columnas nuevas: los CSV antiguos no las tienen y quedan vacías
                            targetRpe = num(r.get("target_rpe"))?.takeIf { it in 1.0..10.0 },
                            tempo = r.get("tempo").ifBlank { null }?.let { com.bestrong.core.training.Tempo.parse(it)?.toString() },
                            gym = r.get("gym").ifBlank { null },
                            athlete = r.get("athlete").ifBlank { null },
                        )
                    }
                    CsvFormat.HEVY -> {
                        val start = parseHevy(r.get("start_time"), zone)
                        val ex = resolver.resolve(r.get("exercise_title"))
                        val weightKg = num(r.get("weight_kg")) ?: num(r.get("weight_lbs"))?.let { WeightUnit.LB.toKg(it) } ?: 0.0
                        Row(
                            key = r.get("title") + "|" + r.get("start_time"), name = r.get("title"),
                            start = start, end = r.get("end_time").takeIf { it.isNotBlank() }?.let { parseHevy(it, zone) },
                            notes = r.get("description"), exercise = ex, exNotes = r.get("exercise_notes"),
                            superset = r.get("superset_id").ifBlank { null },
                            set = WorkoutSet(
                                weightKg = weightKg,
                                reps = num(r.get("reps"))?.toInt() ?: 0,
                                durationSec = num(r.get("duration_seconds"))?.toInt() ?: 0,
                                // La distancia del cardio (en km o millas según la cuenta)
                                distanceM = num(r.get("distance_km"))?.times(1000) ?: num(r.get("distance_miles"))?.times(METERS_PER_MILE) ?: 0.0,
                                type = when (r.get("set_type").lowercase()) {
                                    "warmup" -> SetType.WARMUP
                                    "dropset" -> SetType.DROP
                                    "failure" -> SetType.FAILURE
                                    else -> SetType.NORMAL
                                },
                                rpe = num(r.get("rpe")),
                                completed = true,
                            ),
                        )
                    }
                    CsvFormat.FITNOTES -> {
                        // FitNotes: una fila por serie; el entreno es el día
                        val date = r.get("date")
                        val start = parseDay(date, zone)
                        val ex = resolver.resolve(r.get("exercise"))
                        val lbs = r.get("weight unit").lowercase().startsWith("lb") || col.containsKey("weight (lbs)")
                        val w = (r.get("weight").ifBlank { r.get("weight (kgs)") }.ifBlank { r.get("weight (lbs)") }).replace(',', '.').toDoubleOrNull() ?: 0.0
                        Row(
                            key = date, name = "FitNotes $date", start = start, end = null,
                            notes = "", exercise = ex, exNotes = r.get("comment"), superset = null,
                            set = WorkoutSet(
                                weightKg = if (lbs) WeightUnit.LB.toKg(w) else w,
                                reps = num(r.get("reps"))?.toInt() ?: 0,
                                durationSec = parseClock(r.get("time")),
                                distanceM = num(r.get("distance"))?.let { d -> d * metresPer(r.get("distance unit")) } ?: 0.0,
                                completed = true,
                            ),
                        )
                    }
                    CsvFormat.JEFIT -> error("JEFIT se importa aparte")
                    CsvFormat.STRONG -> {
                        val start = parseStrong(r.get("date"), zone)
                        val ex = resolver.resolve(r.get("exercise name"))
                        val order = r.get("set order")
                        // Strong 6 mete filas del temporizador de descanso entre las series: no son series
                        if (order.equals("rest timer", ignoreCase = true)) return@mapNotNull null
                        val durationMin = parseStrongDuration(r.get("duration"))
                        Row(
                            key = r.get("date") + "|" + r.get("workout name"), name = r.get("workout name"),
                            // El formato nuevo trae la duración del entreno en segundos («Duration (sec)»)
                            start = start,
                            end = durationMin?.let { start + it * 60_000L } ?: num(r.get("duration (sec)"))?.takeIf { it > 0 }?.let { start + (it * 1000).toLong() },
                            notes = r.get("workout notes"), exercise = ex, exNotes = r.get("notes"), superset = null,
                            set = WorkoutSet(
                                // Strong antiguo: «Weight» en la unidad elegida; el nuevo: «Weight (kg)» o «Weight (lbs)»
                                weightKg = num(r.get("weight"))?.let(strongUnit::toKg) ?: num(r.get("weight (kg)"))
                                    ?: num(r.get("weight (lbs)"))?.let { WeightUnit.LB.toKg(it) } ?: 0.0,
                                reps = num(r.get("reps"))?.toInt() ?: 0,
                                // «Duration (sec)» es la del entreno entero, no la de la serie
                                durationSec = num(r.get("seconds"))?.toInt() ?: 0,
                                distanceM = num(r.get("distance (meters)")) ?: num(r.get("distance"))?.let { d ->
                                    if (strongUnit == WeightUnit.LB) d * METERS_PER_MILE else d * 1000
                                } ?: 0.0,
                                type = when (order.uppercase()) { "W" -> SetType.WARMUP; "D" -> SetType.DROP; "F" -> SetType.FAILURE; else -> SetType.NORMAL },
                                rpe = num(r.get("rpe")),
                                completed = true,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                skipped++
                null
            }
        }

        val workouts = parsed.groupBy { it.key }.values.map { group ->
            val first = group.first()
            // Ejercicios en orden de aparición; un mismo ejercicio consecutivo se agrupa
            val exercises = mutableListOf<WorkoutExercise>()
            group.forEach { row ->
                val last = exercises.lastOrNull()
                if (last != null && last.exercise.id == row.exercise.id && !row.startsEntry) {
                    exercises[exercises.lastIndex] = last.copy(sets = last.sets + row.set)
                } else {
                    exercises += WorkoutExercise(exercise = row.exercise, sets = listOf(row.set), notes = row.exNotes, supersetId = row.superset, targetRpe = row.targetRpe, tempo = row.tempo)
                }
            }
            val end = first.end ?: (first.start + 60 * 60_000L)
            Workout(
                id = if (format == CsvFormat.BESTRONG) first.key else com.bestrong.core.util.newId(),
                name = first.name.ifBlank { "Entrenamiento importado" },
                startedAtEpochMs = first.start,
                finishedAtEpochMs = end,
                updatedAtEpochMs = end,
                notes = first.notes,
                exercises = exercises,
                gymName = first.gym,
                athlete = first.athlete,
            )
        }.sortedByDescending { it.startedAtEpochMs }

        return ImportResult(format, workouts, resolver.created, skipped)
    }

    /** JEFIT: cada fila es un ejercicio de un día con «logs» = «100x5,100x5,90x8». */
    private fun importJefit(rows: List<List<String>>, known: List<Exercise>, zone: ZoneId, unit: WeightUnit): ImportResult {
        val header = rows.first().map { it.trim().lowercase() }
        val col = header.withIndex().associate { (i, h) -> h to i }
        fun List<String>.get(name: String): String = col[name]?.let { getOrNull(it) }?.trim().orEmpty()
        val resolver = ExerciseResolver(known)
        var skipped = 0
        val byDay = linkedMapOf<String, MutableList<WorkoutExercise>>()
        rows.drop(1).forEach { r ->
            // Fin de la tabla de registros: empieza otra sección
            if (r.firstOrNull()?.startsWith("#") == true) return@forEach
            val date = r.get("mydate")
            val name = r.get("ename")
            val logs = r.get("logs")
            if (date.isBlank() || name.isBlank() || logs.isBlank()) {
                skipped++
                return@forEach
            }
            val sets = logs.split(',').mapNotNull { entry ->
                val parts = entry.trim().lowercase().split('x')
                val w = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val reps = parts.getOrNull(1)?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
                WorkoutSet(weightKg = unit.toKg(w), reps = reps, completed = true)
            }
            if (sets.isEmpty()) {
                skipped++
                return@forEach
            }
            byDay.getOrPut(date) { mutableListOf() } += WorkoutExercise(exercise = resolver.resolve(name), sets = sets)
        }
        val workouts = byDay.mapNotNull { (date, exercises) ->
            val start = runCatching { parseDay(date, zone) }.getOrNull() ?: return@mapNotNull null
            Workout(name = "JEFIT $date", startedAtEpochMs = start, finishedAtEpochMs = start + 3_600_000L, updatedAtEpochMs = start + 3_600_000L, exercises = exercises)
        }.sortedByDescending { it.startedAtEpochMs }
        return ImportResult(CsvFormat.JEFIT, workouts, resolver.created, skipped)
    }

    private fun parseDay(s: String, zone: ZoneId): Long {
        val d = s.trim().take(10)
        return java.time.LocalDate.parse(d).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    }

    /** «0:01:30» o «90» → segundos. */
    private fun parseClock(s: String): Int {
        if (s.isBlank()) return 0
        s.toIntOrNull()?.let { return it }
        val p = s.split(':').mapNotNull { it.trim().toIntOrNull() }
        return p.fold(0) { acc, v -> acc * 60 + v }
    }

    private fun parseIso(s: String, zone: ZoneId): Long =
        LocalDateTime.parse(s, ISO).atZone(zone).toInstant().toEpochMilli()

    private val hevyFormats = listOf("d MMM yyyy, HH:mm", "dd MMM yyyy, HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
        .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private fun parseHevy(s: String, zone: ZoneId): Long {
        hevyFormats.forEach { f ->
            try { return LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
        }
        return Instant.parse(s).toEpochMilli()
    }

    private val strongFormats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm").map { DateTimeFormatter.ofPattern(it) }

    private fun parseStrong(s: String, zone: ZoneId): Long {
        strongFormats.forEach { f ->
            try { return LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli() } catch (_: DateTimeParseException) {}
        }
        throw DateTimeParseException("Fecha no válida", s, 0)
    }

    /** "1h 5m", "45m", "3600" (s) → minutos. */
    private fun parseStrongDuration(s: String): Long? {
        if (s.isBlank()) return null
        s.toLongOrNull()?.let { return it / 60 }
        val h = Regex("(\\d+)\\s*h").find(s)?.groupValues?.get(1)?.toLong() ?: 0
        val m = Regex("(\\d+)\\s*m").find(s)?.groupValues?.get(1)?.toLong() ?: 0
        return (h * 60 + m).takeIf { it > 0 }
    }

    /** Empareja nombres de otras apps con la biblioteca. */
    private class ExerciseResolver(known: List<Exercise>) {
        private val byId = known.associateBy { it.id }
        private val byName = (known + ExerciseLibrary.all).associateBy { it.name.normalized() }
        private val aliases = ExerciseLibrary.englishAliases.mapKeys { it.key.normalized() }
        val created = mutableListOf<Exercise>()

        fun resolve(name: String, id: String = ""): Exercise {
            if (id.isNotBlank()) byId[id]?.let { return it }
            if (name.isBlank()) throw IllegalArgumentException("Ejercicio sin nombre")
            val n = name.normalized()
            byName[n]?.let { return it }
            // Hevy/Strong: "Bench Press (Barbell)" → alias
            val base = n.substringBefore(" (").trim()
            (aliases[n] ?: aliases[base])?.let { aliasId -> ExerciseLibrary.byId(aliasId)?.let { return it } }
            created.firstOrNull { it.name.normalized() == n }?.let { return it }
            val equipment = when {
                "barbell" in n -> Equipment.BARBELL
                "dumbbell" in n -> Equipment.DUMBBELL
                "cable" in n -> Equipment.CABLE
                "machine" in n -> Equipment.MACHINE
                "bodyweight" in n -> Equipment.BODYWEIGHT
                "kettlebell" in n -> Equipment.KETTLEBELL
                else -> Equipment.OTHER
            }
            val ex = Exercise(
                // Con nombres en otro alfabeto (ruso, chino…) el texto queda vacío: se añade un resumen del nombre para
                // que dos ejercicios distintos no compartan id
                id = "custom_import_" + n.replace(Regex("[^a-z0-9]+"), "_").trim('_').let { slug ->
                    if (slug.isEmpty() || slug.length < n.count { it.isLetterOrDigit() }) slug.ifEmpty { "x" } + "_" + n.hashCode().toUInt().toString(16) else slug
                },
                name = name.trim(),
                primaryMuscle = MuscleGroup.FULL_BODY,
                equipment = equipment,
                tracking = TrackingType.WEIGHT_REPS,
                isCustom = true,
            )
            created += ex
            return ex
        }
    }
}

/** Metros en una milla. */
private const val METERS_PER_MILE = 1609.344
