package com.bestrong.core

import com.bestrong.core.library.ExerciseLibrary
import com.bestrong.core.model.Workout
import com.bestrong.core.model.WorkoutExercise
import com.bestrong.core.model.WorkoutSet
import java.time.LocalDate
import java.time.ZoneOffset

val UTC: ZoneOffset = ZoneOffset.UTC
val bench = ExerciseLibrary.byId("bench_press")!!
val squat = ExerciseLibrary.byId("squat")!!

fun epoch(date: LocalDate, hour: Int = 18): Long = date.atTime(hour, 0).toInstant(UTC).toEpochMilli()

fun finishedWorkout(id: String, date: LocalDate, vararg exercises: Pair<com.bestrong.core.model.Exercise, List<WorkoutSet>>) =
    Workout(
        id = id,
        name = "W $id",
        startedAtEpochMs = epoch(date),
        finishedAtEpochMs = epoch(date, 19),
        exercises = exercises.map { (ex, sets) -> WorkoutExercise(exercise = ex, sets = sets) },
    )

fun done(weight: Double, reps: Int) = WorkoutSet(weightKg = weight, reps = reps, completed = true)
