package com.bestrong.mobile.ui.planning

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bestrong.core.training.BodyFocus
import com.bestrong.core.training.GuidedRoutines
import com.bestrong.core.training.IntervalPlan
import com.bestrong.core.training.IntervalTimer
import com.bestrong.core.util.formatDuration
import com.bestrong.designsystem.BeStrongPalette
import com.bestrong.mobile.shared.R
import kotlinx.coroutines.delay

/** Codifica un plan de intervalos para la ruta de navegación: «hiit:40:20:8», «emom:10», «amrap:12». */
object IntervalRoute {
    fun encode(p: IntervalPlan): String = when (p) {
        is IntervalPlan.Hiit -> "hiit:${p.workSec}:${p.restSec}:${p.rounds}"
        is IntervalPlan.Emom -> "emom:${p.minutes}"
        is IntervalPlan.Amrap -> "amrap:${p.minutes}"
        is IntervalPlan.Circuit -> "circuit:${p.workSec}:${p.restSec}:${p.rounds}:" + android.net.Uri.encode(p.stations.joinToString("|"))
    }

    /**
     * [s] llega ya decodificado por Navigation (no se vuelve a decodificar: un «%» del nombre se
     * rompería) y los nombres de las estaciones pueden llevar «:», así que se corta en 5 trozos como mucho.
     */
    fun decode(s: String): IntervalPlan {
        val parts = s.split(":", limit = 5)
        val n = parts.drop(1).take(3).map { it.toIntOrNull() ?: 1 }
        if (parts.firstOrNull() == "circuit") {
            val stations = parts.getOrNull(4).orEmpty().split('|').filter { it.isNotBlank() }
            return IntervalPlan.Circuit(stations.ifEmpty { listOf("1") }, n.getOrElse(0) { 40 }, n.getOrElse(1) { 20 }, n.getOrElse(2) { 3 })
        }
        return when (parts.firstOrNull()) {
            "emom" -> IntervalPlan.Emom(n.getOrElse(0) { 10 })
            "amrap" -> IntervalPlan.Amrap(n.getOrElse(0) { 10 })
            else -> IntervalPlan.Hiit(n.getOrElse(0) { 20 }, n.getOrElse(1) { 10 }, n.getOrElse(2) { 8 })
        }
    }
}

private fun vibrate(context: android.content.Context, long: Boolean) {
    val v = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.os.Vibrator::class.java)
    } ?: return
    v.vibrate(VibrationEffect.createWaveform(if (long) longArrayOf(0, 400) else longArrayOf(0, 120, 80, 120), -1))
}

/** Temporizador HIIT / Tabata / EMOM / AMRAP a pantalla completa con vibración en cada cambio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalTimerScreen(plan: IntervalPlan, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var elapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var running by rememberSaveable { mutableStateOf(true) }
    val total = IntervalTimer.totalSeconds(plan)
    val phases = remember(plan) { IntervalTimer.phases(plan) }
    fun phaseAt(ms: Long) = IntervalTimer.at(plan, (ms / 1000).toInt())?.let { phases.indexOf(it.first) } ?: phases.size
    LaunchedEffect(running) {
        // El propio bucle detecta el cambio de fase y vibra: sin recomposición (pantalla apagada,
        // app en segundo plano) también avisa. Reloj monótono: no le afectan cambios de hora.
        val start = SystemClock.elapsedRealtime() - elapsedMs
        var lastPhase = phaseAt(elapsedMs)
        while (running) {
            delay(200)
            elapsedMs = SystemClock.elapsedRealtime() - start
            val phase = phaseAt(elapsedMs)
            if (phase != lastPhase) {
                vibrate(context, long = phase >= phases.size)
                lastPhase = phase
            }
            if (phase >= phases.size) running = false
        }
    }
    val current = IntervalTimer.at(plan, (elapsedMs / 1000).toInt())
    val work = current?.first?.work == true
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(IntervalRoute.encode(plan).substringBefore(':').uppercase()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(
            Modifier.padding(padding).fillMaxSize().background(
                if (current == null) {
                    MaterialTheme.colorScheme.surface
                } else if (work) {
                    BeStrongPalette.Orange.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current == null) {
                    Text(stringResource(R.string.timer_done), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = onBack) { Text(stringResource(R.string.action_done)) }
                    return@Column
                }
                val (phase, left) = current
                Text(stringResource(if (phase.work) R.string.timer_work else R.string.timer_rest), style = MaterialTheme.typography.headlineMedium)
                // Circuito: estación actual (y la siguiente durante el cambio)
                (plan as? IntervalPlan.Circuit)?.let { c ->
                    val station = if (phase.work) c.stations[phase.station] else c.stations.getOrElse(phase.station + 1) { c.stations.first() }
                    Text(station, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(formatDuration(left.toLong()), fontSize = 96.sp, fontWeight = FontWeight.Bold)
                val rounds = phases.maxOf { it.round }
                if (rounds > 1) Text(stringResource(R.string.timer_round, phase.round, rounds), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (elapsedMs / 1000f) / total }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                OutlinedButton(onClick = { running = !running }) { Text(stringResource(if (running) R.string.timer_pause else R.string.timer_resume)) }
            }
        }
    }
}

/** Calentamiento o estiramientos guiados: un paso tras otro con cuenta atrás. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedSessionScreen(warmup: Boolean, focus: BodyFocus, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val lang = com.bestrong.mobile.ui.components.uiLanguage()
    val steps = remember(warmup, focus) { if (warmup) GuidedRoutines.warmup(focus) else GuidedRoutines.stretching(focus) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Fin del paso en reloj monótono: la cuenta no se retrasa aunque el bucle se frene
    val endAt by rememberSaveable(index) { mutableLongStateOf(SystemClock.elapsedRealtime() + (steps.getOrNull(index)?.seconds ?: 0) * 1_000L) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val left = ((endAt - now + 999) / 1_000).coerceAtLeast(0).toInt()
    LaunchedEffect(index) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            val remaining = endAt - now
            if (remaining <= 0) break
            delay(remaining.coerceAtMost(250))
        }
        if (index < steps.size) {
            vibrate(context, long = index == steps.lastIndex)
            index++
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guided_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            val step = steps.getOrNull(index)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == null) {
                    Text(stringResource(R.string.timer_done), style = MaterialTheme.typography.displayMedium)
                    Button(onClick = onBack) { Text(stringResource(R.string.action_done)) }
                    return@Column
                }
                Text("${index + 1} / ${steps.size}", style = MaterialTheme.typography.labelLarge)
                Text(step.name(lang), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(formatDuration(left.toLong()), fontSize = 72.sp, fontWeight = FontWeight.Bold)
                steps.getOrNull(index + 1)?.let { Text(stringResource(R.string.guided_next, it.name(lang)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { index++ }) { Text(stringResource(R.string.guided_skip)) }
                }
            }
        }
    }
}
