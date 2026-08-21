package com.k1.gastracker

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k1.gastracker.core.extractVolumeAndCost
import com.k1.gastracker.data.TesseractOcr
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrInstrumentedTest {
    @Test
    fun recognizesSamplePumpImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = context.assets.open("sample_pump.png").use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        val ocr = TesseractOcr(context)
        val text = ocr.recognize(bitmap)
        ocr.close()

        val (volume, cost) = extractVolumeAndCost(text, "EUR")
        assertEquals(42.5, volume!!, 1e-2)
        assertEquals(85.2, cost!!, 1e-2)
    }
}
