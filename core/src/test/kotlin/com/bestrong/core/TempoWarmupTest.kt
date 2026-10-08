package com.bestrong.core

import com.bestrong.core.model.SetType
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.Tempo
import com.bestrong.core.util.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoWarmupTest {
    @Test fun parsesTempo() {
        assertEquals(Tempo(3, 1, 1, 0), Tempo.parse("3-1-1"))
        assertEquals(Tempo(4, 0, 0, 2), Tempo.parse("4-0-X-2"))
        assertEquals("3-1-1-0", Tempo.parse(" 3/1/1 ").toString())
        assertEquals(25, Tempo.parse("3-1-1")!!.timeUnderTension(5))
        assertEquals(listOf(Tempo.Phase.ECCENTRIC to 4, Tempo.Phase.TOP to 2), Tempo.parse("4-0-X-2")!!.phases)
        assertNull(Tempo.parse("rápido"))
        assertNull(Tempo.parse("0-0-0"))
        assertNull(Tempo.parse("3-1"))
    }

    @Test fun warmupKeepsCompletedAndReplacesPending() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(squat))
        val we = w.exercises[0]
        w = WorkoutEditor.updateExercise(w, we.id) { e -> e.copy(sets = e.sets.map { it.copy(weightKg = 100.0, reps = 5) }) }
        w = WorkoutEditor.addWarmup(w, we.id, WeightUnit.KG)
        val sets = w.exercises[0].sets
        assertEquals(listOf(20.0, 40.0, 60.0, 80.0, 100.0), sets.map { it.weightKg }.distinct())
        assertEquals(SetType.WARMUP, sets.first().type)
        // Repetirlo no duplica los calentamientos pendientes
        assertEquals(sets.map { it.copy(id = "") }, WorkoutEditor.addWarmup(w, we.id, WeightUnit.KG).exercises[0].sets.map { it.copy(id = "") })
    }
}
