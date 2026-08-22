package com.k1.gastracker.ui

import android.net.Uri
import com.k1.gastracker.core.ConvertedCost
import com.k1.gastracker.core.DistanceUnit
import com.k1.gastracker.core.PhotoDraft
import com.k1.gastracker.core.Refill
import com.k1.gastracker.core.VolumeUnit

interface GasTrackerActions {
    fun setHomeCurrency(currency: String)
    fun setDistanceUnit(unit: DistanceUnit)
    fun setVolumeUnit(unit: VolumeUnit)
    fun setInputCurrency(currency: String)
    fun startEditing(refill: Refill)
    fun cancelEditing()
    fun saveRefill(refill: Refill)
    fun deleteRefill(refill: Refill)
    fun processPhoto(uri: Uri)
    fun deletePhotoDraft(draft: PhotoDraft)
    fun clearPhotoDrafts()
    suspend fun convertedCosts(targetCurrency: String): Map<Refill, ConvertedCost>
    fun retryFx()
    fun retryLoad()
    fun exportHistory(uri: Uri)
    fun importHistory(uri: Uri)
    fun dismissStorageBanner()
}

class NoOpGasTrackerActions : GasTrackerActions {
    override fun setHomeCurrency(currency: String) {}
    override fun setDistanceUnit(unit: DistanceUnit) {}
    override fun setVolumeUnit(unit: VolumeUnit) {}
    override fun setInputCurrency(currency: String) {}
    override fun startEditing(refill: Refill) {}
    override fun cancelEditing() {}
    override fun saveRefill(refill: Refill) {}
    override fun deleteRefill(refill: Refill) {}
    override fun processPhoto(uri: Uri) {}
    override fun deletePhotoDraft(draft: PhotoDraft) {}
    override fun clearPhotoDrafts() {}
    override suspend fun convertedCosts(targetCurrency: String): Map<Refill, ConvertedCost> = emptyMap()
    override fun retryFx() {}
    override fun retryLoad() {}
    override fun exportHistory(uri: Uri) {}
    override fun importHistory(uri: Uri) {}
    override fun dismissStorageBanner() {}
}
