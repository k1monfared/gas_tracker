package com.k1.gastracker.ui

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
import androidx.compose.ui.unit.dp
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.ui.components.Selector

private val CURRENCIES = listOf("EUR", "USD", "GBP", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY")

@Composable
fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SettingRow(label = "Home currency") {
            Selector(
                selected = state.homeCurrency,
                options = CURRENCIES,
                label = { it },
                onSelect = { viewModel.setHomeCurrency(it) },
            )
        }
        SettingRow(label = "Default distance unit") {
            Selector(
                selected = state.lastDistanceUnit,
                options = DistanceUnit.entries,
                label = { it.label },
                onSelect = { viewModel.setDistanceUnit(it) },
            )
        }
        SettingRow(label = "Default volume unit") {
            Selector(
                selected = state.lastVolumeUnit,
                options = VolumeUnit.entries,
                label = { it.label },
                onSelect = { viewModel.setVolumeUnit(it) },
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("FX rates", style = MaterialTheme.typography.titleSmall)
                if (state.fxLoading) {
                    Text("Updating exchange rates...", style = MaterialTheme.typography.bodyMedium)
                }
                state.fxError?.let {
                    Text(
                        "Could not fetch rates: $it. Cached or 1:1 rates used.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!state.fxLoading && state.fxError == null) {
                    Text("Rates cached locally when available.", style = MaterialTheme.typography.bodyMedium)
                }
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
