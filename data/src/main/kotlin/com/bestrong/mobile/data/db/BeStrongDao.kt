package com.bestrong.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BeStrongDao {
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun workouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    suspend fun workoutsOnce(): List<WorkoutEntity>

    @Upsert
    suspend fun upsertWorkout(entity: WorkoutEntity)

    /** Importación en bloque: ejercicios nuevos y entrenos en una sola transacción. */
    @Upsert
    suspend fun upsertAll(exercises: List<CustomExerciseEntity>, workouts: List<WorkoutEntity>)

    @Query("SELECT json FROM workouts WHERE id = :id")
    suspend fun workoutJson(id: String): String?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String)

    /** Varios a la vez en una sola transacción (Room limita a ~999 parámetros: se trocea fuera). */
    @Query("DELETE FROM workouts WHERE id IN (:ids)")
    suspend fun deleteWorkouts(ids: List<String>)

    @Query("SELECT * FROM routines ORDER BY position, id")
    fun routines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY position, id")
    suspend fun routinesOnce(): List<RoutineEntity>

    @Upsert
    suspend fun upsertRoutine(entity: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Query("SELECT * FROM custom_exercises")
    fun customExercises(): Flow<List<CustomExerciseEntity>>

    @Upsert
    suspend fun upsertCustomExercise(entity: CustomExerciseEntity)

    @Query("DELETE FROM custom_exercises WHERE id = :id")
    suspend fun deleteCustomExercise(id: String)

    @Query("SELECT * FROM measurements ORDER BY date DESC")
    fun measurements(): Flow<List<MeasurementEntity>>

    @Upsert
    suspend fun upsertMeasurement(entity: MeasurementEntity)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteMeasurement(id: String)

    @Query("SELECT value FROM kv WHERE `key` = :key")
    fun value(key: String): Flow<String?>

    @Query("SELECT value FROM kv WHERE `key` = :key")
    suspend fun valueOnce(key: String): String?

    @Upsert
    suspend fun putValue(entity: KeyValueEntity)

    @Query("DELETE FROM kv WHERE `key` = :key")
    suspend fun deleteValue(key: String)
}
