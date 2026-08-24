package com.k1.gastracker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.k1.gastracker.core.ConvertedCost
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.OcrTarget
import com.k1.gastracker.core.PhotoDraft
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.core.classifyPhoto
import com.k1.gastracker.core.extractOdometer
import com.k1.gastracker.core.extractVolumeAndCost
import com.k1.gastracker.core.inferDistanceFromOdometer
import com.k1.gastracker.data.BitmapSampler
import com.k1.gastracker.data.FxRepository
import com.k1.gastracker.data.PhotoCache
import com.k1.gastracker.data.RefillLoadResult
import com.k1.gastracker.data.RefillStore
import com.k1.gastracker.data.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class UiState(
    val refills: List<Refill> = emptyList(),
    val homeCurrency: String = "EUR",
    val lastDistanceUnit: DistanceUnit = DistanceUnit.KM,
    val lastVolumeUnit: VolumeUnit = VolumeUnit.LITER,
    val lastInputCurrency: String = "EUR",
    val editingRefill: Refill? = null,
    val photoDrafts: List<PhotoDraft> = emptyList(),
    val photoLoading: Boolean = false,
    val photoError: String? = null,
    val convertedCosts: Map<Refill, ConvertedCost> = emptyMap(),
    val fxLoading: Boolean = false,
    val fxError: String? = null,
    val fxUnavailableCount: Int = 0,
    val storageError: String? = null,
    val historyWritable: Boolean = true,
    val recoveredFromBackup: Boolean = false,
    val importMessage: String? = null,
)

class AppViewModel internal constructor(
    application: Application,
    private val store: RefillStore,
    private val fxRepo: FxRepository,
    private val photoCache: PhotoCache,
) : AndroidViewModel(application), GasTrackerActions {
    constructor(application: Application) : this(
        application,
        RefillStore(application.filesDir),
        FxRepository(File(application.filesDir, "fx_cache.json")),
        PhotoCache(application),
    )

    private val prefs = application.getSharedPreferences("gastracker", Context.MODE_PRIVATE)
    private var fxJob: Job? = null

    private val _state = MutableStateFlow(
        initialState(
            prefs = prefs,
            photoDrafts = photoCache.load(),
            load = store.load(),
        )
    )

    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshFx()
    }

    override fun setHomeCurrency(currency: String) {
        _state.update { it.copy(homeCurrency = currency) }
        persistPrefs()
        refreshFx()
    }

    override fun setDistanceUnit(unit: DistanceUnit) {
        _state.update { it.copy(lastDistanceUnit = unit) }
        persistPrefs()
    }

    override fun setVolumeUnit(unit: VolumeUnit) {
        _state.update { it.copy(lastVolumeUnit = unit) }
        persistPrefs()
    }

    override fun setInputCurrency(currency: String) {
        _state.update { it.copy(lastInputCurrency = currency) }
        persistPrefs()
    }

    override fun startEditing(refill: Refill) {
        clearPhotoDrafts()
        _state.update {
            it.copy(
                editingRefill = refill,
                lastDistanceUnit = refill.distanceUnit,
                lastVolumeUnit = refill.volumeUnit,
                lastInputCurrency = refill.currency,
            )
        }
    }

    override fun cancelEditing() {
        clearPhotoDrafts()
        _state.update { it.copy(editingRefill = null) }
    }

    override suspend fun convertedCosts(targetCurrency: String): Map<Refill, ConvertedCost> {
        return fxRepo.convertedCosts(_state.value.refills, targetCurrency).costs
    }

    override fun saveRefill(refill: Refill) {
        if (!_state.value.historyWritable) return
        _state.update { current ->
            val old = current.editingRefill
            val list = if (old != null) {
                current.refills.map { if (it == old) refill else it }
            } else {
                current.refills + refill
            }
            current.copy(refills = list, editingRefill = null)
        }
        clearPhotoDrafts()
        persistRefills()
        persistPrefs()
        refreshFx()
    }

    override fun deleteRefill(refill: Refill) {
        if (!_state.value.historyWritable) return
        _state.update { current ->
            current.copy(
                refills = current.refills - refill,
                editingRefill = if (current.editingRefill == refill) null else current.editingRefill,
            )
        }
        persistRefills()
        refreshFx()
    }

    override fun processPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(photoLoading = true, photoError = null) }
            var bitmap: android.graphics.Bitmap? = null
            val result = runCatching {
                val context = getApplication<Application>()
                val image = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapSampler.decode(stream)
                } ?: throw IllegalArgumentException("could not decode image")
                bitmap = image
                val ocr = TesseractOcr(context)
                val text = try {
                    ocr.recognize(image)
                } finally {
                    ocr.close()
                }
                val target = classifyPhoto(text)
                val path = photoCache.saveImage(image)
                val draft = when (target) {
                    OcrTarget.PUMP -> {
                        val (volume, cost) = extractVolumeAndCost(text, _state.value.lastInputCurrency)
                        PhotoDraft(
                            target = target,
                            imagePath = path,
                            rawText = text,
                            volume = volume,
                            cost = cost,
                        )
                    }
                    OcrTarget.ODOMETER -> {
                        val odometer = extractOdometer(text)
                        val distanceKm = odometer?.let {
                            inferDistanceFromOdometer(
                                currentOdometer = it,
                                currentDate = LocalDate.now(),
                                currentUnit = _state.value.lastDistanceUnit,
                                refills = _state.value.refills,
                            )
                        }
                        PhotoDraft(
                            target = target,
                            imagePath = path,
                            rawText = text,
                            odometer = odometer,
                            distanceKm = distanceKm,
                        )
                    }
                    OcrTarget.RECEIPT -> {
                        val (volume, cost) = extractVolumeAndCost(text, _state.value.lastInputCurrency)
                        PhotoDraft(
                            target = target,
                            imagePath = path,
                            rawText = text,
                            volume = volume,
                            cost = cost,
                        )
                    }
                }
                photoCache.append(draft)
            }
            bitmap?.recycle()
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = { drafts ->
                        _state.update { it.copy(photoDrafts = drafts, photoLoading = false, photoError = null) }
                    },
                    onFailure = { e ->
                        _state.update { it.copy(photoLoading = false, photoError = e.message ?: "OCR failed") }
                    }
                )
            }
        }
    }

    override fun deletePhotoDraft(draft: PhotoDraft) {
        val drafts = photoCache.delete(draft)
        _state.update { it.copy(photoDrafts = drafts) }
    }

    override fun clearPhotoDrafts() {
        photoCache.clear()
        _state.update { it.copy(photoDrafts = emptyList(), photoError = null) }
    }

    override fun retryFx() {
        refreshFx()
    }

    override fun retryLoad() {
        applyLoad(store.load())
        refreshFx()
    }

    override fun exportHistory(uri: Uri) {
        val text = if (store.canSave()) {
            store.exportJson(_state.value.refills)
        } else {
            store.rawText() ?: return
        }
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray())
        }
    }

    override fun importHistory(uri: Uri) {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().decodeToString()
        } ?: return
        val result = store.restoreFromImport(text)
        applyLoad(result)
        if (result is RefillLoadResult.Loaded) {
            _state.update { it.copy(importMessage = "Imported ${result.refills.size} refill(s)") }
            persistPrefs()
            refreshFx()
        } else if (result is RefillLoadResult.Failed) {
            _state.update { it.copy(importMessage = result.message) }
        }
    }

    override fun dismissStorageBanner() {
        _state.update { it.copy(recoveredFromBackup = false, importMessage = null) }
    }

    private fun refreshFx() {
        fxJob?.cancel()
        fxJob = viewModelScope.launch {
            _state.update { it.copy(fxLoading = true, fxError = null) }
            val current = _state.value
            val result = fxRepo.convertedCosts(current.refills, current.homeCurrency)
            if (!isActive) return@launch
            _state.update {
                it.copy(
                    fxLoading = false,
                    convertedCosts = result.costs,
                    fxError = result.error,
                    fxUnavailableCount = result.unavailableCount,
                )
            }
        }
    }

    private fun persistPrefs() {
        val s = _state.value
        prefs.edit()
            .putString("home_currency", s.homeCurrency)
            .putString("distance_unit", s.lastDistanceUnit.name)
            .putString("volume_unit", s.lastVolumeUnit.name)
            .putString("input_currency", s.lastInputCurrency)
            .apply()
    }

    private fun persistRefills() {
        val s = _state.value
        if (!s.historyWritable) return
        store.save(s.refills)
    }

    private fun applyLoad(result: RefillLoadResult) {
        _state.update { current -> overlayLoad(current, result) }
    }
}

private fun initialState(
    prefs: android.content.SharedPreferences,
    photoDrafts: List<PhotoDraft>,
    load: RefillLoadResult,
): UiState {
    val base = UiState(
        homeCurrency = prefs.getString("home_currency", null) ?: "EUR",
        lastDistanceUnit = prefs.get("distance_unit", DistanceUnit.KM),
        lastVolumeUnit = prefs.get("volume_unit", VolumeUnit.LITER),
        lastInputCurrency = prefs.getString("input_currency", null) ?: "EUR",
        photoDrafts = photoDrafts,
    )
    return overlayLoad(base, load)
}

private fun overlayLoad(current: UiState, load: RefillLoadResult): UiState = when (load) {
    is RefillLoadResult.Loaded -> current.copy(
        refills = load.refills,
        historyWritable = true,
        storageError = null,
        recoveredFromBackup = load.recoveredFromBackup,
    )
    RefillLoadResult.Missing -> current.copy(
        refills = emptyList(),
        historyWritable = true,
        storageError = null,
        recoveredFromBackup = false,
    )
    is RefillLoadResult.Failed -> current.copy(
        refills = emptyList(),
        historyWritable = false,
        storageError = load.message,
        recoveredFromBackup = false,
    )
}

private fun android.content.SharedPreferences.get(key: String, default: DistanceUnit): DistanceUnit =
    getString(key, null)?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: default

private fun android.content.SharedPreferences.get(key: String, default: VolumeUnit): VolumeUnit =
    getString(key, null)?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() } ?: default
