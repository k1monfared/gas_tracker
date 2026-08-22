package com.k1.gastracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI

class TesseractOcr(context: Context) {
    private val api = TessBaseAPI()

    init {
        TessdataInstaller.ensureEnglishModel(context.filesDir) { path ->
            context.assets.open(path)
        }
        api.init(context.filesDir.absolutePath, "eng")
    }

    fun recognize(bitmap: Bitmap): String {
        val scaled = BitmapSampler.downsample(bitmap)
        val gray = toGrayscale(scaled)
        try {
            api.setImage(gray)
            val text = api.utF8Text
            api.clear()
            return text
        } finally {
            if (gray != scaled) gray.recycle()
            if (scaled != bitmap) scaled.recycle()
        }
    }

    fun close() {
        api.end()
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint()
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return gray
    }
}
