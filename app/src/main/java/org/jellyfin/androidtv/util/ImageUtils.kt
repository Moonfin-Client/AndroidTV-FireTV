package org.jellyfin.androidtv.util

import android.graphics.Bitmap

/**
 * Analyzes a bitmap to determine if it's primarily dark.
 * Only considers non-transparent pixels and calculates average luminance.
 * Returns true if the logo is dark (should use white shadow).
 */
fun isImagePrimarilyDark(bitmap: Bitmap): Boolean {
    val width = bitmap.width
    val height = bitmap.height

    // Sample pixels for performance (every 4th pixel)
    val sampleStep = 4
    var totalLuminance = 0.0
    var pixelCount = 0

    for (y in 0 until height step sampleStep) {
        for (x in 0 until width step sampleStep) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)

            // Only consider non-transparent pixels
            if (alpha > 128) {
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)

                // Calculate luminance using standard formula
                val luminance = 0.299 * r + 0.587 * g + 0.114 * b
                totalLuminance += luminance
                pixelCount++
            }
        }
    }

    if (pixelCount == 0) return false

    val averageLuminance = totalLuminance / pixelCount
    // Consider dark if average luminance is below 100 (out of 255)
    // This is more generous to catch dark logos with some lighter accents
    return averageLuminance < 100
}
