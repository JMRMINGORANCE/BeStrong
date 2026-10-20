package com.bestrong.core.training

import com.bestrong.core.model.Exercise
import com.bestrong.core.model.SetType
import com.bestrong.core.model.TrackingType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats

enum class SuggestionKind {
    /** Subir el peso (todas las series completadas). */
    INCREASE_WEIGHT,

    /** Mismo peso, buscar [ProgressionSuggestion.targetReps] en todas las series. */
    ADD_REPS,

    /** Ejercicio sin peso: +1 repetición por serie. */
    ADD_ONE_REP,

    /** Con rango de repeticiones: +1 rep por serie hasta [ProgressionSuggestion.targetReps] (el tope). */
    RANGE_ADD_REP,

    /** Ejercicio por tiempo: +5 s por serie. */
    ADD_TIME,

    /** RPE alto: repetir y consolidar. */
    REPEAT,

    /** Estancado [ProgressionSuggestion.stallSessions] sesiones: descarga al 90 %. */
    DELOAD,
}

data class ProgressionSuggestion(
    val kind: SuggestionKind,
    /** Series de trabajo propuestas para hoy (peso en kg). */
    val sets: List<WorkoutSet>,
    /** Explicación en español (la app la traduce a partir de [kind]). */
    val reason: String,
    val targetReps: Int = 0,
    val stallSessions: Int = 0,
    /** La subida se quedó corta por el tope semanal de Planificación (para explicarlo). */
    val cappedByWeek: Boolean = false,
)

/**
 * Sobrecarga progresiva por "doble progresión":
 * - Si la última vez completaste todas las reps en todas las series (y el RPE, si lo anotaste, fue ≤ 9) → +peso.
 * - Si alguna serie se quedó corta → mismo peso, intenta igualar las reps de la primera serie.
 * - Si llevas [stallSessions] sesiones sin mejorar el 1RM estimado → descarga al 90 %.
 */
object Progression {

    /**
     * «Fácil» respecto al RPE objetivo con que se hizo la última vez (8 si no tenía): hasta un punto por encima.
     * Cuentan las normales y cluster: un drop o una al fallo planificados van a RPE 10 a propósito.
     */
    private fun wasEasy(last: List<WorkoutSet>, targetRpe: Double?): Boolean {
        val target = targetRpe ?: Autoregulation.TARGET
        // Sin normales, las al fallo (los drops van a RPE 10 a propósito y no dicen nada)
        val rated = last.filter { (it.type == SetType.NORMAL || it.type == SetType.CLUSTER) && it.countsForRecords }
            .ifEmpty { last.filter { it.type == SetType.FAILURE } }
            // Nada que cuente (todo asistido, parciales…): como antes, con todas menos los drops
            .ifEmpty { last.filter { it.type != SetType.DROP } }
        if (rated.isEmpty()) return false
        // Sin series normales (todo al fallo), el objetivo de esas series es el fallo: RPE 10 es lo buscado
        return rated.all { set ->
            val t = if (set.type == SetType.FAILURE) Autoregulation.MAX_RPE else target
            (set.rpe ?: t) <= Autoregulation.easyUpTo(t)
        }
    }

    fun suggest(
        exercise: Exercise,
        history: List<Workout>,
        incrementKg: Double = 2.5,
        stallSessions: Int = 3,
        /** Rango de repeticiones (p. ej. 8..12): sube peso al llegar al tope en todas y vuelve al mínimo. */
        repRange: IntRange? = null,
    ): ProgressionSuggestion? {
        // Una sola búsqueda en el historial: las series y el RPE objetivo con que se hicieron
        val previous = WorkoutStats.previousExercise(exercise.id, history)
        val last = previous?.completedSets.orEmpty().filter { it.type != SetType.WARMUP }
        // El cardio no progresa por peso ni repeticiones
        if (last.isEmpty() || exercise.tracking == TrackingType.CARDIO) return null

        // Sin carga (peso corporal o tiempo) el 1RM estimado es 0 y no sirve para detectar estancamiento
        val loaded = exercise.tracking != TrackingType.DURATION && last.any { it.weightKg > 0.0 }
        val easy = wasEasy(last, previous?.targetRpe)
        val useRange = repRange != null && loaded && exercise.tracking == TrackingType.WEIGHT_REPS
        // Con rango, el 1RM estimado baja a propósito al subir peso y volver al mínimo: el estancamiento se mide
        // por reps con el mismo peso; sin rango, por el 1RM
        val stalled = loaded && if (useRange) isStalledInRange(exercise.id, history, stallSessions, repRange) else isStalled(exercise.id, history, stallSessions)
        return when {
            stalled -> {
                val sets = last.map { it.copy(weightKg = roundTo(it.weightKg * 0.9, incrementKg), completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.DELOAD, sets, "Llevas $stallSessions sesiones sin progresar: descarga al 90 %", stallSessions = stallSessions)
            }
            isUnloaded(exercise.tracking, last) -> unloaded(exercise.tracking, last)
            useRange -> byRange(last, repRange!!, incrementKg, easy)
            else -> fixedReps(last, incrementKg, easy)
        }
    }

    /** Por tiempo, o de repeticiones sin lastre: progresa sin tocar el peso. */
    private fun isUnloaded(tracking: TrackingType, last: List<WorkoutSet>) =
        tracking == TrackingType.DURATION || (tracking == TrackingType.REPS_ONLY && last.all { it.weightKg == 0.0 })

    /** Sin peso: +5 s por serie si es por tiempo, +1 repetición si no. */
    private fun unloaded(tracking: TrackingType, last: List<WorkoutSet>): ProgressionSuggestion =
        if (tracking == TrackingType.DURATION) {
            ProgressionSuggestion(SuggestionKind.ADD_TIME, last.map { it.copy(durationSec = it.durationSec + 5, completed = false, rpe = null) }, "+5 s por serie")
        } else {
            ProgressionSuggestion(SuggestionKind.ADD_ONE_REP, last.map { it.copy(reps = it.reps + 1, completed = false, rpe = null) }, "+1 repetición por serie")
        }

    /** Doble progresión clásica: las reps de la primera serie en todas → más peso; si no, igualarlas. */
    private fun fixedReps(last: List<WorkoutSet>, incrementKg: Double, easy: Boolean): ProgressionSuggestion {
        // Drop sets y similares no cuentan: menos reps a menos peso es lo esperado, no un fallo
        val work = last.filter { it.type == SetType.NORMAL || it.type == SetType.FAILURE || it.type == SetType.CLUSTER }.ifEmpty { last }
        val targetReps = work.first().reps
        val hitAll = work.all { it.reps >= targetReps }
        return when {
            hitAll && easy -> {
                val sets = last.map { it.copy(weightKg = it.weightKg + incrementKg, reps = targetReps, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.INCREASE_WEIGHT, sets, "Completaste todas las series: sube el peso")
            }
            !hitAll -> {
                val sets = last.map { it.copy(reps = targetReps, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.ADD_REPS, sets, "Mismo peso: intenta $targetReps reps en todas las series", targetReps = targetReps)
            }
            else -> ProgressionSuggestion(SuggestionKind.REPEAT, last.map { it.copy(completed = false, rpe = null) }, "RPE alto: repite y consolida")
        }
    }

    /**
     * Doble progresión con rango: si todas las series llegaron al tope del rango (y no fue al límite de RPE),
     * más peso y vuelta al mínimo; si no, mismo peso y una repetición más en cada serie (sin pasar del tope).
     */
    private fun byRange(last: List<WorkoutSet>, range: IntRange, incrementKg: Double, easy: Boolean): ProgressionSuggestion {
        // Igual que el estancamiento: las series efectivas al peso más alto (un drop set no cuenta)
        val working = last.filter { it.type == SetType.NORMAL || it.type == SetType.FAILURE }.ifEmpty { last }
        val top = working.maxOf { it.weightKg }
        val atTop = working.filter { it.weightKg == top }.all { it.reps >= range.last }
        return when {
            atTop && easy -> {
                val sets = last.map { it.copy(weightKg = it.weightKg + incrementKg, reps = range.first, completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.INCREASE_WEIGHT, sets, "Tope del rango en todas: sube el peso y vuelve a ${range.first}")
            }
            // En el tope pero al límite: consolidar antes de subir
            atTop -> ProgressionSuggestion(SuggestionKind.REPEAT, last.map { it.copy(completed = false, rpe = null) }, "RPE alto: repite y consolida")
            else -> {
                val sets = last.map { it.copy(reps = (it.reps + 1).coerceIn(range.first, range.last), completed = false, rpe = null) }
                ProgressionSuggestion(SuggestionKind.RANGE_ADD_REP, sets, "Mismo peso: +1 rep por serie hasta ${range.last}", targetReps = range.last)
            }
        }
    }

    /**
     * Estancado con rango: [sessions] sesiones seguidas con el mismo peso máximo sin subir la media de reps de
     * las series efectivas a ese peso (sin contar drop sets ni descargas, ni cuántas series se hicieron).
     * Si la última llegó al tope del rango en todas, no es estancamiento: toca subir (o consolidar).
     */
    fun isStalledInRange(exerciseId: String, history: List<Workout>, sessions: Int = 3, range: IntRange? = null): Boolean {
        val points = history.filter { it.isFinished }.sortedBy { it.startedAtEpochMs }.mapNotNull { w ->
            val sets = w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets }
                .filter { it.type == SetType.NORMAL || it.type == SetType.FAILURE }
            val top = sets.maxOfOrNull { it.weightKg } ?: return@mapNotNull null
            val atTop = sets.filter { it.weightKg == top }
            Triple(top, atTop.map { it.reps }.average(), atTop.all { range != null && it.reps >= range.last })
        }.takeLast(sessions + 1)
        if (points.size < sessions + 1 || points.last().third) return false
        val (weight, before) = points.first()
        return points.all { it.first == weight } && points.drop(1).all { it.second <= before }
    }

    /** True si el mejor 1RM estimado no ha mejorado en las últimas [sessions] sesiones. */
    fun isStalled(exerciseId: String, history: List<Workout>, sessions: Int = 3): Boolean {
        val points = WorkoutStats.exerciseProgress(exerciseId, history)
        if (points.size < sessions + 1) return false
        val earlier = points.dropLast(sessions)
        val before = earlier.maxOf { it.bestOneRepMax }
        // La sesión justo antes de la ventana (no el máximo de siempre: un bloque pesado de hace meses no puede
        // hacer que todo lo de ahora parezca descarga)
        val heaviest = earlier.last().heaviestWeight
        val window = points.takeLast(sessions)
        // Una sesión claramente más ligera en la ventana es una descarga (o un reinicio): aún no se puede juzgar,
        // y así la descarga no se pide una y otra vez. Sin carga (1RM 0) no hay forma de medir el estancamiento.
        val deloaded = window.any { it.heaviestWeight > 0 && it.heaviestWeight < heaviest * DELOAD_SIGN }
        return !deloaded && before > 0.0 && window.all { it.bestOneRepMax <= before }
    }

    /** Por debajo de este % del peso más alto usado antes, la sesión cuenta como descarga. */
    private const val DELOAD_SIGN = 0.95

    private fun roundTo(value: Double, step: Double) = Math.round(value / step) * step

    /** «8-12» → 8..12 (null si falta o no es válido). */
    fun parseRepRange(text: String?): IntRange? = text?.split('-')?.mapNotNull { it.trim().toIntOrNull() }
        ?.takeIf { it.size == 2 && it[0] in 1..it[1] }?.let { it[0]..it[1] }
}
