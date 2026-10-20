package com.bestrong.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
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
    const val INFO = "info"
    const val CARDIO = "cardio"
    const val ROUTINE_NEW = "routine_new"
    const val CALIBRATE = "calibrate"
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
    val cardioState by container.cardio.state.collectAsStateWithLifecycle()

    // Abierto desde el móvil: empezar rutina o entrenamiento vacío
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        onStartRequestHandled()
        // Con una carrera en marcha no se abre un entreno a la vez (se pisarían el pulso y el servicio)
        if (container.cardio.isActive) {
            nav.navigate(WearRoutes.CARDIO) { launchSingleTop = true }
            return@LaunchedEffect
        }
        if (session.workout.value == null) {
            val routine = container.store.routines.value.firstOrNull { it.id == r }
            if (routine != null) session.startRoutine(routine) else session.startEmpty()
        }
        // Sobre Inicio: si estaba en el descanso no queda debajo un descanso muerto (pantalla vacía al volver)
        nav.navigate(WearRoutes.WORKOUT) {
            popUpTo(WearRoutes.HOME)
            launchSingleTop = true
        }
    }
    // El descanso termina solo: volvemos a la pantalla de series
    LaunchedEffect(rest) {
        if (rest == null && nav.currentDestination?.route == WearRoutes.REST) nav.popBackStack()
    }
    LaunchedEffect(summary, active?.id) {
        val route = nav.currentDestination?.route
        when {
            summary != null -> nav.navigate(WearRoutes.SUMMARY) {
                popUpTo(WearRoutes.HOME)
                launchSingleTop = true
            }
            // Un entreno nuevo (del móvil o del tile) con el resumen a la vista: a sus series, no a un resumen vacío
            route == WearRoutes.SUMMARY && active != null -> nav.navigate(WearRoutes.WORKOUT) { popUpTo(WearRoutes.HOME) }
            // Terminado sin series hechas o cerrado desde el móvil con el menú, la info o la calibración abiertos:
            // a Inicio (si no, se quedaba una pantalla negra)
            active == null && route in SESSION_ROUTES -> nav.navigate(WearRoutes.HOME) { popUpTo(0) }
        }
    }

    BeStrongWearTheme {
        CompositionLocalProvider(LocalWeightUnit provides settings.unit) {
            if (isAmbient && active != null) {
                AmbientWorkoutScreen(session)
                return@CompositionLocalProvider
            }
            if (isAmbient && cardioState != null) {
                AmbientCardioScreen(container.cardio)
                return@CompositionLocalProvider
            }
            // Solo en pantallas sin nada a medias (el editor de rutinas perdería lo añadido y el cardio su resumen)
            val route = nav.currentBackStackEntryAsState().value?.destination?.route
            if (isAmbient && route in setOf(WearRoutes.HOME, WearRoutes.SUMMARY)) {
                AmbientIdleScreen()
                return@CompositionLocalProvider
            }
            AppScaffold {
                // Inicio fijo: si dependiera del estado en vivo, cada cambio rehace el grafo y borra la pila
                SwipeDismissableNavHost(navController = nav, startDestination = WearRoutes.HOME) {
                    composable(WearRoutes.HOME) {
                        HomeScreen(
                            container = container,
                            onStart = { nav.navigate(WearRoutes.WORKOUT) { launchSingleTop = true } },
                            onCardio = { nav.navigate(WearRoutes.CARDIO) { launchSingleTop = true } },
                            onCreateRoutine = { nav.navigate(WearRoutes.ROUTINE_NEW) },
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
                            onInfo = { nav.navigate(WearRoutes.INFO) },
                            onCalibrate = { nav.navigate(WearRoutes.CALIBRATE) },
                            onDiscarded = { nav.navigate(WearRoutes.HOME) { popUpTo(0) } },
                        )
                    }
                    composable(WearRoutes.INFO) { ExerciseInfoScreen(session) }
                    composable(WearRoutes.CARDIO) {
                        // Al salir (también deslizando) el resumen del cardio ya visto no vuelve a salir
                        DisposableEffect(Unit) { onDispose { if (container.cardio.state.value == null) container.cardio.dismissFinished() } }
                        CardioScreen(container.cardio) { nav.navigate(WearRoutes.HOME) { popUpTo(0) } }
                    }
                    composable(WearRoutes.ROUTINE_NEW) { RoutineCreateScreen(container) { nav.popBackStack() } }
                    composable(WearRoutes.CALIBRATE) { CalibrationScreen(session) { nav.popBackStack() } }
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
                // Entreno o cardio en marcha (al abrir la app o empezado desde el móvil): a su pantalla
                val cardioActive = cardioState != null
                LaunchedEffect(active?.id, cardioActive) {
                    if (nav.currentDestination?.route != WearRoutes.HOME) return@LaunchedEffect
                    val target = when {
                        active != null -> WearRoutes.WORKOUT
                        cardioActive -> WearRoutes.CARDIO
                        else -> return@LaunchedEffect
                    }
                    nav.navigate(target) {
                        popUpTo(WearRoutes.HOME)
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}

/** Pantallas que solo tienen sentido con un entreno abierto. */
private val SESSION_ROUTES = setOf(WearRoutes.WORKOUT, WearRoutes.REST, WearRoutes.MENU, WearRoutes.INFO, WearRoutes.PICKER, WearRoutes.CALIBRATE)
