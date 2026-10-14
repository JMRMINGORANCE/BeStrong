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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/**
 * Copia de seguridad semanal en la carpeta que elija el usuario (Drive, almacenamiento local…):
 * un CSV compatible con Hevy/Strong y un JSON completo de rutinas e historial.
 * Además, Android Auto Backup guarda la base de datos en la cuenta de Google (ver backup_rules.xml).
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
        val settingsStore = deps.settings()
        val tree = settingsStore.settings.value.backupFolderUri?.let(Uri::parse)
        // Permiso revocado (carpeta borrada, app de archivos desinstalada…): se desactiva en vez de fallar cada semana
        val granted = tree != null && applicationContext.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (tree == null || !granted) {
            // Se vuelve a mirar: si el usuario acaba de quitar la carpeta él mismo, no es una carpeta perdida
            val lost = tree != null && settingsStore.settings.value.backupFolderUri == tree.toString()
            if (lost) {
                Log.w("AutoBackup", "Sin permiso sobre la carpeta de copias: se desactiva")
                // Se avisa en Perfil: sin esto las copias se paraban sin que nadie lo supiera
                settingsStore.update { it.copy(backupFolderUri = null, backupFolderLost = true) }
                com.bestrong.mobile.notify.Notifier.backupStopped(applicationContext, folderLost = true)
            }
            // Sin carpeta no hay nada que hacer; con la carpeta perdida no se ha copiado nada («Copiar ahora» lo dice)
            return if (lost) Result.failure(androidx.work.workDataOf(OUT_FOLDER_LOST to true)) else Result.success()
        }
        // La semanal y «Copiar ahora» van con nombres distintos: que no escriban a la vez el archivo del día
        return writeLock.withLock { backUp(deps, settingsStore, tree) }
    }

    private suspend fun backUp(deps: WorkerEntryPoint, settingsStore: com.bestrong.mobile.data.SettingsStore, tree: Uri): Result {
        return runCatching {
            val data = deps.backup().data()
            val date = LocalDate.now()
            // Prefijo propio: la rotación nunca toca las exportaciones manuales («bestrong-fecha.csv»)
            write(tree, "$PREFIX$date.csv", "text/csv", WorkoutCsv.export(data.history).toByteArray())
            write(tree, "$PREFIX$date.json", "application/json", Backup.encode(data).toByteArray())
            settingsStore.update { it.copy(lastBackupEpochMs = System.currentTimeMillis(), backupFailNotified = false) }
            // Sin rotación, cada semana se acumulan varios MB en la carpeta (Drive) para siempre
            runCatching { pruneOld(tree) }.onFailure { Log.w("AutoBackup", "No se pudieron borrar copias antiguas", it) }
        }.fold(
            { Result.success() },
            {
                Log.w("AutoBackup", "Copia fallida", it)
                if (runAttemptCount < 2) {
                    Result.retry()
                } else {
                    // Fallo definitivo y más de dos semanas sin copia buena: se avisa una vez (no nada más elegir la
                    // carpeta: el primer intento puede fallar solo por estar sin conexión)
                    val s = settingsStore.settings.value
                    // Desde la última copia buena o, si nunca salió bien, desde que se eligió la carpeta
                    val since = maxOf(s.lastBackupEpochMs, s.backupFolderSetEpochMs)
                    if (since > 0 && System.currentTimeMillis() - since > STALE_MS && !s.backupFailNotified) {
                        com.bestrong.mobile.notify.Notifier.backupStopped(applicationContext, folderLost = false)
                        settingsStore.update { it.copy(backupFailNotified = true) }
                    }
                    Result.failure()
                }
            },
        )
    }

    private fun write(tree: Uri, name: String, mime: String, bytes: ByteArray) {
        val resolver = applicationContext.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        // Primero se escribe la copia nueva y solo si sale bien se borra la del mismo día (reintento):
        // así un fallo a medias nunca deja sin la última copia buena. No se trunca con "wt" porque
        // no todos los proveedores lo admiten.
        val old = children(tree)[name]
        val doc = DocumentsContract.createDocument(resolver, parent, mime, name) ?: error("No se pudo crear $name")
        val written = runCatching { resolver.openOutputStream(doc, "w")?.use { it.write(bytes) } ?: error("No se pudo escribir $name") }
        if (written.isFailure) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            written.getOrThrow()
        }
        if (old != null && old != doc) {
            DocumentsContract.deleteDocument(resolver, old)
            // El proveedor la habrá creado como «nombre (1)»: se le devuelve el nombre si se puede
            runCatching { DocumentsContract.renameDocument(resolver, doc, name) }
        }
    }

    private fun children(tree: Uri): Map<String, Uri> = children(applicationContext, tree)

    /** Deja solo las [KEEP] copias más recientes de cada tipo (el nombre lleva la fecha ISO, así que ordena bien). */
    private fun pruneOld(tree: Uri) {
        val resolver = applicationContext.contentResolver
        children(tree).filterKeys { BACKUP_NAME.matches(it) }.entries
            .groupBy { it.key.substringAfterLast('.') }
            .values.forEach { files ->
                files.sortedByDescending { it.key }.drop(KEEP).forEach { DocumentsContract.deleteDocument(resolver, it.value) }
            }
    }

    companion object {
        /** Copia pedida a mano (una a la vez). */
        private const val NOW_NAME = "auto-backup-now"

        /** Sin copia buena en más de esto (es semanal), un fallo definitivo avisa. */
        private const val STALE_MS = 14 * 86_400_000L

        private const val NAME = "auto-backup"
        private const val KEEP = 8
        private const val PREFIX = "bestrong-auto-"
        private val BACKUP_NAME = Regex("""bestrong-auto-\d{4}-\d{2}-\d{2}( \(\d+\))?\.(csv|json)""")

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            // La primera copia la hace runNow() al elegir la carpeta
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).setInitialDelay(7, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Nombre → documento de los archivos de la carpeta. */
        private fun children(context: Context, tree: Uri): Map<String, Uri> {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildMap {
                    while (c.moveToNext()) put(c.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
                }
            }.orEmpty()
        }

        /** Las copias automáticas completas (JSON) de la carpeta [tree], la más reciente primero: (fecha, documento). */
        fun listJson(context: Context, tree: Uri): List<Pair<String, Uri>> =
            children(context, tree).filterKeys { it.startsWith(PREFIX) && it.endsWith(".json") }
                .entries.sortedByDescending { it.key }
                .map { (name, uri) -> name.removePrefix(PREFIX).removeSuffix(".json") to uri }

        private val writeLock = kotlinx.coroutines.sync.Mutex()

        /** En el resultado de una copia fallida: la carpeta ya no estaba disponible. */
        const val OUT_FOLDER_LOST = "folder_lost"

        /** Lanza una copia ya, si no hay otra en marcha (al elegir la carpeta, «Copiar ahora»). */
        fun runNow(context: Context): androidx.work.Operation =
            // Única: varios toques seguidos no escriben a la vez el mismo archivo del día
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, androidx.work.ExistingWorkPolicy.KEEP, androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>().build())

        /**
         * Copia ya y espera a que acabe: la pedida o, si ya había una en marcha (no se lanza otra), esa. Devuelve
         * true si salió bien, false si falló y null si la carpeta ya no estaba.
         */
        suspend fun runNowAndWait(context: Context): Boolean? {
            val wm = WorkManager.getInstance(context)
            val request = androidx.work.OneTimeWorkRequestBuilder<AutoBackupWorker>().build()
            val running = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                wm.enqueueUniqueWork(NOW_NAME, androidx.work.ExistingWorkPolicy.KEEP, request).result.get()
                wm.getWorkInfosForUniqueWork(NOW_NAME).get()
            }
            val target = running.firstOrNull { it.id == request.id }?.id ?: running.firstOrNull { !it.state.isFinished }?.id ?: request.id
            val info = wm.getWorkInfoByIdFlow(target).first { it == null || it.state.isFinished }
            return when {
                info?.state == androidx.work.WorkInfo.State.SUCCEEDED -> true
                info?.outputData?.getBoolean(OUT_FOLDER_LOST, false) == true -> null
                else -> false
            }
        }
    }
}
