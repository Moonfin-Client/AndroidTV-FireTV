package org.jellyfin.androidtv.util.coil

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import timber.log.Timber

class SubsetTransformation(
	private val x: Int,
	private val y: Int,
	private val width: Int,
	private val height: Int,
) : Transformation() {
	override val cacheKey: String = "$x,$y,$width,$height"

	override suspend fun transform(
		input: Bitmap,
		size: Size,
	): Bitmap {
		val clampedX = x.coerceIn(0, input.width - 1)
		val clampedY = y.coerceIn(0, input.height - 1)
		val clampedWidth = width.coerceAtMost(input.width - clampedX)
		val clampedHeight = height.coerceAtMost(input.height - clampedY)

		if (clampedWidth <= 0 || clampedHeight <= 0) {
			Timber.w("SubsetTransformation: invalid crop region after clamping (input=${input.width}x${input.height}, requested=[$x,$y,$width,$height])")
			return input
		}

		if (clampedX != x || clampedY != y || clampedWidth != width || clampedHeight != height) {
			Timber.d("SubsetTransformation: clamped crop from [$x,$y,$width,$height] to [$clampedX,$clampedY,$clampedWidth,$clampedHeight] for ${input.width}x${input.height} bitmap")
		}

		return Bitmap.createBitmap(input, clampedX, clampedY, clampedWidth, clampedHeight)
	}
}
