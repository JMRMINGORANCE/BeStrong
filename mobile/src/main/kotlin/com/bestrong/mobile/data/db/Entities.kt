package com.bestrong.mobile.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Los modelos de dominio se guardan como JSON; las columnas extra sirven para ordenar/filtrar. */
@Entity(tableName = "workouts", indices = [Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val json: String,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val json: String,
)

@Entity(tableName = "custom_exercises")
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val json: String,
)

@Entity(tableName = "measurements", indices = [Index("date")])
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val json: String,
)

/** Almacén clave-valor (entrenamiento activo, etc.). */
@Entity(tableName = "kv")
data class KeyValueEntity(
    @PrimaryKey val key: String,
    val value: String,
)
