package com.k1.gastracker.ui

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.OcrTarget
import com.k1.gastracker.core.PhotoDraft
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.core.classifyPhoto
import com.k1.gastracker.core.extractOdometer
import com.k1.gastracker.core.extractVolumeAndCost
import com.k1.gastracker.data.FxRepository
import com.k1.gastracker.data.PhotoCache
import com.k1.gastracker.data.RefillStore
import com.k1.gastracker.data.TesseractOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val convertedCosts: Map<Refill, Double?> = emptyMap(),
    val fxLoading: Boolean = false,
    val fxError: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RefillStore(application)
    private val fxRepo = FxRepository(application)
    private val photoCache = PhotoCache(application)
    private val prefs = application.getSharedPreferences("gastracker", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            refills = store.load(),
            homeCurrency = prefs.getString("home_currency", null) ?: "EUR",
            lastDistanceUnit = prefs.get("distance_unit", DistanceUnit.KM),
            lastVolumeUnit = prefs.get("volume_unit", VolumeUnit.LITER),
            lastInputCurrency = prefs.getString("input_currency", null) ?: "EUR",
            photoDrafts = photoCache.load(),
        )
    )

    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshFx()
    }

    fun setHomeCurrency(currency: String) {
        _state.update { it.copy(homeCurrency = currency) }
        persist()
        refreshFx()
    }

    fun setDistanceUnit(unit: DistanceUnit) {
        _state.update { it.copy(lastDistanceUnit = unit) }
        persist()
    }

    fun setVolumeUnit(unit: VolumeUnit) {
        _state.update { it.copy(lastVolumeUnit = unit) }
        persist()
    }

    fun setInputCurrency(currency: String) {
        _state.update { it.copy(lastInputCurrency = currency) }
        persist()
    }

    fun startEditing(refill: Refill) {
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

    fun cancelEditing() {
        _state.update { it.copy(editingRefill = null) }
    }

    fun saveRefill(refill: Refill) {
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
        persist()
        refreshFx()
    }

    fun deleteRefill(refill: Refill) {
        _state.update { current ->
            current.copy(
                refills = current.refills - refill,
                editingRefill = if (current.editingRefill == refill) null else current.editingRefill,
            )
        }
        persist()
        refreshFx()
    }

    fun processPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(photoLoading = true, photoError = null) }
            val result = runCatching {
                val context = getApplication<Application>()
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw IllegalArgumentException("could not decode image")
                val ocr = TesseractOcr(context)
                val text = runCatching { ocr.recognize(bitmap) }.also { ocr.close() }.getOrThrow()
                val target = classifyPhoto(text)
                val path = photoCache.saveImage(bitmap)
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
                        PhotoDraft(
                            target = target,
                            imagePath = path,
                            rawText = text,
                            odometer = odometer,
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
                val drafts = photoCache.load() + draft
                photoCache.save(drafts)
                drafts
            }
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

    fun deletePhotoDraft(draft: PhotoDraft) {
        photoCache.delete(draft)
        _state.update { it.copy(photoDrafts = photoCache.load()) }
    }

    fun clearPhotoDrafts() {
        photoCache.clear()
        _state.update { it.copy(photoDrafts = emptyList(), photoError = null) }
    }

    fun refillsInHomeCurrency(): List<Refill> {
        val home = _state.value.homeCurrency
        return _state.value.refills.map { refill ->
            val converted = _state.value.convertedCosts[refill]
            if (refill.currency == home || converted == null) {
                refill.copy(currency = home)
            } else {
                refill.copy(cost = converted, currency = home)
            }
        }
    }

    private fun refreshFx() {
        viewModelScope.launch {
            _state.update { it.copy(fxLoading = true, fxError = null) }
            val current = _state.value
            val result = runCatching {
                fxRepo.convertedCosts(current.refills, current.homeCurrency)
            }
            _state.update {
                it.copy(
                    fxLoading = false,
                    convertedCosts = result.getOrDefault(emptyMap()),
                    fxError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun persist() {
        val s = _state.value
        store.save(s.refills)
        prefs.edit()
            .putString("home_currency", s.homeCurrency)
            .putString("distance_unit", s.lastDistanceUnit.name)
            .putString("volume_unit", s.lastVolumeUnit.name)
            .putString("input_currency", s.lastInputCurrency)
            .apply()
    }
}

private fun android.content.SharedPreferences.get(key: String, default: DistanceUnit): DistanceUnit =
    getString(key, null)?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: default

private fun android.content.SharedPreferences.get(key: String, default: VolumeUnit): VolumeUnit =
    getString(key, null)?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() } ?: default
