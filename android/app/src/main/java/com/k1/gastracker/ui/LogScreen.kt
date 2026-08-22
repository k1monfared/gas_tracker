package com.k1.gastracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.k1.gastracker.R
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.OcrTarget
import com.k1.gastracker.core.PhotoDraft
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.core.fromKm
import com.k1.gastracker.core.pastAverageEfficiency
import com.k1.gastracker.core.previousOdometerForDate
import com.k1.gastracker.core.toKm
import com.k1.gastracker.core.toLiters
import com.k1.gastracker.ui.components.Selector
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CURRENCIES = listOf("EUR", "USD", "GBP", "CAD", "CHF", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY")

private fun parseNumber(s: String): Double? {
    val v = s.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return v.takeIf { it.isFinite() }
}

private fun parseInt(s: String): Int? = s.trim().toIntOrNull()

private fun loadThumbnail(path: String): Bitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inSampleSize = 8 }
    BitmapFactory.decodeFile(path, options)
}.getOrNull()

@Composable
fun LogScreen(state: UiState, actions: GasTrackerActions) {
    val context = LocalContext.current

    val cameraFile = remember { File(context.filesDir, "camera_temp.jpg") }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cameraFile,
            )
            actions.processPhoto(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) actions.processPhoto(uri)
    }

    var distanceText by rememberSaveable { mutableStateOf("") }
    var odometerText by rememberSaveable { mutableStateOf("") }
    var volumeText by rememberSaveable { mutableStateOf("") }
    var costText by rememberSaveable { mutableStateOf("") }
    var octaneText by rememberSaveable { mutableStateOf("") }
    var stationText by rememberSaveable { mutableStateOf("") }
    var dateIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var volumeError by rememberSaveable { mutableStateOf<String?>(null) }
    var odometerError by rememberSaveable { mutableStateOf<String?>(null) }
    var costError by rememberSaveable { mutableStateOf<String?>(null) }
    var octaneError by rememberSaveable { mutableStateOf<String?>(null) }
    var showEfficiencyDialog by rememberSaveable { mutableStateOf(false) }
    var appliedDraftCount by rememberSaveable { mutableIntStateOf(0) }
    var pendingVolume by rememberSaveable { mutableStateOf<Double?>(null) }

    val date = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    val relevantRefills = state.editingRefill?.let { state.refills - it } ?: state.refills
    val previousOdometer = remember(date, relevantRefills, state.lastDistanceUnit) {
        previousOdometerForDate(date, state.lastDistanceUnit, relevantRefills)
    }
    val tooLowTemplate = stringResource(R.string.odometer_too_low, "%.1f".format(previousOdometer ?: 0.0), state.lastDistanceUnit.label)

    fun syncFromDistance() {
        val distance = parseNumber(distanceText)
        if (distance != null && distance >= 0 && previousOdometer != null) {
            odometerText = "%.1f".format(previousOdometer + distance)
            odometerError = null
        }
    }

    fun syncFromOdometer() {
        val odometer = parseNumber(odometerText)
        if (odometer != null && previousOdometer != null) {
            if (odometer < previousOdometer) {
                odometerError = tooLowTemplate
                distanceText = ""
            } else {
                odometerError = null
                distanceText = "%.1f".format(odometer - previousOdometer)
            }
        } else {
            odometerError = null
        }
    }

    fun clearForm() {
        distanceText = ""
        odometerText = ""
        volumeText = ""
        costText = ""
        octaneText = ""
        stationText = ""
        dateIso = LocalDate.now().toString()
        volumeError = null
        odometerError = null
        costError = null
        octaneError = null
        pendingVolume = null
    }

    fun applyDraft(draft: PhotoDraft) {
        draft.volume?.let { volumeText = it.toString() }
        draft.cost?.let { costText = it.toString() }
        draft.odometer?.let { odo ->
            odometerText = odo.toString()
            syncFromOdometer()
        }
        draft.distanceKm?.let { distanceText = fromKm(it, state.lastDistanceUnit).toString() }
    }

    fun buildRefill(): Refill? {
        val volume = parseNumber(volumeText)
        val distance = parseNumber(distanceText)
        val odometer = parseNumber(odometerText)
        val cost = if (costText.isBlank()) null else parseNumber(costText)
        val octane = if (octaneText.isBlank()) null else parseInt(octaneText)
        when {
            volume == null || volume <= 0 -> {
                volumeError = context.getString(R.string.volume_positive)
                return null
            }
            odometer != null && previousOdometer != null && odometer < previousOdometer -> {
                odometerError = tooLowTemplate
                return null
            }
            costText.isNotBlank() && (cost == null || cost < 0) -> {
                costError = context.getString(R.string.cost_invalid)
                return null
            }
            octaneText.isNotBlank() && (octane == null || octane < 0) -> {
                octaneError = context.getString(R.string.octane_invalid)
                return null
            }
            else -> {
                volumeError = null
                odometerError = null
                costError = null
                octaneError = null
                return Refill(
                    date = date,
                    volume = volume,
                    distance = distance?.takeIf { it > 0 },
                    odometer = odometer?.takeIf { it > 0 },
                    cost = cost,
                    distanceUnit = state.lastDistanceUnit,
                    volumeUnit = state.lastVolumeUnit,
                    currency = state.lastInputCurrency,
                    octane = octane,
                    station = stationText.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    LaunchedEffect(state.editingRefill) {
        val source = state.editingRefill
        if (source != null) {
            distanceText = source.distance?.toString() ?: ""
            odometerText = source.odometer?.toString() ?: ""
            volumeText = source.volume.toString()
            costText = source.cost?.toString() ?: ""
            octaneText = source.octane?.toString() ?: ""
            stationText = source.station ?: ""
            dateIso = source.date.toString()
        }
    }

    LaunchedEffect(state.photoDrafts.size) {
        if (state.editingRefill != null) return@LaunchedEffect
        val drafts = state.photoDrafts
        if (drafts.isEmpty()) {
            appliedDraftCount = 0
            return@LaunchedEffect
        }
        drafts.drop(appliedDraftCount).forEach { applyDraft(it) }
        appliedDraftCount = drafts.size
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
                        dateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEfficiencyDialog) {
        AlertDialog(
            onDismissRequest = { showEfficiencyDialog = false },
            title = { Text(stringResource(R.string.unusual_efficiency_title)) },
            text = { Text(stringResource(R.string.unusual_efficiency_body)) },
            confirmButton = {
                TextButton(onClick = {
                    buildRefill()?.let {
                        actions.saveRefill(it)
                        clearForm()
                    }
                    showEfficiencyDialog = false
                    pendingVolume = null
                }) { Text(stringResource(R.string.save_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { showEfficiencyDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.historyWritable) {
            Text(
                stringResource(R.string.history_locked),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.editingRefill != null) {
            Text(stringResource(R.string.editing_refill), style = MaterialTheme.typography.titleMedium)
        }

        if (state.editingRefill == null) {
            Text(stringResource(R.string.photo_entry), style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        cameraFile.parentFile?.mkdirs()
                        cameraFile.createNewFile()
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            cameraFile,
                        )
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.take_photo), style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.pick_photo), style = MaterialTheme.typography.labelMedium)
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
                    onRemove = { actions.deletePhotoDraft(draft) },
                )
            }
        }

        OutlinedTextField(
            value = distanceText,
            onValueChange = {
                distanceText = it
                syncFromDistance()
            },
            label = { Text(stringResource(R.string.distance_driven)) },
            supportingText = { Text(stringResource(R.string.distance_supporting)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Selector(
                    selected = state.lastDistanceUnit,
                    options = DistanceUnit.entries,
                    label = { it.label },
                    onSelect = { actions.setDistanceUnit(it) },
                )
            },
        )
        OutlinedTextField(
            value = odometerText,
            onValueChange = {
                odometerText = it
                syncFromOdometer()
            },
            label = { Text(stringResource(R.string.odometer_reading)) },
            isError = odometerError != null,
            supportingText = {
                if (previousOdometer != null) {
                    Text(odometerError ?: stringResource(R.string.odometer_previous, "%.1f".format(previousOdometer), state.lastDistanceUnit.label))
                } else {
                    Text(odometerError ?: stringResource(R.string.odometer_hint))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Selector(
                    selected = state.lastDistanceUnit,
                    options = DistanceUnit.entries,
                    label = { it.label },
                    onSelect = { actions.setDistanceUnit(it) },
                )
            },
        )
        OutlinedTextField(
            value = volumeText,
            onValueChange = { volumeText = it },
            label = { Text(stringResource(R.string.fuel_filled)) },
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
                    onSelect = { actions.setVolumeUnit(it) },
                )
            },
        )
        OutlinedTextField(
            value = costText,
            onValueChange = { costText = it },
            label = { Text(stringResource(R.string.total_cost)) },
            isError = costError != null,
            supportingText = { costError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Selector(
                    selected = state.lastInputCurrency,
                    options = CURRENCIES,
                    label = { it },
                    onSelect = { actions.setInputCurrency(it) },
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = octaneText,
                onValueChange = { octaneText = it },
                label = { Text(stringResource(R.string.octane)) },
                isError = octaneError != null,
                supportingText = { Text(octaneError ?: stringResource(R.string.optional)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = stationText,
                onValueChange = { stationText = it },
                label = { Text(stringResource(R.string.station)) },
                supportingText = { Text(stringResource(R.string.optional)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.height(48.dp)) {
                Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
            Button(
                enabled = state.historyWritable,
                onClick = {
                    val refill = buildRefill() ?: return@Button
                    val volume = refill.volume
                    val distance = parseNumber(distanceText)
                    val odometer = parseNumber(odometerText)
                    val distanceKm = when {
                        distance != null && distance > 0 -> toKm(distance, state.lastDistanceUnit)
                        odometer != null && previousOdometer != null -> toKm(odometer - previousOdometer, state.lastDistanceUnit)
                        else -> null
                    }
                    val currentEff = if (distanceKm != null && distanceKm > 0) {
                        toLiters(volume, state.lastVolumeUnit) / distanceKm * 100.0
                    } else null
                    val avgEff = pastAverageEfficiency(relevantRefills)
                    if (avgEff != null && currentEff != null && currentEff > avgEff * 1.5) {
                        pendingVolume = volume
                        showEfficiencyDialog = true
                    } else {
                        actions.saveRefill(refill)
                        clearForm()
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Text(if (state.editingRefill != null) stringResource(R.string.update_refill) else stringResource(R.string.save_refill))
            }
        }
        if (state.editingRefill != null) {
            OutlinedButton(onClick = {
                actions.cancelEditing()
                clearForm()
            }, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.cancel_edit))
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
    val kind = draft.target.name.lowercase().replaceFirstChar { it.uppercase() }
    Card(Modifier.fillMaxWidth().semantics { contentDescription = kind }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val thumbnail = remember(draft.imagePath) { loadThumbnail(draft.imagePath) }
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.photo_draft_desc, kind),
                    modifier = Modifier.size(64.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(kind, style = MaterialTheme.typography.labelLarge)
                Text(
                    when (draft.target) {
                        OcrTarget.PUMP -> {
                            val vol = draft.volume?.let { "%.2f L".format(it) } ?: ""
                            val cost = draft.cost?.let { "%.2f $currency".format(it) } ?: ""
                            listOfNotNull(vol.takeIf { it.isNotBlank() }, cost.takeIf { it.isNotBlank() })
                                .joinToString(" / ").ifEmpty { stringResource(R.string.no_values_found) }
                        }
                        OcrTarget.ODOMETER -> {
                            val odo = draft.odometer?.let { "%.1f ${distanceUnit.label}".format(it) } ?: stringResource(R.string.no_odometer_found)
                            val dist = draft.distanceKm?.let { "%.1f ${distanceUnit.label} inferred".format(fromKm(it, distanceUnit)) }
                            listOfNotNull(odo, dist).joinToString(" / ")
                        }
                        OcrTarget.RECEIPT -> {
                            val vol = draft.volume?.let { "%.2f L".format(it) }
                            val cost = draft.cost?.let { "%.2f $currency".format(it) }
                            listOfNotNull(vol, cost, draft.station, draft.receiptDate)
                                .joinToString(" / ").ifEmpty { stringResource(R.string.receipt) }
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
            TextButton(onClick = onRemove, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.remove)) }
        }
    }
}
