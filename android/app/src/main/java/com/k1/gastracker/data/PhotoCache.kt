package com.k1.gastracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun load(): List<PhotoDraft> {
        if (!cacheFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PhotoDraftDto>>(cacheFile.readText())
                .map { it.toDomain() }
                .filter { File(it.imagePath).exists() }
        }.getOrDefault(emptyList())
    }

    fun save(drafts: List<PhotoDraft>) {
        val tmp = File(context.filesDir, "photo_cache.json.tmp")
        tmp.writeText(json.encodeToString(drafts.map { it.toDto() }))
        tmp.renameTo(cacheFile)
    }

    fun clear() {
        cacheFile.delete()
        photosDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(draft: PhotoDraft) {
        File(draft.imagePath).delete()
        save(load().filter { it.imagePath != draft.imagePath })
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        return file.absolutePath
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
