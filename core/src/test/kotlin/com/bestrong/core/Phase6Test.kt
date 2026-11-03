package com.bestrong.core

import com.bestrong.core.io.CsvFormat
import com.bestrong.core.io.EncryptedBackup
import com.bestrong.core.io.RoutineLink
import com.bestrong.core.io.WorkoutCsv
import com.bestrong.core.io.WrongPasswordException
import com.bestrong.core.io.Xlsx
import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.library.Programs
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import java.io.ByteArrayInputStream
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Phase6Test {
    @Test fun encryptedBackupRoundTrip() {
        val enc = EncryptedBackup.encrypt("{\"hola\":1}", "secreto".toCharArray())
        assertTrue(EncryptedBackup.isEncrypted(enc))
        assertEquals("{\"hola\":1}", EncryptedBackup.decrypt(enc, "secreto".toCharArray()))
        try {
            EncryptedBackup.decrypt(enc, "otra".toCharArray())
            fail("Debería fallar con otra contraseña")
        } catch (_: WrongPasswordException) {
        }
    }

    @Test fun xlsxIsAValidZip() {
        val w = Workout(
            name = "Empuje <A&B>",
            startedAtEpochMs = 0,
            finishedAtEpochMs = 1,
            exercises = listOf(WorkoutExercise(exercise = ExerciseLibrary.byId("bench_press")!!, sets = listOf(WorkoutSet(weightKg = 80.0, reps = 5, completed = true)))),
        )
        val bytes = Xlsx.workouts(listOf(w), ZoneOffset.UTC)
        val names = mutableListOf<String>()
        var sheet = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                names += e.name
                if (e.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes().decodeToString()
            }
        }
        assertTrue("xl/workbook.xml" in names && "[Content_Types].xml" in names)
        assertTrue(sheet.contains("Empuje &lt;A&amp;B&gt;"))
        assertTrue(sheet.contains("<v>80.0</v>"))
        assertEquals("AA", Xlsx.column(26))
    }

    @Test fun importFitNotesAndJefit() {
        val fitnotes = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
            2026-09-01,Flat Barbell Bench Press,Chest,80,kgs,5,,,,
            2026-09-01,Flat Barbell Bench Press,Chest,80,kgs,5,,,,
            2026-09-02,Barbell Squat,Legs,225,lbs,5,,,,
        """.trimIndent()
        val r = WorkoutCsv.import(fitnotes, ExerciseLibrary.all, ZoneOffset.UTC)
        assertEquals(CsvFormat.FITNOTES, r.format)
        assertEquals(2, r.workouts.size)
        val squatDay = r.workouts.first()
        assertEquals(102.06, squatDay.exercises.single().sets.single().weightKg, 0.01)

        val jefit = """
            ### SETTINGS ###
            unit,kg
            ### EXERCISE LOGS ###
            _id,belongplan,eid,mydate,ename,logs
            1,1,10,2026-09-03,Bench Press,"100x5,100x5,90x8"
            2,1,11,2026-09-03,Squat,"140x3"
            3,1,12,2026-09-05,Deadlift,
        """.trimIndent()
        val j = WorkoutCsv.import(jefit, ExerciseLibrary.all, ZoneOffset.UTC)
        assertEquals(CsvFormat.JEFIT, j.format)
        assertEquals(1, j.workouts.size)
        assertEquals(3, j.workouts.single().exercises.first().sets.size)
        assertEquals("bench_press", j.workouts.single().exercises.first().exercise.id)
        assertEquals(1, j.skippedRows)
    }

    @Test fun icsCalendar() {
        val ics = com.bestrong.core.io.Ics.generate(
            listOf(com.bestrong.core.io.CalendarEvent(java.time.LocalDate.of(2026, 10, 12), "Torso, fuerza", 60)),
            java.time.LocalTime.of(18, 0),
            java.time.ZoneId.of("Europe/Madrid"),
        )
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("DTSTART:20261012T160000Z"))
        assertTrue(ics.contains("DTEND:20261012T170000Z"))
        assertTrue(ics.contains("SUMMARY:Torso\\, fuerza"))
    }

    @Test fun routineLink() {
        val routine = Programs.all.first().toRoutines("es").first()
        val link = RoutineLink.encode(routine)
        assertTrue(link.startsWith("bestrong://routine?d="))
        assertTrue("Enlace demasiado largo para un QR: ${link.length}", link.length < 2_000)
        val back = RoutineLink.decode(link)
        assertNotNull(back)
        assertEquals(routine.exercises.map { it.exercise.id }, back!!.exercises.map { it.exercise.id })
        assertFalse(back.id == routine.id)
        assertEquals(null, RoutineLink.decode("bestrong://routine?d=%%%"))
    }
}
