package com.bestrong.core.session

import com.bestrong.core.model.Exercise
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.model.WorkoutSource
import com.bestrong.core.training.RestPolicy
import com.bestrong.core.training.WarmupGenerator
import com.bestrong.core.util.WeightUnit

/**
 * Operaciones inmutables sobre el entrenamiento en curso. Las usan tanto el móvil como el reloj.
 */
object WorkoutEditor {

    fun empty(nowEpochMs: Long, source: WorkoutSource = WorkoutSource.PHONE, name: String = defaultName(nowEpochMs)) =
        Workout(name = name, startedAtEpochMs = nowEpochMs, source = source)

    fun defaultName(nowEpochMs: Long): String {
        val hour = java.time.Instant.ofEpochMilli(nowEpochMs).atZone(java.time.ZoneId.systemDefault()).hour
        return when (hour) {
            in 5..11 -> "Entrenamiento de mañana"
            in 12..15 -> "Entrenamiento de mediodía"
            in 16..20 -> "Entrenamiento de tarde"
            else -> "Entrenamiento de noche"
        }
    }

    fun addExercises(
        workout: Workout,
        exercises: List<Exercise>,
        previousSets: (String) -> List<WorkoutSet> = { emptyList() },
        restFor: (Exercise) -> Int = { RestPolicy.defaultFor(it) },
    ): Workout =
        workout.copy(
            exercises = workout.exercises + exercises.map { ex ->
                val prev = previousSets(ex.id)
                val sets = if (prev.isNotEmpty()) {
                    prev.map { it.copy(id = com.bestrong.core.util.newId(), completed = false, rpe = null) }
                } else if (ex.tracking == TrackingType.CARDIO) {
                    listOf(WorkoutSet(durationSec = TrackingType.DEFAULT_CARDIO_SEC))
                } else {
                    List(3) { WorkoutSet(reps = if (ex.tracking == TrackingType.DURATION) 0 else 10, durationSec = if (ex.tracking == TrackingType.DURATION) 30 else 0) }
                }
                WorkoutExercise(exercise = ex, sets = sets, restSeconds = restFor(ex))
            },
        )

    // --- Orden y tiempo restante ----------------------------------------------------------

    /** Saltar un ejercicio y volver a él después: pasa al final (las superseries se mantienen juntas). */
    fun skipExercise(workout: Workout, workoutExerciseId: String): Workout {
        val we = workout.exercises.firstOrNull { it.id == workoutExerciseId } ?: return workout
        val group = we.supersetId?.let { id -> workout.exercises.filter { it.supersetId == id } } ?: listOf(we)
        return workout.copy(exercises = workout.exercises.filterNot { it in group } + group)
    }

    /**
     * Tiempo estimado para terminar: cada serie pendiente ≈ [secondsPerSet] más el descanso
     * de su ejercicio (salvo la última del entreno).
     */
    fun estimatedRemainingSeconds(workout: Workout, secondsPerSet: Int = 40): Int {
        val pending = workout.exercises.flatMap { we -> we.sets.filter { !it.completed }.map { we.restSeconds } }
        if (pending.isEmpty()) return 0
        return pending.size * secondsPerSet + pending.dropLast(1).sum()
    }

    // --- Pausa y entreno olvidado -----------------------------------------------------------

    fun pause(workout: Workout, nowEpochMs: Long): Workout =
        if (workout.isPaused) workout else workout.copy(pausedAtEpochMs = nowEpochMs)

    fun resume(workout: Workout, nowEpochMs: Long): Workout {
        val since = workout.pausedAtEpochMs ?: return workout
        return workout.copy(pausedAtEpochMs = null, pausedTotalMs = workout.pausedTotalMs + (nowEpochMs - since).coerceAtLeast(0))
    }

    /** Lleva más de [limitMs] sin ningún cambio (se dejó abierto sin querer). */
    fun isForgotten(workout: Workout, nowEpochMs: Long, limitMs: Long = 3 * 3_600_000L): Boolean =
        !workout.isPaused && nowEpochMs - workout.updatedAtEpochMs > limitMs

    /** Termina un entreno olvidado a la hora del último cambio, no a la de ahora. */
    fun finishAtLastActivity(workout: Workout): Workout {
        val resumed = resume(workout, workout.pausedAtEpochMs ?: workout.updatedAtEpochMs)
        return finish(resumed, resumed.updatedAtEpochMs)
    }

    /** Sustituye un ejercicio por una alternativa conservando el descanso y adaptando los pesos. */
    fun substitute(workout: Workout, workoutExerciseId: String, to: Exercise, previousOfNew: List<WorkoutSet>, stepKg: Double): Workout =
        updateExercise(workout, workoutExerciseId) { we ->
            we.copy(exercise = to, sets = com.bestrong.core.library.Alternatives.convertSets(we.exercise, to, we.sets, previousOfNew, stepKg))
        }

    fun removeExercise(workout: Workout, workoutExerciseId: String): Workout =
        workout.copy(exercises = workout.exercises.filterNot { it.id == workoutExerciseId })

    fun moveExercise(workout: Workout, from: Int, to: Int): Workout {
        if (from !in workout.exercises.indices || to !in workout.exercises.indices) return workout
        val list = workout.exercises.toMutableList()
        list.add(to, list.removeAt(from))
        return workout.copy(exercises = list)
    }

    fun replaceExercise(workout: Workout, workoutExerciseId: String, exercise: Exercise): Workout =
        updateExercise(workout, workoutExerciseId) { it.copy(exercise = exercise) }

    fun updateExercise(workout: Workout, workoutExerciseId: String, block: (WorkoutExercise) -> WorkoutExercise): Workout =
        workout.copy(exercises = workout.exercises.map { if (it.id == workoutExerciseId) block(it) else it })

    /** Añade una serie copiando la última (peso y reps). */
    fun addSet(workout: Workout, workoutExerciseId: String): Workout = updateExercise(workout, workoutExerciseId) { we ->
        val last = we.sets.lastOrNull()
        we.copy(
            sets = we.sets + WorkoutSet(
                weightKg = last?.weightKg ?: 0.0,
                reps = last?.reps ?: 10,
                durationSec = last?.durationSec ?: 0,
                type = if (last?.type == SetType.WARMUP) SetType.NORMAL else last?.type ?: SetType.NORMAL,
            ),
        )
    }

    /** Añade series de aproximación antes de la primera serie de trabajo (sustituye calentamientos pendientes). */
    fun addWarmup(workout: Workout, workoutExerciseId: String, unit: WeightUnit, barKg: Double? = null): Workout = updateExercise(workout, workoutExerciseId) { we ->
        val working = we.sets.firstOrNull { it.type != SetType.WARMUP }?.weightKg ?: return@updateExercise we
        val warmups = if (barKg != null) WarmupGenerator.generate(working, unit, barKg) else WarmupGenerator.generate(working, unit)
        if (warmups.isEmpty()) return@updateExercise we
        we.copy(sets = we.sets.filter { it.type == SetType.WARMUP && it.completed } + warmups + we.sets.filter { it.type != SetType.WARMUP })
    }

    fun removeSet(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateExercise(workout, workoutExerciseId) { we -> we.copy(sets = we.sets.filterNot { it.id == setId }) }

    fun updateSet(workout: Workout, workoutExerciseId: String, setId: String, block: (WorkoutSet) -> WorkoutSet): Workout =
        updateExercise(workout, workoutExerciseId) { we -> we.copy(sets = we.sets.map { if (it.id == setId) block(it) else it }) }

    /**
     * Cambia peso y/o reps de una serie y lo propaga a las series siguientes pendientes del mismo tipo
     * que aún tenían el valor antiguo (autocompletar: cambias la primera y se ajustan las demás).
     */
    fun updateSetPropagating(workout: Workout, workoutExerciseId: String, setId: String, weightKg: Double? = null, reps: Int? = null): Workout =
        updateExercise(workout, workoutExerciseId) { we ->
            val i = we.sets.indexOfFirst { it.id == setId }
            if (i < 0) return@updateExercise we
            val old = we.sets[i]
            we.copy(
                sets = we.sets.mapIndexed { j, s ->
                    when {
                        j == i -> s.copy(weightKg = weightKg ?: s.weightKg, reps = reps ?: s.reps)
                        j > i && !s.completed && s.type == old.type -> s.copy(
                            weightKg = if (weightKg != null && s.weightKg == old.weightKg) weightKg else s.weightKg,
                            reps = if (reps != null && s.reps == old.reps) reps else s.reps,
                        )
                        else -> s
                    }
                },
            )
        }

    /** Suma [deltaKg] al peso de la serie (botones ±); en ejercicios de carga no baja de 0. */
    fun stepWeight(workout: Workout, workoutExerciseId: String, setId: String, deltaKg: Double, allowNegative: Boolean = false): Workout =
        updateSet(workout, workoutExerciseId, setId) { s ->
            val v = Math.round((s.weightKg + deltaKg) * 1000) / 1000.0
            s.copy(weightKg = if (allowNegative) v else v.coerceAtLeast(0.0))
        }

    fun stepReps(workout: Workout, workoutExerciseId: String, setId: String, delta: Int): Workout =
        updateSet(workout, workoutExerciseId, setId) { it.copy(reps = (it.reps + delta).coerceAtLeast(0)) }

    /** Edición de un entreno ya terminado: quita ejercicios que se quedan sin series. */
    fun cleanupFinished(workout: Workout): Workout = workout.copy(exercises = workout.exercises.filter { it.sets.isNotEmpty() })

    fun toggleSetCompleted(workout: Workout, workoutExerciseId: String, setId: String, nowEpochMs: Long = System.currentTimeMillis()): Workout =
        updateSet(workout, workoutExerciseId, setId) {
            val started = it.startedAtEpochMs
            if (it.isRunning && started != null) {
                // Marcar un cardio en marcha lo termina con el tiempo del cronómetro
                it.copy(durationSec = ((nowEpochMs - started) / 1000).toInt().coerceAtLeast(1), completed = true, completedAtEpochMs = nowEpochMs)
            } else {
                it.copy(completed = !it.completed, completedAtEpochMs = if (it.completed) null else nowEpochMs)
            }
        }

    // --- Cronómetro de cardio ------------------------------------------------------------------

    /** Arranca el cronómetro de una serie de cardio (solo uno a la vez: los demás se paran sin guardar). */
    fun startCardio(workout: Workout, workoutExerciseId: String, setId: String, nowEpochMs: Long = System.currentTimeMillis()): Workout =
        workout.copy(
            exercises = workout.exercises.map { we ->
                we.copy(
                    sets = we.sets.map { s ->
                        when {
                            we.id == workoutExerciseId && s.id == setId -> s.copy(startedAtEpochMs = nowEpochMs, completed = false, completedAtEpochMs = null)
                            s.isRunning -> s.copy(startedAtEpochMs = null)
                            else -> s
                        }
                    },
                )
            },
            updatedAtEpochMs = nowEpochMs,
        )

    /** Para el cronómetro: la serie queda hecha con el tiempo medido. */
    fun finishCardio(workout: Workout, workoutExerciseId: String, setId: String, nowEpochMs: Long = System.currentTimeMillis()): Workout =
        if (findSet(workout, workoutExerciseId, setId)?.isRunning == true) toggleSetCompleted(workout, workoutExerciseId, setId, nowEpochMs) else workout

    private fun findSet(workout: Workout, workoutExerciseId: String, setId: String): WorkoutSet? =
        workout.exercises.firstOrNull { it.id == workoutExerciseId }?.sets?.firstOrNull { it.id == setId }

    /**
     * Vuelve a dejar pendiente una serie hecha (deshacer). En el cardio se quita también la hora de
     * arranque: si no, el cronómetro volvería a correr desde el principio y falsearía la duración.
     */
    fun uncompleteSet(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateSet(workout, workoutExerciseId, setId) { it.copy(completed = false, completedAtEpochMs = null, startedAtEpochMs = null) }

    /** Descarta el cronómetro sin completar la serie. */
    fun cancelCardio(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateSet(workout, workoutExerciseId, setId) { it.copy(startedAtEpochMs = null) }

    /** El bloque de cardio en marcha, si hay uno. */
    fun runningCardio(workout: Workout): Pair<WorkoutExercise, WorkoutSet>? =
        workout.exercises.firstNotNullOfOrNull { we -> we.sets.firstOrNull { it.isRunning }?.let { we to it } }

    /** Cicla Normal → Calentamiento → Drop → Fallo → Normal. */
    fun cycleSetType(workout: Workout, workoutExerciseId: String, setId: String): Workout =
        updateSet(workout, workoutExerciseId, setId) {
            val next = when (it.type) {
                SetType.NORMAL -> SetType.WARMUP
                SetType.WARMUP -> SetType.DROP
                SetType.DROP -> SetType.FAILURE
                SetType.FAILURE -> SetType.NORMAL
                else -> SetType.NORMAL
            }
            it.copy(type = next)
        }

    // --- Superseries ------------------------------------------------------------------------

    /** Une el ejercicio con el siguiente en una superserie (o lo añade a la del anterior). */
    fun linkWithNext(workout: Workout, workoutExerciseId: String): Workout {
        val i = workout.exercises.indexOfFirst { it.id == workoutExerciseId }
        if (i < 0 || i >= workout.exercises.lastIndex) return workout
        val a = workout.exercises[i]
        val b = workout.exercises[i + 1]
        val group = a.supersetId ?: b.supersetId ?: com.bestrong.core.util.newId()
        val oldB = b.supersetId
        return workout.copy(
            exercises = workout.exercises.map {
                when {
                    it.id == a.id || it.id == b.id -> it.copy(supersetId = group)
                    oldB != null && it.supersetId == oldB -> it.copy(supersetId = group)
                    else -> it
                }
            },
        )
    }

    /** Saca el ejercicio de su superserie; si queda uno solo en el grupo, el grupo desaparece. */
    fun unlinkSuperset(workout: Workout, workoutExerciseId: String): Workout {
        val group = workout.exercises.firstOrNull { it.id == workoutExerciseId }?.supersetId ?: return workout
        val cleared = workout.exercises.map { if (it.id == workoutExerciseId) it.copy(supersetId = null) else it }
        val remaining = cleared.count { it.supersetId == group }
        return workout.copy(exercises = if (remaining <= 1) cleared.map { if (it.supersetId == group) it.copy(supersetId = null) else it } else cleared)
    }

    /** Índices de los ejercicios de la superserie de [index] (solo él mismo si no tiene). */
    fun supersetGroup(workout: Workout, index: Int): List<Int> {
        val group = workout.exercises.getOrNull(index)?.supersetId ?: return listOf(index)
        return workout.exercises.indices.filter { workout.exercises[it].supersetId == group }
    }

    /**
     * Qué hacer tras completar una serie del ejercicio [index]:
     * - en superserie, pasar al siguiente ejercicio del grupo con series pendientes sin descanso;
     * - al cerrar la ronda (o fuera de superserie), descansar el tiempo del ejercicio.
     * Devuelve (índice del siguiente ejercicio o null si se terminó todo, segundos de descanso).
     */
    /** Descanso máximo tras una serie de calentamiento. */
    const val WARMUP_REST_SECONDS = 60

    fun afterSetCompleted(workout: Workout, index: Int): Pair<Int?, Int> {
        val group = supersetGroup(workout, index)
        val current = workout.exercises.getOrNull(index) ?: return null to 0
        if (group.size > 1) {
            val pos = group.indexOf(index)
            val doneHere = current.sets.count { it.completed }
            // Siguiente del grupo que aún no ha hecho la serie de esta ronda
            val nextInRound = (group.drop(pos + 1)).firstOrNull { i ->
                val ex = workout.exercises[i]
                ex.sets.count { it.completed } < doneHere && ex.sets.any { !it.completed }
            }
            if (nextInRound != null) return nextInRound to 0
            val firstPending = group.firstOrNull { i -> workout.exercises[i].sets.any { !it.completed } }
            if (firstPending != null) return firstPending to workout.exercises[group.last()].restSeconds
        } else if (current.sets.any { !it.completed }) {
            // Tras una serie de aproximación basta un descanso corto (no los 3 min de la serie efectiva)
            val justDone = current.sets.filter { it.completed }.maxByOrNull { it.completedAtEpochMs ?: 0L }
            val rest = if (justDone?.type == SetType.WARMUP) current.restSeconds.coerceAtMost(WARMUP_REST_SECONDS) else current.restSeconds
            return index to rest
        }
        val next = nextPendingSet(workout)?.first
        return next to if (next == null) 0 else current.restSeconds
    }

    /** Siguiente serie pendiente: (índice de ejercicio, serie) o null si está todo hecho. */
    fun nextPendingSet(workout: Workout): Pair<Int, WorkoutSet>? {
        workout.exercises.forEachIndexed { i, we ->
            we.sets.firstOrNull { !it.completed }?.let { return i to it }
        }
        return null
    }

    /**
     * Termina el entrenamiento: descarta las series no completadas y los ejercicios vacíos.
     */
    fun finish(workout: Workout, nowEpochMs: Long): Workout {
        // Un cronómetro de cardio en marcha cuenta como hecho hasta ahora (si no, se perdería el bloque)
        val stopped = runningCardio(workout)?.let { (we, set) -> finishCardio(workout, we.id, set.id, nowEpochMs) } ?: workout
        return resume(stopped, nowEpochMs).copy(
            finishedAtEpochMs = nowEpochMs,
            exercises = stopped.exercises
                .map { it.copy(sets = it.completedSets) }
                .filter { it.sets.isNotEmpty() },
        )
    }
}
