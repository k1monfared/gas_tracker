package com.k1.gastracker.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

object BitmapSampler {
    fun decode(stream: InputStream, maxSide: Int = 1600): Bitmap {
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val width = bounds.outWidth
        val height = bounds.outHeight
        while (width / sample > maxSide || height / sample > maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalArgumentException("could not decode image")
    }

    fun downsample(bitmap: Bitmap, maxSide: Int = 1600): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / largest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
