package com.bestrong.mobile.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.util.formatDuration
import com.bestrong.core.util.formatVolume
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.ui.components.BarChart
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.StatTile
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: ProfileViewModel, onOpenMeasurements: () -> Unit, onOpenExercise: (String) -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val hcGranted by vm.healthConnectGranted.collectAsStateWithLifecycle()
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val ok = granted.containsAll(vm.healthConnectPermissions)
        vm.updateSettings { it.copy(healthConnectExport = ok) }
        vm.refreshHealthConnect()
    }
    LaunchedEffect(Unit) { vm.refreshHealthConnect() }

    Scaffold(topBar = { TopAppBar(title = { Text("Perfil") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Entrenamientos", "${stats.totalWorkouts}", Modifier.weight(1f))
                    StatTile("Racha semanal", "🔥 ${stats.weeklyStreak}", Modifier.weight(1f))
                    StatTile("Volumen total", "${formatVolume(stats.totalVolume / 1000)} t", Modifier.weight(1f))
                    StatTile("Tiempo", formatDuration(stats.totalDurationSec), Modifier.weight(1f))
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("Volumen semanal (kg)")
                        val f = DateTimeFormatter.ofPattern("d/M")
                        BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), it.volume) })
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("Entrenamientos por semana")
                        val f = DateTimeFormatter.ofPattern("d/M")
                        BarChart(stats.weeks.map { ChartPoint(f.format(it.weekStart), it.workouts.toDouble()) })
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionHeader("Series por músculo (30 días)")
                        if (stats.muscles30d.isEmpty()) Text("Sin datos todavía")
                        val max = stats.muscles30d.values.maxOrNull() ?: 1.0
                        stats.muscles30d.forEach { (m, sets) ->
                            Row {
                                Text(m.label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(progress = { (sets / max).toFloat() }, modifier = Modifier.weight(0.45f).padding(top = 8.dp))
                                Text(formatWeight(sets), Modifier.weight(0.15f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("Mejores 1RM estimados")
                        if (stats.topOneRepMax.isEmpty()) Text("Completa series para ver tus récords")
                        stats.topOneRepMax.forEach { pr ->
                            ListItem(
                                headlineContent = { Text(pr.exerciseName) },
                                trailingContent = { Text("${formatWeight(pr.value)} kg", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.clickable { onOpenExercise(pr.exerciseId) },
                            )
                        }
                    }
                }
            }
            item {
                Card(onClick = onOpenMeasurements) {
                    ListItem(
                        headlineContent = { Text("Medidas corporales") },
                        supportingContent = {
                            Text(measurements.firstOrNull()?.weightKg?.let { "Último peso: ${formatWeight(it)} kg" } ?: "Registra tu peso y medidas")
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        SectionHeader("Ajustes", Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Vibrar al terminar el descanso") },
                            trailingContent = { Switch(settings.vibrateOnRestEnd, { v -> vm.updateSettings { it.copy(vibrateOnRestEnd = v) } }) },
                        )
                        ListItem(
                            headlineContent = { Text("Mantener pantalla encendida al entrenar") },
                            trailingContent = { Switch(settings.keepScreenOn, { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }) },
                        )
                        ListItem(
                            headlineContent = { Text("Exportar a Health Connect / Samsung Health") },
                            supportingContent = {
                                Text(
                                    when {
                                        !vm.healthConnectAvailable -> "Health Connect no está disponible en este dispositivo"
                                        settings.healthConnectExport && !hcGranted -> "Faltan permisos"
                                        else -> "Guarda cada entrenamiento como sesión de pesas"
                                    },
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settings.healthConnectExport && hcGranted,
                                    enabled = vm.healthConnectAvailable,
                                    onCheckedChange = { on ->
                                        if (on && !hcGranted) hcLauncher.launch(vm.healthConnectPermissions)
                                        else vm.updateSettings { it.copy(healthConnectExport = on) }
                                    },
                                )
                            },
                        )
                        ListItem(
                            headlineContent = { Text("Sincronizar con Galaxy Watch") },
                            supportingContent = { Text("Envía rutinas e historial al reloj ahora") },
                            trailingContent = { TextButton(onClick = vm::syncWatchNow) { Text("Sincronizar") } },
                        )
                    }
                }
            }
        }
    }
}
