package com.bestrong.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bestrong.core.model.BodyMeasurement
import com.bestrong.core.util.formatWeight
import com.bestrong.mobile.R
import com.bestrong.mobile.ui.components.ChartPoint
import com.bestrong.mobile.ui.components.LineChart
import com.bestrong.mobile.ui.components.LocalWeightUnit
import com.bestrong.mobile.ui.components.SectionHeader
import com.bestrong.mobile.ui.components.formatDate
import com.bestrong.mobile.ui.components.formatShortDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(vm: ProfileViewModel, onBack: () -> Unit) {
    val unit = LocalWeightUnit.current
    val list by vm.measurements.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.measurements)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, stringResource(R.string.add_measurement)) } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val weights = list.filter { it.weightKg != null }.sortedBy { it.dateEpochMs }
            if (weights.size >= 2) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(stringResource(R.string.body_weight_unit, unit.label))
                            LineChart(weights.map { ChartPoint(formatShortDate(it.dateEpochMs), it.weightKg!!) }, valueFormatter = { unit.format(it) })
                        }
                    }
                }
            }
            items(list, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(formatDate(m.dateEpochMs)) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    m.weightKg?.let { stringResource(R.string.m_weight, unit.format(it, withUnit = true)) },
                                    m.bodyFatPct?.let { stringResource(R.string.m_fat, formatWeight(it)) },
                                    m.waistCm?.let { stringResource(R.string.m_waist, formatWeight(it)) },
                                    m.chestCm?.let { stringResource(R.string.m_chest, formatWeight(it)) },
                                    m.armCm?.let { stringResource(R.string.m_arm, formatWeight(it)) },
                                    m.thighCm?.let { stringResource(R.string.m_thigh, formatWeight(it)) },
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = { IconButton(onClick = { vm.deleteMeasurement(m.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) } },
                    )
                }
            }
        }
    }
    if (adding) {
        val fields = remember { List(6) { mutableStateOf("") } }
        val labels = listOf(
            stringResource(R.string.f_weight, unit.label),
            stringResource(R.string.f_fat),
            stringResource(R.string.f_waist),
            stringResource(R.string.f_chest),
            stringResource(R.string.f_arm),
            stringResource(R.string.f_thigh),
        )
        fun v(i: Int) = fields[i].value.replace(',', '.').toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.new_measurement)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEachIndexed { i, l ->
                        OutlinedTextField(
                            fields[i].value,
                            { fields[i].value = it },
                            label = { Text(l) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.saveMeasurement(BodyMeasurement(dateEpochMs = System.currentTimeMillis(), weightKg = v(0)?.let(unit::toKg), bodyFatPct = v(1), waistCm = v(2), chestCm = v(3), armCm = v(4), thighCm = v(5)))
                    adding = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
