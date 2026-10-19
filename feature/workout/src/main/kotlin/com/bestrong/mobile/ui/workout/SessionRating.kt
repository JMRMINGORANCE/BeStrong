package com.bestrong.mobile.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bestrong.core.model.SessionRating
import com.bestrong.mobile.shared.R

/** Valoración rápida al terminar: energía, dolor y motivación (1–5). */
@Composable
fun SessionRatingPicker(onRate: (SessionRating) -> Unit) {
    var rating by remember { mutableStateOf(SessionRating()) }
    // Nada marcado hasta que se toca (si no, parece que ya se respondió); los valores por defecto siguen ahí
    var picked by remember { mutableStateOf(setOf<Int>()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.rating_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.rating_scale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RatingRow(stringResource(R.string.rating_energy), rating.energy.takeIf { 0 in picked }) { rating = rating.copy(energy = it); picked = picked + 0; onRate(rating) }
        RatingRow(stringResource(R.string.rating_pain), rating.pain.takeIf { 1 in picked }) { rating = rating.copy(pain = it); picked = picked + 1; onRate(rating) }
        RatingRow(stringResource(R.string.rating_motivation), rating.motivation.takeIf { 2 in picked }) { rating = rating.copy(motivation = it); picked = picked + 2; onRate(rating) }
    }
}

@Composable
private fun RatingRow(label: String, value: Int?, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        (1..5).forEach { n ->
            FilterChip(
                selected = n == value,
                onClick = { onChange(n) },
                label = { Text("$n") },
                modifier = Modifier.semantics { contentDescription = "$label $n" },
            )
        }
    }
}
