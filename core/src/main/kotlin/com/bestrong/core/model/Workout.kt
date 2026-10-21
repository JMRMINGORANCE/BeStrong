package com.bestrong.core.model

import com.bestrong.core.util.newId
import kotlinx.serialization.Serializable

@Serializable
enum class SetType(val short: String) {
    WARMUP("C"),
    NORMAL(""),
    DROP("D"),
    FAILURE("F"),

    /** Cluster / rest-pause: 8 + 3 + 2 con pausas cortas (las reps se suman). */
    CLUSTER("R"),

    /** Repeticiones parciales (no cuentan para récords). */
    PARTIAL("P"),

    /** Negativas o excéntricas (no cuentan para récords). */
    NEGATIVE("N"),
}

@Serializable
enum class WorkoutSource { PHONE, WATCH }

@Serializable
data class WorkoutSet(
    val id: String = newId(),
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val durationSec: Int = 0,
    val type: SetType = SetType.NORMAL,
    val rpe: Double? = null,
    val completed: Boolean = false,
    /** Nota de esta serie («me dolió el hombro»). */
    val note: String = "",
    /** Hecha con ayuda del compañero o con trampas: no cuenta como récord. */
    val assisted: Boolean = false,
    /** Ejercicios unilaterales: reps del lado derecho ([reps] = izquierdo); null = bilateral. */
    val repsRight: Int? = null,
    /** Ejercicios por distancia (paseo del granjero, trineo, remo…), en metros. */
    val distanceM: Double = 0.0,
    /** Cuándo se marcó como hecha: sitúa los bloques de cardio en la gráfica de pulso. */
    val completedAtEpochMs: Long? = null,
    /** Cardio con cronómetro: cuándo arrancó (en marcha mientras no esté completada). */
    val startedAtEpochMs: Long? = null,
    /** Reps planeadas en la rutina (null = sin plan): «la próxima vez» compara lo hecho con esto. */
    val targetReps: Int? = null,
    /**
     * Cuándo se desmarcó (deshacer): al fusionar el móvil y el reloj gana el cambio más reciente, también si fue
     * quitar la marca.
     */
    val uncompletedAtEpochMs: Long? = null,
) {
    /** Cronómetro de cardio en marcha. */
    val isRunning: Boolean get() = startedAtEpochMs != null && !completed

    /** Cuenta para récords: ni calentamiento, ni parciales/negativas, ni con ayuda. */
    val countsForRecords: Boolean
        get() = type != SetType.WARMUP && type != SetType.PARTIAL && type != SetType.NEGATIVE && !assisted

    /** La misma carga como serie nueva por hacer: sin marcar, sin horas (cronómetro), RPE, nota ni ayuda. */
    fun asPending(): WorkoutSet = copy(id = newId(), completed = false, rpe = null, completedAtEpochMs = null, startedAtEpochMs = null, note = "", assisted = false)

    /** Tiene algo apuntado (reps, tiempo o distancia): se puede dar por hecha tal cual. */
    val hasTarget: Boolean get() = totalReps > 0 || durationSec > 0 || distanceM > 0

    /** Repeticiones totales (ambos lados si es unilateral). */
    val totalReps: Int get() = reps + (repsRight ?: 0)

    /** Diferencia entre lados (0 = igual); null si no es unilateral o no hay datos. */
    val sideImbalance: Double?
        get() {
            val right = repsRight ?: return null
            val max = maxOf(reps, right)
            return if (max <= 0) null else kotlin.math.abs(reps - right).toDouble() / max
        }

    /** Reps que cuentan para récords: en unilaterales, las del lado más flojo (un lado derecho vacío, 0, no cuenta). */
    val recordReps: Int get() = repsRight?.takeIf { it > 0 }?.let { minOf(reps, it) } ?: reps

    /** 1RM estimado (Epley). Las series de calentamiento no cuentan; en unilaterales, con el lado más flojo. */
    val estimatedOneRepMax: Double
        get() = when {
            !countsForRecords || recordReps <= 0 || weightKg <= 0 -> 0.0
            recordReps == 1 -> weightKg
            else -> weightKg * (1 + recordReps / 30.0)
        }

    /** Volumen (kg × reps). El peso negativo (asistencia) no suma. */
    val volume: Double get() = if (type == SetType.WARMUP) 0.0 else weightKg.coerceAtLeast(0.0) * totalReps
}

/** Valoración rápida de la sesión, de 1 a 5. */
@Serializable
data class SessionRating(val energy: Int = 3, val pain: Int = 1, val motivation: Int = 3)

@Serializable
data class WorkoutExercise(
    val id: String = newId(),
    val exercise: Exercise,
    val sets: List<WorkoutSet> = listOf(WorkoutSet()),
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val notes: String = "",
    /** Ejercicios con el mismo id forman una superserie (se alternan sin descanso). */
    val supersetId: String? = null,
    /** Tempo de la repetición, p. ej. "3-1-1-0" (bajada-pausa-subida-pausa, en segundos). Ver [com.bestrong.core.training.Tempo]. */
    val tempo: String? = null,
    /** RPE objetivo que trae la rutina (null = sin objetivo). */
    val targetRpe: Double? = null,
    /**
     * Series efectivas planeadas, guardado al terminar solo si se hicieron menos (las no hechas se quitan): con
     * series saltadas no se propone subir el peso. Null = se hicieron todas.
     */
    val plannedSets: Int? = null,
) {
    val completedSets: List<WorkoutSet> get() = sets.filter { it.completed }

    /** Terminado con menos series efectivas de las planeadas. */
    val skippedWorkSets: Boolean get() = (plannedSets ?: 0) > completedSets.count { it.type != SetType.WARMUP }

    companion object {
        const val DEFAULT_REST_SECONDS = 120
    }
}

@Serializable
data class Workout(
    val id: String = newId(),
    val name: String,
    val routineId: String? = null,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val exercises: List<WorkoutExercise> = emptyList(),
    val notes: String = "",
    val source: WorkoutSource = WorkoutSource.PHONE,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val calories: Int? = null,
    /** Última modificación: resuelve conflictos al sincronizar móvil ↔ reloj en vivo. */
    val updatedAtEpochMs: Long = startedAtEpochMs,
    /** Pausado desde (null = en marcha). */
    val pausedAtEpochMs: Long? = null,
    /** Tiempo total en pausa ya acumulado (ms). */
    val pausedTotalMs: Long = 0,
    /** Cómo te sentiste (se pregunta al terminar). */
    val rating: SessionRating? = null,
    /** Modo pareja: entreno de otra persona en el mismo móvil (null = el tuyo). */
    val athlete: String? = null,
    /** Gimnasio donde se hizo (su nombre, para el historial y la búsqueda); null si no se sabe (reloj, antiguos). */
    val gymName: String? = null,
    /** Pulso a lo largo del entreno (una muestra cada ~10 s, medido por el reloj). */
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    /** De dónde salen las calorías (null = no hay). */
    val caloriesSource: CaloriesSource? = null,
    /** Calorías activas: las totales menos lo que habrías gastado en reposo en ese tiempo. */
    val activeCalories: Int? = null,
    /**
     * Ejercicios y series borrados durante el entreno, con las series hechas que se conocían al borrar: al fusionar
     * con el reloj no vuelven, salvo que el otro lado tenga trabajo hecho que quien borró no conocía.
     */
    val deletedIds: Map<String, Set<String>> = emptyMap(),
) {
    /** Calorías calculadas (pulso o MET), no medidas directamente por el reloj. */
    val caloriesEstimated: Boolean get() = caloriesSource != null && caloriesSource != CaloriesSource.WATCH

    val isPaused: Boolean get() = pausedAtEpochMs != null

    val isFinished: Boolean get() = finishedAtEpochMs != null

    val totalVolume: Double
        get() = exercises.sumOf { ex -> ex.completedSets.sumOf { it.volume } }

    val completedSetCount: Int
        get() = exercises.sumOf { it.completedSets.size }

    /** Series efectivas hechas (sin calentamientos): lo que se enseña como «series» en estadísticas y logros. */
    val workSetCount: Int
        get() = exercises.sumOf { e -> e.completedSets.count { it.type != SetType.WARMUP } }

    val totalSetCount: Int
        get() = exercises.sumOf { it.sets.size }

    /** Duración sin contar las pausas. */
    fun durationSeconds(nowEpochMs: Long = System.currentTimeMillis()): Long {
        val end = finishedAtEpochMs ?: pausedAtEpochMs ?: nowEpochMs
        return (end - startedAtEpochMs - pausedTotalMs).coerceAtLeast(0) / 1000
    }
}

/** Origen de las calorías, de más a menos fiable. */
@Serializable
enum class CaloriesSource {
    /** Medidas por el reloj (Health Services, sensores y perfil del usuario). */
    WATCH,

    /** Calculadas con el pulso registrado durante el entreno (ecuación de Keytel). */
    HEART_RATE,

    /** Estimadas por MET, duración, intensidad y peso. */
    ESTIMATE,
}

/** Pulso en un momento del entreno: segundos desde el inicio y pulsaciones por minuto. */
@Serializable
data class HeartRateSample(val offsetSec: Int, val bpm: Int)
