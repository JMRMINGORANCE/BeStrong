package com.bestrong.mobile.health

import com.bestrong.core.model.CaloriesSource
import com.bestrong.core.model.Workout
import com.bestrong.core.training.BodyProfile
import com.bestrong.core.training.Energy
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.SettingsStore
import java.time.Year
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
    /** Tus datos: peso (medidas o Health Connect), altura, edad, sexo y pulso en reposo. */
    suspend fun profile(): BodyProfile {
        val s = settings.settings.value
        val snapshot = runCatching { reader.read() }.getOrNull()
        val weight = repository.measurements.value.sortedByDescending { it.dateEpochMs }.firstNotNullOfOrNull { it.weightKg }
            ?: snapshot?.weights?.maxByOrNull { it.first }?.second
        return BodyProfile(
            weightKg = weight,
            heightCm = s.heightCm?.toDouble() ?: snapshot?.heightCm,
            age = s.birthYear?.let { Year.now().value - it }?.takeIf { it in 10..100 },
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
            profile = profile(),
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
