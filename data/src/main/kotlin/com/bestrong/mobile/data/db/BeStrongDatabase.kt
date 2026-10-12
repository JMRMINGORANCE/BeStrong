package com.bestrong.mobile.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutEntity::class, RoutineEntity::class, CustomExerciseEntity::class, MeasurementEntity::class, KeyValueEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BeStrongDatabase : RoomDatabase() {
    abstract fun dao(): BeStrongDao

    companion object {
        fun create(context: Context): BeStrongDatabase =
            Room.databaseBuilder(context, BeStrongDatabase::class.java, "bestrong.db").build()
    }
}
