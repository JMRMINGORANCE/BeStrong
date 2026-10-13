package com.bestrong.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bestrong.core.io.Backup
import com.bestrong.core.io.BackupData
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ActiveProgram
import com.bestrong.mobile.data.db.BeStrongDatabase
import com.bestrong.mobile.data.db.WorkoutEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Base de datos Room real (en memoria) + repositorio: persistencia versionada, fusión y copias. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: BeStrongDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var repo: BeStrongRepository
    private val bench = ExerciseLibrary.byId("bench_press")!!

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BeStrongDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repo = BeStrongRepository(db.dao(), scope)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test fun oldJsonWithoutVersionIsMigratedOnRead() = runBlocking {
        // Entreno guardado por una versión antigua (sin schemaVersion ni updatedAtEpochMs)
        db.dao().upsertWorkout(WorkoutEntity("w1", 100, """{"id":"w1","name":"Viejo","startedAtEpochMs":100,"finishedAtEpochMs":900}"""))
        val w = repo.historyOnce().single()
        assertEquals("Viejo", w.name)
        assertEquals(900L, w.updatedAtEpochMs)
    }

    @Test fun remoteActiveWorkoutIsMergedSetBySet() = runBlocking {
        val base = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench))
        repo.setActive(base)
        val local = repo.activeWorkout.filterNotNull().first()
        val we = local.exercises[0]
        // El móvil completa la serie 1…
        repo.updateActive { WorkoutEditor.toggleSetCompleted(it, we.id, we.sets[0].id) }
        // …y llega del reloj la serie 2 completada (sobre el estado inicial, más reciente)
        val watch = WorkoutEditor.toggleSetCompleted(local, we.id, we.sets[1].id).copy(updatedAtEpochMs = System.currentTimeMillis() + 10_000)
        repo.applyRemoteActive(watch)
        val merged = withTimeout(5_000) { repo.activeWorkout.first { w -> w?.exercises?.get(0)?.sets?.count { it.completed } == 2 } }
        assertEquals(listOf(true, true, false), merged!!.exercises[0].sets.map { it.completed })
    }

    @Test fun backupRestoreAddsOnlyMissing() = runBlocking {
        val w = WorkoutEditor.finish(WorkoutEditor.addExercises(WorkoutEditor.empty(1_000), listOf(bench)), 5_000)
        repo.saveWorkout(w)
        val data = BackupData(
            history = listOf(w, w.copy(id = "otro", startedAtEpochMs = 9_000, name = "Otro")),
            measurements = listOf(BodyMeasurement(id = "m1", dateEpochMs = 1, weightKg = 80.0)),
        )
        assertEquals(2, repo.restore(data))
        assertEquals(2, repo.historyOnce().size)
        assertEquals(0, repo.restore(data))
    }

    @Test fun activeProgramRoundTrip() = runBlocking {
        val p = ActiveProgram("gzclp", 123, mapOf("squat" to 100.0), weeklyGoal = 4)
        repo.setActiveProgram(p)
        assertEquals(p, withTimeout(5_000) { repo.activeProgram.filterNotNull().first() })
        repo.setActiveProgram(null)
        assertNull(withTimeout(5_000) { repo.activeProgram.first { it == null } })
    }

    @Test fun backupCarriesActiveProgramAndExtras() = runBlocking {
        val p = ActiveProgram("gzclp", 123, mapOf("squat" to 100.0))
        repo.setActiveProgram(p)
        val extras = repo.backupData().copy(
            planning = JsonObject(mapOf("minutesPerSession" to JsonPrimitive(45))),
            settings = JsonObject(mapOf("rest" to JsonPrimitive(90))),
            techniqueNotes = mapOf("squat" to "Rodillas fuera"),
        )
        val decoded = Backup.decode(Backup.encode(extras))
        assertEquals(extras, decoded)
        // Copia antigua sin los campos nuevos: se lee igual
        assertNull(Backup.decode("""{"app":"BeStrong","schemaVersion":1}""").activeProgram)
        // El programa solo se restaura si no hay ninguno activo
        repo.setActiveProgram(null)
        assertEquals(1, repo.restore(decoded))
        assertEquals(p, withTimeout(5_000) { repo.activeProgram.filterNotNull().first() })
        assertEquals(0, repo.restore(decoded))
    }

    @Test fun activeOnceReadsDatabase() = runBlocking {
        assertNull(repo.activeOnce())
        val w = WorkoutEditor.empty(0)
        repo.setActive(w)
        assertEquals(w.id, repo.activeOnce()?.id)
    }
}
