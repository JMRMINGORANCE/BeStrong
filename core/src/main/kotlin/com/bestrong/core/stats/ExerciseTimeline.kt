package com.bestrong.core.stats

import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise

/** Qué pasó en un hito de la historia de un ejercicio. */
enum class MilestoneKind {
    /** La primera vez que se hizo. */
    FIRST_TIME,

    /** Nuevo mejor 1RM estimado. */
    RECORD_ONE_REP_MAX,

    /** Nuevo peso máximo. */
    RECORD_WEIGHT,

    /** Más repeticiones que nunca (ejercicios sin peso). */
    RECORD_REPS,

    /** Primera vez con un peso redondo (60, 80, 100 kg…). */
    ROUND_WEIGHT,

    /** Primera vez en otro gimnasio. */
    GYM_CHANGE,

    /** Vuelta tras un parón largo. */
    COMEBACK,

    /** Sesión claramente más ligera (descarga). */
    DELOAD,
}

/**
 * Un hito: [value] es el valor nuevo (kg, reps o días de parón) y [previous] el de antes si lo hay; [gym] el
 * gimnasio en un cambio de gimnasio.
 */
data class Milestone(
    val kind: MilestoneKind,
    val workoutId: String,
    val dateEpochMs: Long,
    val value: Double = 0.0,
    val previous: Double? = null,
    val gym: String? = null,
)

/** La historia de un ejercicio contada con sus hitos (para la línea de tiempo de la ficha). */
object ExerciseTimeline {
    /** Pesos redondos que se celebran la primera vez que se alcanzan (en kg). */
    val ROUND_WEIGHTS_KG = listOf(40.0, 60.0, 80.0, 100.0, 120.0, 140.0, 160.0, 180.0, 200.0, 220.0, 250.0, 300.0)

    /** Días sin hacerlo a partir de los cuales la siguiente sesión cuenta como vuelta. */
    const val COMEBACK_DAYS = 28

    /** Una sesión con el peso de trabajo por debajo de esta fracción del habitual (y luego vuelve) es una descarga. */
    const val DELOAD_FRACTION = 0.85

    /** Los hitos de [exerciseId], del más reciente al más antiguo. */
    fun milestones(history: List<Workout>, exerciseId: String): List<Milestone> {
        val sessions = history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }
            .mapNotNull { w -> w.exercises.firstOrNull { it.exercise.id == exerciseId && workSets(it).isNotEmpty() }?.let { w to it } }
        if (sessions.isEmpty()) return emptyList()
        val out = mutableListOf<Milestone>()
        var best1rm = 0.0
        var heaviest = 0.0
        var mostReps = 0
        var lastGym: String? = null
        var prevStart: Long? = null
        sessions.forEachIndexed { i, (w, we) ->
            val date = w.finishedAtEpochMs ?: w.startedAtEpochMs
            val sets = workSets(we)
            val withWeight = we.exercise.tracking == TrackingType.WEIGHT_REPS
            val top1rm = sets.filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
            val topWeight = sets.filter { it.countsForRecords }.maxOfOrNull { it.weightKg } ?: 0.0
            val topReps = sets.filter { it.countsForRecords }.maxOfOrNull { it.recordReps } ?: 0
            if (i == 0) {
                out += Milestone(MilestoneKind.FIRST_TIME, w.id, date, value = if (withWeight) topWeight else topReps.toDouble())
            } else {
                prevStart?.let { p ->
                    val days = ((w.startedAtEpochMs - p) / DAY_MS).toInt()
                    if (days >= COMEBACK_DAYS) out += Milestone(MilestoneKind.COMEBACK, w.id, date, value = days.toDouble())
                }
                if (withWeight) {
                    // Un récord por sesión: el de peso (lo que se recuerda) y, si no lo hubo, el de 1RM estimado
                    when {
                        topWeight > heaviest + EPS -> out += Milestone(MilestoneKind.RECORD_WEIGHT, w.id, date, topWeight, heaviest)
                        top1rm > best1rm + EPS -> out += Milestone(MilestoneKind.RECORD_ONE_REP_MAX, w.id, date, top1rm, best1rm)
                    }
                    if (isDeload(sessions, i)) out += Milestone(MilestoneKind.DELOAD, w.id, date, topWeight)
                } else if (topReps > mostReps) {
                    out += Milestone(MilestoneKind.RECORD_REPS, w.id, date, topReps.toDouble(), mostReps.toDouble())
                }
            }
            // En la primera sesión ya está «primera vez» con su peso
            if (withWeight && i > 0) {
                ROUND_WEIGHTS_KG.filter { it > heaviest + EPS && topWeight + EPS >= it }.maxOrNull()?.let { r ->
                    out += Milestone(MilestoneKind.ROUND_WEIGHT, w.id, date, r)
                }
            }
            val gym = w.gymName?.takeIf { it.isNotBlank() }
            if (gym != null && lastGym != null && gym != lastGym) out += Milestone(MilestoneKind.GYM_CHANGE, w.id, date, gym = gym)
            if (gym != null) lastGym = gym
            best1rm = maxOf(best1rm, top1rm)
            heaviest = maxOf(heaviest, topWeight)
            mostReps = maxOf(mostReps, topReps)
            prevStart = w.startedAtEpochMs
        }
        // Del más reciente al más antiguo; en la misma sesión, lo más importante primero
        return out.sortedWith(compareByDescending<Milestone> { it.dateEpochMs }.thenBy { it.kind.ordinal })
    }

    /** Mejor 1RM estimado de cada sesión (de la más antigua a la más reciente), para la mini gráfica. */
    fun oneRepMaxSeries(history: List<Workout>, exerciseId: String): List<Pair<Long, Double>> =
        history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.mapNotNull { w ->
            val we = w.exercises.firstOrNull { it.exercise.id == exerciseId } ?: return@mapNotNull null
            val best = workSets(we).filter { it.countsForRecords }.maxOfOrNull { it.estimatedOneRepMax } ?: return@mapNotNull null
            if (best > 0) w.startedAtEpochMs to best else null
        }

    private fun workSets(we: WorkoutExercise) = we.completedSets.filter { it.type != SetType.WARMUP }

    /**
     * Descarga: el peso de trabajo está claramente por debajo del de las dos sesiones anteriores y la siguiente vuelve
     * a subir (una sesión floja aislada sin vuelta no cuenta).
     */
    private fun isDeload(sessions: List<Pair<Workout, WorkoutExercise>>, i: Int): Boolean {
        if (i < 2 || i >= sessions.lastIndex) return false
        fun top(k: Int) = workSets(sessions[k].second).maxOfOrNull { it.weightKg } ?: 0.0
        val usual = minOf(top(i - 1), top(i - 2))
        if (usual <= 0) return false
        return top(i) < usual * DELOAD_FRACTION && top(i + 1) >= usual * DELOAD_FRACTION
    }

    private const val DAY_MS = 86_400_000L
    private const val EPS = 1e-6
}
