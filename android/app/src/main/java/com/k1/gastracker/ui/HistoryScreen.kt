package com.k1.gastracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.k1.gastracker.R
import com.k1.gastracker.core.ConvertedCost
import com.k1.gastracker.core.Refill
import java.util.Locale

@Composable
fun HistoryScreen(state: UiState, actions: GasTrackerActions) {
    if (state.storageError != null && !state.historyWritable) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.history_unavailable))
        }
        return
    }
    if (state.refills.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.no_refills_yet))
        }
        return
    }
    val sorted = state.refills.sortedByDescending { it.date }
    var pendingIndex by rememberSaveable { mutableIntStateOf(-1) }
    val pending = sorted.getOrNull(pendingIndex)

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingIndex = -1 },
            title = { Text(stringResource(R.string.delete_refill_title)) },
            text = { Text(stringResource(R.string.delete_refill_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteRefill(pending)
                    pendingIndex = -1
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingIndex = -1 }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sorted.size) { index ->
            val refill = sorted[index]
            RefillRow(
                refill = refill,
                state = state,
                onEdit = { actions.startEditing(refill) },
                onDelete = { pendingIndex = index },
            )
        }
    }
}

@Composable
private fun RefillRow(
    refill: Refill,
    state: UiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val converted = state.convertedCosts[refill] as? ConvertedCost.Ready
    val summary = buildString {
        append(refill.date)
        append(" ")
        append(fmtVolume(refill))
        refill.cost?.let { append(" ").append(trim(it)).append(" ").append(refill.currency) }
    }
    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = summary },
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
                        refill.odometer?.let { append("  ·  ${trim(it)} ${refill.distanceUnit.label}") }
                        refill.distance?.let { append("  ·  ${trim(it)} ${refill.distanceUnit.label}") }
                        refill.cost?.let { append("  ·  ${trim(it)} ${refill.currency}") }
                        converted?.let {
                            append("  ≈  ${trim(it.amount)} ${state.homeCurrency}")
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
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

private fun fmtVolume(refill: Refill): String = "${trim(refill.volume)} ${refill.volumeUnit.label}"
