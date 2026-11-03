package com.bestrong.mobile.backup

import com.bestrong.core.io.BackupData
import com.bestrong.mobile.data.BeStrongRepository
import com.bestrong.mobile.data.ExerciseMediaStore
import com.bestrong.mobile.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copia completa: lo de la base de datos (historial, rutinas, ejercicios, medidas, programa activo)
 * más ajustes y notas de técnica, que se guardan fuera de ella (la planificación de copias antiguas se ignora).
 */
@Singleton
class FullBackup @Inject constructor(
    private val repo: BeStrongRepository,
    private val settings: SettingsStore,
    private val media: ExerciseMediaStore,
) {
    suspend fun data(): BackupData = repo.backupData().copy(
        settings = settings.backupJson(),
        techniqueNotes = media.allNotes(),
    )

    /** Añade lo que falte (no borra nada); los ajustes de la copia sustituyen a los actuales. Devuelve cuántos elementos se añadieron. */
    suspend fun restore(data: BackupData): Int {
        val added = repo.restore(data)
        data.settings?.let { settings.restoreBackup(it) }
        return added + (data.techniqueNotes?.let { media.restoreNotes(it) } ?: 0)
    }
}
