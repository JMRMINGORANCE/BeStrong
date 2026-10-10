package com.bestrong.core

import com.bestrong.core.io.RoutineLink
import com.bestrong.core.model.Routine
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.WorkoutStats
import com.bestrong.core.sync.SyncContract
import com.bestrong.core.sync.WorkoutMerge
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 8: sincronización con un reloj simulado (los mensajes pasan por bytes, como en la
 * Data Layer, con retrasos y desorden) y rendimiento con un historial grande.
 */
class Phase8Test {

    /** Un dispositivo: su copia del entreno y la bandeja de mensajes pendientes del otro. */
    private class Device(var workout: Workout, var clock: Long) {
        val inbox = ArrayDeque<ByteArray>()

        fun edit(block: (Workout) -> Workout): ByteArray {
            clock += 1_000
            workout = block(workout).copy(updatedAtEpochMs = clock)
            return SyncContract.encodeWorkout(workout)
        }

        fun receiveOne() {
            val bytes = inbox.removeFirstOrNull() ?: return
            workout = WorkoutMerge.merge(workout, SyncContract.decodeWorkout(bytes))
            clock = maxOf(clock, workout.updatedAtEpochMs)
        }
    }

    private fun startWorkout(): Workout = Workout(
        id = "live",
        name = "Pierna",
        startedAtEpochMs = 0,
        exercises = listOf(
            WorkoutExercise(id = "sq", exercise = squat, sets = List(4) { i -> WorkoutSet(id = "sq$i", weightKg = 100.0, reps = 5) }),
            WorkoutExercise(id = "bp", exercise = bench, sets = List(4) { i -> WorkoutSet(id = "bp$i", weightKg = 80.0, reps = 8) }),
        ),
    )

    private fun complete(w: Workout, setId: String, weight: Double? = null): Workout = w.copy(
        exercises = w.exercises.map { e ->
            e.copy(sets = e.sets.map { s -> if (s.id == setId) s.copy(completed = true, weightKg = weight ?: s.weightKg) else s })
        },
    )

    private fun completedIds(w: Workout) = w.exercises.flatMap { e -> e.sets.filter { it.completed }.map { it.id } }.toSet()

    @Test
    fun simulatedWatch_neverLosesCompletedSets_andConverges() {
        val setIds = listOf("sq0", "sq1", "sq2", "sq3", "bp0", "bp1", "bp2", "bp3")
        repeat(200) { seed ->
            val rnd = Random(seed)
            val phone = Device(startWorkout(), clock = 10_000)
            val watch = Device(startWorkout(), clock = 10_500)
            val completedSomewhere = mutableSetOf<String>()
            repeat(30) {
                when (rnd.nextInt(4)) {
                    0 -> setIds.random(rnd).let { id ->
                        completedSomewhere += id
                        watch.inbox += phone.edit { complete(it, id) }
                    }
                    1 -> setIds.random(rnd).let { id ->
                        completedSomewhere += id
                        phone.inbox += watch.edit { complete(it, id, weight = 102.5) }
                    }
                    2 -> phone.receiveOne()
                    else -> watch.receiveOne()
                }
            }
            // Se entregan los mensajes pendientes y se hace un último intercambio completo
            while (phone.inbox.isNotEmpty() || watch.inbox.isNotEmpty()) {
                phone.receiveOne()
                watch.receiveOne()
            }
            watch.inbox += SyncContract.encodeWorkout(phone.workout)
            watch.receiveOne()
            phone.inbox += SyncContract.encodeWorkout(watch.workout)
            phone.receiveOne()

            assertEquals("semilla $seed: el móvil perdió series", completedSomewhere, completedIds(phone.workout))
            assertEquals("semilla $seed: no convergen", completedIds(phone.workout), completedIds(watch.workout))
            assertEquals("semilla $seed: contenido distinto", phone.workout.exercises, watch.workout.exercises)
        }
    }

    @Test
    fun merge_isIdempotent_andConflictsAreReported() {
        val base = startWorkout()
        val phone = complete(base, "sq0").copy(updatedAtEpochMs = 1_000)
        val watch = complete(base, "sq0", weight = 105.0).copy(updatedAtEpochMs = 2_000)
        val merged = WorkoutMerge.merge(phone, watch)
        assertEquals(merged, WorkoutMerge.merge(merged, merged))
        assertEquals(105.0, merged.exercises[0].sets[0].weightKg, 0.0)

        val conflicts = WorkoutMerge.conflicts(phone, watch)
        assertEquals(1, conflicts.size)
        assertEquals(0, conflicts[0].setIndex)
        assertEquals(100.0, conflicts[0].discarded.weightKg, 0.0)
        // Sin diferencias no hay conflicto
        assertTrue(WorkoutMerge.conflicts(phone, phone.copy(updatedAtEpochMs = 3_000)).isEmpty())
    }

    @Test
    fun largeHistory_statsStayFast() {
        val start = LocalDate.of(2020, 1, 1)
        val history = List(1000) { i ->
            finishedWorkout(
                "w$i",
                start.plusDays(i * 2L),
                squat to List(5) { done(100.0 + i % 40, 5) },
                bench to List(5) { done(70.0 + i % 30, 8) },
            )
        }
        val t0 = System.nanoTime()
        val bytes = SyncContract.encodeWorkouts(history)
        val decoded = SyncContract.decodeWorkouts(bytes)
        val records = WorkoutStats.personalRecords(decoded)
        val weeks = WorkoutStats.weeklySummary(decoded, 52, start.plusDays(2000), UTC)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(1000, decoded.size)
        assertTrue(records.isNotEmpty() && weeks.isNotEmpty())
        // Margen amplio para máquinas de CI lentas: en un móvil actual son unas decenas de ms
        assertTrue("1000 entrenos tardaron $ms ms", ms < 5_000)
    }

    @Test
    fun routineLink_rejectsCompressionBombs() {
        // 20 MB de espacios comprimidos caben en un enlace corto: no debe descomprimirse entero
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(ByteArray(20 * 1024 * 1024) { ' '.code.toByte() })
        deflater.finish()
        val buf = ByteArray(64 * 1024)
        val n = deflater.deflate(buf)
        deflater.end()
        val link = "bestrong://routine?d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf.copyOf(n))
        assertNull(RoutineLink.decode(link))
        assertNull(RoutineLink.decode("bestrong://routine?d=" + "A".repeat(20_000)))
        // Una rutina normal sigue funcionando
        assertNotNull(RoutineLink.decode(RoutineLink.encode(Routine(name = "Push"))))
    }
}
