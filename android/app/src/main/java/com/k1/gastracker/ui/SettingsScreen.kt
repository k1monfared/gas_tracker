package com.k1.gastracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k1.gastracker.R
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.ui.components.Selector

private val CURRENCIES = listOf("EUR", "USD", "GBP", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY")

@Composable
fun SettingsScreen(state: UiState, actions: GasTrackerActions) {
    val exportName = stringResource(R.string.export_filename)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) actions.exportHistory(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) actions.importHistory(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        SettingRow(label = stringResource(R.string.home_currency)) {
            Selector(
                selected = state.homeCurrency,
                options = CURRENCIES,
                label = { it },
                onSelect = { actions.setHomeCurrency(it) },
            )
        }
        SettingRow(label = stringResource(R.string.default_distance_unit)) {
            Selector(
                selected = state.lastDistanceUnit,
                options = DistanceUnit.entries,
                label = { it.label },
                onSelect = { actions.setDistanceUnit(it) },
            )
        }
        SettingRow(label = stringResource(R.string.default_volume_unit)) {
            Selector(
                selected = state.lastVolumeUnit,
                options = VolumeUnit.entries,
                label = { it.label },
                onSelect = { actions.setVolumeUnit(it) },
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.fx_rates), style = MaterialTheme.typography.titleSmall)
                if (state.fxLoading) {
                    Text(stringResource(R.string.fx_updating), style = MaterialTheme.typography.bodyMedium)
                }
                state.fxError?.let {
                    Text(
                        stringResource(R.string.fx_fetch_failed, it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = actions::retryFx) {
                        Text(stringResource(R.string.fx_retry))
                    }
                }
                if (!state.fxLoading && state.fxError == null) {
                    Text(stringResource(R.string.fx_cached), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportLauncher.launch(exportName) },
                        enabled = state.historyWritable || state.storageError != null,
                    ) {
                        Text(stringResource(R.string.export_history))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Text(stringResource(R.string.import_history))
                    }
                }
                Text(stringResource(R.string.privacy_summary), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            control()
        }
    }
}
