package com.bestrong.core.training

/** Tipos de cardio que el cálculo de calorías distingue. */
enum class CardioKind { RUN, WALK, TREADMILL, BIKE, STATIONARY_BIKE, ELLIPTICAL, ROWER, STAIRS, JUMP_ROPE }

/** Un bloque de cardio dentro del entreno (una serie completada de un ejercicio de cardio). */
data class CardioBlock(
    val kind: CardioKind,
    val durationSec: Int,
    val distanceM: Double = 0.0,
    /** Segundo del entreno en que terminó (para buscar su pulso); null si no se sabe. */
    val endOffsetSec: Int? = null,
) {
    val startOffsetSec: Int? get() = endOffsetSec?.let { (it - durationSec).coerceAtLeast(0) }
}

/**
 * Gasto del cardio, con la mejor fuente disponible para cada bloque:
 *
 * - **Velocidad** en cinta, carrera y caminata (ecuaciones metabólicas del ACSM: VO₂ = 3,5 +
 *   0,1·v caminando o 0,2·v corriendo, v en m/min) y **potencia** en el remo (vatios a partir del
 *   ritmo, P = 2,8 / (s/m)³, y VO₂ de Hagerman: 14,72·W + 250 ml/min). Son medidas del trabajo.
 * - **Pulso** del tramo (reserva de frecuencia cardiaca ≈ reserva de VO₂, Swain 1997), con el VO₂máx
 *   estimado de Uth (15,3 × FCmáx / FCreposo). En cardio sí vale: pulso y consumo de oxígeno
 *   suben a la par. Recoge la resistencia o la inclinación que la app no conoce.
 * - **MET** del Compendio de Actividades Físicas para la máquina, corregido con tu basal.
 *
 * En la cinta no se conoce la inclinación: la velocidad da el mínimo y el pulso, si lo hay, manda.
 */
object CardioEnergy {
    private val byExerciseId = mapOf(
        "running" to CardioKind.RUN,
        "walking" to CardioKind.WALK,
        "cycling" to CardioKind.BIKE,
        "treadmill" to CardioKind.TREADMILL,
        "elliptical" to CardioKind.ELLIPTICAL,
        "stationary_bike" to CardioKind.STATIONARY_BIKE,
        "rowing_machine" to CardioKind.ROWER,
        "stair_climber" to CardioKind.STAIRS,
        "jump_rope" to CardioKind.JUMP_ROPE,
    )

    fun kindOf(exerciseId: String): CardioKind? = byExerciseId[exerciseId]

    /** MET del Compendio (esfuerzo moderado) cuando no hay ni velocidad útil ni pulso. */
    fun defaultMet(kind: CardioKind): Double = when (kind) {
        CardioKind.RUN -> 9.8 // ≈ 9,7 km/h
        CardioKind.TREADMILL -> 8.0
        CardioKind.WALK -> 3.5
        CardioKind.BIKE -> 7.5
        CardioKind.STATIONARY_BIKE -> 7.0
        CardioKind.ELLIPTICAL -> 5.0
        CardioKind.ROWER -> 7.0
        CardioKind.STAIRS -> 9.0
        CardioKind.JUMP_ROPE -> 11.8
    }

    /** Velocidad en m/min si la distancia es creíble para ese cardio; null si no. */
    fun speedMPerMin(block: CardioBlock): Double? {
        if (block.durationSec <= 0 || block.distanceM <= 0) return null
        val v = block.distanceM / (block.durationSec / 60.0)
        val plausible = when (block.kind) {
            CardioKind.RUN, CardioKind.WALK, CardioKind.TREADMILL -> v in 20.0..400.0 // 1,2–24 km/h
            CardioKind.BIKE -> v in 50.0..1000.0
            CardioKind.ROWER -> v in 100.0..400.0 // 1:15–5:00 /500 m
            else -> false
        }
        return v.takeIf { plausible }
    }

    /** VO₂ bruto (ml/kg/min) por la velocidad (ACSM), sin inclinación. Más de 8 km/h = corriendo. */
    fun acsmVo2(speedMPerMin: Double): Double =
        if (speedMPerMin < RUN_THRESHOLD_M_PER_MIN) 3.5 + 0.1 * speedMPerMin else 3.5 + 0.2 * speedMPerMin

    /** Potencia en el remo (vatios) a partir del ritmo (fórmula de Concept2). */
    fun rowerWatts(speedMPerMin: Double): Double {
        val secPerM = 60.0 / speedMPerMin
        return 2.8 / (secPerM * secPerM * secPerM)
    }

    /** kcal/min del remo con la potencia (Hagerman: VO₂ = 14,72·W + 250 ml/min; 5 kcal por litro de O₂). */
    fun rowerKcalPerMin(watts: Double): Double = (14.72 * watts + 250.39) / 1000.0 * KCAL_PER_L_O2

    /** VO₂máx estimado (ml/kg/min), Uth 2004: 15,3 × FCmáx / FCreposo. */
    fun vo2Max(p: BodyProfile): Double = 15.3 * Energy.maxHr(p.age) / (p.restingHr ?: Energy.DEFAULT_RESTING_HR)

    /** VO₂ (ml/kg/min) con el pulso medio del tramo: %reserva de FC ≈ %reserva de VO₂ (Swain). */
    fun hrVo2(avgBpm: Int, p: BodyProfile): Double {
        val hrr = Energy.heartRateReserve(avgBpm, p)
        return 3.5 + hrr * (vo2Max(p) - 3.5)
    }

    /** kcal a partir del VO₂ relativo: 5 kcal por litro de oxígeno. */
    fun vo2Kcal(vo2MlKgMin: Double, weightKg: Double, durationSec: Int): Double =
        vo2MlKgMin * weightKg / 1000.0 * KCAL_PER_L_O2 * durationSec / 60.0

    /** Bici al aire libre: MET del Compendio según la velocidad media. */
    fun bikeMet(speedMPerMin: Double): Double {
        val kmh = speedMPerMin * 60 / 1000
        return when {
            kmh < 16 -> 4.0
            kmh < 19 -> 6.8
            kmh < 22.5 -> 8.0
            kmh < 25.5 -> 10.0
            kmh < 30.5 -> 12.0
            else -> 15.8
        }
    }

    const val KCAL_PER_L_O2 = 5.0
    const val RUN_THRESHOLD_M_PER_MIN = 134.0
}
