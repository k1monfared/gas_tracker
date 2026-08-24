package com.k1.gastracker

import com.k1.gastracker.data.TessdataInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.util.zip.GZIPOutputStream

class TessdataInstallerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun decompressesGzippedModelOnce() {
        val payload = "tesseract-model".toByteArray()
        val gz = java.io.ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(payload) }
        val filesDir = tmp.root
        val installed = TessdataInstaller.ensureEnglishModel(filesDir) {
            ByteArrayInputStream(gz.toByteArray())
        }
        assertEquals(payload.toList(), installed.readBytes().toList())
        val again = TessdataInstaller.ensureEnglishModel(filesDir) {
            throw AssertionError("should not reopen assets when the model exists")
        }
        assertTrue(again.exists())
        assertEquals(installed.absolutePath, again.absolutePath)
    }

    @Test
    fun assetPathIsNotAaptGzipSuffix() {
        assertEquals("tessdata/eng.traineddata.gzip", TessdataInstaller.ASSET_PATH)
    }
}
