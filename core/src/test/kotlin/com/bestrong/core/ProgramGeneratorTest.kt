package com.bestrong.core

import com.bestrong.core.model.Equipment
import com.bestrong.core.training.Goal
import com.bestrong.core.training.ProgramGenerator
import com.bestrong.core.training.RoutineGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramGeneratorTest {
    private val gym = Equipment.entries.toSet()

    @Test fun oneRoutinePerDayForEveryGoal() {
        for (goal in Goal.entries) {
            for (days in 2..6) {
                val p = ProgramGenerator.build(days, goal, gym, 60)
                assertEquals("$goal $days", days, p.routines.size)
                assertTrue(p.routines.all { r -> r.exercises.size >= 3 && r.folder == p.name })
                assertTrue(p.name.contains("$days"))
            }
        }
    }

    @Test fun splitDependsOnGoal() {
        assertEquals(listOf("push", "pull", "legs"), RoutineGenerator.split(3, Goal.HYPERTROPHY))
        assertEquals(listOf("full", "full", "full"), RoutineGenerator.split(3, Goal.STRENGTH))
        assertEquals(listOf("upper", "lower", "full", "upper", "lower"), RoutineGenerator.split(5, Goal.FAT_LOSS))
    }

    @Test fun prescriptionDependsOnGoal() {
        val strength = ProgramGenerator.build(3, Goal.STRENGTH, gym, 60).routines.first()
        assertEquals(5, strength.exercises.first().sets.size)
        assertEquals(5, strength.exercises.first().sets.first().reps)
        // Perder grasa: descansos cortos, superseries y remate de burpees
        val fat = ProgramGenerator.build(3, Goal.FAT_LOSS, gym, 60).routines.first()
        assertTrue(fat.exercises.all { it.restSeconds <= 60 })
        assertTrue(fat.exercises.any { it.supersetId != null })
        assertEquals("burpee", fat.exercises.last().exercise.id)
    }

    @Test fun bodyweightOnlyUsesBodyweight() {
        val p = ProgramGenerator.build(4, Goal.GENERAL, setOf(Equipment.BODYWEIGHT), 45)
        assertTrue(p.routines.all { r -> r.exercises.all { it.exercise.equipment == Equipment.BODYWEIGHT } })
    }
}
