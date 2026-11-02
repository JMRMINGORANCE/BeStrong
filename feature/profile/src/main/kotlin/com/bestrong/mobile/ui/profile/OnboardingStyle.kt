package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.STYLE_CARD_MIN_WIDTH
import com.bestrong.mobile.ui.components.StyleGrid

/**
 * Primer arranque, tras los permisos: elegir el estilo visual de la app y del reloj. Al tocar uno la app cambia al
 * momento (se ve cómo queda); se puede cambiar cuando se quiera en Ajustes → Apariencia.
 */
@Composable
fun OnboardingStyleDialog(vm: ProfileViewModel, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = com.bestrong.mobile.ui.theme.LocalScreenColor.current,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.style_onb_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.style_onb_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 520.dp),
                    )
                    BoxWithConstraints(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        val columns = (maxWidth / STYLE_CARD_MIN_WIDTH).toInt().coerceIn(2, 3)
                        StyleGrid(settings.appearance.style, { st -> vm.updateSettings { it.copy(appearance = it.appearance.copy(style = st)) } }, columns = columns)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.onb_perm_continue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
