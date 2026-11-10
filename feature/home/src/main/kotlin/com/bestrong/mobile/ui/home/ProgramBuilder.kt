package com.bestrong.mobile.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.library.TrainingProgram
import com.bestrong.core.model.Routine
import com.bestrong.core.training.BodyFocus
import com.bestrong.core.training.CareZone
import com.bestrong.core.training.FocusArea
import com.bestrong.core.training.Goal
import com.bestrong.core.training.PeriodizedProgram
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.ExerciseThumbStrip
import com.bestrong.mobile.ui.components.MetaPill
import com.bestrong.mobile.ui.components.Panel
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.brandGradient
import com.bestrong.mobile.ui.components.displayName

/** Cada objetivo con su icono, nombre y explicación corta. */
private data class GoalOption(val goal: Goal, val icon: ImageVector, val title: Int, val body: Int)

private val GOALS = listOf(
    GoalOption(Goal.HYPERTROPHY, Icons.Default.FitnessCenter, R.string.goal_muscle, R.string.goal_hypertrophy_desc),
    // Ganar músculo y perder grasa a la vez, justo después de ganar músculo (el más parecido)
    GoalOption(Goal.RECOMPOSITION, Icons.Default.Balance, R.string.goal_recomposition, R.string.goal_recomposition_desc),
    GoalOption(Goal.STRENGTH, Icons.Default.SportsGymnastics, R.string.goal_strength, R.string.goal_strength_desc),
    GoalOption(Goal.FAT_LOSS, Icons.Default.LocalFireDepartment, R.string.goal_fat_loss, R.string.goal_fat_loss_desc),
    GoalOption(Goal.ENDURANCE, Icons.AutoMirrored.Filled.DirectionsRun, R.string.goal_endurance, R.string.goal_endurance_desc),
    GoalOption(Goal.GENERAL, Icons.Default.Favorite, R.string.goal_general, R.string.goal_general_desc),
)

// Las mismas opciones que en Planificar
private val MINUTES = listOf(30, 45, 60, 75, 90)

/**
 * «Crea tu programa»: el objetivo, los días por semana, el material y el tiempo, y el programa a medida al momento;
 * debajo, los programas clásicos ordenados por lo cerca que están de esos días.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramBuilderScreen(
    vm: HomeViewModel,
    onPickPeriodized: (PeriodizedProgram) -> Unit,
    onPickClassic: (TrainingProgram) -> Unit,
    onDismiss: () -> Unit,
) {
    // Empieza con lo que se dijo en la bienvenida: objetivo, días y dónde se entrena
    val settings by vm.settings.collectAsStateWithLifecycle()
    var goal by rememberSaveable { mutableStateOf(settings.trainingGoal) }
    var days by rememberSaveable { mutableIntStateOf(settings.weeklyGoal.coerceIn(DAY_OPTIONS.first(), DAY_OPTIONS.last())) }
    var kit by rememberSaveable { mutableStateOf(defaultKit(settings.activeGym.equipment)) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    // Sin entrenos todavía (recién llegado), de partida el nivel principiante
    var level by rememberSaveable {
        mutableStateOf(if (vm.history.value.none { it.isFinished }) com.bestrong.core.training.Level.BEGINNER else com.bestrong.core.training.Level.INTERMEDIATE)
    }
    // Zonas a cuidar y a reforzar (propuesta #5): solo para este programa, no se guardan
    var careNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var focusNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val care = remember(careNames) { careNames.mapTo(HashSet()) { CareZone.valueOf(it) } }
    val focus = remember(focusNames) { focusNames.mapTo(HashSet()) { FocusArea.valueOf(it) } }
    val program = remember(goal, days, kit, minutes, level, care, focus) { vm.buildProgram(days, goal, kit, minutes, level, care, focus) }
    val lang = com.bestrong.mobile.ui.components.appLanguage()
    BackHandler(onBack = onDismiss)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            Button(
                onClick = { vm.addGenerated(program, goal); onDismiss() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).height(56.dp),
            ) {
                Icon(Icons.Default.Check, null)
                Text(stringResource(R.string.builder_create), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("programs_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    com.bestrong.mobile.ui.components.ScreenTitle(stringResource(R.string.builder_title))
                    Text(stringResource(R.string.builder_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SectionHeader(stringResource(R.string.builder_goal), large = true) }
            items(GOALS, key = { it.goal.name }) { g -> GoalCard(g, selected = goal == g.goal) { goal = g.goal } }
            // Cuántos días a la semana (sin calendario: las rutinas se hacen en orden, la que toca la propone Inicio)
            item {
                SectionHeader(stringResource(R.string.builder_which_days), large = true) {
                    MetaPill(pluralStringResource(R.plurals.days_count, days, days))
                }
            }
            item { Segmented(DAY_OPTIONS.map { "$it" }, DAY_OPTIONS.indexOf(days)) { days = DAY_OPTIONS[it] } }
            item { SectionHeader(stringResource(R.string.builder_level), large = true) }
            item {
                val levels = com.bestrong.core.training.Level.entries
                Segmented(
                    listOf(stringResource(R.string.level_beginner), stringResource(R.string.level_intermediate), stringResource(R.string.level_advanced)),
                    levels.indexOf(level),
                ) { level = levels[it] }
            }
            item { SectionHeader(stringResource(R.string.builder_equipment), large = true) }
            item { KitPicker(kit) { kit = it } }
            item { SectionHeader(stringResource(R.string.builder_minutes), large = true) }
            item { Segmented(MINUTES.map { "$it min" }, MINUTES.indexOf(minutes)) { minutes = MINUTES[it] } }
            item { SectionHeader(stringResource(R.string.propuesta5_care_title), large = true) }
            item {
                ZoneChips(
                    hint = stringResource(R.string.propuesta5_care_hint),
                    options = CareZone.entries.map { it.name to stringResource(CARE_LABELS.getValue(it)) },
                    selected = careNames,
                    max = CareZone.entries.size,
                ) { careNames = it }
            }
            item { SectionHeader(stringResource(R.string.propuesta5_focus_title), large = true) }
            item {
                ZoneChips(
                    hint = stringResource(R.string.propuesta5_focus_hint),
                    options = FocusArea.entries.map { it.name to stringResource(FOCUS_LABELS.getValue(it)) },
                    selected = focusNames,
                    max = BodyFocus.MAX_FOCUS,
                ) { focusNames = it }
            }
            if (care.isNotEmpty() || focus.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.propuesta5_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.builder_preview), large = true) }
            item { ProgramHeader(program.name, program.description) }
            items(program.routines.size, key = { "gen-$it" }) { i -> GeneratedRoutineCard(program.routines[i]) }
            // Los clásicos: primero los de esos días
            item { SectionHeader(stringResource(R.string.programs_classic), large = true) }
            val periodized = vm.periodizedPrograms.sortedBy { kotlin.math.abs(it.daysPerWeek - days) }
            items(periodized, key = { "p-" + it.id }) { p ->
                ClassicCard(p.name(lang), p.description(lang), p.daysPerWeek, stringResource(R.string.programs_periodized)) { onPickPeriodized(p) }
            }
            val classics = vm.programs.sortedBy { kotlin.math.abs(it.daysPerWeek - days) }
            items(classics, key = { it.id }) { p ->
                ClassicCard(p.name(lang), p.description(lang), p.daysPerWeek, null) { onPickClassic(p) }
            }
        }
    }
}

/** Tarjeta de un objetivo: icono en círculo, nombre y para qué sirve; la elegida, con el degradado de marca. */
@Composable
private fun GoalCard(option: GoalOption, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) brandGradient() else SolidColor(scheme.surfaceContainer))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (selected) scheme.surfaceContainerLowest.copy(alpha = 0.25f) else scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(option.icon, null, tint = if (selected) scheme.onPrimary else scheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = if (selected) scheme.onPrimary else scheme.onSurface)
            Text(stringResource(option.body), style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, null, tint = scheme.onPrimary)
    }
}

/** Material: gimnasio, mancuernas o nada. */
@Composable
private fun KitPicker(kit: Kit, onPick: (Kit) -> Unit) {
    val options = listOf(
        Triple(Kit.GYM, Icons.Default.Store, R.string.builder_gym),
        Triple(Kit.DUMBBELLS, Icons.Default.FitnessCenter, R.string.builder_dumbbells),
        Triple(Kit.BODYWEIGHT, Icons.Default.Home, R.string.builder_bodyweight),
    )
    val scheme = MaterialTheme.colorScheme
    // selectableGroup: TalkBack dice «1 de 3»
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (k, icon, label) ->
            val on = k == kit
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.large)
                    .background(if (on) brandGradient() else SolidColor(scheme.surfaceContainer))
                    .selectable(selected = on, role = Role.RadioButton) { onPick(k) }
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, tint = if (on) scheme.onPrimary else scheme.primary)
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (on) scheme.onPrimary else scheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Chips de varias zonas con su explicación. Con [max] ya elegidas, las demás se desactivan (reforzar: como mucho 2).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneChips(hint: String, options: List<Pair<String, String>>, selected: List<String>, max: Int, onChange: (List<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, label) ->
                val on = key in selected
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) selected - key else selected + key) },
                    enabled = on || selected.size < max,
                    label = { Text(label) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private val CARE_LABELS = mapOf(
    CareZone.SHOULDER to R.string.propuesta5_zone_shoulder,
    CareZone.ELBOW to R.string.propuesta5_zone_elbow,
    CareZone.WRIST to R.string.propuesta5_zone_wrist,
    CareZone.NECK to R.string.propuesta5_zone_neck,
    CareZone.LOWER_BACK to R.string.propuesta5_zone_lower_back,
    CareZone.HIP to R.string.propuesta5_zone_hip,
    CareZone.KNEE to R.string.propuesta5_zone_knee,
    CareZone.ANKLE to R.string.propuesta5_zone_ankle,
)

private val FOCUS_LABELS = mapOf(
    FocusArea.CHEST to R.string.propuesta5_area_chest,
    FocusArea.BACK to R.string.propuesta5_area_back,
    FocusArea.SHOULDERS to R.string.propuesta5_area_shoulders,
    FocusArea.ARMS to R.string.propuesta5_area_arms,
    FocusArea.GLUTES to R.string.propuesta5_area_glutes,
    FocusArea.LEGS to R.string.propuesta5_area_legs,
    FocusArea.CALVES to R.string.propuesta5_area_calves,
    FocusArea.CORE to R.string.propuesta5_area_core,
)

/** Selector en píldora (la opción elegida, rellena). */
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(scheme.surfaceContainer).padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                // Alto mínimo, no fijo: «Fortgeschritten» o la letra grande ocupan dos líneas sin cortarse
                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (on) brandGradient() else SolidColor(androidx.compose.ui.graphics.Color.Transparent))
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontWeight = FontWeight.Bold, color = if (on) scheme.onPrimary else scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/** Nombre del programa a medida y cómo está hecho. */
@Composable
private fun ProgramHeader(name: String, description: String) {
    com.bestrong.mobile.ui.components.HeroCard {
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Una rutina del programa a medida: nombre, imágenes y series × reps de cada ejercicio. */
@Composable
private fun GeneratedRoutineCard(r: Routine) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            MetaPill(pluralStringResource(R.plurals.exercise_count, r.exercises.size, r.exercises.size))
            // Cuánto dura, más o menos: para ver si encaja en el tiempo elegido
            val minutes = remember(r) { (com.bestrong.core.session.WorkoutEditor.estimatedRemainingSeconds(r.toWorkout(0)) + 59) / 60 }
            if (minutes > 0) MetaPill(stringResource(R.string.routine_estimated, minutes), Modifier.padding(start = 6.dp))
        }
        ExerciseThumbStrip(r.exercises.map { it.exercise }, 44.dp, max = 6)
        r.exercises.forEach { re ->
            val sets = re.sets.size
            val target = re.sets.firstOrNull()?.let { if (it.durationSec > 0) "${it.durationSec} s" else "${it.reps}" }.orEmpty()
            Row {
                Text(re.exercise.displayName(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$sets × $target", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Programa clásico: nombre, días, si es periodizado, y su explicación. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassicCard(name: String, description: String, days: Int, tag: String?, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaPill(pluralStringResource(R.plurals.days_count, days, days))
            tag?.let { MetaPill(it) }
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Material de partida según el gimnasio activo: con máquinas o barra, gimnasio; con mancuernas, mancuernas; si no, nada. */
private fun defaultKit(equipment: Set<com.bestrong.core.model.Equipment>): Kit = when {
    equipment.any { it == com.bestrong.core.model.Equipment.BARBELL || it == com.bestrong.core.model.Equipment.MACHINE || it == com.bestrong.core.model.Equipment.CABLE } -> Kit.GYM
    com.bestrong.core.model.Equipment.DUMBBELL in equipment -> Kit.DUMBBELLS
    else -> Kit.BODYWEIGHT
}

/** Días por semana que se pueden elegir para el programa a medida. */
private val DAY_OPTIONS = (2..6).toList()
