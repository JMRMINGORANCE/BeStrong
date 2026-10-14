package com.bestrong.core

import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.model.moveKeepingSupersets
import com.bestrong.core.model.normalizedSupersets
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.ProgramTexts
import com.bestrong.core.training.missedSessions
import org.junit.Assert.assertEquals
import org.junit.Test

class Iteration11Test {
    @Test fun estimateAddsRunningRestAndShortWarmupRest() {
        val sets = listOf(WorkoutSet(type = SetType.WARMUP), WorkoutSet(), WorkoutSet())
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = bench, sets = sets, restSeconds = 180)))
        // 3 series × 40 s + 60 s tras el calentamiento + 180 s entre las efectivas
        assertEquals(3 * 40 + 60 + 180, WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = 0))
        // Con 100 s de descanso aún por delante
        assertEquals(100 + 3 * 40 + 60 + 180, WorkoutEditor.estimatedRemainingSeconds(w, nowEpochMs = 0, restEndsAtEpochMs = 100_000))
    }

    @Test fun userPartKeepsWhatTheUserWroteInProgramNotes() {
        val gen = listOf(ProgramTexts.ES.amrapNote, ProgramTexts.EN.amrapNote)
        assertEquals("", ProgramTexts.userPart("T1 · Última serie: máximas repeticiones (+)", gen))
        assertEquals("", ProgramTexts.userPart("T2", gen))
        assertEquals("me costó", ProgramTexts.userPart("T1 · Última serie: máximas repeticiones (+) me costó", gen))
        assertEquals("hombro raro", ProgramTexts.userPart("hombro raro", gen))
    }
}

class Iteration11ImprovementsTest {
    @Test fun duplicateSetInsertsPendingCopyBelow() {
        val sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true, completedAtEpochMs = 1, note = "x"), WorkoutSet(weightKg = 80.0, reps = 8))
        val we = WorkoutExercise(exercise = bench, sets = sets)
        val out = WorkoutEditor.duplicateSet(Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we)), we.id, sets[0].id).exercises[0].sets
        assertEquals(3, out.size)
        assertEquals(100.0, out[1].weightKg, 0.0)
        assertEquals(false, out[1].completed)
        assertEquals("", out[1].note)
        assertEquals(80.0, out[2].weightKg, 0.0)
    }

    @Test fun completeRemainingSkipsEmptySets() {
        val sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5), WorkoutSet(weightKg = 0.0, reps = 0), WorkoutSet(weightKg = 90.0, reps = 6, completed = true, completedAtEpochMs = 5))
        val we = WorkoutExercise(exercise = bench, sets = sets)
        assertEquals(1, WorkoutEditor.completableCount(we))
        val out = WorkoutEditor.completeRemaining(Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we)), we.id, 1_000).exercises[0].sets
        assertEquals(listOf(true, false, true), out.map { it.completed })
        assertEquals(1_000L, out[0].completedAtEpochMs)
        assertEquals(5L, out[2].completedAtEpochMs)
    }

    @Test fun laggingMusclesFromThursdayForUsuallyTrainedMuscles() {
        // Lunes 2/3/2026: 4 semanas de 12 series de pecho; esta semana solo 3
        val monday = java.time.LocalDate.of(2026, 3, 2)
        val history = (1..4).map { k -> finishedWorkout("w$k", monday.minusWeeks(k.toLong()), bench to List(12) { done(80.0, 5) }) } +
            finishedWorkout("now", monday, bench to List(3) { done(80.0, 5) })
        val wed = com.bestrong.core.stats.WorkoutStats.laggingMuscles(history, monday.plusDays(2), UTC)
        assertEquals(emptyList<Any>(), wed)
        val thu = com.bestrong.core.stats.WorkoutStats.laggingMuscles(history, monday.plusDays(3), UTC)
        assertEquals(listOf(bench.primaryMuscle to 3.0), thu)
    }

    @Test fun restByExercise() {
        val t0 = 1_000_000L
        val sets = listOf(WorkoutSet(completed = true, completedAtEpochMs = t0), WorkoutSet(completed = true, completedAtEpochMs = t0 + 160_000))
        val we = WorkoutExercise(exercise = bench, sets = sets, restSeconds = 90)
        val map = com.bestrong.core.stats.RestStats.byExercise(Workout(name = "w", startedAtEpochMs = 0, finishedAtEpochMs = t0 + 200_000, exercises = listOf(we)))
        assertEquals(120, map.getValue(we.id).realSeconds)
    }
}

class Iteration12Test {
    @Test fun addedExerciseDoesNotInheritStopwatchOrNotes() {
        val tread = com.bestrong.core.library.ExerciseLibrary.all.first { it.tracking == com.bestrong.core.model.TrackingType.CARDIO }
        val prev = listOf(WorkoutSet(durationSec = 600, completed = true, startedAtEpochMs = 5, completedAtEpochMs = 600_005, note = "n", assisted = true))
        val w = WorkoutEditor.addExercises(Workout(name = "w", startedAtEpochMs = 0), listOf(tread), previousSets = { prev })
        val s = w.exercises[0].sets[0]
        assertEquals(false, s.isRunning)
        assertEquals(null, s.completedAtEpochMs)
        assertEquals("", s.note)
        assertEquals(false, s.assisted)
        assertEquals(600, s.durationSec)
    }

    @Test fun completeRemainingCountsUnilateralRightSide() {
        val we = WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(reps = 0, repsRight = 8)))
        assertEquals(1, WorkoutEditor.completableCount(we))
    }

    @Test fun userPartCollapsesSeparators() {
        assertEquals("a · b", ProgramTexts.userPart("a · X · b", listOf("X")))
    }
}

class TrimToFitTest {
    @Test fun removesPendingSetsFromTheEndUntilItFits() {
        val curl = com.bestrong.core.library.ExerciseLibrary.byId("barbell_curl") ?: com.bestrong.core.library.ExerciseLibrary.all[5]
        val main = WorkoutExercise(exercise = bench, restSeconds = 120, sets = List(3) { WorkoutSet(weightKg = 100.0, reps = 5) })
        val acc = WorkoutExercise(exercise = curl, restSeconds = 60, sets = List(3) { WorkoutSet(weightKg = 20.0, reps = 10) })
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(main, acc))
        val est = { x: Workout -> WorkoutEditor.estimatedRemainingSeconds(x, nowEpochMs = 0) }
        // 6×40 + 2×120 + 120 (cambio) + 2×60 = 720; con 500 s debe caer el accesorio
        val trimmed = WorkoutEditor.trimToFit(w, 500, est)
        assertEquals(true, est(trimmed) <= 500)
        assertEquals(3, trimmed.exercises[0].sets.size)
        // Nunca quita la siguiente serie
        val minimal = WorkoutEditor.trimToFit(w, 0, est)
        assertEquals(1, minimal.exercises.size)
        assertEquals(main.sets[0].id, minimal.exercises[0].sets.single().id)
    }
}

class Iteration13Test {
    private val curl = com.bestrong.core.library.ExerciseLibrary.all[5]

    @Test fun undoRemovalOnlyRestoresWhatWasRemoved() {
        val a = WorkoutExercise(exercise = bench, sets = List(3) { WorkoutSet(reps = 5) })
        val b = WorkoutExercise(exercise = curl, sets = List(2) { WorkoutSet(reps = 10) })
        val before = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(a, b))
        val after = before.copy(exercises = listOf(a.copy(sets = a.sets.take(2))))
        val undo = WorkoutEditor.undoRemoval(before, after)
        // Después del recorte el usuario añade una serie al primero: se conserva
        val extra = WorkoutSet(reps = 1)
        val later = after.copy(exercises = listOf(after.exercises[0].copy(sets = after.exercises[0].sets + extra)))
        val restored = undo(later)
        assertEquals(listOf(a.id, b.id), restored.exercises.map { it.id })
        assertEquals(listOf(a.sets[0].id, a.sets[1].id, a.sets[2].id, extra.id), restored.exercises[0].sets.map { it.id })
    }

    @Test fun trimKeepsNextSetOfStartedExerciseAndClearsLoneSuperset() {
        val e1 = WorkoutExercise(exercise = bench, restSeconds = 60, sets = listOf(WorkoutSet(reps = 5, completed = true), WorkoutSet(reps = 5)))
        val e2 = WorkoutExercise(exercise = curl, restSeconds = 60, supersetId = "s", sets = listOf(WorkoutSet(reps = 5, completed = true), WorkoutSet(reps = 5), WorkoutSet(reps = 5)))
        val e3 = WorkoutExercise(exercise = com.bestrong.core.library.ExerciseLibrary.all[7], restSeconds = 60, supersetId = "s", sets = listOf(WorkoutSet(reps = 5)))
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(e1, e2, e3))
        val out = WorkoutEditor.trimToFit(w, 0) { WorkoutEditor.estimatedRemainingSeconds(it, nowEpochMs = 0) }
        // e3 desaparece; de e2 queda la hecha y la siguiente
        assertEquals(2, out.exercises.size)
        assertEquals(2, out.exercises[1].sets.size)
        assertEquals(null, out.exercises[1].supersetId)
    }

    @Test fun bulkCompletedSetsAreNotRest() {
        val t0 = 1_000_000L
        val sets = listOf(
            WorkoutSet(reps = 5, completed = true, completedAtEpochMs = t0),
            WorkoutSet(reps = 5, completed = true, completedAtEpochMs = t0 + 540_000),
            WorkoutSet(reps = 5, completed = true, completedAtEpochMs = t0 + 540_000),
        )
        val we = WorkoutExercise(exercise = bench, sets = sets, restSeconds = 90)
        assertEquals(null, com.bestrong.core.stats.RestStats.summary(Workout(name = "w", startedAtEpochMs = 0, finishedAtEpochMs = t0 + 600_000, exercises = listOf(we))))
    }
}

class RepsToBeatTest {
    @Test fun repsToBeatUsesSameFormulaAsSets() {
        val best = WorkoutSet(weightKg = 100.0, reps = 5, completed = true).estimatedOneRepMax // 116,67
        val r = com.bestrong.core.stats.OneRepMax.repsToBeat(best, 100.0)!!
        assertEquals(6, r)
        assertEquals(true, WorkoutSet(weightKg = 100.0, reps = r).estimatedOneRepMax > best)
        assertEquals(1, com.bestrong.core.stats.OneRepMax.repsToBeat(best, 120.0))
        assertEquals(null, com.bestrong.core.stats.OneRepMax.repsToBeat(best, 20.0))
    }
}

class RoutineMuscleTest {
    @Test fun countsWorkingSetsPerMuscle() {
        val r = com.bestrong.core.model.Routine(
            name = "r",
            exercises = listOf(
                com.bestrong.core.model.RoutineExercise(
                    exercise = bench,
                    sets = listOf(com.bestrong.core.model.RoutineSet(type = SetType.WARMUP), com.bestrong.core.model.RoutineSet(), com.bestrong.core.model.RoutineSet()),
                ),
            ),
        )
        val m = com.bestrong.core.stats.WorkoutStats.routineSetsPerMuscle(r)
        assertEquals(2.0, m.getValue(bench.primaryMuscle), 0.0)
        bench.secondaryMuscles.forEach { assertEquals(1.0, m.getValue(it), 0.0) }
    }
}

class Iteration14Test {
    private fun re(id: String, ss: String?) = com.bestrong.core.model.RoutineExercise(id = id, exercise = bench, supersetId = ss)

    @Test fun normalizedSupersetsSplitsAndDropsLone() {
        val r = com.bestrong.core.model.Routine(name = "r", exercises = listOf(re("a", "x"), re("b", null), re("c", "x"), re("d", "x"), re("e", "y")))
        val result = r.normalizedSupersets()
        // «a» quedó suelta (su pareja está lejos): deja de ser superserie; c-d siguen juntas; e sola, fuera
        assertEquals(listOf(null, null, "x", "x", null), result.exercises.map { it.supersetId })
    }

    @Test fun trimWithNothingToRemoveKeepsWorkoutIdentical() {
        val lone = WorkoutExercise(exercise = bench, supersetId = "s", sets = listOf(WorkoutSet(reps = 5)))
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(lone))
        val out = WorkoutEditor.trimToFit(w, 0) { WorkoutEditor.estimatedRemainingSeconds(it, nowEpochMs = 0) }
        assertEquals(w, out)
    }

    @Test fun coachReportShowsBothSides() {
        val w = finishedWorkout("w", java.time.LocalDate.of(2026, 3, 2), bench to listOf(done(20.0, 8).copy(repsRight = 7)))
        val text = com.bestrong.core.io.CoachReport.text(listOf(w), 0, com.bestrong.core.util.WeightUnit.KG, UTC, java.util.Locale.ROOT)
        assertEquals(true, text.contains("20×8|7"))
    }
}

class YearSummaryTest {
    @Test fun yearSummaryCountsDaysStreakAndGains() {
        val d = java.time.LocalDate.of(2026, 1, 5)
        val history = listOf(
            finishedWorkout("a", d, bench to listOf(done(80.0, 5))),
            finishedWorkout("b", d.plusWeeks(1), bench to listOf(done(90.0, 5))),
            finishedWorkout("c", d.plusWeeks(3), bench to listOf(done(85.0, 5))),
            finishedWorkout("old", java.time.LocalDate.of(2025, 6, 1), bench to listOf(done(200.0, 1))),
        )
        val y = com.bestrong.core.stats.Strength.yearSummary(history, 2026, UTC)!!
        assertEquals(3, y.period.workouts)
        assertEquals(3, y.trainingDays)
        assertEquals(2, y.longestWeeklyStreak)
        assertEquals(bench.id, y.biggestGains.single().first.id)
        assertEquals(null, com.bestrong.core.stats.Strength.yearSummary(history, 2024, UTC))
    }
}

class MoveKeepingSupersetsTest {
    private fun re(id: String, ss: String?) = com.bestrong.core.model.RoutineExercise(id = id, exercise = bench, supersetId = ss)

    @Test fun movingPastASupersetJumpsTheWholeBlock() {
        val r = com.bestrong.core.model.Routine(name = "r", exercises = listOf(re("a", "x"), re("b", "x"), re("c", null)))
        val up = r.moveKeepingSupersets(2, down = false).normalizedSupersets()
        assertEquals(listOf("c", "a", "b"), up.exercises.map { it.id })
        assertEquals(listOf(null, "x", "x"), up.exercises.map { it.supersetId })
        // Mover un miembro de la superserie hacia fuera mueve el bloque entero
        val down = r.moveKeepingSupersets(1, down = true).normalizedSupersets()
        assertEquals(listOf("c", "a", "b"), down.exercises.map { it.id })
        // Dentro de la superserie, solo cambian de orden
        assertEquals(listOf("b", "a", "c"), r.moveKeepingSupersets(1, down = false).exercises.map { it.id })
    }
}

class RpeTrendTest {
    @Test fun effortRisingWhenRpeUpWithoutGains() {
        val d = java.time.LocalDate.of(2026, 3, 2)
        val rpes = listOf(7.0, 7.0, 7.5, 8.0, 8.5, 9.0)
        val history = rpes.mapIndexed { i, r -> finishedWorkout("w$i", d.plusDays(i * 3L), bench to listOf(done(100.0, 5).copy(rpe = r))) }
        val trend = com.bestrong.core.stats.Strength.rpeTrend(history, setOf(bench.id))
        assertEquals(6, trend.size)
        assertEquals(true, com.bestrong.core.stats.Strength.effortRising(trend))
        val progressing = history.mapIndexed { i, w -> if (i == 5) finishedWorkout("p", d.plusDays(20), bench to listOf(done(110.0, 5).copy(rpe = 9.0))) else w }
        assertEquals(false, com.bestrong.core.stats.Strength.effortRising(com.bestrong.core.stats.Strength.rpeTrend(progressing, setOf(bench.id))))
    }
}

class Iteration16Test {
    @Test fun predictDateNeverInThePast() {
        val day = 86_400_000L
        val points = listOf(0L to 50.0, day to 100.0, 2 * day to 100.0, 3 * day to 100.0)
        val eta = com.bestrong.core.stats.Strength.predictDate(points, 105.0, nowEpochMs = 3 * day)
        assertEquals(true, eta == null || eta > 3 * day)
        // Datos viejos: nunca una fecha anterior a hoy
        val old = com.bestrong.core.stats.Strength.predictDate(listOf(0L to 50.0, day to 60.0, 2 * day to 70.0), 80.0, nowEpochMs = 400 * day)
        assertEquals(null, old)
    }

    @Test fun noFatigueWarningWithoutWeight() {
        val trend = listOf(7.0, 7.0, 7.0, 8.0, 8.5, 9.0).mapIndexed { i, r -> Triple(i.toLong(), r, 0.0) }
        assertEquals(false, com.bestrong.core.stats.Strength.effortRising(trend))
    }

    @Test fun sharedRoutineIsNeverArchived() {
        val r = com.bestrong.core.model.Routine(name = "r", archived = true, exercises = listOf(com.bestrong.core.model.RoutineExercise(exercise = bench)))
        val back = com.bestrong.core.io.RoutineLink.decode(com.bestrong.core.io.RoutineLink.encode(r))!!
        assertEquals(false, back.archived)
    }
}

class BodyWeightGoalTest {
    @Test fun losingWeightGoalPredictsDate() {
        val day = 86_400_000L
        val points = (0..9).map { it * 7 * day to 85.0 - it * 0.5 }
        val now = points.last().first
        val p = com.bestrong.core.stats.BodyWeightGoal.progress(points, 78.0, startKg = 85.0, nowEpochMs = now)!!
        assertEquals(2.5, p.remainingKg, 1e-6)
        assertEquals(false, p.reached)
        assertEquals(true, p.etaEpochMs != null && p.etaEpochMs!! > now)
        assertEquals(true, com.bestrong.core.stats.BodyWeightGoal.progress(points, 82.0, startKg = 85.0, nowEpochMs = now)!!.reached)
        // Primer pesaje de hace años a 70, ahora 80,5 y objetivo 80 para bajar: no está cumplido
        val bulked = listOf(0L to 70.0) + points.map { it.first + day to it.second }
        assertEquals(false, com.bestrong.core.stats.BodyWeightGoal.progress(bulked, 79.0, startKg = 81.0, nowEpochMs = now)!!.reached)
    }
}

class ExerciseGoalsTest {
    @Test fun goalValuePerTracking() {
        val plank = com.bestrong.core.library.ExerciseLibrary.byId("plank")!!
        val d = java.time.LocalDate.of(2026, 3, 2)
        val h = listOf(finishedWorkout("a", d, plank to listOf(WorkoutSet(durationSec = 60, completed = true), WorkoutSet(durationSec = 90, completed = true))))
        val pts = com.bestrong.core.stats.ExerciseGoals.points(h, plank.id, plank.tracking)
        assertEquals(90.0, pts.single().second, 0.0)
        assertEquals(false, com.bestrong.core.stats.ExerciseGoals.supports(com.bestrong.core.model.TrackingType.CARDIO))
    }
}

class Iteration17Test {
    @Test fun repsGoalCountsPerSideAndIgnoresAssistance() {
        val t = com.bestrong.core.model.TrackingType.REPS_ONLY
        assertEquals(8.0, com.bestrong.core.stats.ExerciseGoals.valueOf(WorkoutSet(reps = 8, repsRight = 9, completed = true), t), 0.0)
        assertEquals(0.0, com.bestrong.core.stats.ExerciseGoals.valueOf(WorkoutSet(reps = 15, weightKg = -30.0, completed = true), t), 0.0)
    }
}

class BackOffTest {
    @Test fun backOffAfterLastWorkingSetAtNinetyPercent() {
        val sets = listOf(WorkoutSet(weightKg = 60.0, reps = 5, type = SetType.WARMUP), WorkoutSet(weightKg = 100.0, reps = 5), WorkoutSet(weightKg = 70.0, reps = 10, type = SetType.DROP))
        val we = WorkoutExercise(exercise = bench, sets = sets)
        val out = WorkoutEditor.addBackOff(Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we)), we.id, 2.5).exercises[0].sets
        assertEquals(5, out.size)
        assertEquals(listOf(60.0, 100.0, 90.0, 90.0, 70.0), out.map { it.weightKg })
        assertEquals(5, out[2].reps)
    }
}

class RepRangeProgressionTest {
    @Test fun doubleProgressionWithinRange() {
        val d = java.time.LocalDate.of(2026, 3, 2)
        val mid = listOf(finishedWorkout("a", d, bench to listOf(done(80.0, 10), done(80.0, 9), done(80.0, 8))))
        val s1 = com.bestrong.core.training.Progression.suggest(bench, mid, 2.5, repRange = 8..12)!!
        assertEquals(listOf(11, 10, 9), s1.sets.map { it.reps })
        assertEquals(80.0, s1.sets[0].weightKg, 0.0)
        val top = listOf(finishedWorkout("b", d, bench to listOf(done(80.0, 12), done(80.0, 12), done(80.0, 12))))
        val s2 = com.bestrong.core.training.Progression.suggest(bench, top, 2.5, repRange = 8..12)!!
        assertEquals(82.5, s2.sets[0].weightKg, 0.0)
        assertEquals(8, s2.sets[0].reps)
        assertEquals(com.bestrong.core.training.SuggestionKind.RANGE_ADD_REP, s1.kind)
    }

    @Test fun rangeProgressionIsNotADeload() {
        val d = java.time.LocalDate.of(2026, 3, 2)
        val seq = listOf(80.0 to 12, 82.5 to 8, 82.5 to 9, 82.5 to 10)
        val history = seq.mapIndexed { i, (kg, r) -> finishedWorkout("w$i", d.plusDays(i * 3L), bench to List(3) { done(kg, r) }) }
        val s = com.bestrong.core.training.Progression.suggest(bench, history, 2.5, repRange = 8..12)!!
        assertEquals(com.bestrong.core.training.SuggestionKind.RANGE_ADD_REP, s.kind)
        assertEquals(82.5, s.sets[0].weightKg, 0.0)
    }
}

class Iteration18Test {
    @Test fun backOffNeverZero() {
        val we = WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(weightKg = 2.5, reps = 10)))
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we))
        assertEquals(1, WorkoutEditor.addBackOff(w, we.id, 2.5).exercises[0].sets.size)
    }

    @Test fun legacyWeightGoalKeepsDirectionAfterOvershoot() {
        val day = 86_400_000L
        val now = 60 * day
        val points = listOf(now - 50 * day to 84.0, now - 30 * day to 82.0, now to 79.0)
        // Sin peso inicial guardado: el objetivo de bajar a 80 está cumplido (no «falta 1 kg»)
        assertEquals(true, com.bestrong.core.stats.BodyWeightGoal.progress(points, 80.0, startKg = null, nowEpochMs = now)!!.reached)
    }

    @Test fun parseRepRange() {
        assertEquals(8..12, com.bestrong.core.training.Progression.parseRepRange("8-12"))
        assertEquals(null, com.bestrong.core.training.Progression.parseRepRange("12-8"))
        assertEquals(null, com.bestrong.core.training.Progression.parseRepRange(null))
    }
}

class TimeOfDayTest {
    @Test fun eveningStrongerThanMorning() {
        val zone = java.time.ZoneOffset.UTC
        val base = java.time.LocalDate.of(2026, 3, 2)
        fun at(day: Long, hour: Int, kg: Double, id: String) = finishedWorkout(id, base.plusDays(day), bench to listOf(done(kg, 5))).let { w ->
            val start = base.plusDays(day).atTime(hour, 0).toInstant(zone).toEpochMilli()
            w.copy(startedAtEpochMs = start, finishedAtEpochMs = start + 3_600_000)
        }
        val history = (0L until 3L).map { at(it * 2, 8, 90.0, "m$it") } + (0L until 3L).map { at(it * 2 + 1, 19, 100.0, "e$it") }
        val now = base.plusDays(10).atStartOfDay(zone).toInstant().toEpochMilli()
        val stats = com.bestrong.core.stats.TimeOfDay.stats(history, zone, now)
        assertEquals(com.bestrong.core.stats.DayPart.EVENING, stats.first().part)
        assertEquals(2, stats.size)
    }
}

class Iteration19Test {
    @Test fun rangeStallDeloadsWhenRepsDoNotGrow() {
        val d = java.time.LocalDate.of(2026, 3, 2)
        val history = (0..3).map { i -> finishedWorkout("w$i", d.plusDays(i * 3L), bench to listOf(done(82.5, 9), done(82.5, 9), done(82.5, 8))) }
        val s = com.bestrong.core.training.Progression.suggest(bench, history, 2.5, repRange = 8..12)!!
        assertEquals(com.bestrong.core.training.SuggestionKind.DELOAD, s.kind)
    }

    @Test fun dropSetNeverZero() {
        val we = WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(weightKg = 2.5, reps = 10)))
        val w = Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we))
        assertEquals(1, WorkoutEditor.addDropSet(w, we.id, 2.5).exercises[0].sets.size)
    }
}

class BodyCompositionTest {
    @Test fun indices() {
        assertEquals(0.45, com.bestrong.core.stats.BodyComposition.waistToHeight(81.0, 180)!!, 1e-9)
        assertEquals(com.bestrong.core.stats.BodyRange.HEALTHY, com.bestrong.core.stats.BodyComposition.waistToHeightRange(0.45))
        // 80 kg, 15 % grasa, 1,80 m → 68 / 3,24 = 20,99
        assertEquals(20.99, com.bestrong.core.stats.BodyComposition.ffmi(80.0, 15.0, 180)!!, 0.01)
        assertEquals(null, com.bestrong.core.stats.BodyComposition.ffmi(80.0, 15.0, 0))
    }
}

class WeightRateTest {
    @Test fun tooFastAboveOnePercentPerWeek() {
        assertEquals(true, com.bestrong.core.stats.WeightRate.tooFast(80.0, -1.0))
        assertEquals(false, com.bestrong.core.stats.WeightRate.tooFast(80.0, -0.5))
        assertEquals(false, com.bestrong.core.stats.WeightRate.tooFast(80.0, null))
    }
}

class Iteration20Test {
    @Test fun noDeloadWhenAtTopOrWithDropSets() {
        val d = java.time.LocalDate.of(2026, 3, 2)
        // Al tope 3 veces (la subida se perdió al redondear): no es estancamiento
        val atTop = (0..3).map { i -> finishedWorkout("t$i", d.plusDays(i * 3L), bench to List(3) { done(80.0, 12) }) }
        assertEquals(false, com.bestrong.core.training.Progression.isStalledInRange(bench.id, atTop, 3, 8..12))
        // Un drop set en la primera sesión no cuenta: las reps a 80 suben
        val first = finishedWorkout("a", d, bench to listOf(done(80.0, 10), done(80.0, 10), done(80.0, 9), done(60.0, 10).copy(type = SetType.DROP)))
        val rest = listOf(30, 31, 32).mapIndexed { i, total -> finishedWorkout("b$i", d.plusDays(3L + i * 3), bench to listOf(done(80.0, total / 3), done(80.0, total / 3), done(80.0, total - 2 * (total / 3)))) }
        assertEquals(false, com.bestrong.core.training.Progression.isStalledInRange(bench.id, listOf(first) + rest, 3, 8..12))
    }

    @Test fun loadAboveNeverRoundsBack() {
        val gym = com.bestrong.core.training.GymProfile.gym()
        val above = gym.loadAbove(80.0, com.bestrong.core.model.Equipment.BARBELL)
        assertEquals(true, above > 80.0)
    }
}

class WeightRateTrendTest {
    @Test fun needsThreePointsOverTwoWeeks() {
        val day = 86_400_000L
        val now = 30 * day
        assertEquals(null, com.bestrong.core.stats.WeightRate.weeklyChange(listOf(now - 7 * day to 80.0, now to 81.0), now))
        val ok = listOf(now - 20 * day to 80.0, now - 10 * day to 79.0, now to 78.0)
        assertEquals(-0.7, com.bestrong.core.stats.WeightRate.weeklyChange(ok, now)!!, 0.01)
    }
}

class AssistedDropTest {
    @Test fun assistedDropAddsAssistance() {
        val pull = com.bestrong.core.library.ExerciseLibrary.all.first { it.tracking == com.bestrong.core.model.TrackingType.REPS_ONLY }
        val we = WorkoutExercise(exercise = pull, sets = listOf(WorkoutSet(weightKg = -20.0, reps = 8)))
        val out = WorkoutEditor.addDropSet(Workout(name = "w", startedAtEpochMs = 0, exercises = listOf(we)), we.id, 2.5).exercises[0].sets
        assertEquals(-22.5, out[1].weightKg, 0.0)
    }
}

class Iteration20ImprovementsTest {
    @Test fun missedSessionsOnlyPastAndUntrained() {
        val monday = java.time.LocalDate.of(2026, 3, 2)
        val plan = com.bestrong.core.training.WeekPlan(mapOf(1 to "a", 2 to "b", 5 to "c"))
        // Jueves: lunes entrenado, martes no, viernes aún no ha llegado
        assertEquals(listOf(java.time.DayOfWeek.TUESDAY to "b"), plan.missedSessions(monday, monday.plusDays(3), setOf(monday)))
    }

    @Test fun neededWeeklyRate() {
        val week = 7 * 86_400_000L
        assertEquals(0.5, com.bestrong.core.stats.BodyWeightGoal.neededWeeklyKg(2.0, 0, 4 * week)!!, 1e-9)
        assertEquals(null, com.bestrong.core.stats.BodyWeightGoal.neededWeeklyKg(2.0, 5 * week, 4 * week))
    }
}

class MissedLaterTest {
    @Test fun doneLaterIsNotMissed() {
        val monday = java.time.LocalDate.of(2026, 3, 2)
        val plan = com.bestrong.core.training.WeekPlan(mapOf(3 to "push"))
        // Planificada el miércoles, hecha el jueves; viernes: no se avisa
        val out = plan.missedSessions(monday, monday.plusDays(4), setOf(monday.plusDays(3))) { id, since -> id == "push" && !monday.plusDays(3).isBefore(since) }
        assertEquals(emptyList<Any>(), out)
    }
}

class YearAgoTest {
    @Test fun comparesSameExerciseAYearApart() {
        val today = java.time.LocalDate.of(2026, 3, 10)
        val history = listOf(
            finishedWorkout("old", today.minusYears(1).plusDays(2), bench to listOf(done(70.0, 8))),
            finishedWorkout("new", today.minusDays(3), bench to listOf(done(85.0, 8))),
        )
        val c = com.bestrong.core.stats.YearAgo.compare(history, today, UTC).single()
        assertEquals(70.0, c.thenWeightKg, 0.0)
        assertEquals(85.0, c.nowWeightKg, 0.0)
        assertEquals(true, c.change > 0.2)
    }
}

class CollarTest {
    @Test fun collarsCountInLoads() {
        val gym = com.bestrong.core.training.GymProfile.gym().copy(collarKg = 5.0)
        assertEquals(25.0, gym.nearestBarbell(20.0), 0.0)
        assertEquals(65.0, gym.nearestBarbell(65.0), 0.0)
        val load = gym.plateLoad(65.0, com.bestrong.core.util.WeightUnit.KG)
        assertEquals(listOf(20.0), load.perSide)
    }

    @Test
    fun watchRoundsSuggestionToActiveGym() {
        val ex = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST, equipment = com.bestrong.core.model.Equipment.BARBELL)
        val gym = com.bestrong.core.training.GymProfile(name = "G", platesKg = listOf(20.0, 10.0, 5.0, 2.5))
        val s = com.bestrong.core.sync.SharedSettings(activeGym = gym)
        val prev = listOf(com.bestrong.core.model.WorkoutSet(weightKg = 60.0, reps = 5, completed = true))
        val up = com.bestrong.core.training.ProgressionSuggestion(com.bestrong.core.training.SuggestionKind.INCREASE_WEIGHT, listOf(com.bestrong.core.model.WorkoutSet(weightKg = 61.25, reps = 5)), "")
        // 61,25 se redondea a 60 (discos de 2,5): la subida no se pierde, pasa a 65
        assertEquals(65.0, s.loadable(ex, up, prev).single().weightKg, 1e-9)
        // Sin gimnasio sincronizado se deja la sugerencia tal cual
        assertEquals(61.25, com.bestrong.core.sync.SharedSettings().loadable(ex, up, prev).single().weightKg, 1e-9)
    }

    @Test
    fun autoregulationFollowsTargetRpe() {
        val ex = com.bestrong.core.model.Exercise(id = "sq", name = "Sentadilla", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        val done = WorkoutSet(id = "a", weightKg = 100.0, reps = 5, completed = true, rpe = 7.0)
        val pending = WorkoutSet(id = "b", weightKg = 100.0, reps = 5)
        val we = WorkoutExercise(exercise = ex, sets = listOf(done, pending))
        // Sin objetivo (8): RPE 7 es fácil → subir
        assertEquals(1, com.bestrong.core.training.Autoregulation.suggest(we, "a", 2.5)?.direction)
        // Con objetivo 6 (técnica): RPE 7 ya es duro, pero no tanto como para bajar
        assertEquals(null, com.bestrong.core.training.Autoregulation.suggest(we.copy(targetRpe = 6.0), "a", 2.5))
        // Con objetivo 10 un RPE 10 es lo buscado: no se baja
        val top = we.copy(targetRpe = 10.0, sets = listOf(done.copy(rpe = 10.0), pending))
        assertEquals(null, com.bestrong.core.training.Autoregulation.suggest(top, "a", 2.5))
    }

    @Test
    fun rpeVsTargetAveragesWorkingSets() {
        val ex = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        val sets = listOf(
            WorkoutSet(weightKg = 40.0, reps = 8, completed = true, type = SetType.WARMUP, rpe = 5.0),
            WorkoutSet(weightKg = 80.0, reps = 8, completed = true, rpe = 9.0),
            WorkoutSet(weightKg = 80.0, reps = 8, completed = true, rpe = 10.0),
        )
        val w = Workout(name = "W", startedAtEpochMs = 0, exercises = listOf(WorkoutExercise(exercise = ex, sets = sets, targetRpe = 8.0)))
        val r = com.bestrong.core.stats.RpeTarget.vsTarget(w).single()
        // El calentamiento no cuenta: media 9,5 frente a 8 → más duro de lo previsto
        assertEquals(9.5, r.averageRpe, 1e-9)
        assertEquals(1, r.direction)
        // Sin objetivo no sale
        assertEquals(0, com.bestrong.core.stats.RpeTarget.vsTarget(w.copy(exercises = w.exercises.map { it.copy(targetRpe = null) })).size)
    }

    @Test
    fun highTargetRpeThresholdsStayReachable() {
        // Con objetivo 9 un RPE 10 sí es pasarse; con 10 es lo buscado; sin objetivo, como siempre
        assertEquals(10.0, com.bestrong.core.training.Autoregulation.high(9.0), 1e-9)
        assertEquals(9.5, com.bestrong.core.training.Autoregulation.high(null), 1e-9)
        assertEquals(Double.MAX_VALUE, com.bestrong.core.training.Autoregulation.high(10.0), 1e-9)
        assertEquals(9.0, com.bestrong.core.training.Autoregulation.easyUpTo(null), 1e-9)
        assertEquals(9.5, com.bestrong.core.training.Autoregulation.easyUpTo(9.0), 1e-9)
    }

    @Test
    fun weeklyCapInPoundsIsLoadable() {
        val lb = com.bestrong.core.util.WeightUnit.LB
        val best = lb.toKg(90.0)
        val capped = com.bestrong.core.training.ProgressionCap.limitIn(lb, lb.toKg(95.0), best, 0.025, lb.stepKg())
        // El tope (≈ 92 lb) se baja al escalón de 5 lb, sin bajar de las 90 de la semana pasada
        assertEquals(90.0, lb.fromKg(capped), 1e-6)
    }

    @Test
    fun rpeLoadInvertsEpley() {
        // 1 rep a RPE 10 = el 1RM; 5 reps a RPE 8 = una serie de 7 al fallo
        assertEquals(100.0, com.bestrong.core.stats.RpeLoad.weightFor(100.0, 1, 10.0), 1e-9)
        assertEquals(100.0 / (1 + 7 / 30.0), com.bestrong.core.stats.RpeLoad.weightFor(100.0, 5, 8.0), 1e-9)
    }

    @Test
    fun csvKeepsTargetRpeAndTempo() {
        val ex = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        val w = Workout(
            name = "W", startedAtEpochMs = 1_700_000_000_000, finishedAtEpochMs = 1_700_003_600_000,
            exercises = listOf(WorkoutExercise(exercise = ex, sets = listOf(WorkoutSet(weightKg = 80.0, reps = 5, completed = true)), targetRpe = 8.5, tempo = "3-1-1-0")),
        )
        val back = com.bestrong.core.io.WorkoutCsv.import(com.bestrong.core.io.WorkoutCsv.export(listOf(w)), listOf(ex)).workouts.single().exercises.single()
        assertEquals(8.5, back.targetRpe!!, 1e-9)
        assertEquals("3-1-1-0", back.tempo)
    }

    @Test
    fun plannedFailureSetDoesNotAutoregulate() {
        val ex = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        val failure = WorkoutSet(id = "f", weightKg = 80.0, reps = 8, completed = true, type = SetType.FAILURE, rpe = 10.0)
        val we = WorkoutExercise(exercise = ex, sets = listOf(failure, WorkoutSet(id = "p", weightKg = 80.0, reps = 8)))
        assertEquals(null, com.bestrong.core.training.Autoregulation.suggest(we, "f", 2.5))
    }

    @Test
    fun loadHintRoundsMachinesToTheirStep() {
        val gym = com.bestrong.core.training.GymProfile(name = "G")
        // Polea: el gimnasio no la redondea, se usa el incremento del ejercicio
        assertEquals(62.5, com.bestrong.core.stats.RpeLoad.roundToLoadable(63.7, com.bestrong.core.util.WeightUnit.KG, 2.5, gym, com.bestrong.core.model.Equipment.CABLE), 1e-9)
    }

    @Test
    fun oneRepMaxCountsRepsInReserve() {
        // 5 × 100 a RPE 8 = 7 al fallo; sin RPE, Epley con 5; con RPE muy bajo no se fía
        assertEquals(100.0 * (1 + 7 / 30.0), com.bestrong.core.stats.RpeLoad.oneRepMaxOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true, rpe = 8.0)), 1e-9)
        assertEquals(100.0 * (1 + 5 / 30.0), com.bestrong.core.stats.RpeLoad.oneRepMaxOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true)), 1e-9)
        assertEquals(100.0 * (1 + 5 / 30.0), com.bestrong.core.stats.RpeLoad.oneRepMaxOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true, rpe = 4.0)), 1e-9)
    }

    @Test
    fun explosiveTempoKeepsX() {
        assertEquals("3-1-X-0", com.bestrong.core.training.Tempo.parse("3/1/x")?.toString())
    }

    @Test
    fun failureSetLowRpeStillRaises() {
        val ex = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        val f = WorkoutSet(id = "f", weightKg = 80.0, reps = 8, completed = true, type = SetType.FAILURE, rpe = 8.0)
        val we = WorkoutExercise(exercise = ex, sets = listOf(f, WorkoutSet(id = "p", weightKg = 80.0, reps = 8, type = SetType.FAILURE)))
        // Al fallo con RPE 8: quedaba mucho, se propone subir; con RPE 10 no se toca
        assertEquals(1, com.bestrong.core.training.Autoregulation.suggest(we, "f", 2.5)?.direction)
        assertEquals(null, com.bestrong.core.training.Autoregulation.suggest(we.copy(sets = listOf(f.copy(rpe = 10.0), we.sets[1])), "f", 2.5))
    }

    @Test
    fun dismissedTipsForgetOldDatedKeys() {
        val today = java.time.LocalDate.of(2026, 10, 14)
        val keys = setOf("year_ago:2026-01-02", "year_ago:2026-10-01", "tip_plates", "year_ago:2026-10", "year_ago:2026-01", "missed:2026-01-05:2", "missed:2026-10-12:1")
        val kept = com.bestrong.core.util.DismissedTips.prune(keys, today)
        assertEquals(setOf("year_ago:2026-10-01", "tip_plates", "year_ago:2026-10", "missed:2026-10-12:1"), kept)
    }

    @Test
    fun quickRoutinesPutTodaysPlanFirst() {
        val ex = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        fun r(id: String) = com.bestrong.core.model.Routine(id = id, name = id, exercises = listOf(com.bestrong.core.model.RoutineExercise(exercise = ex)))
        val done = Workout(name = "A", routineId = "a", startedAtEpochMs = 1_000, finishedAtEpochMs = 2_000)
        val picked = com.bestrong.core.training.QuickRoutines.pick(listOf(r("a"), r("b"), r("c")), listOf(done), plannedTodayId = "c", todayStartEpochMs = 500)
        // La de hoy primero; luego la que nunca se hizo antes que la hecha
        assertEquals(listOf("c", "b"), picked.map { it.id })
    }

    @Test
    fun explosiveConcentricStillCues() {
        val t = com.bestrong.core.training.Tempo.parse("3-1-X-0")!!
        assertEquals(listOf(com.bestrong.core.training.Tempo.Phase.ECCENTRIC, com.bestrong.core.training.Tempo.Phase.BOTTOM, com.bestrong.core.training.Tempo.Phase.CONCENTRIC), t.guidePhases.map { it.first })
        // El tiempo bajo tensión no cambia: la subida explosiva sigue contando 0 s
        assertEquals(4, t.repSeconds)
    }

    @Test
    fun backupSummaryCountsAndRange() {
        val data = com.bestrong.core.io.BackupData(history = listOf(Workout(name = "A", startedAtEpochMs = 5), Workout(name = "B", startedAtEpochMs = 9)))
        val s = com.bestrong.core.io.BackupSummary.of(data)
        assertEquals(2, s.workouts)
        assertEquals(5L, s.firstWorkoutEpochMs)
        assertEquals(9L, s.lastWorkoutEpochMs)
        assertEquals(false, s.hasSettings)
    }

    @Test
    fun comparisonCountsTimeAndSkipsMissing() {
        val plank = com.bestrong.core.model.Exercise(id = "plank", name = "Plancha", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST, tracking = com.bestrong.core.model.TrackingType.DURATION)
        val bp = com.bestrong.core.model.Exercise(id = "bp", name = "Press", primaryMuscle = com.bestrong.core.model.MuscleGroup.CHEST)
        fun w(id: String, t: Long, sec: Int, withBench: Boolean) = Workout(
            id = id, name = id, startedAtEpochMs = t, finishedAtEpochMs = t + 1,
            exercises = listOf(WorkoutExercise(exercise = plank, sets = listOf(WorkoutSet(durationSec = sec, completed = true)))) +
                if (withBench) listOf(WorkoutExercise(exercise = bp, sets = listOf(WorkoutSet(weightKg = 80.0, reps = 5, completed = true)))) else emptyList(),
        )
        val before = w("a", 1, 60, true)
        val now = w("b", 10, 90, false).let { it.copy(exercises = it.exercises + WorkoutExercise(exercise = bp, sets = listOf(WorkoutSet(weightKg = 80.0, reps = 5)))) }
        val c = com.bestrong.core.stats.WorkoutStats.compareWithPrevious(now, listOf(before))
        // La plancha: +30 s; el press se saltó hoy (ninguna serie hecha): no sale
        assertEquals(30, c.single().durationDelta)
        assertEquals("plank", c.single().exerciseId)
    }
}
