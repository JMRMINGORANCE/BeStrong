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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bestrong.mobile.shared.R
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
                title = { Text(stringResource(R.string.privacy)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.privacy_intro))
            Text(stringResource(R.string.privacy_hc_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_hc))
            Text(stringResource(R.string.privacy_watch_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_watch))
            Text(stringResource(R.string.privacy_csv_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_csv))
            Text(stringResource(R.string.privacy_images_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_images))
            Text(stringResource(R.string.privacy_proposals_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_proposals))
            Text(stringResource(R.string.privacy_credits_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_credits))
            Text(stringResource(R.string.privacy_delete_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_delete))
        }
    }
}
