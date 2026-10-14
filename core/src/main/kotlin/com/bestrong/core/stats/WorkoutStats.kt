package com.bestrong.core.stats

import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class RecordType(val label: String) {
    HEAVIEST_WEIGHT("Peso máximo"),
    BEST_ONE_REP_MAX("Mejor 1RM estimado"),
    BEST_SET_VOLUME("Mejor volumen en una serie"),
    MOST_REPS("Más repeticiones"),
}

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val type: RecordType,
    val value: Double,
    val workoutId: String,
    val dateEpochMs: Long,
)

data class ExerciseProgressPoint(
    val dateEpochMs: Long,
    val bestOneRepMax: Double,
    val heaviestWeight: Double,
    val volume: Double,
    val bestReps: Int,
    /** Cardio: metros y segundos de la sesión. */
    val distanceM: Double = 0.0,
    val durationSec: Int = 0,
)

data class WeekBucket(val weekStart: LocalDate, val workouts: Int, val volume: Double, val durationSec: Long)

enum class VolumeStatus { NONE, LOW, OPTIMAL, HIGH }

/** Series semanales recomendadas por músculo (rango habitual en hipertrofia: 10–20). */
object MuscleTargets {
    const val MIN_WEEKLY_SETS = 10.0
    const val MAX_WEEKLY_SETS = 20.0

    /** [minSets]: el mínimo semanal que se pone el usuario (10 por defecto). */
    fun status(sets: Double, minSets: Double = MIN_WEEKLY_SETS): VolumeStatus = when {
        sets <= 0.0 -> VolumeStatus.NONE
        sets < minSets -> VolumeStatus.LOW
        sets <= MAX_WEEKLY_SETS -> VolumeStatus.OPTIMAL
        else -> VolumeStatus.HIGH
    }
}

/** Cálculos sobre el historial. Todas las funciones son puras: [history] = entrenamientos terminados. */
object WorkoutStats {
    /**
     * Sesiones seguidas sin superar el mejor 1RM estimado (las últimas, desde la del récord); 0 si la última fue
     * récord o hay pocas sesiones (menos de seis) para hablar de estancamiento.
     */
    fun sessionsSinceBest(progress: List<ExerciseProgressPoint>): Int {
        // Las últimas sesiones (un récord de hace años, antes de un parón, no cuenta) desde la primera vez que se
        // llegó a lo mejor: igualarlo (100×5 cada semana) también es estar estancado, como en Progression.isStalled
        val points = progress.filter { it.bestOneRepMax > 0 }.takeLast(PLATEAU_WINDOW)
        if (points.size < PLATEAU_MIN_HISTORY) return 0
        val top = points.maxOf { it.bestOneRepMax }
        return points.lastIndex - points.indexOfFirst { it.bestOneRepMax >= top - 1e-6 }
    }

    private const val PLATEAU_MIN_HISTORY = 6
    private const val PLATEAU_WINDOW = 12

    private fun Workout.finishedAt(): Long = finishedAtEpochMs ?: startedAtEpochMs

    /** Series de la última vez que se hizo el ejercicio (columna "Anterior" de Hevy). */
    fun previousSets(exerciseId: String, history: List<Workout>, excludeWorkoutId: String? = null): List<WorkoutSet> =
        history.asSequence()
            .filter { it.id != excludeWorkoutId && it.isFinished }
            .sortedByDescending { it.startedAtEpochMs }
            .mapNotNull { w -> w.exercises.firstOrNull { it.exercise.id == exerciseId }?.completedSets?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
            .orEmpty()

    /** El ejercicio tal como se hizo la última vez (con series completadas): su RPE objetivo, notas… */
    fun previousExercise(exerciseId: String, history: List<Workout>): WorkoutExercise? =
        history.asSequence()
            .filter { it.isFinished }
            .sortedByDescending { it.startedAtEpochMs }
            .mapNotNull { w -> w.exercises.firstOrNull { it.exercise.id == exerciseId }?.takeIf { it.completedSets.isNotEmpty() } }
            .firstOrNull()

    /**
     * [previousSets] de todos los ejercicios a la vez: id de ejercicio → series completadas de la
     * última sesión en que se hizo. Una sola pasada por el historial.
     */
    fun lastSetsByExercise(history: List<Workout>): Map<String, List<WorkoutSet>> {
        val result = HashMap<String, List<WorkoutSet>>()
        history.asSequence().filter { it.isFinished }.sortedByDescending { it.startedAtEpochMs }
            // Como previousSets: cuenta la primera aparición del ejercicio en cada entreno
            .flatMap { w -> w.exercises.distinctBy { it.exercise.id } }
            .forEach { we -> we.completedSets.takeIf { it.isNotEmpty() }?.let { result.putIfAbsent(we.exercise.id, it) } }
        return result
    }

    private fun recordValue(type: RecordType, set: WorkoutSet): Double = when (type) {
        RecordType.HEAVIEST_WEIGHT -> set.weightKg
        RecordType.BEST_ONE_REP_MAX -> set.estimatedOneRepMax
        RecordType.BEST_SET_VOLUME -> set.volume
        RecordType.MOST_REPS -> set.reps.toDouble()
    }

    /**
     * Variantes enlazadas: trata todas las variantes de [family] (p. ej. press banca, inclinado,
     * con mancuernas) como si fueran [asExerciseId], para ver historial y récords juntos.
     */
    fun mergeVariants(history: List<Workout>, asExerciseId: String, family: Collection<String>): List<Workout> {
        if (family.size <= 1) return history
        return history.map { w ->
            if (w.exercises.none { it.exercise.id in family && it.exercise.id != asExerciseId }) {
                w
            } else {
                w.copy(exercises = w.exercises.map { we -> if (we.exercise.id in family) we.copy(exercise = we.exercise.copy(id = asExerciseId)) else we })
            }
        }
    }

    /** Mayor peso de trabajo de un ejercicio entre dos instantes (null si no se hizo). */
    fun bestWeightBetween(exerciseId: String, history: List<Workout>, fromEpochMs: Long, toEpochMs: Long): Double? =
        history.filter { it.isFinished && it.startedAtEpochMs in fromEpochMs until toEpochMs }
            .flatMap { w -> w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets } }
            .filter { it.type != SetType.WARMUP && it.weightKg > 0 }
            .maxOfOrNull { it.weightKg }

    /** Mejores marcas por ejercicio y tipo. */
    fun personalRecords(history: List<Workout>): Map<String, Map<RecordType, PersonalRecord>> {
        val result = mutableMapOf<String, MutableMap<RecordType, PersonalRecord>>()
        history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.forEach { w ->
            w.exercises.forEach { we ->
                we.completedSets.filter { it.countsForRecords }.forEach { set ->
                    RecordType.entries.forEach { type ->
                        val value = recordValue(type, set)
                        val current = result[we.exercise.id]?.get(type)
                        if (value > 0 && (current == null || value > current.value)) {
                            result.getOrPut(we.exercise.id) { mutableMapOf() }[type] =
                                PersonalRecord(we.exercise.id, we.exercise.name, type, value, w.id, w.finishedAt())
                        }
                    }
                }
            }
        }
        return result
    }

    /** Comparación de un ejercicio con la última vez que se hizo. */
    data class ExerciseComparison(
        val exerciseId: String,
        val exerciseName: String,
        val volumeDelta: Double,
        val best1rmDelta: Double,
        val repsDelta: Int,
        /** Segundos totales (ejercicios de tiempo y cardio). */
        val durationDelta: Int = 0,
        /** Metros totales (distancia y cardio). */
        val distanceDelta: Double = 0.0,
        /** Si las dos sesiones registraron distancia (si no, el cardio se compara por tiempo). */
        val hasDistance: Boolean = false,
    )

    /** Ejercicio a ejercicio, qué cambió frente a la sesión anterior en que se hizo. */
    fun compareWithPrevious(workout: Workout, history: List<Workout>): List<ExerciseComparison> {
        val earlier = history.filter { it.id != workout.id && it.isFinished && it.startedAtEpochMs < workout.startedAtEpochMs }
        val sorted = earlier.sortedByDescending { it.startedAtEpochMs }

        // Un ejercicio en dos bloques (o dos veces en la sesión anterior) cuenta con todas sus series
        // Sin calentamiento: una serie de aproximación de más no es progreso
        fun setsOf(w: Workout, id: String) = w.exercises.filter { it.exercise.id == id }.flatMap { e -> e.completedSets.filter { it.type != SetType.WARMUP } }
        return workout.exercises.distinctBy { it.exercise.id }.mapNotNull { we ->
            val prev = sorted.firstNotNullOfOrNull { w -> setsOf(w, we.exercise.id).takeIf { it.isNotEmpty() } } ?: return@mapNotNull null
            // Saltado hoy (sin series hechas): no es un retroceso
            val now = setsOf(workout, we.exercise.id).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ExerciseComparison(
                exerciseId = we.exercise.id,
                exerciseName = we.exercise.name,
                volumeDelta = now.sumOf { it.volume } - prev.sumOf { it.volume },
                best1rmDelta = (now.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0) - (prev.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0),
                repsDelta = now.sumOf { it.totalReps } - prev.sumOf { it.totalReps },
                durationDelta = now.sumOf { it.durationSec } - prev.sumOf { it.durationSec },
                distanceDelta = now.sumOf { it.distanceM } - prev.sumOf { it.distanceM },
                // Las dos con distancia: con una sola, comparar km saldría un «−5 km» falso
                hasDistance = now.any { it.distanceM > 0 } && prev.any { it.distanceM > 0 },
            )
        }
    }

    /**
     * El historial en [gymName] si allí ya se hizo [exerciseId] (otro material, otras cargas); si no, o sin
     * gimnasio, todo. Para rellenar y sugerir con lo hecho en el mismo sitio.
     */
    fun atGym(history: List<Workout>, gymName: String?, exerciseId: String): List<Workout> {
        if (gymName == null) return history
        val there = history.filter { it.gymName == gymName }
        return if (previousSets(exerciseId, there).isNotEmpty()) there else history
    }

    /** Entrenos por día de un año, para el mapa de calor. */
    fun dailyCounts(history: List<Workout>, year: Int, zone: ZoneId): Map<LocalDate, Int> =
        history.map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
            .filter { it.year == year }
            .groupingBy { it }.eachCount()

    /** Búsqueda en el historial por nombre de entreno, ejercicio o nota. */
    fun search(history: List<Workout>, query: String, nameOf: (com.bestrong.core.model.Exercise) -> String = { it.name }): List<Workout> {
        // Sin acentos ni mayúsculas: «biceps» encuentra «Curl de bíceps»
        fun String.plain() = java.text.Normalizer.normalize(lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        val q = query.trim().plain()
        if (q.isEmpty()) return history
        return history.filter { w ->
            w.name.plain().contains(q) || w.notes.plain().contains(q) || w.gymName?.plain()?.contains(q) == true ||
                w.athlete?.plain()?.contains(q) == true ||
                w.exercises.any { we -> nameOf(we.exercise).plain().contains(q) || we.notes.plain().contains(q) || we.sets.any { it.note.plain().contains(q) } }
        }
    }

    /** Ejercicios unilaterales con descompensación (> [threshold]) en la última sesión. */
    fun imbalances(workout: Workout, threshold: Double = 0.15): List<Pair<String, Double>> =
        workout.exercises.mapNotNull { we ->
            val worst = we.completedSets.mapNotNull { it.sideImbalance }.maxOrNull() ?: return@mapNotNull null
            if (worst > threshold) we.exercise.id to worst else null
        }

    /** Récords que [workout] bate respecto al historial anterior. */
    fun newRecordsIn(workout: Workout, history: List<Workout>): List<PersonalRecord> {
        val before = personalRecords(history.filter { it.id != workout.id && it.startedAtEpochMs < workout.startedAtEpochMs })
        val records = mutableListOf<PersonalRecord>()
        workout.exercises.forEach { we ->
            val sets = we.completedSets.filter { it.countsForRecords }
            if (sets.isEmpty()) return@forEach
            val previous = before[we.exercise.id] ?: return@forEach // la primera vez no cuenta como récord
            RecordType.entries.forEach { type ->
                val best = sets.maxOf { recordValue(type, it) }
                val old = previous[type]?.value ?: 0.0
                if (best > old && best > 0) {
                    records += PersonalRecord(we.exercise.id, we.exercise.name, type, best, workout.id, workout.finishedAt())
                }
            }
        }
        return records
    }

    /**
     * [newRecordsIn] de varios entrenos a la vez ([include]) en una sola pasada cronológica por
     * [history], en vez de recalcular todos los récords una vez por entreno. Mismo resultado.
     */
    fun newRecordsInEach(history: List<Workout>, include: (Workout) -> Boolean): Map<String, List<PersonalRecord>> {
        val best = HashMap<String, MutableMap<RecordType, Double>>()
        val result = HashMap<String, List<PersonalRecord>>()
        // Entrenos a la misma hora (importados solo con fecha) no cuentan como «anteriores» entre sí
        history.filter { it.isFinished }.groupBy { it.startedAtEpochMs }.toSortedMap().values.forEach { group ->
            val bestsByWorkout = group.map { w -> w to w.exercises.flatMap { we -> bestsOf(we) } }
            bestsByWorkout.filter { (w, _) -> include(w) }.forEach { (w, bests) ->
                // La primera vez que se hace un ejercicio no cuenta como récord
                result[w.id] = bests.filter { (we, type, value) -> best[we.exercise.id]?.let { value > (it[type] ?: 0.0) } == true }
                    .map { (we, type, value) -> PersonalRecord(we.exercise.id, we.exercise.name, type, value, w.id, w.finishedAt()) }
            }
            // Se aplica al terminar el grupo: dentro de él todo se compara con lo anterior
            bestsByWorkout.flatMap { it.second }.forEach { (we, type, value) -> best.getOrPut(we.exercise.id) { mutableMapOf() }.merge(type, value, ::maxOf) }
        }
        return result
    }

    /** Mejor valor (> 0) de cada tipo de récord en ese ejercicio del entreno. */
    private fun bestsOf(we: WorkoutExercise): List<Triple<WorkoutExercise, RecordType, Double>> {
        val sets = we.completedSets.filter { it.countsForRecords }
        if (sets.isEmpty()) return emptyList()
        return RecordType.entries.map { type -> Triple(we, type, sets.maxOf { recordValue(type, it) }) }.filter { it.third > 0 }
    }

    fun exerciseProgress(exerciseId: String, history: List<Workout>): List<ExerciseProgressPoint> =
        history.filter { it.isFinished }
            .sortedBy { it.startedAtEpochMs }
            .mapNotNull { w ->
                val sets = w.exercises.filter { it.exercise.id == exerciseId }
                    .flatMap { it.completedSets }.filter { it.type != SetType.WARMUP }
                if (sets.isEmpty()) {
                    null
                } else {
                    ExerciseProgressPoint(
                        dateEpochMs = w.startedAtEpochMs,
                        bestOneRepMax = sets.maxOf { it.estimatedOneRepMax },
                        heaviestWeight = sets.maxOf { it.weightKg },
                        volume = sets.sumOf { it.volume },
                        bestReps = sets.maxOf { it.reps },
                        distanceM = sets.sumOf { it.distanceM },
                        durationSec = sets.sumOf { it.durationSec },
                    )
                }
            }

    fun weekStart(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Últimas [weeks] semanas (de más antigua a más reciente), incluidas las vacías. */
    fun weeklySummary(history: List<Workout>, weeks: Int, today: LocalDate, zone: ZoneId): List<WeekBucket> {
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val grouped = history.filter { it.isFinished }.groupBy { weekStart(it.startedAtEpochMs, zone) }
        return (weeks - 1 downTo 0).map { offset ->
            val start = currentWeek.minusWeeks(offset.toLong())
            val ws = grouped[start].orEmpty()
            WeekBucket(start, ws.size, ws.sumOf { it.totalVolume }, ws.sumOf { it.durationSeconds() })
        }
    }

    /**
     * Racha semanal: semanas consecutivas con al menos un entrenamiento. La semana actual
     * sin entrenar todavía no rompe la racha.
     */
    fun weeklyStreak(history: List<Workout>, today: LocalDate, zone: ZoneId): Int {
        val weeksTrained = history.filter { it.isFinished }.map { weekStart(it.startedAtEpochMs, zone) }.toSet()
        var week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (week !in weeksTrained) week = week.minusWeeks(1)
        var streak = 0
        while (week in weeksTrained) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    /** Días con entrenamiento (para el calendario). */
    fun trainingDays(history: List<Workout>, zone: ZoneId): Set<LocalDate> =
        history.filter { it.isFinished }
            .map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }
            .toSet()

    /** Series por músculo de la semana (lunes a domingo) que contiene [day]. */
    fun weeklySetsPerMuscle(history: List<Workout>, day: LocalDate, zone: ZoneId): Map<MuscleGroup, Double> {
        val start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(7)
        val inWeek = history.filter {
            val d = Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate()
            !d.isBefore(start) && d.isBefore(end)
        }
        return setsPerMuscle(inWeek, 0)
    }

    /** Series por músculo de cada una de las últimas [weeks] semanas (lunes, series), de la más antigua a la actual. */
    fun muscleTrend(history: List<Workout>, today: LocalDate, zone: ZoneId, weeks: Int = 8): Map<MuscleGroup, List<Pair<LocalDate, Double>>> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val perWeek = (weeks - 1 downTo 0).map { k -> monday.minusWeeks(k.toLong()).let { it to weeklySetsPerMuscle(history, it, zone) } }
        val muscles = perWeek.flatMap { it.second.keys }.toSet()
        return muscles.associateWith { m -> perWeek.map { (d, sets) -> d to (sets[m] ?: 0.0) } }
            .toList().sortedByDescending { (_, l) -> l.sumOf { it.second } }.toMap()
    }

    /**
     * Media semanal de series de [trend] en las semanas ya cerradas (la actual va a medias) y cómo
     * queda frente al rango habitual para ganar músculo ([MuscleTargets]).
     */
    fun weeklySetsZone(weeks: List<Pair<LocalDate, Double>>, closedWeeks: Int = 4, minSets: Double = MuscleTargets.MIN_WEEKLY_SETS): Pair<Double, VolumeStatus>? {
        // Las semanas antes de empezar a entrenar ese músculo no cuentan (bajarían la media)
        val closed = weeks.dropLast(1).dropWhile { it.second == 0.0 }.takeLast(closedWeeks)
        if (closed.size < 2) return null
        val avg = closed.map { it.second }.average()
        val zone = MuscleTargets.status(avg, minSets)
        return avg to zone
    }

    /**
     * Músculos que esta semana van por detrás: de jueves en adelante, los que normalmente llegas a
     * [MuscleTargets.MIN_WEEKLY_SETS] (media de las semanas cerradas) y esta semana aún no. Como mucho [limit],
     * los más atrasados primero, con las series que llevas.
     */
    fun laggingMuscles(history: List<Workout>, today: LocalDate, zone: ZoneId, limit: Int = 3, minSets: Double = MuscleTargets.MIN_WEEKLY_SETS): List<Pair<MuscleGroup, Double>> {
        if (today.dayOfWeek < DayOfWeek.THURSDAY) return emptyList()
        return muscleTrend(history, today, zone).mapNotNull { (m, weeks) ->
            // «Lo entrenas normalmente» con el criterio general; tu mínimo solo decide si esta semana vas corto
            val usual = weeklySetsZone(weeks, minSets = minOf(minSets, MuscleTargets.MIN_WEEKLY_SETS))?.second ?: return@mapNotNull null
            val now = weeks.last().second
            (m to now).takeIf { usual >= VolumeStatus.OPTIMAL && now < minSets }
        }.sortedBy { it.second }.take(limit)
    }

    /** Series efectivas por músculo que tiene una rutina (como [setsPerMuscle], sin calentamientos). */
    fun routineSetsPerMuscle(routine: com.bestrong.core.model.Routine): Map<MuscleGroup, Double> {
        val result = mutableMapOf<MuscleGroup, Double>()
        routine.exercises.forEach { re ->
            val n = re.sets.count { it.type != SetType.WARMUP }.toDouble()
            if (n == 0.0) return@forEach
            result.merge(re.exercise.primaryMuscle, n, Double::plus)
            re.exercise.secondaryMuscles.forEach { result.merge(it, n * 0.5, Double::plus) }
        }
        return result.toList().sortedByDescending { it.second }.toMap()
    }

    /** Series efectivas por grupo muscular (el principal cuenta 1, los secundarios 0,5). */
    fun setsPerMuscle(history: List<Workout>, sinceEpochMs: Long): Map<MuscleGroup, Double> {
        val result = mutableMapOf<MuscleGroup, Double>()
        history.filter { it.isFinished && it.startedAtEpochMs >= sinceEpochMs }.forEach { w ->
            w.exercises.forEach { we ->
                val n = we.completedSets.count { it.type != SetType.WARMUP }.toDouble()
                if (n == 0.0) return@forEach
                result.merge(we.exercise.primaryMuscle, n, Double::plus)
                we.exercise.secondaryMuscles.forEach { result.merge(it, n * 0.5, Double::plus) }
            }
        }
        return result.toList().sortedByDescending { it.second }.toMap()
    }
}
