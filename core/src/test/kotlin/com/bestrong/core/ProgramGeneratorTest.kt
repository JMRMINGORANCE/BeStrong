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

    @Test fun uniqueDayNamesAndFullDaysWithoutWeights() {
        for (goal in Goal.entries) {
            for (days in 2..6) {
                val names = ProgramGenerator.build(days, goal, gym, 60).routines.map { it.name }
                assertEquals("$goal $days $names", names.size, names.toSet().size)
                val home = ProgramGenerator.build(days, goal, setOf(Equipment.BODYWEIGHT), 60).routines
                assertTrue("$goal $days", home.all { it.exercises.size >= 3 })
            }
        }
    }

    @Test fun finisherWithinBudgetAndAlone() {
        val day = ProgramGenerator.build(3, Goal.FAT_LOSS, gym, 30).routines.first()
        val max = ProgramGenerator.build(3, Goal.FAT_LOSS, gym, 30).routines.maxOf { it.exercises.size }
        assertTrue(max <= 8)
        assertEquals(null, day.exercises.last().supersetId)
        // Ninguna superserie se queda con un solo ejercicio
        assertTrue(day.exercises.groupBy { it.supersetId }.filterKeys { it != null }.values.all { it.size == 2 })
    }

    @Test fun allowedFilterIsRespected() {
        val p = ProgramGenerator.build(4, Goal.STRENGTH, gym, 60, allowed = { it.id != "squat" })
        assertTrue(p.routines.none { r -> r.exercises.any { it.exercise.id == "squat" } })
    }

    @Test fun levelChangesSets() {
        val beginner = ProgramGenerator.build(3, Goal.HYPERTROPHY, gym, 60, level = com.bestrong.core.training.Level.BEGINNER).routines.first()
        val advanced = ProgramGenerator.build(3, Goal.HYPERTROPHY, gym, 60, level = com.bestrong.core.training.Level.ADVANCED).routines.first()
        assertEquals(3, beginner.exercises.first().sets.size)
        assertEquals(5, advanced.exercises.first().sets.size)
    }

    @Test fun scheduleFillsChosenDaysWithoutOverwriting() {
        val monday = java.time.LocalDate.of(2026, 10, 12)
        val existing = mapOf(monday.toEpochDay() to com.bestrong.core.training.WeekPlan().assign(java.time.DayOfWeek.MONDAY, "mine"))
        val plans = ProgramGenerator.schedule(existing, listOf("a", "b", "c"), ProgramGenerator.defaultWeekdays(3), monday, weeks = 2)
        val first = plans.getValue(monday.toEpochDay())
        assertEquals("mine", first.routineFor(java.time.DayOfWeek.MONDAY))
        // A, B, C por orden en los días libres
        assertEquals("a", first.routineFor(java.time.DayOfWeek.WEDNESDAY))
        assertEquals("b", first.routineFor(java.time.DayOfWeek.FRIDAY))
        assertEquals("c", plans.getValue(monday.plusWeeks(1).toEpochDay()).routineFor(java.time.DayOfWeek.MONDAY))
        // Creado un jueves: el lunes y el miércoles ya pasados quedan libres y el viernes empieza por la A
        val thursday = ProgramGenerator.schedule(emptyMap(), listOf("a", "b", "c"), listOf(1, 3, 5), monday, weeks = 1, from = monday.plusDays(3))
            .getValue(monday.toEpochDay())
        assertEquals(null, thursday.routineFor(java.time.DayOfWeek.MONDAY))
        assertEquals("a", thursday.routineFor(java.time.DayOfWeek.FRIDAY))
        assertEquals(listOf(1, 2, 4, 5), ProgramGenerator.defaultWeekdays(4))
    }
}
