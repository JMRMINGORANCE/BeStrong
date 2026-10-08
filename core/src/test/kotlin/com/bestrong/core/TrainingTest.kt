package com.bestrong.core

import com.bestrong.core.library.ExerciseInfo
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.SetType
import com.bestrong.core.session.WorkoutEditor
import com.bestrong.core.training.PlateCalculator
import com.bestrong.core.training.Progression
import com.bestrong.core.training.SuggestionKind
import com.bestrong.core.training.WarmupGenerator
import com.bestrong.core.util.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingTest {
    private val d = LocalDate.of(2026, 9, 1)

    @Test fun platesKg() {
        val load = PlateCalculator.calculate(102.5, WeightUnit.KG)
        assertEquals(listOf(25.0, 15.0, 1.25), load.perSide)
        assertEquals(102.5, load.achieved, 0.0)
        assertEquals(0.0, load.remainder, 0.0)
    }

    @Test fun platesLbAndRemainder() {
        val load = PlateCalculator.calculate(226.0, WeightUnit.LB)
        assertEquals(listOf(45.0, 45.0), load.perSide)
        assertEquals(1.0, load.remainder, 0.001)
        assertTrue(PlateCalculator.calculate(15.0, WeightUnit.KG).perSide.isEmpty())
    }

    @Test fun units() {
        assertEquals("225", WeightUnit.LB.format(WeightUnit.LB.toKg(225.0)))
        assertEquals("82,5 kg", WeightUnit.KG.format(82.5, withUnit = true))
        assertEquals("12.345 kg", WeightUnit.KG.formatVolume(12_345.0))
        assertEquals("220 lb", WeightUnit.LB.formatVolume(WeightUnit.LB.toKg(220.0)))
        assertEquals(100.0, WeightUnit.KG.parseToKg("100")!!, 0.0)
    }

    @Test fun warmup() {
        val sets = WarmupGenerator.generate(100.0, WeightUnit.KG)
        assertEquals(listOf(20.0, 40.0, 60.0, 80.0), sets.map { it.weightKg })
        assertTrue(sets.all { it.type == SetType.WARMUP })
        assertTrue(WarmupGenerator.generate(20.0, WeightUnit.KG).isEmpty())
    }

    @Test fun progressionIncreasesWhenAllRepsHit() {
        val h = listOf(finishedWorkout("a", d, bench to listOf(done(80.0, 8), done(80.0, 8), done(80.0, 8))))
        val s = Progression.suggest(bench, h)!!
        assertEquals(SuggestionKind.INCREASE_WEIGHT, s.kind)
        assertEquals(listOf(82.5, 82.5, 82.5), s.sets.map { it.weightKg })
        assertTrue(s.sets.none { it.completed })
    }

    @Test fun progressionAddsRepsWhenShort() {
        val h = listOf(finishedWorkout("a", d, bench to listOf(done(80.0, 8), done(80.0, 6))))
        val s = Progression.suggest(bench, h)!!
        assertEquals(SuggestionKind.ADD_REPS, s.kind)
        assertEquals(listOf(8, 8), s.sets.map { it.reps })
        assertEquals(80.0, s.sets.last().weightKg, 0.0)
    }

    @Test fun progressionRespectsHighRpe() {
        val h = listOf(finishedWorkout("a", d, bench to listOf(done(80.0, 8).copy(rpe = 10.0))))
        assertEquals(SuggestionKind.REPEAT, Progression.suggest(bench, h)!!.kind)
        assertNull(Progression.suggest(squat, h))
    }

    @Test fun deloadWhenStalled() {
        val h = listOf(
            finishedWorkout("1", d, bench to listOf(done(100.0, 5))),
            finishedWorkout("2", d.plusDays(3), bench to listOf(done(100.0, 4))),
            finishedWorkout("3", d.plusDays(6), bench to listOf(done(100.0, 4))),
            finishedWorkout("4", d.plusDays(9), bench to listOf(done(100.0, 3))),
        )
        assertTrue(Progression.isStalled(bench.id, h))
        val s = Progression.suggest(bench, h)!!
        assertEquals(SuggestionKind.DELOAD, s.kind)
        assertEquals(90.0, s.sets.single().weightKg, 0.0)
        assertFalse(Progression.isStalled(bench.id, h.take(2)))
    }

    @Test fun supersetFlow() {
        var w = WorkoutEditor.addExercises(WorkoutEditor.empty(0), listOf(bench, squat, ExerciseLibrary.byId("barbell_curl")!!))
        val (a, b, c) = w.exercises
        w = WorkoutEditor.linkWithNext(w, a.id)
        assertEquals(listOf(0, 1), WorkoutEditor.supersetGroup(w, 0))
        // Serie de A → pasa a B sin descanso
        w = WorkoutEditor.toggleSetCompleted(w, a.id, a.sets[0].id)
        assertEquals(1 to 0, WorkoutEditor.afterSetCompleted(w, 0))
        // Serie de B → cierra la ronda: vuelve a A con descanso
        w = WorkoutEditor.toggleSetCompleted(w, b.id, b.sets[0].id)
        assertEquals(0 to b.restSeconds, WorkoutEditor.afterSetCompleted(w, 1))
        // C fuera de la superserie: sigue en C con su descanso
        w = WorkoutEditor.toggleSetCompleted(w, c.id, c.sets[0].id)
        assertEquals(2 to c.restSeconds, WorkoutEditor.afterSetCompleted(w, 2))
        // Deshacer la superserie con solo dos ejercicios la elimina
        w = WorkoutEditor.unlinkSuperset(w, a.id)
        assertTrue(w.exercises.all { it.supersetId == null })
    }

    @Test fun everyLibraryExerciseHasInstructions() {
        val missing = ExerciseLibrary.all.filter { ExerciseInfo.instructions(it.id) == null }.map { it.id }
        assertEquals(emptyList<String>(), missing)
        ExerciseLibrary.englishAliases.values.forEach { assertTrue(it, ExerciseLibrary.byId(it) != null) }
    }
}

class LocalizationTest {
    @Test fun everyExerciseHasEnglishNameAndInstructions() {
        val lib = com.bestrong.core.library.ExerciseLibrary
        val missing = lib.all.filter { lib.displayName(it, "en") == it.name && it.id != "squat" && it.id != "plank" && it.id != "burpee" && it.id != "deadlift" && it.id != "shrug" && it.id != "crunch" && it.id != "face_pull" && it.id != "hip_thrust" && it.id != "pec_deck" }
            .filter { com.bestrong.core.library.englishNames[it.id] == null }
        org.junit.Assert.assertEquals(emptyList<String>(), missing.map { it.id })
        lib.all.forEach { org.junit.Assert.assertNotNull(it.id, com.bestrong.core.library.ExerciseInfo.instructions(it.id, "en")) }
        org.junit.Assert.assertTrue(lib.search(lib.all, "bench", nameOf = { e -> lib.displayName(e, "en") }).any { it.id == "bench_press" })
    }
}
