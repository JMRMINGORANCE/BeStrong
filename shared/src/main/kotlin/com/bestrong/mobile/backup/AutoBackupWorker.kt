package com.bestrong.mobile.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bestrong.core.io.Backup
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.mobile.di.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Copia de seguridad semanal en la carpeta que elija el usuario (Drive, almacenamiento local…):
 * un CSV compatible con Hevy/Strong y un JSON completo de rutinas e historial.
 * Además, Android Auto Backup guarda la base de datos en la cuenta de Google (ver backup_rules.xml).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val settingsStore = deps.settings()
        val folder = settingsStore.settings.value.backupFolderUri ?: return Result.success()
        return runCatching {
            val data = deps.backup().data()
            val tree = Uri.parse(folder)
            val date = LocalDate.now()
            write(tree, "bestrong-$date.csv", "text/csv", WorkoutCsv.export(data.history).toByteArray())
            write(tree, "bestrong-$date.json", "application/json", Backup.encode(data).toByteArray())
            settingsStore.update { it.copy(lastBackupEpochMs = System.currentTimeMillis()) }
        }.fold(
            { Result.success() },
            {
                Log.w("AutoBackup", "Copia fallida", it)
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            },
        )
    }

    private fun write(tree: Uri, name: String, mime: String, bytes: ByteArray) {
        val resolver = applicationContext.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, mime, name) ?: error("No se pudo crear $name")
        resolver.openOutputStream(doc, "w")?.use { it.write(bytes) } ?: error("No se pudo escribir $name")
    }

    companion object {
        private const val NAME = "auto-backup"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Copia inmediata (al elegir la carpeta). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}
