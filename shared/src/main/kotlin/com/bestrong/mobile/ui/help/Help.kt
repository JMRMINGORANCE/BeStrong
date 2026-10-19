package com.bestrong.mobile.ui.help

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bestrong.mobile.shared.R
import kotlinx.coroutines.launch

private val GLOSSARY = listOf(
    R.string.g_rpe to R.string.g_rpe_d,
    R.string.g_rir to R.string.g_rir_d,
    R.string.g_1rm to R.string.g_1rm_d,
    R.string.g_tm to R.string.g_tm_d,
    R.string.g_amrap to R.string.g_amrap_d,
    R.string.g_emom to R.string.g_emom_d,
    R.string.g_superset to R.string.g_superset_d,
    R.string.g_drop to R.string.g_drop_d,
    R.string.g_deload to R.string.g_deload_d,
    R.string.g_volume to R.string.g_volume_d,
    R.string.g_overload to R.string.g_overload_d,
    R.string.g_tempo to R.string.g_tempo_d,
    R.string.g_dots to R.string.g_dots_d,
)

private val FAQ = listOf(
    R.string.faq_watch_q to R.string.faq_watch_a,
    R.string.faq_backup_q to R.string.faq_backup_a,
    R.string.faq_rest_q to R.string.faq_rest_a,
    R.string.faq_hc_q to R.string.faq_hc_a,
    R.string.faq_units_q to R.string.faq_units_a,
    R.string.faq_progress_q to R.string.faq_progress_a,
)

/** Ayuda a pantalla completa: novedades, glosario de términos y preguntas frecuentes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpDialog(onDismiss: () -> Unit, onReplayTutorial: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.bestrong.mobile.ui.theme.ScreenBackdrop(MaterialTheme.colorScheme.secondary) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.help)) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.action_close)) } },
                    )
                },
            ) { padding ->
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val content = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
                    item {
                        OutlinedButton(onClick = onReplayTutorial, modifier = content) { Text(stringResource(R.string.tutorial_replay)) }
                    }
                    item { Heading(R.string.whats_new, content) }
                    item { Text(stringResource(R.string.whats_new_body), content) }
                    item { Heading(R.string.glossary, content) }
                    items(GLOSSARY) { (term, def) -> Entry(term, def, content) }
                    item { Heading(R.string.faq, content) }
                    items(FAQ) { (q, a) -> Entry(q, a, content) }
                    item { Spacer(Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Heading(@StringRes text: Int, modifier: Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Entry(@StringRes title: Int, @StringRes body: Int, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.Bold)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Una pantalla de la bienvenida: título, texto, el icono grande y los tres que lo rodean. */
private data class TutorialPage(
    @StringRes val title: Int,
    @StringRes val text: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val orbit: List<androidx.compose.ui.graphics.vector.ImageVector>,
)

private val TUTORIAL = listOf(
    TutorialPage(R.string.tut1_t, R.string.tut1_d, Icons.Default.FitnessCenter, listOf(Icons.Default.Bolt, Icons.Default.Lock, Icons.Default.Watch)),
    TutorialPage(R.string.tut2_t, R.string.tut2_d, Icons.Default.CalendarMonth, listOf(Icons.Default.Repeat, Icons.Default.Star, Icons.Default.Today)),
    TutorialPage(R.string.tut3_t, R.string.tut3_d, Icons.Default.CheckCircle, listOf(Icons.Default.Timer, Icons.Default.Vibration, Icons.Default.SwipeRight)),
    TutorialPage(R.string.tut5_t, R.string.tut5_d, Icons.Default.Watch, listOf(Icons.Default.Favorite, Icons.Default.Timer, Icons.AutoMirrored.Filled.DirectionsRun)),
    TutorialPage(R.string.tut4_t, R.string.tut4_d, Icons.Default.EmojiEvents, listOf(Icons.Default.Insights, Icons.Default.LocalFireDepartment, Icons.Default.Straighten)),
)

/**
 * Bienvenida a pantalla completa (primer arranque o desde Ayuda): una pantalla por idea, con una ilustración grande
 * y animada, se pasa deslizando o con «Siguiente»; la última deja elegir lo básico para empezar.
 */
@Composable
fun TutorialDialog(
    onDone: () -> Unit,
    unit: com.bestrong.core.util.WeightUnit = com.bestrong.core.util.WeightUnit.KG,
    weeklyGoal: Int = 3,
    /** Última página: lo básico para empezar (unidad, días por semana y si se entrena en casa). */
    onSetup: (com.bestrong.core.util.WeightUnit, Int, Boolean, com.bestrong.core.training.Goal) -> Unit = { _, _, _, _ -> },
    atHome: Boolean = false,
    trainingGoal: com.bestrong.core.training.Goal = com.bestrong.core.training.Goal.HYPERTROPHY,
) {
    var chosenUnit by rememberSaveable { mutableStateOf(unit) }
    var chosenGoal by rememberSaveable { mutableIntStateOf(weeklyGoal) }
    var chosenHome by rememberSaveable { mutableStateOf(atHome) }
    var chosenTraining by rememberSaveable { mutableStateOf(trainingGoal) }
    val pages = TUTORIAL.size + 1
    val pager = androidx.compose.foundation.pager.rememberPagerState { pages }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val finish = {
        onSetup(chosenUnit, chosenGoal, chosenHome, chosenTraining)
        onDone()
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val setup = pager.currentPage == TUTORIAL.size
        // Cada pantalla tiñe el fondo con su color (acento, ámbar, verde…)
        val tint = tutorialTint(pager.currentPage)
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = com.bestrong.mobile.ui.theme.LocalScreenColor.current, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Transparent)),
                ),
            ) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        // «Saltar» lleva a la configuración (lo único que conviene no saltarse)
                        if (!setup) TextButton(onClick = { scope.launch { pager.animateScrollToPage(TUTORIAL.size) } }) { Text(stringResource(R.string.skip)) }
                    }
                    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                        if (page == TUTORIAL.size) {
                            SetupScreen(chosenUnit, chosenGoal, chosenHome, { chosenUnit = it }, { chosenGoal = it }, { chosenHome = it }, chosenTraining) { chosenTraining = it }
                        } else {
                            TutorialScreen(TUTORIAL[page], tutorialTint(page))
                        }
                    }
                    PageDots(pages, pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp))
                    Button(
                        onClick = { if (setup) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(if (setup) R.string.start else R.string.next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun tutorialTint(page: Int): androidx.compose.ui.graphics.Color = when (page % 3) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.tertiary
}

/** Una pantalla de la bienvenida: la ilustración arriba y el texto grande debajo. */
@Composable
private fun TutorialScreen(page: TutorialPage, tint: androidx.compose.ui.graphics.Color) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TutorialIllustration(page, tint, Modifier.size(280.dp))
        Spacer(Modifier.height(32.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(page.text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

/**
 * El icono de la pantalla en un círculo con el degradado de su color, con halos y tres iconos pequeños que flotan
 * alrededor (se mueven despacio, sin distraer).
 */
@Composable
private fun TutorialIllustration(page: TutorialPage, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) =
    BrandIllustration(page.icon, page.orbit, tint, modifier)

/**
 * La ilustración de la bienvenida (icono grande en un círculo con degradado, halos y tres iconos que flotan) para
 * usarla en toda la app: estados vacíos, tarjetas destacadas…
 */
@Composable
fun BrandIllustration(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    orbit: List<androidx.compose.ui.graphics.vector.ImageVector>,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    // Fuera de la bienvenida va quieta: una animación infinita detrás de una lista solo gasta batería
    val float = if (animated) floatingPhase() else 0.5f
    val on = MaterialTheme.colorScheme.surfaceContainerLowest
    BrandIllustrationBody(icon, orbit, tint, on, float, modifier)
}

@Composable
private fun floatingPhase(): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "tutorial")
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "float",
    )
    return float
}

@Composable
private fun BrandIllustrationBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    orbit: List<androidx.compose.ui.graphics.vector.ImageVector>,
    tint: androidx.compose.ui.graphics.Color,
    on: androidx.compose.ui.graphics.Color,
    float: Float,
    modifier: Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Todo a escala del tamaño de la ilustración (grande en las pantallas, pequeña en la de configuración)
        val bubble = maxWidth * 0.2f
        val mainIcon = maxWidth * 0.23f
        val gap = maxWidth * 0.04f
        // Halos
        listOf(1f, 0.78f, 0.58f).forEachIndexed { i, f ->
            Box(Modifier.fillMaxSize(f).clip(CircleShape).background(tint.copy(alpha = 0.08f + i * 0.06f)))
        }
        Box(
            Modifier.fillMaxSize(0.42f).graphicsLayer { scaleX = 1f + float * 0.04f; scaleY = 1f + float * 0.04f }.clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(tint, androidx.compose.ui.graphics.lerp(tint, MaterialTheme.colorScheme.secondary, 0.5f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(mainIcon), tint = on)
        }
        // Los pequeños, repartidos alrededor y flotando a destiempo
        val spots = listOf(Alignment.TopEnd, Alignment.BottomStart, Alignment.CenterStart)
        orbit.forEachIndexed { i, small ->
            val dy = (if (i % 2 == 0) float else 1f - float) * 14f
            Box(
                Modifier.align(spots[i % spots.size]).padding(gap).graphicsLayer { translationY = dy * density }
                    .size(bubble).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(small, null, Modifier.size(bubble * 0.5f), tint = tint)
            }
        }
    }
}

/** Puntos de la página: el actual, alargado y del color de acento. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by androidx.compose.animation.core.animateDpAsState(if (i == current) 28.dp else 8.dp, label = "dot")
            Box(
                Modifier.height(8.dp).width(width).clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** Última pantalla: unidad, días por semana y dónde se entrena, en una tarjeta. */
@Composable
private fun SetupScreen(
    unit: com.bestrong.core.util.WeightUnit,
    goal: Int,
    home: Boolean,
    onUnit: (com.bestrong.core.util.WeightUnit) -> Unit,
    onGoal: (Int) -> Unit,
    onHome: (Boolean) -> Unit,
    training: com.bestrong.core.training.Goal,
    onTraining: (com.bestrong.core.training.Goal) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Ilustración pequeña: con la grande, en muchos móviles las opciones de abajo quedaban tapadas por el botón
        TutorialIllustration(TutorialPage(R.string.setup_title, R.string.setup_title, Icons.Default.Tune, listOf(Icons.Default.Straighten, Icons.Default.CalendarMonth, Icons.Default.Home)), MaterialTheme.colorScheme.primary, Modifier.size(SETUP_ART))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.widthIn(max = 480.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TrainingGoalChoice(training, onTraining)
                SetupPage(unit, goal, onUnit, onGoal)
                PlaceChoice(home, onHome)
                // La pista al final: antes quedaba entre medias y empujaba «¿Dónde entrenas?» por debajo del botón
                Text(stringResource(R.string.setup_import_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SetupPage(
    unit: com.bestrong.core.util.WeightUnit,
    goal: Int,
    onUnit: (com.bestrong.core.util.WeightUnit) -> Unit,
    onGoal: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_unit), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            com.bestrong.core.util.WeightUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = u == unit,
                    onClick = { onUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(i, com.bestrong.core.util.WeightUnit.entries.size),
                ) { Text(u.label) }
            }
        }
        Text(stringResource(R.string.setup_goal), style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in 1..7) FilterChip(selected = d == goal, onClick = { onGoal(d) }, label = { Text("$d") })
        }
    }
}

/** «¿Cuál es tu objetivo?»: ganar músculo, fuerza, perder grasa, resistencia o salud. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TrainingGoalChoice(selected: com.bestrong.core.training.Goal, onChange: (com.bestrong.core.training.Goal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.builder_goal), style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            trainingGoals().forEach { (g, label) -> FilterChip(selected = g == selected, onClick = { onChange(g) }, label = { Text(stringResource(label)) }) }
        }
    }
}

/** Los objetivos con su nombre, en el orden en que se ofrecen. */
fun trainingGoals(): List<Pair<com.bestrong.core.training.Goal, Int>> = listOf(
    com.bestrong.core.training.Goal.HYPERTROPHY to R.string.goal_muscle,
    com.bestrong.core.training.Goal.FAT_LOSS to R.string.goal_fat_loss,
    com.bestrong.core.training.Goal.STRENGTH to R.string.goal_strength,
    com.bestrong.core.training.Goal.ENDURANCE to R.string.goal_endurance,
    com.bestrong.core.training.Goal.GENERAL to R.string.goal_general,
)

/** «¿Dónde entrenas?»: elige el perfil de material (discos y barra o mancuernas de casa) para los pesos sugeridos. */
@Composable
private fun PlaceChoice(home: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_place), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !home, onClick = { onChange(false) }, label = { Text(stringResource(R.string.setup_place_gym)) })
            FilterChip(selected = home, onClick = { onChange(true) }, label = { Text(stringResource(R.string.setup_place_home)) })
        }
    }
}

/** Novedades de la versión, una vez tras actualizar. */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new)) },
        text = { Text(stringResource(R.string.whats_new_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it)) } },
    )
}

/** Consejos ya descartados y cómo descartar uno nuevo (se guardan en Ajustes). */
class Tips(val dismissed: Set<String>, val onDismiss: (String) -> Unit)

val LocalTips = staticCompositionLocalOf { Tips(emptySet()) {} }

/** Consejo contextual que aparece una sola vez hasta que se descarta. */
@Composable
fun TipCard(id: String, @StringRes text: Int, modifier: Modifier = Modifier) {
    val tips = LocalTips.current
    if (id in tips.dismissed) return
    // Como los demás paneles: esquinas grandes y la bombilla en su círculo
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            // El botón debajo: al lado estrechaba el consejo a una palabra por línea
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                TextButton(onClick = { tips.onDismiss(id) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.got_it)) }
            }
        }
    }
}

private val SETUP_ART = 96.dp
