package com.k1.gastracker.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.k1.gastracker.core.OcrTarget
import com.k1.gastracker.core.PhotoDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class PhotoCache(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, "photo_cache.json")
    private val photosDir = File(context.filesDir, "photos").also { it.mkdirs() }
    private val lock = Any()

    fun load(): List<PhotoDraft> = synchronized(lock) { loadUnlocked() }

    fun save(drafts: List<PhotoDraft>) = synchronized(lock) { saveUnlocked(drafts) }

    fun append(draft: PhotoDraft): List<PhotoDraft> = synchronized(lock) {
        val drafts = loadUnlocked() + draft
        saveUnlocked(drafts)
        drafts
    }

    fun clear() = synchronized(lock) {
        cacheFile.delete()
        photosDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(draft: PhotoDraft): List<PhotoDraft> = synchronized(lock) {
        File(draft.imagePath).delete()
        val drafts = loadUnlocked().filter { it.imagePath != draft.imagePath }
        saveUnlocked(drafts)
        drafts
    }

    fun saveImage(sourceUri: Uri): String {
        val file = File(photosDir, "photo_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    fun saveImage(bitmap: Bitmap): String {
        val file = File(photosDir, "photo_${UUID.randomUUID()}.jpg")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
        return file.absolutePath
    }

    private fun loadUnlocked(): List<PhotoDraft> {
        if (!cacheFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PhotoDraftDto>>(cacheFile.readText())
                .map { it.toDomain() }
                .filter { File(it.imagePath).exists() }
        }.getOrDefault(emptyList())
    }

    private fun saveUnlocked(drafts: List<PhotoDraft>) {
        val tmp = File(context.filesDir, "photo_cache.json.tmp")
        tmp.writeText(json.encodeToString(drafts.map { it.toDto() }))
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    @Serializable
    private data class PhotoDraftDto(
        val target: String,
        val imagePath: String,
        val rawText: String,
        val volume: Double? = null,
        val cost: Double? = null,
        val odometer: Double? = null,
        val distanceKm: Double? = null,
        val station: String? = null,
        val receiptDate: String? = null,
    )

    private fun PhotoDraftDto.toDomain() = PhotoDraft(
        target = runCatching { OcrTarget.valueOf(target) }.getOrDefault(OcrTarget.PUMP),
        imagePath = imagePath,
        rawText = rawText,
        volume = volume,
        cost = cost,
        odometer = odometer,
        distanceKm = distanceKm,
        station = station,
        receiptDate = receiptDate,
    )

    private fun PhotoDraft.toDto() = PhotoDraftDto(
        target = target.name,
        imagePath = imagePath,
        rawText = rawText,
        volume = volume,
        cost = cost,
        odometer = odometer,
        distanceKm = distanceKm,
        station = station,
        receiptDate = receiptDate,
    )
}
