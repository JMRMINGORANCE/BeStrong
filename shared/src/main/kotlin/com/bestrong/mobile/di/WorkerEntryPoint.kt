package com.bestrong.mobile.di

import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Acceso a dependencias desde los Workers de WorkManager (sin hilt-work). */
@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun repository(): com.bestrong.mobile.data.BeStrongRepository
    fun settings(): com.bestrong.mobile.data.SettingsStore
    fun media(): com.bestrong.mobile.data.ExerciseMediaStore
    fun backup(): com.bestrong.mobile.backup.FullBackup
    fun actions(): com.bestrong.mobile.WorkoutActions
    fun planning(): com.bestrong.mobile.data.PlanningStore
}
