package com.bestrong.core

import com.bestrong.core.io.Csv
import com.bestrong.core.io.MeasurementCsv
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Equipment
import com.bestrong.core.model.SetType
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import com.bestrong.core.stats.Strength
import com.bestrong.core.training.Goal
import com.bestrong.core.training.Injuries
import com.bestrong.core.training.InjuryZone
import com.bestrong.core.training.Peaking
import com.bestrong.core.training.ProgramGenerator
import com.bestrong.core.training.Progression
import com.bestrong.core.training.SuggestionKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos de borde de cálculos e importación que fallaban. */
class CoreFixesTest {
    private val bench = ExerciseLibrary.byId("bench_press")!!

    @Test fun generatedDaysNeverNearlyEmptyWithInjuries() {
        val zones = listOf(setOf(InjuryZone.SHOULDER), setOf(InjuryZone.KNEE), InjuryZone.entries.toSet())
        val equipment = listOf(Equipment.entries.toSet(), setOf(Equipment.BODYWEIGHT))
        for (z in zones) {
            for (eq in equipment) {
                for (g in Goal.entries) {
                    for (d in 2..6) {
                        ProgramGenerator.build(d, g, eq, 60, allowed = { Injuries.isSafe(it, z) }).routines.forEach { r ->
                            assertTrue("$z $g $d días ${r.name}: ${r.exercises.size}", r.exercises.size >= 2)
                        }
                    }
                }
            }
        }
    }

    @Test fun dropSetDoesNotBlockWeightIncrease() {
        val sets = listOf(
            WorkoutSet(weightKg = 100.0, reps = 8, completed = true, rpe = 8.0),
            WorkoutSet(weightKg = 100.0, reps = 8, completed = true, rpe = 8.0),
            WorkoutSet(weightKg = 80.0, reps = 6, completed = true, type = SetType.DROP),
        )
        val w = Workout(name = "a", startedAtEpochMs = 1000, finishedAtEpochMs = 2000, exercises = listOf(WorkoutExercise(exercise = bench, sets = sets)))
        assertEquals(SuggestionKind.INCREASE_WEIGHT, Progression.suggest(bench, listOf(w))?.kind)
    }

    @Test fun peakingCountsPartialWeek() {
        assertEquals(2, Peaking.plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14)).size)
        assertEquals(12, Peaking.plan(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(84)).size)
    }

    @Test fun monthComparesWithPreviousCalendarMonth() {
        fun w(id: String, d: LocalDate, kg: Double) = Workout(
            id = id, name = id,
            startedAtEpochMs = d.atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            finishedAtEpochMs = d.atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            exercises = listOf(WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(weightKg = kg, reps = 10, completed = true)))),
        )
        val h = listOf(w("a", LocalDate.of(2026, 1, 2), 100.0), w("b", LocalDate.of(2026, 1, 20), 100.0), w("c", LocalDate.of(2026, 2, 10), 200.0))
        assertEquals(0.0, Strength.monthSummary(h, YearMonth.of(2026, 2), ZoneOffset.UTC).volumeChange!!, 1e-9)
    }

    @Test fun csvKeepsTwoEntriesOfSameExercise() {
        val w = Workout(
            id = "w2", name = "N", startedAtEpochMs = 1_700_000_000_000, finishedAtEpochMs = 1_700_000_360_000,
            exercises = listOf(
                WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(weightKg = 100.0, reps = 5, completed = true)), notes = "heavy"),
                WorkoutExercise(exercise = bench, sets = listOf(WorkoutSet(weightKg = 60.0, reps = 12, completed = true)), notes = "light"),
            ),
        )
        val back = WorkoutCsv.import(WorkoutCsv.export(listOf(w), ZoneOffset.UTC), emptyList(), ZoneOffset.UTC).workouts.single()
        assertEquals(listOf("heavy", "light"), back.exercises.map { it.notes })
    }

    @Test fun strayQuoteInsideFieldIsLiteral() {
        assertEquals(listOf(listOf("a", "5\" bar", "c"), listOf("d", "e", "f")), Csv.parse("a,5\" bar,c\nd,e,f\n"))
        assertEquals(listOf(listOf("x", "a,b")), Csv.parse("x,\"a,b\"\n"))
    }

    @Test fun guidedStepsTranslatedToEveryAppLanguage() {
        val steps = com.bestrong.core.training.BodyFocus.entries.flatMap {
            com.bestrong.core.training.GuidedRoutines.warmup(it) + com.bestrong.core.training.GuidedRoutines.stretching(it)
        }
        for (lang in listOf("ca", "gl", "pt", "fr", "de")) {
            steps.forEach { st -> assertTrue("${st.nameEs} sin traducir a $lang", st.name(lang) != st.nameEn || st.nameEn == st.nameEs || st.nameEs == "Cobra") }
        }
        assertEquals("Armkreisen", com.bestrong.core.training.GuidedStep("Círculos de brazos", "Arm circles", 30).name("de"))
        assertEquals("Arm circles", com.bestrong.core.training.GuidedStep("Círculos de brazos", "Arm circles", 30).name("en"))
    }

    @Test fun programTextsTranslatedToEveryAppLanguage() {
        val pairs = com.bestrong.core.library.Programs.all.flatMap { p ->
            listOf(p.nameEs to p.nameEn, p.descriptionEs to p.descriptionEn) + p.routines.map { it.nameEs to it.nameEn }
        } + com.bestrong.core.training.PeriodizedPrograms.all.flatMap { p ->
            listOf(p.nameEs to p.nameEn, p.descriptionEs to p.descriptionEn) + p.days.map { it.nameEs to it.nameEn }
        }
        // Los iguales en español e inglés (A, B, PHUL, 5/3/1…) no necesitan traducción
        val missing = pairs.filter { (es, en) -> es != en }.distinct().flatMap { (es, _) ->
            listOf("ca", "gl", "pt", "fr", "de").filterNot { com.bestrong.core.i18n.ContentText.has(es, it) }.map { "$es → $it" }
        }
        assertEquals(emptyList<String>(), missing)
    }

    @Test fun movingExerciseNeverLeavesSplitSuperset() {
        val squat = ExerciseLibrary.byId("squat")!!
        val curl = ExerciseLibrary.byId("barbell_curl")!!
        var w = com.bestrong.core.session.WorkoutEditor.addExercises(com.bestrong.core.session.WorkoutEditor.empty(0), listOf(bench, squat, curl))
        w = com.bestrong.core.session.WorkoutEditor.linkWithNext(w, w.exercises[0].id)
        // A+B en superserie, C suelto: subir C deja A, C, B → A y B ya no pueden seguir agrupadas
        w = com.bestrong.core.session.WorkoutEditor.moveExercise(w, 2, 1)
        assertTrue(w.exercises.all { it.supersetId == null })
    }

    @Test fun substituteKeepsCompletedSetsOnOriginal() {
        val db = ExerciseLibrary.byId("db_bench_press")!!
        var w = com.bestrong.core.session.WorkoutEditor.addExercises(com.bestrong.core.session.WorkoutEditor.empty(0), listOf(bench))
        val we = w.exercises[0]
        w = com.bestrong.core.session.WorkoutEditor.toggleSetCompleted(w, we.id, we.sets[0].id)
        w = com.bestrong.core.session.WorkoutEditor.substitute(w, we.id, db, emptyList(), 2.0)
        assertEquals(listOf("bench_press", "db_bench_press"), w.exercises.map { it.exercise.id })
        assertTrue(w.exercises[0].sets.all { it.completed })
        assertTrue(w.exercises[1].sets.none { it.completed })
        assertEquals(we.sets.size - 1, w.exercises[1].sets.size)
    }

    @Test fun measurementCsvStripsBom() {
        val m = MeasurementCsv.read("2024-01-01,80.5,,,,,\n2024-01-08,80.0,,,,,\n", ZoneOffset.UTC)
        assertEquals(2, m.rows.size)
    }

    @Test fun spokenKilosWinOverPoundsUnit() {
        val lb = com.bestrong.core.util.WeightUnit.LB
        assertEquals(80.0, com.bestrong.core.training.VoiceSetParser.parse("80 kilos 8 reps").weightKg(lb)!!, 1e-9)
        assertEquals(lb.toKg(80.0), com.bestrong.core.training.VoiceSetParser.parse("80 por 8").weightKg(lb)!!, 1e-9)
    }
}
