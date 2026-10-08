package com.bestrong.core

import com.bestrong.core.io.Csv
import com.bestrong.core.io.CsvFormat
import com.bestrong.core.io.CsvImportException
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.SetType
import com.bestrong.core.util.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvTest {
    @Test fun parserHandlesQuotes() {
        val rows = Csv.parse("a,b,c\n\"x, y\",\"he said \"\"hi\"\"\",\"multi\nline\"\n")
        assertEquals(listOf("x, y", "he said \"hi\"", "multi\nline"), rows[1])
        assertEquals(';', Csv.detectDelimiter("Date;Workout Name;Exercise Name\n"))
    }

    @Test fun roundTripBeStrong() {
        val w = finishedWorkout("w1", LocalDate.of(2026, 3, 2), bench to listOf(done(80.0, 8), done(82.5, 6).copy(rpe = 8.5)), squat to listOf(done(100.0, 5)))
            .copy(notes = "Notas, con coma")
        val csv = WorkoutCsv.export(listOf(w), UTC)
        val r = WorkoutCsv.import(csv, ExerciseLibrary.all, UTC)
        assertEquals(CsvFormat.BESTRONG, r.format)
        val back = r.workouts.single()
        assertEquals(w.id, back.id)
        assertEquals(w.startedAtEpochMs, back.startedAtEpochMs)
        assertEquals(w.notes, back.notes)
        assertEquals(listOf(80.0, 82.5), back.exercises[0].sets.map { it.weightKg })
        assertEquals(8.5, back.exercises[0].sets[1].rpe!!, 0.0)
        assertTrue(r.newExercises.isEmpty())
    }

    @Test fun importHevy() {
        val csv = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Push","26 Jan 2026, 18:05","26 Jan 2026, 19:10","","Bench Press (Barbell)",,"",0,"warmup",40,10,,,
            "Push","26 Jan 2026, 18:05","26 Jan 2026, 19:10","","Bench Press (Barbell)",,"",1,"normal",80,8,,,8
            "Push","26 Jan 2026, 18:05","26 Jan 2026, 19:10","","Zercher Squat",,"",0,"normal",60,5,,,
            "Legs","28 Jan 2026, 18:00","28 Jan 2026, 19:00","","Squat (Barbell)",,"",0,"normal",100,5,,,
        """.trimIndent()
        val r = WorkoutCsv.import(csv, ExerciseLibrary.all, UTC)
        assertEquals(CsvFormat.HEVY, r.format)
        assertEquals(2, r.workouts.size)
        val push = r.workouts.first { it.name == "Push" }
        assertEquals("bench_press", push.exercises[0].exercise.id)
        assertEquals(SetType.WARMUP, push.exercises[0].sets[0].type)
        assertEquals(65L * 60 * 1000, push.finishedAtEpochMs!! - push.startedAtEpochMs)
        assertEquals(listOf("Zercher Squat"), r.newExercises.map { it.name })
    }

    @Test fun importStrongInPounds() {
        val csv = """
            Date;Workout Name;Duration;Exercise Name;Set Order;Weight;Reps;Distance;Seconds;Notes;Workout Notes;RPE
            2026-02-01 10:00:00;Upper;1h 5m;Deadlift (Barbell);1;225;5;0;0;;;
            2026-02-01 10:00:00;Upper;1h 5m;Deadlift (Barbell);W;135;5;0;0;;;
        """.trimIndent()
        val r = WorkoutCsv.import(csv, ExerciseLibrary.all, UTC, strongUnit = WeightUnit.LB)
        assertEquals(CsvFormat.STRONG, r.format)
        val sets = r.workouts.single().exercises.single().sets
        assertEquals(102.06, sets[0].weightKg, 0.01)
        assertEquals(SetType.WARMUP, sets[1].type)
    }

    @Test(expected = CsvImportException::class)
    fun unknownFormat() {
        WorkoutCsv.import("foo,bar\n1,2\n", ExerciseLibrary.all, UTC)
    }
}
