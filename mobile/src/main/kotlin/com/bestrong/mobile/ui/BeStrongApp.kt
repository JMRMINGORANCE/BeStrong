package com.bestrong.mobile.ui

import androidx.annotation.StringRes
import com.bestrong.mobile.R
import androidx.compose.ui.res.stringResource
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.bestrong.mobile.LaunchRequest
import com.bestrong.mobile.ui.components.LocalWeightUnit
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.hilt.navigation.compose.hiltViewModel
import com.bestrong.mobile.ui.exercises.ExerciseDetailScreen
import com.bestrong.mobile.ui.exercises.ExerciseLibraryScreen
import com.bestrong.mobile.ui.exercises.ExercisePickerScreen
import com.bestrong.mobile.ui.exercises.ExercisesViewModel
import com.bestrong.mobile.ui.history.HistoryScreen
import com.bestrong.mobile.ui.history.HistoryViewModel
import com.bestrong.mobile.ui.home.HomeScreen
import com.bestrong.mobile.ui.home.HomeViewModel
import com.bestrong.mobile.ui.profile.MeasurementsScreen
import com.bestrong.mobile.ui.profile.ProfileScreen
import com.bestrong.mobile.ui.profile.ProfileViewModel
import com.bestrong.mobile.ui.routines.RoutineEditorScreen
import com.bestrong.mobile.ui.routines.RoutineEditorViewModel
import com.bestrong.mobile.ui.workout.ActiveWorkoutScreen
import com.bestrong.mobile.ui.workout.ActiveWorkoutViewModel

enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    TRAIN("train", R.string.nav_train, Icons.Default.FitnessCenter),
    HISTORY("history", R.string.nav_history, Icons.Default.History),
    EXERCISES("exercises", R.string.nav_exercises, Icons.AutoMirrored.Filled.List),
    PROFILE("profile", R.string.nav_profile, Icons.Default.Person),
}

private object Routes {
    const val WORKOUT = "workout"
    const val PICKER = "picker"
    const val ROUTINE = "routine?id={id}"
    const val EXERCISE = "exercise/{id}"
    const val MEASUREMENTS = "measurements"
    const val PICKED = "picked_exercises"
    fun routine(id: String?) = if (id == null) "routine" else "routine?id=$id"
    fun exercise(id: String) = "exercise/$id"
}

/** Estado global de la app que necesita la raíz de la UI (unidad de peso). */
@HiltViewModel
class AppViewModel @Inject constructor(settingsStore: SettingsStore) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsStore.settings
}

/**
 * NavigationSuiteScaffold muestra barra inferior con el Fold plegado y navigation rail
 * desplegado. Se oculta en pantallas de foco (entrenamiento, editor, selector).
 */
@Composable
fun BeStrongApp(launchRequest: StateFlow<LaunchRequest?>, onLaunchHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevel = TopLevel.entries.firstOrNull { it.route == route }
    val home: HomeViewModel = hiltViewModel()
    val appVm: AppViewModel = hiltViewModel()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val request by launchRequest.collectAsStateWithLifecycle()

    // Peticiones desde la notificación del entreno o el widget
    LaunchedEffect(request) {
        val openWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } }
        when (val r = request) {
            LaunchRequest.OpenWorkout -> openWorkout()
            LaunchRequest.StartEmpty -> home.startEmpty(openWorkout)
            is LaunchRequest.StartRoutine -> home.startRoutineById(r.routineId, openWorkout)
            null -> return@LaunchedEffect
        }
        onLaunchHandled()
    }

    val layoutType = if (topLevel == null) NavigationSuiteType.None
    else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())

    CompositionLocalProvider(LocalWeightUnit provides settings.unit) {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                TopLevel.entries.forEach { dest ->
                    item(
                        selected = dest == topLevel,
                        onClick = { nav.navigateTopLevel(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.label)) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            },
        ) {
            NavHost(nav, startDestination = TopLevel.TRAIN.route) {
                composable(TopLevel.TRAIN.route) {
                    HomeScreen(
                        vm = home,
                        onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                        onEditRoutine = { nav.navigate(Routes.routine(it)) },
                    )
                }
                composable(TopLevel.HISTORY.route) {
                    HistoryScreen(hiltViewModel<HistoryViewModel>(), onWorkoutStarted = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } })
                }
                composable(TopLevel.EXERCISES.route) {
                    ExerciseLibraryScreen(hiltViewModel<ExercisesViewModel>())
                }
                composable(TopLevel.PROFILE.route) {
                    ProfileScreen(
                        vm = hiltViewModel<ProfileViewModel>(),
                        onOpenMeasurements = { nav.navigate(Routes.MEASUREMENTS) },
                        onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                    )
                }
                composable(Routes.WORKOUT) { backStackEntry ->
                    // Ligado a la Activity: el temporizador de descanso sigue al minimizar el entrenamiento
                    val activity = LocalContext.current as ComponentActivity
                    val vm = hiltViewModel<ActiveWorkoutViewModel>(viewModelStoreOwner = activity)
                    val picked by backStackEntry.savedStateHandle.getStateFlow<ArrayList<String>?>(Routes.PICKED, null).collectAsStateWithLifecycle()
                    LaunchedEffect(picked) {
                        picked?.let {
                            vm.addExercises(it)
                            backStackEntry.savedStateHandle[Routes.PICKED] = null
                        }
                    }
                    ActiveWorkoutScreen(
                        vm = vm,
                        onAddExercises = { nav.navigate(Routes.PICKER) },
                        onBack = { nav.popBackStack() },
                        onFinished = { nav.navigateTopLevel(TopLevel.HISTORY.route) },
                    )
                }
                composable(Routes.PICKER) {
                    ExercisePickerScreen(
                        vm = hiltViewModel<ExercisesViewModel>(),
                        onBack = { nav.popBackStack() },
                        onPicked = { ids ->
                            nav.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED, ArrayList(ids))
                            nav.popBackStack()
                        },
                    )
                }
                composable(Routes.ROUTINE, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id")
                    val vm = hiltViewModel<RoutineEditorViewModel>()
                    val picked by backStackEntry.savedStateHandle.getStateFlow<ArrayList<String>?>(Routes.PICKED, null).collectAsStateWithLifecycle()
                    LaunchedEffect(picked) {
                        picked?.let {
                            vm.addExercises(it)
                            backStackEntry.savedStateHandle[Routes.PICKED] = null
                        }
                    }
                    RoutineEditorScreen(vm, onAddExercises = { nav.navigate(Routes.PICKER) }, onClose = { nav.popBackStack() })
                }
                composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id").orEmpty()
                    ExerciseDetailScreen(hiltViewModel<ExercisesViewModel>(), id, onBack = { nav.popBackStack() })
                }
                composable(Routes.MEASUREMENTS) {
                    MeasurementsScreen(hiltViewModel<ProfileViewModel>(), onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
