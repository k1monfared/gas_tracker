package com.k1.gastracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit
import com.k1.gastracker.data.FxRepository
import com.k1.gastracker.data.RefillStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val refills: List<Refill> = emptyList(),
    val homeCurrency: String = "EUR",
    val lastDistanceUnit: DistanceUnit = DistanceUnit.KM,
    val lastVolumeUnit: VolumeUnit = VolumeUnit.LITER,
    val lastInputCurrency: String = "EUR",
    val editingRefill: Refill? = null,
    val convertedCosts: Map<Refill, Double?> = emptyMap(),
    val fxLoading: Boolean = false,
    val fxError: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RefillStore(application)
    private val fxRepo = FxRepository(application)
    private val prefs = application.getSharedPreferences("gastracker", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            refills = store.load(),
            homeCurrency = prefs.getString("home_currency", null) ?: "EUR",
            lastDistanceUnit = prefs.get("distance_unit", DistanceUnit.KM),
            lastVolumeUnit = prefs.get("volume_unit", VolumeUnit.LITER),
            lastInputCurrency = prefs.getString("input_currency", null) ?: "EUR",
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
