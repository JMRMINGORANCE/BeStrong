package com.bestrong.core

import com.bestrong.core.model.MuscleGroup
import com.bestrong.core.model.SetType
import com.bestrong.core.stats.RecordType
import com.bestrong.core.stats.WorkoutStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutStatsTest {
    private val d1 = LocalDate.of(2026, 9, 7)   // lunes
    private val d2 = LocalDate.of(2026, 9, 14)
    private val d3 = LocalDate.of(2026, 9, 21)

    private val history = listOf(
        finishedWorkout("a", d1, bench to listOf(done(80.0, 8), done(80.0, 7))),
        finishedWorkout("b", d2, bench to listOf(done(85.0, 5)), squat to listOf(done(100.0, 5))),
    )

    @Test fun epleyOneRepMax() {
        assertEquals(100.0, done(75.0, 10).estimatedOneRepMax, 0.001)
        assertEquals(120.0, done(120.0, 1).estimatedOneRepMax, 0.001)
        assertEquals(0.0, done(100.0, 5).copy(type = SetType.WARMUP).estimatedOneRepMax, 0.001)
    }

    @Test fun previousSetsReturnsMostRecentWorkout() {
        val prev = WorkoutStats.previousSets(bench.id, history)
        assertEquals(listOf(85.0), prev.map { it.weightKg })
        assertTrue(WorkoutStats.previousSets("unknown", history).isEmpty())
    }

    @Test fun personalRecords() {
        val prs = WorkoutStats.personalRecords(history)[bench.id]!!
        assertEquals(85.0, prs[RecordType.HEAVIEST_WEIGHT]!!.value, 0.0)
        assertEquals(8.0, prs[RecordType.MOST_REPS]!!.value, 0.0)
        assertEquals(640.0, prs[RecordType.BEST_SET_VOLUME]!!.value, 0.0)
        assertEquals("a", prs[RecordType.MOST_REPS]!!.workoutId)
    }

    @Test fun newRecordsDetected() {
        val w = finishedWorkout("c", d3, bench to listOf(done(90.0, 3)), squat to listOf(done(90.0, 5)))
        val types = WorkoutStats.newRecordsIn(w, history + w).filter { it.exerciseId == bench.id }.map { it.type }
        assertEquals(listOf(RecordType.HEAVIEST_WEIGHT), types)
        assertTrue(WorkoutStats.newRecordsIn(w, history + w).none { it.exerciseId == squat.id })
    }

    @Test fun firstTimeIsNotARecord() {
        val w = finishedWorkout("first", d1, squat to listOf(done(60.0, 5)))
        assertTrue(WorkoutStats.newRecordsIn(w, listOf(w)).isEmpty())
    }

    @Test fun weeklyStreak() {
        assertEquals(2, WorkoutStats.weeklyStreak(history, d2.plusDays(3), UTC))
        // Semana actual sin entrenar no rompe la racha
        assertEquals(2, WorkoutStats.weeklyStreak(history, d3.plusDays(2), UTC))
        // Una semana completa sin entrenar sí la rompe
        assertEquals(0, WorkoutStats.weeklyStreak(history, d3.plusDays(8), UTC))
    }

    @Test fun weeklySummaryIncludesEmptyWeeks() {
        val weeks = WorkoutStats.weeklySummary(history, 4, d3, UTC)
        assertEquals(listOf(0, 1, 1, 0), weeks.map { it.workouts })
        assertEquals(d3, weeks.last().weekStart)
    }

    @Test fun setsPerMuscle() {
        val m = WorkoutStats.setsPerMuscle(history, 0)
        assertEquals(3.0, m[MuscleGroup.CHEST]!!, 0.0)
        assertEquals(1.5, m[MuscleGroup.TRICEPS]!!, 0.0)
    }

    @Test fun progressIsChronological() {
        val p = WorkoutStats.exerciseProgress(bench.id, history.reversed())
        assertEquals(listOf(80.0, 85.0), p.map { it.heaviestWeight })
    }
}
