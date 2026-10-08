package com.bestrong.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.ui.theme.BeStrongTheme

/**
 * Política de privacidad. Health Connect la abre desde su pantalla de permisos
 * (ACTION_SHOW_PERMISSIONS_RATIONALE / VIEW_PERMISSION_USAGE); también se enlaza desde Perfil.
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BeStrongTheme { PrivacyPolicy(onBack = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicy(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacidad") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Política de privacidad de BeStrong", style = MaterialTheme.typography.titleLarge)
            Text("Todos tus datos (entrenamientos, rutinas, medidas y ajustes) se guardan solo en tu móvil y en tu reloj. BeStrong no tiene servidores, no crea cuentas y no envía datos a terceros.")
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Text("Si activas la exportación, BeStrong escribe en Health Connect cada entrenamiento terminado como sesión de pesas (hora de inicio, fin, nombre y notas) y las calorías estimadas por el reloj. BeStrong no lee ningún dato de Health Connect. Puedes retirar el permiso en cualquier momento desde Ajustes › Health Connect.")
            Text("Galaxy Watch", style = MaterialTheme.typography.titleMedium)
            Text("El reloj mide tu pulso y calorías con Health Services solo mientras hay un entrenamiento en curso. Los datos viajan entre el móvil y el reloj mediante la conexión Bluetooth/Wi-Fi de Wear OS y no salen de tus dispositivos.")
            Text("Exportar e importar", style = MaterialTheme.typography.titleMedium)
            Text("Los archivos CSV que exportas se guardan donde tú elijas. BeStrong solo lee los CSV que tú seleccionas para importar.")
            Text("Borrar tus datos", style = MaterialTheme.typography.titleMedium)
            Text("Desinstalar la app o borrar sus datos desde Ajustes de Android elimina toda la información de BeStrong del dispositivo.")
        }
    }
}
