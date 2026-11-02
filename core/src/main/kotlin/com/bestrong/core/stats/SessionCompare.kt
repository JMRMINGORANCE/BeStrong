package com.bestrong.core.stats

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet

/**
 * Comparador de sesiones: dos entrenos lado a lado (cifras generales y, por ejercicio, las series de cada uno).
 * [Result.a] es la sesión que se está viendo y [Result.b] la otra (normalmente una anterior).
 */
object SessionCompare {
    /** Cifras de una sesión: volumen, mejor 1RM estimado, series efectivas, duración y descanso medio real. */
    data class Totals(val volumeKg: Double, val bestOneRepMaxKg: Double, val workSets: Int, val durationSec: Long, val avgRestSec: Int?)

    /** Un ejercicio en las dos sesiones (series hechas, juntando sus bloques); vacío si no se hizo en una. */
    data class ExerciseRow(
        val exerciseId: String,
        val exerciseName: String,
        val setsA: List<WorkoutSet>,
        val setsB: List<WorkoutSet>,
    ) {
        val volumeA: Double get() = setsA.sumOf { it.volume }
        val volumeB: Double get() = setsB.sumOf { it.volume }
        val bestA: Double get() = setsA.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
        val bestB: Double get() = setsB.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0
    }

    data class Result(val a: Workout, val b: Workout, val totalsA: Totals, val totalsB: Totals, val rows: List<ExerciseRow>)

    fun totals(w: Workout): Totals = Totals(
        volumeKg = w.totalVolume,
        bestOneRepMaxKg = w.exercises.flatMap { it.completedSets }.maxOfOrNull { it.estimatedOneRepMax } ?: 0.0,
        workSets = w.workSetCount,
        durationSec = w.durationSeconds(),
        avgRestSec = RestStats.summary(w)?.realSeconds,
    )

    fun compare(a: Workout, b: Workout): Result {
        val ids = (a.exercises.map { it.exercise.id } + b.exercises.map { it.exercise.id }).distinct()
        val rows = ids.map { id ->
            fun sets(w: Workout) = w.exercises.filter { it.exercise.id == id }.flatMap { it.completedSets }
            val name = (a.exercises + b.exercises).first { it.exercise.id == id }.exercise.name
            ExerciseRow(id, name, sets(a), sets(b))
        }.filter { it.setsA.isNotEmpty() || it.setsB.isNotEmpty() }
        return Result(a, b, totals(a), totals(b), rows)
    }

    /**
     * Con qué sesión comparar [w] por defecto: la anterior de la misma rutina; si no, la anterior que más ejercicios
     * comparte; si no, la anterior sin más. Solo del mismo atleta y terminadas.
     */
    fun defaultPartner(w: Workout, history: List<Workout>): Workout? {
        val before = history.filter { it.id != w.id && it.isFinished && it.athlete == w.athlete && it.startedAtEpochMs < w.startedAtEpochMs }
            .sortedByDescending { it.startedAtEpochMs }
        w.routineId?.let { r -> before.firstOrNull { it.routineId == r }?.let { return it } }
        val mine = w.exercises.map { it.exercise.id }.toSet()
        // La más reciente entre las que más ejercicios comparten (la lista ya va de más nueva a más vieja)
        val shared = before.map { it to it.exercises.map { e -> e.exercise.id }.distinct().count { id -> id in mine } }.filter { it.second > 0 }
        val most = shared.maxOfOrNull { it.second }
        return shared.firstOrNull { it.second == most }?.first ?: before.firstOrNull()
    }

    /** Candidatas para elegir a mano: primero las que comparten ejercicios, luego el resto, de la más reciente. */
    fun candidates(w: Workout, history: List<Workout>): List<Workout> {
        val mine = w.exercises.map { it.exercise.id }.toSet()
        return history.filter { it.id != w.id && it.isFinished && it.athlete == w.athlete }
            .sortedWith(compareByDescending<Workout> { o -> o.exercises.any { it.exercise.id in mine } }.thenByDescending { it.startedAtEpochMs })
    }

    /** +1 si la serie [a] es mejor que [b] (1RM estimado, o reps/tiempo sin peso), −1 si peor, 0 igual. */
    fun setDirection(a: WorkoutSet, b: WorkoutSet): Int {
        val ea = a.estimatedOneRepMax
        val eb = b.estimatedOneRepMax
        val d = when {
            ea > 0 || eb > 0 -> ea - eb
            a.totalReps != b.totalReps -> (a.totalReps - b.totalReps).toDouble()
            else -> (a.durationSec - b.durationSec).toDouble()
        }
        return when {
            d > EPS -> 1
            d < -EPS -> -1
            else -> 0
        }
    }

    /** Las series de trabajo (sin calentamientos), para emparejarlas por orden. */
    fun workSets(sets: List<WorkoutSet>): List<WorkoutSet> = sets.filter { it.type != SetType.WARMUP }

    private const val EPS = 0.05
}
