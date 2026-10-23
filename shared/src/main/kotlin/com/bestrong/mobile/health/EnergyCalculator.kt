package com.bestrong.mobile.health

import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.Workout
import com.bestrong.core.training.BodyProfile
import com.bestrong.core.training.Energy
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completa las calorías (totales y activas) y el pulso de un entreno terminado con la mejor
 * fuente disponible (ver [Energy]): reloj > pulso registrado durante el entreno > estimación.
 */
@Singleton
class EnergyCalculator @Inject constructor(
    private val repository: BeStrongRepository,
    private val settings: SettingsStore,
    private val reader: HealthConnectReader,
) {
    /**
     * Tus datos en [atEpochMs]: peso (medidas o Health Connect) de ese día o el anterior más cercano, altura, edad,
     * sexo y pulso en reposo. Corregir un entreno antiguo no le cambia las calorías por el peso de hoy.
     */
    suspend fun profile(atEpochMs: Long = System.currentTimeMillis()): BodyProfile {
        val s = settings.settings.value
        val snapshot = runCatching { reader.read() }.getOrNull()
        // De la base de datos: al arrancar la app en frío (entreno llegado del reloj) la lista en memoria aún está vacía
        val measured = repository.measurementsOnce().sortedByDescending { it.dateEpochMs }
        // El peso más reciente a esa fecha, sea apuntado a mano o de la báscula (Health Connect): uno manual de
        // hace un año ya no gana a la báscula de esta semana; sin ninguno anterior, el más antiguo que haya
        val dated = measured.mapNotNull { m -> m.weightKg?.let { m.dateEpochMs to it } } + snapshot?.weights.orEmpty()
        val weight = (dated.filter { it.first <= atEpochMs }.maxByOrNull { it.first } ?: dated.minByOrNull { it.first })?.second
        return BodyProfile(
            weightKg = weight,
            heightCm = s.heightCm?.toDouble() ?: snapshot?.heightCm,
            age = s.birthYear?.let { java.time.Instant.ofEpochMilli(atEpochMs).atZone(java.time.ZoneId.systemDefault()).year - it }?.takeIf { it in 10..100 },
            sex = s.sex,
            restingHr = snapshot?.restingHr,
        )
    }

    /**
     * [watchKcal]: calorías medidas por el reloj (null si se entrenó sin él). Los entrenos del
     * modo pareja no se calculan: tus datos corporales no son los de la otra persona.
     */
    suspend fun complete(workout: Workout, watchKcal: Int?): Workout {
        val end = workout.finishedAtEpochMs
        if (end == null || workout.athlete != null) return workout
        // Pulso: el del reloj de BeStrong; si no hay, el registrado en Health Connect en ese tiempo
        // (no se guarda en el entreno para no volver a exportarlo a Health Connect duplicado)
        val external = if (workout.heartRateSamples.isEmpty()) reader.heartRateDuring(workout.startedAtEpochMs, end) else emptyList()
        val heartRate = workout.heartRateSamples.ifEmpty { external }
        val result = Energy.forWorkout(
            workout = workout,
            profile = profile(workout.startedAtEpochMs),
            watchKcal = watchKcal,
            heartRate = heartRate,
        )
        return workout.copy(
            calories = result?.totalKcal ?: watchKcal,
            activeCalories = result?.activeKcal,
            caloriesSource = result?.source ?: watchKcal?.let { CaloriesSource.WATCH },
            avgHeartRate = workout.avgHeartRate ?: external.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt(),
            maxHeartRate = workout.maxHeartRate ?: external.maxOfOrNull { it.bpm },
        )
    }
}
