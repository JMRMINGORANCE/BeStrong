package com.bestrong.core

import com.bestrong.core.io.Backup
import com.bestrong.core.io.BackupData
import com.bestrong.core.library.Programs
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.session.WorkoutEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    @Test fun roundTripKeepsEverything() {
        val w = WorkoutEditor.finish(WorkoutEditor.addExercises(WorkoutEditor.empty(1_000), listOf(bench, squat)), 5_000)
        val data = BackupData(
            history = listOf(w),
            routines = Programs.all.first().toRoutines("es"),
            customExercises = listOf(bench.copy(id = "mi_ejercicio", name = "Mi ejercicio", isCustom = true)),
            measurements = listOf(BodyMeasurement(dateEpochMs = 10, weightKg = 80.5)),
        )
        assertEquals(data, Backup.decode(Backup.encode(data)))
    }

    @Test fun oldBackupWithoutVersionIsMigrated() {
        // Copia v0: entrenos sin updatedAtEpochMs ni schemaVersion
        val old = """{"app":"BeStrong","history":[{"id":"w1","name":"Viejo","startedAtEpochMs":100,"finishedAtEpochMs":900}],"routines":[]}"""
        val restored = Backup.decode(old)
        assertEquals(1, restored.history.size)
        assertEquals(900L, restored.history[0].updatedAtEpochMs)
    }

    @Test fun rejectsForeignOrFutureFiles() {
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"foo":1}""") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("no es json") }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"app":"BeStrong","schemaVersion":999}""") }
    }
}
