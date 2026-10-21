package com.bestrong.core.stats

import com.bestrong.core.model.Workout

/**
 * Carga para unas repeticiones a un RPE: el RPE dice cuántas reps quedaban en recámara (RPE 8 = 2), así que
 * 5 reps a RPE 8 equivalen a una serie de 7 al fallo. Se invierte la misma fórmula del 1RM estimado de la app
 * (Epley), para que la tabla y las marcas cuadren.
 */
object RpeLoad {
    private const val EPLEY = 30.0
    private const val MAX_RPE = 10.0
    private const val RECENT_WEEKS = 8L
    private const val WEEK_MS = 7 * 86_400_000L

    /**
     * RPE por debajo del cual no se usa para el 1RM: con tantas reps en recámara la estimación no es fiable (y el
     * reloj, al estimarlo por la velocidad, pone 6 a todo lo que deja 4 o más).
     */
    private const val MIN_RELIABLE_RPE = 7.0

    /**
     * 1RM estimado de una serie contando las reps en recámara si se anotó un RPE de 7 a 10 (5 × 100 kg a RPE 8 equivale
     * a 7 al fallo); sin RPE, el de siempre. Las series que no cuentan para récords dan 0.
     */
    fun oneRepMaxOf(set: com.bestrong.core.model.WorkoutSet): Double {
        val rpe = set.rpe?.takeIf { it in MIN_RELIABLE_RPE..MAX_RPE }
        // Unilateral: el lado más flojo, como el 1RM de siempre
        val usable = set.countsForRecords && set.recordReps > 0 && set.weightKg > 0
        return if (rpe != null && usable) set.weightKg / fraction(set.recordReps, rpe) else set.estimatedOneRepMax
    }

    /** Fracción del 1RM para [reps] a [rpe] (1 rep a RPE 10 = 100 %). */
    fun fraction(reps: Int, rpe: Double): Double {
        val toFailure = reps + (MAX_RPE - rpe).coerceAtLeast(0.0)
        return if (toFailure <= 1.0) 1.0 else 1 / (1 + toFailure / EPLEY)
    }

    fun weightFor(oneRepMaxKg: Double, reps: Int, rpe: Double): Double = oneRepMaxKg * fraction(reps, rpe)

    /**
     * [kg] a un peso que se pueda usar: en kg, con los discos o mancuernas del [gym] si los hay para ese
     * material; si no (máquinas, poleas, libras…), al incremento del ejercicio [stepKg].
     */
    fun roundToLoadable(
        kg: Double,
        unit: com.bestrong.core.util.WeightUnit,
        stepKg: Double,
        gym: com.bestrong.core.training.GymProfile?,
        equipment: com.bestrong.core.model.Equipment,
    ): Double {
        // ¿Tiene el gimnasio discos o mancuernas para este material? Se prueba con un peso imposible: si lo cambia,
        // es que redondea (y entonces un peso que ya se puede montar se queda tal cual)
        val kgGym = gym?.takeIf { unit == com.bestrong.core.util.WeightUnit.KG }
        val probe = kg + PROBE_KG
        val viaGym = kgGym?.takeIf { kotlin.math.abs(it.nearestLoad(probe, equipment) - probe) > 1e-9 }?.nearestLoad(kg, equipment)
        val step = unit.fromKg(stepKg)
        // Redondeo «a la mitad hacia arriba» (62,5 con paso de 5 → 65, no 60 como el redondeo al par)
        return viaGym ?: if (step <= 0) kg else unit.toKg(kotlin.math.floor(unit.fromKg(kg) / step + 0.5) * step)
    }

    /**
     * Mejor 1RM estimado de las últimas [RECENT_WEEKS] semanas (una marca de hace años no dice lo que levantas
     * hoy); si no hay, el mejor de todo el historial. Null si nunca se hizo con peso.
     */
    fun recentOneRepMax(exerciseId: String, history: List<Workout>, nowEpochMs: Long): Double? {
        val best = { ws: List<Workout> ->
            ws.flatMap { w -> w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.completedSets } }
                .maxOfOrNull { oneRepMaxOf(it) }?.takeIf { it > 0 }
        }
        val finished = history.filter { it.isFinished }
        return best(finished.filter { it.startedAtEpochMs >= nowEpochMs - RECENT_WEEKS * WEEK_MS }) ?: best(finished)
    }

    /** Peso de prueba que ningún disco ni mancuerna da (para saber si el gimnasio redondea ese material). */
    private const val PROBE_KG = 0.137
}
