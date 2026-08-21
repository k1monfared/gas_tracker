package com.k1.gastracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.ui.components.Selector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CURRENCIES = listOf("EUR", "USD", "GBP", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY")

private fun parseNumber(s: String): Double? =
    s.trim().replace(',', '.').toDoubleOrNull()

private fun parseInt(s: String): Int? =
    s.trim().toIntOrNull()

@Composable
fun LogScreen(state: UiState, viewModel: AppViewModel) {
    var useOdometer by remember { mutableStateOf(false) }
    var distanceText by remember { mutableStateOf("") }
    var odometerText by remember { mutableStateOf("") }
    var volumeText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var octaneText by remember { mutableStateOf("") }
    var stationText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showPicker by remember { mutableStateOf(false) }
    var volumeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.editingRefill) {
        state.editingRefill?.let { r ->
            useOdometer = r.odometer != null
            distanceText = r.distance?.toString() ?: ""
            odometerText = r.odometer?.toString() ?: ""
            volumeText = r.volume.toString()
            costText = r.cost?.toString() ?: ""
            octaneText = r.octane?.toString() ?: ""
            stationText = r.station ?: ""
            date = r.date
        } ?: run {
            useOdometer = false
            distanceText = ""
            odometerText = ""
            volumeText = ""
            costText = ""
            octaneText = ""
            stationText = ""
            date = LocalDate.now()
        }
    }

    if (showPicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 24 * 60 * 60 * 1000,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.editingRefill != null) {
            Text("Editing refill", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use current odometer")
            Switch(
                checked = useOdometer,
                onCheckedChange = { useOdometer = it },
            )
        }
        if (useOdometer) {
            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it },
                label = { Text("Odometer reading") },
                supportingText = { Text("current mileage on the car") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Selector(
                        selected = state.lastDistanceUnit,
                        options = DistanceUnit.entries,
                        label = { it.label },
                        onSelect = { viewModel.setDistanceUnit(it) },
                    )
                },
            )
        } else {
            OutlinedTextField(
                value = distanceText,
                onValueChange = { distanceText = it },
                label = { Text("Distance driven") },
                supportingText = { Text("since previous refill, optional") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Selector(
                        selected = state.lastDistanceUnit,
                        options = DistanceUnit.entries,
                        label = { it.label },
                        onSelect = { viewModel.setDistanceUnit(it) },
                    )
                },
            )
        }
        OutlinedTextField(
            value = volumeText,
            onValueChange = { volumeText = it },
            label = { Text("Fuel filled") },
            isError = volumeError != null,
            supportingText = { volumeError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Selector(
                    selected = state.lastVolumeUnit,
                    options = VolumeUnit.entries,
                    label = { it.label },
                    onSelect = { viewModel.setVolumeUnit(it) },
                )
            },
        )
        OutlinedTextField(
            value = costText,
            onValueChange = { costText = it },
            label = { Text("Total cost") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Selector(
                    selected = state.lastInputCurrency,
                    options = CURRENCIES,
                    label = { it },
                    onSelect = { viewModel.setInputCurrency(it) },
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = octaneText,
                onValueChange = { octaneText = it },
                label = { Text("Octane") },
                supportingText = { Text("optional") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = stationText,
                onValueChange = { stationText = it },
                label = { Text("Station") },
                supportingText = { Text("optional") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showPicker = true }) {
                Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
            Button(
                onClick = {
                    val volume = parseNumber(volumeText)
                    when {
                        volume == null || volume <= 0 ->
                            volumeError = "enter a positive amount"
                        else -> {
                            volumeError = null
                            val distance = parseNumber(distanceText)
                            val odometer = parseNumber(odometerText)
                            viewModel.saveRefill(
                                Refill(
                                    date = date,
                                    volume = volume,
                                    distance = if (useOdometer) null else distance,
                                    odometer = if (useOdometer) odometer else null,
                                    cost = parseNumber(costText),
                                    distanceUnit = state.lastDistanceUnit,
                                    volumeUnit = state.lastVolumeUnit,
                                    currency = state.lastInputCurrency,
                                    octane = parseInt(octaneText),
                                    station = stationText.takeIf { it.isNotBlank() },
                                )
                            )
                            distanceText = ""
                            odometerText = ""
                            volumeText = ""
                            costText = ""
                            octaneText = ""
                            stationText = ""
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.editingRefill != null) "Update refill" else "Save refill")
            }
        }
        if (state.editingRefill != null) {
            OutlinedButton(onClick = { viewModel.cancelEditing() }) {
                Text("Cancel edit")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
