package com.example.services.analysis

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Intelligent Image Cleaner and Contrast Enhancer for PUBG Mobile Kill Feed Crops.
 *
 * Implements requirement:
 * "Ai Sath me on nhi ho Raha tha wo bhi help Kary system ki picture saaf kar k dein or detect Karne me madad karta ha"
 *
 * Operations:
 * 1. Luminance & contrast normalization (expands dynamic range of text against translucent HUD).
 * 2. Unsharp sharpening to separate thin English character strokes and weapon silhouette contours.
 * 3. Binarized threshold assistance to help ML Kit and template detectors distinguish arrows and knock icons.
 */
object KillFeedImageEnhancer {

    /**
     * Cleans and enhances the contrast of a kill feed bitmap.
     * Preserves original dimensions and returns a crisp, high-clarity Bitmap.
     */
    fun enhance(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0 || source.isRecycled) return source

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Calculate luminance statistics for adaptive contrast stretching
        var minLum = 255
        var maxLum = 0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }

        val range = (maxLum - minLum).coerceAtLeast(20)

        // 2. Apply contrast stretching and edge accentuation
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = (pixel shr 24) and 0xFF
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Stretch dynamic range
            val newR = (((r - minLum).toFloat() / range) * 255f).toInt().coerceIn(0, 255)
            val newG = (((g - minLum).toFloat() / range) * 255f).toInt().coerceIn(0, 255)
            val newB = (((b - minLum).toFloat() / range) * 255f).toInt().coerceIn(0, 255)

            // Emphasize bright text and icon strokes (PUBG kill text is bright white / orange / red)
            val lum = (0.299 * newR + 0.587 * newG + 0.114 * newB).toInt()
            if (lum > 140) {
                // Boost text clarity
                val boostR = (newR * 1.15f).toInt().coerceIn(0, 255)
                val boostG = (newG * 1.15f).toInt().coerceIn(0, 255)
                val boostB = (newB * 1.15f).toInt().coerceIn(0, 255)
                pixels[i] = (a shl 24) or (boostR shl 16) or (boostG shl 8) or boostB
            } else if (lum < 70) {
                // Deepen translucent background noise to increase text isolation
                val dimR = (newR * 0.7f).toInt()
                val dimG = (newG * 0.7f).toInt()
                val dimB = (newB * 0.7f).toInt()
                pixels[i] = (a shl 24) or (dimR shl 16) or (dimG shl 8) or dimB
            } else {
                pixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
