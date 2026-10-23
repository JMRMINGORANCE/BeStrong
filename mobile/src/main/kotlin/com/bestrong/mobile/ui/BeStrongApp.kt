package com.bestrong.mobile.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bestrong.core.training.BodyFocus
import com.bestrong.mobile.BuildConfig
import com.bestrong.mobile.LaunchRequest
import com.bestrong.mobile.data.SettingsStore
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.AppInfo
import com.bestrong.mobile.ui.components.LocalAppInfo
import com.bestrong.mobile.ui.components.LocalBodyUnit
import com.bestrong.mobile.ui.components.LocalSetColumns
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SetColumns
import com.bestrong.mobile.ui.exercises.ExerciseDetailScreen
import com.bestrong.mobile.ui.exercises.ExerciseLibraryScreen
import com.bestrong.mobile.ui.exercises.ExercisePickerScreen
import com.bestrong.mobile.ui.exercises.ExercisesViewModel
import com.bestrong.mobile.ui.help.LocalTips
import com.bestrong.mobile.ui.help.Tips
import com.bestrong.mobile.ui.help.TutorialDialog
import com.bestrong.mobile.ui.help.WhatsNewDialog
import com.bestrong.mobile.ui.history.HistoryScreen
import com.bestrong.mobile.ui.history.HistoryViewModel
import com.bestrong.mobile.ui.home.HomeScreen
import com.bestrong.mobile.ui.home.HomeViewModel
import com.bestrong.mobile.ui.planning.GuidedSessionScreen
import com.bestrong.mobile.ui.planning.IntervalRoute
import com.bestrong.mobile.ui.planning.IntervalTimerScreen
import com.bestrong.mobile.ui.planning.PlanningScreen
import com.bestrong.mobile.ui.planning.PlanningViewModel
import com.bestrong.mobile.ui.planning.ProgramEditorScreen
import com.bestrong.mobile.ui.profile.MeasurementsScreen
import com.bestrong.mobile.ui.profile.ProfileScreen
import com.bestrong.mobile.ui.profile.ProfileViewModel
import com.bestrong.mobile.ui.routines.RoutineEditorScreen
import com.bestrong.mobile.ui.routines.RoutineEditorViewModel
import com.bestrong.mobile.ui.workout.ActiveWorkoutScreen
import com.bestrong.mobile.ui.workout.ActiveWorkoutViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

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
    const val EXISTING = "picker_existing"
    const val PLANNING = "planning"
    const val PROGRAM_EDITOR = "program_editor?id={id}"
    const val TIMER = "timer/{plan}"
    const val GUIDED = "guided/{warmup}/{focus}"
    fun programEditor(id: String?) = if (id == null) "program_editor" else "program_editor?id=$id"
    fun timer(plan: String) = "timer/$plan"
    fun guided(warmup: Boolean, focus: String) = "guided/$warmup/$focus"
    fun routine(id: String?) = if (id == null) "routine" else "routine?id=$id"
    fun exercise(id: String) = "exercise/$id"
}

/** Estado global de la app que necesita la raíz de la UI (unidad de peso). */
@HiltViewModel
class AppViewModel @Inject constructor(private val settingsStore: SettingsStore) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsStore.settings
    fun update(block: (UserSettings) -> UserSettings) = settingsStore.update(block)
}

/** En las pruebas de interfaz (Robolectric) no se muestran tutorial ni novedades. */
private val isRobolectric = android.os.Build.FINGERPRINT == "robolectric"

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

    // Tutorial la primera vez; novedades una vez tras cada actualización
    if (!isRobolectric) {
        if (!settings.tutorialDone) {
            // Los gimnasios de serie se crean con nombre en español: se traducen al terminar la bienvenida
            val gymName = stringResource(R.string.gym_default_gym)
            val homeName = stringResource(R.string.gym_default_home)
            TutorialDialog(
                onDone = { appVm.update { it.copy(tutorialDone = true, lastSeenVersion = BuildConfig.VERSION_CODE) } },
                // En EE. UU. (y Liberia, Myanmar) se entrena en libras: la opción marcada de partida
                unit = if (java.util.Locale.getDefault().country in POUND_COUNTRIES) com.bestrong.core.util.WeightUnit.LB else settings.unit,
                weeklyGoal = settings.weeklyGoal,
                onSetup = { unit, goal, home, training ->
                    appVm.update { s ->
                        s.copy(unit = unit, weeklyGoal = goal, trainingGoal = training, pendingBuilder = true).trainingAt(home).let { t ->
                            t.copy(
                                gyms = t.gyms.map { g ->
                                    when (g.name) {
                                        "Gimnasio" -> g.copy(name = gymName)
                                        "Casa" -> g.copy(name = homeName)
                                        else -> g
                                    }
                                },
                            )
                        }
                    }
                },
                atHome = com.bestrong.core.model.Equipment.BARBELL !in settings.activeGym.equipment,
                trainingGoal = settings.trainingGoal,
                sex = settings.sex,
                onSex = { sex -> appVm.update { it.copy(sex = sex) } },
            )
        } else if (settings.lastSeenVersion < BuildConfig.VERSION_CODE) {
            WhatsNewDialog { appVm.update { it.copy(lastSeenVersion = BuildConfig.VERSION_CODE) } }
        }
    }

    // Peticiones desde la notificación del entreno o el widget
    LaunchedEffect(request) {
        // Si el entreno ya está en la pila (p. ej. debajo del selector de ejercicios), se vuelve a él en vez de
        // apilar otro: si no, Atrás llevaba al selector viejo y al terminar quedaba un entreno vacío en Entrenar
        val openWorkout = { if (!nav.popBackStack(Routes.WORKOUT, inclusive = false)) nav.navigate(Routes.WORKOUT) { launchSingleTop = true } }
        when (val r = request) {
            LaunchRequest.OpenWorkout -> openWorkout()
            LaunchRequest.StartEmpty -> home.startEmpty(openWorkout)
            is LaunchRequest.StartRoutine -> home.startRoutineById(r.routineId, openWorkout)
            is LaunchRequest.ImportRoutine -> home.importRoutineLink(r.link)
            is LaunchRequest.OpenMuscle -> nav.openMuscle(r.muscle)
            null -> return@LaunchedEffect
        }
        onLaunchHandled()
    }

    // Rutina por enlace o QR: se confirma antes de añadirla
    val pendingImport by home.pendingImport.collectAsStateWithLifecycle()
    pendingImport?.let { routine ->
        AlertDialog(
            onDismissRequest = home::dismissImport,
            title = { Text(stringResource(R.string.routine_import_confirm_title)) },
            text = { Text(pluralStringResource(R.plurals.routine_import_confirm_text, routine.exercises.size, routine.name, routine.exercises.size)) },
            confirmButton = { TextButton(onClick = { home.confirmImport() }) { Text(stringResource(R.string.action_import)) } },
            dismissButton = { TextButton(onClick = home::dismissImport) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    val layoutType = if (topLevel == null) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    CompositionLocalProvider(
        LocalWeightUnit provides settings.unit,
        com.bestrong.mobile.ui.components.LocalDumbbellTotal provides settings.dumbbellTotal,
        LocalBodyUnit provides (settings.bodyUnit ?: settings.unit),
        LocalSetColumns provides SetColumns(settings.showPreviousColumn, settings.showComparison),
        LocalAppInfo provides AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.REPO_URL, BuildConfig.DONATION_URL),
        LocalTips provides Tips(settings.dismissedTips) { id ->
            // Al descartar uno, se olvidan los avisos con fecha que ya no pueden volver
            appVm.update { it.copy(dismissedTips = com.bestrong.core.util.DismissedTips.prune(it.dismissedTips + id, java.time.LocalDate.now())) }
        },
    ) {
        // Barra de navegación sobre una superficie algo más clara que el fondo y la pestaña activa en una
        // píldora del color de acento
        val scheme = androidx.compose.material3.MaterialTheme.colorScheme
        val itemColors = androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults.itemColors(
            navigationBarItemColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                indicatorColor = scheme.primary,
                selectedIconColor = scheme.onPrimary,
                selectedTextColor = scheme.primary,
                unselectedIconColor = scheme.onSurfaceVariant,
                unselectedTextColor = scheme.onSurfaceVariant,
            ),
            navigationRailItemColors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                indicatorColor = scheme.primary,
                selectedIconColor = scheme.onPrimary,
                selectedTextColor = scheme.primary,
                unselectedIconColor = scheme.onSurfaceVariant,
                unselectedTextColor = scheme.onSurfaceVariant,
            ),
        )
        // Cada pestaña con su color de fondo, como las pantallas de la bienvenida
        val tabTint = when (topLevel) {
            TopLevel.entries.getOrNull(1) -> scheme.secondary
            TopLevel.entries.getOrNull(2) -> scheme.tertiary
            else -> scheme.primary
        }
        com.bestrong.mobile.ui.theme.ScreenBackdrop(tabTint) {
            // Con letra muy grande no caben las cuatro etiquetas: solo la de la pestaña elegida
            val navLabels = androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.5f
            NavigationSuiteScaffold(
                layoutType = layoutType,
                navigationSuiteColors = androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults.colors(
                    navigationBarContainerColor = scheme.surfaceContainerLow,
                    navigationRailContainerColor = scheme.surfaceContainerLow,
                ),
                navigationSuiteItems = {
                    TopLevel.entries.forEach { dest ->
                        item(
                            colors = itemColors,
                            selected = dest == topLevel,
                            onClick = { nav.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.label)) },
                            label = { Text(stringResource(dest.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            alwaysShowLabel = navLabels,
                        )
                    }
                },
            ) {
                NavHost(nav, startDestination = TopLevel.TRAIN.route) {
                    composable(TopLevel.TRAIN.route) {
                        HomeScreen(
                            vm = home,
                            onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                            // Un doble toque no abre dos editores (el de debajo pisaría lo guardado en el de arriba)
                            onEditRoutine = { if (entry?.destination?.route == TopLevel.TRAIN.route) nav.navigate(Routes.routine(it)) { launchSingleTop = true } },
                            onOpenPlanning = { nav.navigate(Routes.PLANNING) { launchSingleTop = true } },
                            onOpenMuscle = { muscle -> nav.openMuscle(muscle.name) },
                            onOpenExercise = { nav.navigate(Routes.exercise(it)) { launchSingleTop = true } },
                        )
                    }
                    composable(TopLevel.HISTORY.route) {
                        HistoryScreen(
                            hiltViewModel<HistoryViewModel>(),
                            onWorkoutStarted = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                            onGoTrain = { nav.navigateTopLevel(TopLevel.TRAIN.route) },
                        )
                    }
                    composable(TopLevel.EXERCISES.route) { entry ->
                        val exercisesVm = hiltViewModel<ExercisesViewModel>()
                        // Desde Inicio («vas corto de espalda»): la biblioteca se abre filtrada por ese músculo
                        val muscle by entry.savedStateHandle.getStateFlow<String?>(KEY_MUSCLE_FILTER, null).collectAsStateWithLifecycle()
                        LaunchedEffect(muscle) {
                            muscle?.let { exercisesVm.showMuscle(it) }
                            entry.savedStateHandle[KEY_MUSCLE_FILTER] = null
                        }
                        ExerciseLibraryScreen(exercisesVm)
                    }
                    composable(TopLevel.PROFILE.route) {
                        ProfileScreen(
                            vm = hiltViewModel<ProfileViewModel>(),
                            onOpenMeasurements = { nav.navigate(Routes.MEASUREMENTS) { launchSingleTop = true } },
                            onGoTrain = { nav.navigateTopLevel(TopLevel.TRAIN.route) },
                            onOpenExercise = { nav.navigate(Routes.exercise(it)) { launchSingleTop = true } },
                        )
                    }
                    composable(Routes.WORKOUT) { backStackEntry ->
                        // Ligado a la Activity: el temporizador de descanso sigue al minimizar el entrenamiento
                        val activity = LocalActivity.current as ComponentActivity
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
                            onAddExercises = {
                                // El selector marca los que ya están (para no duplicar sin querer)
                                backStackEntry.savedStateHandle[Routes.EXISTING] = ArrayList(vm.workout.value?.exercises?.map { it.exercise.id }.orEmpty())
                                nav.navigate(Routes.PICKER)
                            },
                            onBack = { nav.popBackStack() },
                            // Se quita antes la pantalla del entreno: si no, Entrenar la restauraba vacía («sin entreno»)
                            onFinished = {
                                nav.popBackStack(Routes.WORKOUT, inclusive = true)
                                nav.navigateTopLevel(TopLevel.HISTORY.route)
                            },
                            // Vuelta a Inicio sin restaurar estado guardado (si no, reaparecería la pantalla del entreno vacía)
                            onDiscarded = { if (!nav.popBackStack(TopLevel.TRAIN.route, inclusive = false)) nav.navigate(TopLevel.TRAIN.route) },
                        )
                    }
                    composable(Routes.PICKER) {
                        ExercisePickerScreen(
                            vm = hiltViewModel<ExercisesViewModel>(),
                            existing = remember { nav.previousBackStackEntry?.savedStateHandle?.get<ArrayList<String>>(Routes.EXISTING)?.toSet().orEmpty() },
                            onBack = { nav.popBackStack() },
                            onPicked = { ids ->
                                // Solo desde el selector visible: un segundo toque durante la animación cerraba también el editor
                                if (nav.currentBackStackEntry?.destination?.route == Routes.PICKER) {
                                    nav.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED, ArrayList(ids))
                                    nav.popBackStack()
                                }
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
                        RoutineEditorScreen(
                            vm,
                            onAddExercises = {
                                backStackEntry.savedStateHandle[Routes.EXISTING] = ArrayList(vm.routine.value.exercises.map { it.exercise.id })
                                nav.navigate(Routes.PICKER)
                            },
                            onClose = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id").orEmpty()
                        ExerciseDetailScreen(hiltViewModel<ExercisesViewModel>(), id, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.MEASUREMENTS) {
                        MeasurementsScreen(hiltViewModel<ProfileViewModel>(), onBack = { nav.popBackStack() })
                    }
                    composable(Routes.PLANNING) {
                        PlanningScreen(
                            vm = hiltViewModel<PlanningViewModel>(),
                            onBack = { nav.popBackStack() },
                            onEditProgram = { nav.navigate(Routes.programEditor(it)) },
                            onTimer = { nav.navigate(Routes.timer(IntervalRoute.encode(it))) },
                            onGuided = { warmup, focus -> nav.navigate(Routes.guided(warmup, focus.name)) },
                        )
                    }
                    composable(Routes.PROGRAM_EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true })) { backStackEntry ->
                        val parent = remember(backStackEntry) { nav.getBackStackEntry(Routes.PLANNING) }
                        ProgramEditorScreen(hiltViewModel<PlanningViewModel>(parent), backStackEntry.arguments?.getString("id"), onClose = { nav.popBackStack() })
                    }
                    composable(Routes.TIMER, arguments = listOf(navArgument("plan") { type = NavType.StringType })) { backStackEntry ->
                        IntervalTimerScreen(IntervalRoute.decode(backStackEntry.arguments?.getString("plan").orEmpty()), onBack = { nav.popBackStack() })
                    }
                    composable(
                        Routes.GUIDED,
                        arguments = listOf(navArgument("warmup") { type = NavType.BoolType }, navArgument("focus") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        val focus = runCatching { BodyFocus.valueOf(backStackEntry.arguments?.getString("focus").orEmpty()) }.getOrDefault(BodyFocus.FULL)
                        GuidedSessionScreen(backStackEntry.arguments?.getBoolean("warmup") ?: true, focus, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

private const val KEY_MUSCLE_FILTER = "muscle_filter"

/** Biblioteca filtrada por ese músculo (nombre del enum), desde Inicio o el widget. */
private fun NavHostController.openMuscle(muscle: String) {
    navigateTopLevel(TopLevel.EXERCISES.route)
    currentBackStackEntry?.savedStateHandle?.set(KEY_MUSCLE_FILTER, muscle)
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private val POUND_COUNTRIES = setOf("US", "LR", "MM")
