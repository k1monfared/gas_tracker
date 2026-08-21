package com.k1.gastracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class TesseractOcr(context: Context) {
    private val api = TessBaseAPI()
    private val dataDir = File(context.filesDir, "tessdata")

    init {
        val trained = File(dataDir, "eng.traineddata")
        if (!trained.exists()) {
            dataDir.mkdirs()
            context.assets.open("tessdata/eng.traineddata").use { input ->
                trained.outputStream().use { output -> input.copyTo(output) }
            }
        }
        api.init(context.filesDir.absolutePath, "eng")
    }

    fun recognize(bitmap: Bitmap): String {
        val gray = toGrayscale(bitmap)
        api.setImage(gray)
        val text = api.utF8Text
        api.clear()
        return text
    }

    fun close() {
        api.end()
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val gray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint()
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return gray
    }
}
