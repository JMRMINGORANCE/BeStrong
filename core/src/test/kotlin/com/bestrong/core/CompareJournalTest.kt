package com.bestrong.core

import com.bestrong.core.io.Persistence
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.Journal
import com.bestrong.core.stats.SessionCompare
import com.bestrong.core.stats.WorkoutStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comparador de sesiones y diario (etiquetas). */
class CompareJournalTest {
    private val day = 86_400_000L
    private fun ex(id: String) = ExerciseLibrary.byId(id)!!
    private fun w(
        at: Long,
        kg: Double,
        id: String = "bench_press",
        routine: String? = "torso",
        tags: List<String> = emptyList(),
        name: String = "Torso A",
    ) = Workout(
        id = "w$at$id",
        name = name,
        routineId = routine,
        startedAtEpochMs = at,
        finishedAtEpochMs = at + 3_600_000,
        exercises = listOf(WorkoutExercise(exercise = ex(id), sets = List(3) { WorkoutSet(weightKg = kg, reps = 8, completed = true) })),
        tags = tags,
    )

    @Test fun defaultPartnerIsPreviousOfSameRoutine() {
        val old = w(1 * day, 80.0)
        val other = w(2 * day, 50.0, id = "squat", routine = "pierna", name = "Pierna")
        val now = w(3 * day, 82.5)
        assertEquals(old.id, SessionCompare.defaultPartner(now, listOf(now, other, old))?.id)
    }

    @Test fun defaultPartnerFallsBackToSharedExercises() {
        val shared = w(1 * day, 80.0, routine = null)
        val unrelated = w(2 * day, 50.0, id = "squat", routine = null)
        val now = w(3 * day, 82.5, routine = null)
        assertEquals(shared.id, SessionCompare.defaultPartner(now, listOf(now, unrelated, shared))?.id)
        // Nada antes: no hay pareja
        assertNull(SessionCompare.defaultPartner(shared, listOf(now, shared)))
    }

    @Test fun compareTotalsAndRows() {
        val a = w(2 * day, 82.5)
        val b = w(1 * day, 80.0)
        val r = SessionCompare.compare(a, b)
        assertEquals(82.5 * 8 * 3, r.totalsA.volumeKg, 1e-6)
        assertEquals(80.0 * 8 * 3, r.totalsB.volumeKg, 1e-6)
        assertEquals(1, r.rows.size)
        assertTrue(r.rows[0].bestA > r.rows[0].bestB)
        assertEquals(1, SessionCompare.setDirection(r.rows[0].setsA[0], r.rows[0].setsB[0]))
        assertEquals(0, SessionCompare.setDirection(r.rows[0].setsA[0], r.rows[0].setsA[1]))
        assertEquals(2, SessionCompare.workSets(listOf(WorkoutSet(type = SetType.WARMUP), WorkoutSet(), WorkoutSet())).size)
    }

    @Test fun tagsNormalizeAndCount() {
        assertEquals("dormí-mal", Journal.normalizeTag("  #Dormí   Mal "))
        assertEquals(listOf("a"), Journal.withTag(listOf("a"), "#A"))
        assertEquals(listOf("a", "b"), Journal.withTag(listOf("a"), "b"))
        assertEquals(listOf("a"), Journal.withTag(listOf("a"), "  # "))
        val h = listOf(w(1 * day, 80.0, tags = listOf("x", "y")), w(2 * day, 80.0, tags = listOf("y")))
        assertEquals(listOf("y" to 2, "x" to 1), Journal.tagCounts(h))
    }

    @Test fun tagEffectComparesWithinRoutine() {
        val h = listOf(
            w(1 * day, 100.0),
            w(2 * day, 100.0),
            w(3 * day, 90.0, tags = listOf("dormí-mal")),
            w(4 * day, 90.0, tags = listOf("dormí-mal")),
        )
        val effect = Journal.tagEffect(h, "dormí-mal")!!
        assertEquals(0.9 - 1, effect, 1e-6)
        // Pocas sesiones con la etiqueta: sin dato
        assertNull(Journal.tagEffect(h.take(3), "dormí-mal"))
    }

    @Test fun searchFindsTagsAndOldJsonHasNoTags() {
        val tagged = w(1 * day, 80.0, tags = listOf("nuevo-gimnasio"))
        assertEquals(listOf(tagged), WorkoutStats.search(listOf(tagged, w(2 * day, 80.0)), "#nuevo"))
        assertTrue(WorkoutStats.searchText(tagged).contains("#nuevo-gimnasio"))
        // Un JSON guardado antes de las etiquetas se lee igual, sin ellas
        val json = Persistence.encodeWorkout(w(1 * day, 80.0)).replace(",\"tags\":[]", "")
        assertTrue("tags" !in json)
        assertEquals(emptyList<String>(), Persistence.decodeWorkout(json).tags)
        // Y con etiquetas, se conservan al guardar y leer
        assertEquals(listOf("nuevo-gimnasio"), Persistence.decodeWorkout(Persistence.encodeWorkout(tagged)).tags)
    }
}
