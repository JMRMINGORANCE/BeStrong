package com.bestrong.core.training

import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.HeartRateSample
import com.bestrong.core.stats.Sex
import kotlin.math.roundToInt

/**
 * Pulso durante el entreno a partir de las muestras del reloj.
 *
 * - Media **ponderada por tiempo**: cada lectura cuenta lo que dura hasta la siguiente (con un
 *   tope de [maxGapMs], para que un hueco sin sensor no pese de más). Una media simple de
 *   muestras se sesga cuando llegan a ritmos distintos (en lotes con la pantalla apagada).
 * - Máximo de todas las lecturas (también de las que llegan en lote).
 * - Serie reducida a una muestra cada [bucketSec] s (la media de cada tramo) para guardarla
 *   y exportarla a Health Connect sin ocupar demasiado.
 */
class HeartRateTracker(
    private val startedAtEpochMs: Long,
    private val bucketSec: Int = 10,
    private val maxGapMs: Long = 30_000,
) {
    private var hasLast = false
    private var lastBpm = 0
    private var lastAtMs = 0L
    private var weightedSum = 0.0
    private var weightMs = 0L
    private var maxBpm = 0
    private var count = 0
    private val buckets = sortedMapOf<Int, IntArray>() // tramo → [suma, n]

    /** Añade una lectura; las desordenadas o anteriores a la última se ignoran para la media. */
    fun add(bpm: Int, atEpochMs: Long) {
        if (bpm !in 25..250) return
        if (hasLast && atEpochMs > lastAtMs) {
            val dt = (atEpochMs - lastAtMs).coerceAtMost(maxGapMs)
            weightedSum += lastBpm.toDouble() * dt
            weightMs += dt
        }
        if (!hasLast || atEpochMs >= lastAtMs) {
            hasLast = true
            lastBpm = bpm
            lastAtMs = atEpochMs
        }
        if (bpm > maxBpm) maxBpm = bpm
        count++
        val offset = ((atEpochMs - startedAtEpochMs) / 1000).toInt().coerceAtLeast(0)
        val b = buckets.getOrPut(offset / bucketSec) { IntArray(2) }
        b[0] += bpm
        b[1] += 1
    }

    val latest: Int? get() = lastBpm.takeIf { hasLast }

    /** Media ponderada por tiempo (o la única lectura si solo hay una). */
    val average: Int?
        get() = when {
            count == 0 -> null
            weightMs == 0L -> lastBpm
            else -> (weightedSum / weightMs).roundToInt()
        }

    val max: Int? get() = maxBpm.takeIf { it > 0 }

    /** Serie para guardar con el entreno: un punto por tramo con la media de sus lecturas. */
    fun samples(): List<HeartRateSample> =
        buckets.map { (bucket, v) -> HeartRateSample(bucket * bucketSec, (v[0].toDouble() / v[1]).roundToInt()) }
}

/** Datos personales para calcular el gasto: cuantos más, más preciso. */
data class BodyProfile(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val age: Int? = null,
    val sex: Sex = Sex.MALE,
    /** Pulso en reposo (Health Connect); si no se sabe se usa 70. */
    val restingHr: Int? = null,
)

/** Resultado del cálculo: calorías totales, activas y de dónde salen. */
data class EnergyResult(val totalKcal: Int, val activeKcal: Int?, val source: CaloriesSource)

/**
 * Gasto energético de un entreno de fuerza, con la mejor fuente disponible:
 *
 * 1. **Reloj** (Health Services): sensores ópticos y el perfil del usuario. Es la medida.
 * 2. **Pulso** registrado durante el entreno (reloj o Health Connect) como medida de la
 *    **intensidad**: la reserva de frecuencia cardiaca media elige el MET del Compendio (3,5 / 5,0 /
 *    6,0). No se usan fórmulas aeróbicas pulso→calorías (Keytel…): en fuerza el pulso sube más que
 *    el consumo de oxígeno (respuesta presora) y sobrestiman el gasto hasta el doble.
 * 3. **MET** del Compendio de Actividades Físicas (Herrmann et al. 2024) según la densidad del
 *    entreno (series por minuto).
 *
 * En los dos últimos casos el MET se «corrige» con el metabolismo basal propio (Mifflin-St Jeor)
 * en lugar del MET estándar de 3,5 ml O₂/kg/min, que sobrestima en personas mayores, más pesadas
 * o con más grasa (Kozey et al. 2010).
 *
 * Las calorías activas son las totales menos el gasto en reposo durante ese tiempo: es lo que
 * suman Samsung Health y Health Connect sin contar dos veces el metabolismo basal.
 */
object Energy {
    /** Fuerza, varios ejercicios, 8–15 repeticiones (Compendio 02054). */
    const val STRENGTH_MET = 3.5

    /** Fuerza con poco descanso (Compendio 02052, esfuerzo vigoroso). */
    const val DENSE_STRENGTH_MET = 5.0

    /** Series por minuto activo a partir de las cuales el entreno se considera denso (≈1 min de descanso). */
    const val DENSE_SETS_PER_MIN = 0.5

    /** Metabolismo basal (kcal/día), Mifflin-St Jeor: la ecuación más precisa en adultos. Null si faltan datos. */
    fun bmrKcalPerDay(p: BodyProfile): Double? {
        val w = p.weightKg?.takeIf { it > 0 }
        val h = p.heightCm?.takeIf { it > 0 }
        val a = p.age?.takeIf { it > 0 }
        if (w == null || h == null || a == null) return null
        return 10 * w + 6.25 * h - 5 * a + if (p.sex == Sex.MALE) 5 else -161
    }

    /** Gasto en reposo por hora: el basal propio si se conoce; si no, 1 kcal/kg/h (1 MET estándar). */
    fun restingKcalPerHour(p: BodyProfile): Double? =
        bmrKcalPerDay(p)?.let { it / 24 } ?: p.weightKg?.takeIf { it > 0 }?.let { it * 1.0 }

    /** MET según la densidad del entreno (series completadas por minuto activo). */
    fun strengthMet(completedSets: Int, activeSeconds: Long): Double {
        if (activeSeconds <= 0) return STRENGTH_MET
        val setsPerMin = completedSets / (activeSeconds / 60.0)
        return if (setsPerMin >= DENSE_SETS_PER_MIN) DENSE_STRENGTH_MET else STRENGTH_MET
    }

    /** kcal = MET × gasto en reposo por hora × horas activas. Null si no hay peso. */
    fun metKcal(met: Double, activeSeconds: Long, p: BodyProfile): Double? {
        if (activeSeconds <= 0) return null
        return restingKcalPerHour(p)?.let { met * it * activeSeconds / 3600.0 }
    }

    /** Compatibilidad: estimación MET estándar con solo peso y duración. */
    fun estimateStrengthKcal(activeSeconds: Long, bodyWeightKg: Double?, met: Double = STRENGTH_MET): Int? =
        metKcal(met, activeSeconds, BodyProfile(weightKg = bodyWeightKg))?.roundToInt()

    /** Fuerza vigorosa: halterofilia, culturismo (Compendio 02050). */
    const val VIGOROUS_STRENGTH_MET = 6.0

    /** Pulso máximo estimado (Tanaka 2001: 208 − 0,7 × edad); 190 si no se sabe la edad. */
    fun maxHr(age: Int?): Int = age?.let { (208 - 0.7 * it).roundToInt() } ?: 190

    /** Reserva de frecuencia cardiaca usada (0–1) con el pulso medio del entreno. */
    fun heartRateReserve(avgBpm: Int, p: BodyProfile): Double {
        val rest = p.restingHr ?: 70
        val max = maxHr(p.age)
        if (max <= rest) return 0.0
        return ((avgBpm - rest).toDouble() / (max - rest)).coerceIn(0.0, 1.0)
    }

    /** MET según la intensidad que marca el pulso (reserva <35 %, 35–55 %, >55 %). */
    fun metFromHeartRate(avgBpm: Int, p: BodyProfile): Double {
        val hrr = heartRateReserve(avgBpm, p)
        return when {
            hrr < 0.35 -> STRENGTH_MET
            hrr < 0.55 -> DENSE_STRENGTH_MET
            else -> VIGOROUS_STRENGTH_MET
        }
    }

    /** Media de la serie de pulso; null si cubre menos de la mitad del entreno. */
    fun coveredAverageBpm(samples: List<HeartRateSample>, bucketSec: Int, activeSeconds: Long): Int? {
        val covered = samples.size.toLong() * bucketSec
        val enough = samples.isNotEmpty() && activeSeconds > 0 && covered * 2 >= activeSeconds
        return if (enough) samples.map { it.bpm }.average().roundToInt() else null
    }

    /** Activas = totales − reposo durante el tiempo activo (nunca negativas). */
    fun activeKcal(totalKcal: Int, activeSeconds: Long, p: BodyProfile): Int? {
        val resting = restingKcalPerHour(p) ?: return null
        return (totalKcal - resting * activeSeconds / 3600.0).coerceAtLeast(0.0).roundToInt()
    }

    /**
     * El mejor cálculo posible: reloj > pulso > MET. [watchKcal] son las calorías del reloj;
     * [heartRate] la serie de pulso (del reloj o de Health Connect) con su tamaño de tramo.
     */
    fun compute(
        activeSeconds: Long,
        completedSets: Int,
        profile: BodyProfile,
        watchKcal: Int? = null,
        heartRate: List<HeartRateSample> = emptyList(),
        bucketSec: Int = 10,
    ): EnergyResult? {
        val hrAvg = coveredAverageBpm(heartRate, bucketSec, activeSeconds)
        val source = when {
            watchKcal != null && watchKcal > 0 -> CaloriesSource.WATCH
            hrAvg != null -> CaloriesSource.HEART_RATE
            else -> CaloriesSource.ESTIMATE
        }
        val total = when (source) {
            CaloriesSource.WATCH -> watchKcal?.toDouble()
            CaloriesSource.HEART_RATE -> hrAvg?.let { metKcal(metFromHeartRate(it, profile), activeSeconds, profile) }
            CaloriesSource.ESTIMATE -> metKcal(strengthMet(completedSets, activeSeconds), activeSeconds, profile)
        }
        return total?.roundToInt()?.let { EnergyResult(it, activeKcal(it, activeSeconds, profile), source) }
    }
}
