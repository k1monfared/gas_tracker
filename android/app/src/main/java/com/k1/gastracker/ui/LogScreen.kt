package com.k1.gastracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.OcrTarget
import com.k1.gastracker.core.PhotoDraft
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.ui.components.Selector
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CURRENCIES = listOf("EUR", "USD", "GBP", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY")

private fun parseNumber(s: String): Double? =
    s.trim().replace(',', '.').toDoubleOrNull()

private fun parseInt(s: String): Int? =
    s.trim().toIntOrNull()

private fun loadThumbnail(path: String): Bitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inSampleSize = 8 }
    BitmapFactory.decodeFile(path, options)
}.getOrNull()

@Composable
fun LogScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current

    val cameraFile = remember { File(context.filesDir, "camera_temp.jpg").also { it.createNewFile() } }
    val cameraUri = remember {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cameraFile,
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) viewModel.processPhoto(cameraUri)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.processPhoto(uri)
    }

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

    LaunchedEffect(state.editingRefill, state.photoDrafts) {
        val source = state.editingRefill
        if (source != null) {
            useOdometer = source.odometer != null
            distanceText = source.distance?.toString() ?: ""
            odometerText = source.odometer?.toString() ?: ""
            volumeText = source.volume.toString()
            costText = source.cost?.toString() ?: ""
            octaneText = source.octane?.toString() ?: ""
            stationText = source.station ?: ""
            date = source.date
        } else {
            state.photoDrafts.forEach { draft ->
                draft.volume?.let { volumeText = it.toString() }
                draft.cost?.let { costText = it.toString() }
                draft.odometer?.let { odo ->
                    odometerText = odo.toString()
                    useOdometer = true
                }
            }
            if (state.photoDrafts.isEmpty()) {
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

        if (state.editingRefill == null) {
            Text("Photo entry", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { cameraLauncher.launch(cameraUri) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Take photo", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Pick photo", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (state.photoLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(24.dp),
                )
            }

            state.photoError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            state.photoDrafts.forEach { draft ->
                PhotoDraftCard(
                    draft = draft,
                    currency = state.lastInputCurrency,
                    distanceUnit = state.lastDistanceUnit,
                    onRemove = { viewModel.deletePhotoDraft(draft) },
                )
            }
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

@Composable
private fun PhotoDraftCard(
    draft: PhotoDraft,
    currency: String,
    distanceUnit: DistanceUnit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val thumbnail = remember(draft.imagePath) { loadThumbnail(draft.imagePath) }
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    draft.target.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    when (draft.target) {
                        OcrTarget.PUMP -> {
                            val vol = draft.volume?.let { "%.2f L".format(it) } ?: ""
                            val cost = draft.cost?.let { "%.2f $currency".format(it) } ?: ""
                            listOfNotNull(vol.takeIf { it.isNotBlank() }, cost.takeIf { it.isNotBlank() })
                                .joinToString(" / ").ifEmpty { "No values found" }
                        }
                        OcrTarget.ODOMETER -> {
                            draft.odometer?.let { "%.1f ${distanceUnit.label}".format(it) }
                                ?: "No odometer found"
                        }
                        OcrTarget.RECEIPT -> {
                            val vol = draft.volume?.let { "%.2f L".format(it) }
                            val cost = draft.cost?.let { "%.2f $currency".format(it) }
                            listOfNotNull(vol, cost, draft.station, draft.receiptDate)
                                .joinToString(" / ").ifEmpty { "Receipt" }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    draft.rawText.replace("\n", " ").take(60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}
