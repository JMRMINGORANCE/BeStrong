package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.bestrong.wear.WearContainer
import kotlinx.coroutines.flow.StateFlow

object WearRoutes {
    const val HOME = "home"
    const val WORKOUT = "workout"
    const val REST = "rest"
    const val MENU = "menu"
    const val PICKER = "picker"
    const val SUMMARY = "summary"
}

@Composable
fun WearApp(container: WearContainer, startRequest: StateFlow<String?>, ambient: StateFlow<Boolean>, onStartRequestHandled: () -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    val session = container.session
    val request by startRequest.collectAsStateWithLifecycle()
    val active by session.workout.collectAsStateWithLifecycle()
    val rest by session.rest.collectAsStateWithLifecycle()
    val summary by session.summary.collectAsStateWithLifecycle()
    val isAmbient by ambient.collectAsStateWithLifecycle()
    val settings by container.store.settings.collectAsStateWithLifecycle()

    // Abierto desde el móvil: empezar rutina o entrenamiento vacío
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        if (session.workout.value == null) {
            val routine = container.store.routines.value.firstOrNull { it.id == r }
            if (routine != null) session.startRoutine(routine) else session.startEmpty()
        }
        onStartRequestHandled()
        nav.navigate(WearRoutes.WORKOUT) { launchSingleTop = true }
    }
    // El descanso termina solo: volvemos a la pantalla de series
    LaunchedEffect(rest) {
        if (rest == null && nav.currentDestination?.route == WearRoutes.REST) nav.popBackStack()
    }
    LaunchedEffect(summary) {
        if (summary != null) nav.navigate(WearRoutes.SUMMARY) { popUpTo(WearRoutes.HOME) }
    }

    BeStrongWearTheme {
      CompositionLocalProvider(LocalWeightUnit provides settings.unit) {
        if (isAmbient && active != null) {
            AmbientWorkoutScreen(session)
            return@CompositionLocalProvider
        }
        SwipeDismissableNavHost(navController = nav, startDestination = if (active != null) WearRoutes.WORKOUT else WearRoutes.HOME) {
            composable(WearRoutes.HOME) {
                HomeScreen(
                    container = container,
                    onStart = { nav.navigate(WearRoutes.WORKOUT) { launchSingleTop = true } },
                )
            }
            composable(WearRoutes.WORKOUT) {
                WorkoutScreen(
                    session = session,
                    onRest = { nav.navigate(WearRoutes.REST) { launchSingleTop = true } },
                    onMenu = { nav.navigate(WearRoutes.MENU) },
                    onAddExercise = { nav.navigate(WearRoutes.PICKER) },
                    onNoWorkout = { nav.navigate(WearRoutes.HOME) { popUpTo(0) } },
                )
            }
            composable(WearRoutes.REST) { RestScreen(session) }
            composable(WearRoutes.MENU) {
                MenuScreen(
                    session = session,
                    onBack = { nav.popBackStack() },
                    onAddExercise = { nav.navigate(WearRoutes.PICKER) },
                    onDiscarded = { nav.navigate(WearRoutes.HOME) { popUpTo(0) } },
                )
            }
            composable(WearRoutes.PICKER) {
                ExercisePickerScreen(container) { exercise ->
                    session.addExercise(exercise)
                    nav.popBackStack(WearRoutes.WORKOUT, inclusive = false)
                }
            }
            composable(WearRoutes.SUMMARY) {
                SummaryScreen(session) {
                    session.dismissSummary()
                    nav.navigate(WearRoutes.HOME) { popUpTo(0) }
                }
            }
        }
      }
    }
}
