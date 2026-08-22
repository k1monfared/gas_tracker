package com.k1.gastracker.data

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

object TessdataInstaller {
    const val ASSET_PATH = "tessdata/eng.traineddata.gzip"
    private const val MODEL_NAME = "eng.traineddata"

    fun ensureEnglishModel(filesDir: File, openAsset: (String) -> InputStream): File {
        val dir = File(filesDir, "tessdata").also { it.mkdirs() }
        val trained = File(dir, MODEL_NAME)
        if (trained.exists() && trained.length() > 0L) return trained
        val tmp = File(dir, "$MODEL_NAME.tmp")
        openAsset(ASSET_PATH).use { raw ->
            GZIPInputStream(raw).use { gzip ->
                tmp.outputStream().use { output -> gzip.copyTo(output) }
            }
        }
        if (!tmp.renameTo(trained)) {
            tmp.copyTo(trained, overwrite = true)
            tmp.delete()
        }
        return trained
    }
}
