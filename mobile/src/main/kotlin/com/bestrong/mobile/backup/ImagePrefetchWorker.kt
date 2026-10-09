package com.bestrong.mobile.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Descarga por adelantado (solo con Wi-Fi) las animaciones e imágenes de los ejercicios
 * para que se vean en el gimnasio aunque no haya cobertura. Quedan en la caché de disco de Coil.
 */
class ImagePrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val media = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java).media()
        val loader = applicationContext.imageLoader
        media.images.values.forEach { img ->
            loader.execute(
                ImageRequest.Builder(applicationContext)
                    .data(img.url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "image-prefetch"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ImagePrefetchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
