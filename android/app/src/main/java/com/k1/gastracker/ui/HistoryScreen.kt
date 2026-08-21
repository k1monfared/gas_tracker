package com.k1.gastracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.k1.gastracker.core.Refill
import java.util.Locale

@Composable
fun HistoryScreen(state: UiState, viewModel: AppViewModel) {
    if (state.refills.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No refills logged yet.")
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.refills.sortedByDescending { it.date }) { refill ->
            RefillRow(refill, state, viewModel)
        }
    }
}

@Composable
private fun RefillRow(refill: Refill, state: UiState, viewModel: AppViewModel) {
    val converted = state.convertedCosts[refill]
    Card(
        onClick = { viewModel.startEditing(refill) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(refill.date.toString(), style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(fmtVolume(refill))
                        refill.distance?.let { append("  ·  ${trim(it)} ${refill.distanceUnit.label}") }
                        refill.cost?.let { append("  ·  ${trim(it)} ${refill.currency}") }
                        converted?.let {
                            append("  ≈  ${trim(it)} ${state.homeCurrency}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val extra = buildString {
                    refill.octane?.let { append("Octane $it") }
                    if (refill.octane != null && !refill.station.isNullOrBlank()) append("  ·  ")
                    refill.station?.let { append(it) }
                }
                if (extra.isNotBlank()) {
                    Text(
                        extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { viewModel.deleteRefill(refill) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

private fun fmtVolume(refill: Refill): String = "${trim(refill.volume)} ${refill.volumeUnit.label}"
