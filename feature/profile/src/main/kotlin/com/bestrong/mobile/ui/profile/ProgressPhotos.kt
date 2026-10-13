package com.bestrong.mobile.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bestrong.mobile.data.ProgressPhoto
import com.bestrong.mobile.security.BiometricLock
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatShortDate

/** Fotos de progreso: bloqueadas con huella/PIN, solo en el móvil, con comparación lado a lado. */
@Composable
fun ProgressPhotosSection(vm: ProfileViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    // El desbloqueo dura mientras la pantalla está abierta (no se guarda al girar ni al volver)
    val locks = settings.lockProgressPhotos && BiometricLock.isAvailable(context)
    var unlocked by remember { mutableStateOf(!locks) }
    // Elegir una foto abre otra pantalla: eso no cuenta como salir de la app
    var picking by rememberSaveable { mutableStateOf(false) }
    PhotoPrivacy(unlocked, locks) { if (!picking) unlocked = false }
    var comparing by rememberSaveable { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<String>() }
    var toDelete by remember { mutableStateOf<ProgressPhoto?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        picking = false
        uri?.let(vm::addPhoto)
    }
    val unlockTitle = stringResource(R.string.progress_unlock_title)

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.progress_photos))
            Text(stringResource(R.string.progress_photos_locked), style = MaterialTheme.typography.bodySmall)
            if (!unlocked) {
                Button(onClick = { BiometricLock.authenticate(context, unlockTitle, onSuccess = { unlocked = true }) }) {
                    Icon(Icons.Default.Lock, null)
                    Text(stringResource(R.string.progress_unlock), Modifier.padding(start = 8.dp))
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    picking = true
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.AddAPhoto, null)
                    Text(stringResource(R.string.progress_add), Modifier.padding(start = 8.dp))
                }
                if (photos.size >= 2) {
                    TextButton(onClick = { comparing = !comparing; chosen.clear() }) { Text(stringResource(R.string.progress_compare)) }
                }
            }
            if (comparing) Text(stringResource(R.string.progress_compare_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            // Perezosa: con años de fotos solo se cargan las que se ven
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.file.name }) { p ->
                    val id = p.file.name
                    val isSelected = id in chosen
                    val desc = stringResource(R.string.progress_photo_of, formatShortDate(p.dateEpochMs))
                    Column {
                        AsyncImage(
                            model = p.file,
                            contentDescription = desc,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 110.dp, height = 146.dp).clip(RoundedCornerShape(12.dp))
                                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                .semantics { selected = isSelected }
                                .clickable {
                                    if (comparing) {
                                        if (isSelected) {
                                            chosen.remove(id)
                                        } else if (chosen.size < 2) {
                                            chosen.add(id)
                                        }
                                    } else {
                                        toDelete = p
                                    }
                                },
                        )
                        Text(formatShortDate(p.dateEpochMs), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (comparing && chosen.size == 2) {
        val pair = chosen.mapNotNull { id -> photos.firstOrNull { it.file.name == id } }.sortedBy { it.dateEpochMs }
        AlertDialog(
            onDismissRequest = { chosen.clear() },
            title = { Text(stringResource(R.string.progress_compare)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { p ->
                        Column(Modifier.weight(1f)) {
                            AsyncImage(
                                model = p.file,
                                contentDescription = stringResource(R.string.progress_photo_of, formatShortDate(p.dateEpochMs)),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)),
                            )
                            Text(formatShortDate(p.dateEpochMs), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chosen.clear() }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.progress_delete)) },
            text = {
                AsyncImage(
                    model = p.file,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                )
            },
            confirmButton = { TextButton(onClick = { vm.deletePhoto(p); toDelete = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Al salir de la app las fotos se vuelven a bloquear, y mientras se ven no salen en la miniatura de Recientes. */
@Composable
private fun PhotoPrivacy(unlocked: Boolean, locks: Boolean, onLock: () -> Unit) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val lock by androidx.compose.runtime.rememberUpdatedState(onLock)
    DisposableEffect(lifecycle, locks) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && locks) lock()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(activity, unlocked, locks) {
        val secure = unlocked && locks
        if (secure) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
