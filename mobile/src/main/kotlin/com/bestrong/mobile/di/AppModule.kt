package com.bestrong.mobile.di

import android.content.Context
import com.bestrong.mobile.data.db.BeStrongDao
import com.bestrong.mobile.data.db.BeStrongDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ámbito de corrutinas que vive lo mismo que la app (sincronización, guardados en segundo plano). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeStrongDatabase = BeStrongDatabase.create(context)

    @Provides
    fun provideDao(db: BeStrongDatabase): BeStrongDao = db.dao()
}

/** Acceso a dependencias desde los Workers de WorkManager (sin hilt-work). */
@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun repository(): com.bestrong.mobile.data.BeStrongRepository
    fun settings(): com.bestrong.mobile.data.SettingsStore
    fun media(): com.bestrong.mobile.data.ExerciseMediaStore
}
