package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bestrong.core.stats.Sex
import com.bestrong.mobile.data.UserSettings
import com.bestrong.mobile.shared.R
import com.bestrong.mobile.ui.components.SectionHeader
import java.time.Year

/**
 * Datos para calcular el gasto con tu metabolismo basal (Mifflin-St Jeor) y tu pulso máximo
 * (Tanaka). El peso sale de las medidas; la altura, si se deja vacía, de Health Connect.
 */
@Composable
fun BodyDataCard(settings: UserSettings, onUpdate: ((UserSettings) -> UserSettings) -> Unit) {
    var year by remember(settings.birthYear) { mutableStateOf(settings.birthYear?.toString().orEmpty()) }
    var height by remember(settings.heightCm) { mutableStateOf(settings.heightCm?.toString().orEmpty()) }
    val thisYear = Year.now().value
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(stringResource(R.string.body_data))
            Text(stringResource(R.string.body_data_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { v ->
                        year = v.filter(Char::isDigit).take(4)
                        val y = year.toIntOrNull()?.takeIf { it in (thisYear - 100)..(thisYear - 10) }
                        onUpdate { it.copy(birthYear = y) }
                    },
                    label = { Text(stringResource(R.string.birth_year)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { v ->
                        height = v.filter(Char::isDigit).take(3)
                        val h = height.toIntOrNull()?.takeIf { it in 100..250 }
                        onUpdate { it.copy(heightCm = h) }
                    },
                    label = { Text(stringResource(R.string.height_cm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.sex == Sex.MALE, onClick = { onUpdate { it.copy(sex = Sex.MALE) } }, label = { Text(stringResource(R.string.sex_male)) })
                FilterChip(selected = settings.sex == Sex.FEMALE, onClick = { onUpdate { it.copy(sex = Sex.FEMALE) } }, label = { Text(stringResource(R.string.sex_female)) })
            }
        }
    }
}
