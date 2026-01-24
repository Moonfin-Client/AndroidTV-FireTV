package org.jellyfin.androidtv.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * StackBlur algorithm for blurring bitmaps on all Android versions.
 * Based on Mario Klingemann's algorithm: http://www.quasimondo.com/2004/02/25/stackblur-2004/ https://github.com/Quasimondo/QuasimondoJS/blob/master/blur/StackBlur.js
 */
object BitmapBlur {
	
	fun blur(bitmap: Bitmap, radius: Int): Bitmap {
		if (radius <= 0) return bitmap
		
		val blurRadius = min(25, max(1, radius))
		
		val scaleFactor = 4
		val scaledWidth = max(1, bitmap.width / scaleFactor)
		val scaledHeight = max(1, bitmap.height / scaleFactor)
		
		val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
		
		val blurredBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
		if (scaledBitmap != bitmap) {
			scaledBitmap.recycle()
		}
		
		stackBlur(blurredBitmap, blurRadius)
		
		val result = Bitmap.createScaledBitmap(blurredBitmap, bitmap.width, bitmap.height, true)
		blurredBitmap.recycle()
		
		return result
	}
	
	private fun stackBlur(bitmap: Bitmap, radius: Int) {
		val w = bitmap.width
		val h = bitmap.height
		
		val pix = IntArray(w * h)
		bitmap.getPixels(pix, 0, w, 0, 0, w, h)
		
		val wm = w - 1
		val hm = h - 1
		val wh = w * h
		val div = radius + radius + 1
		
		val r = IntArray(wh)
		val g = IntArray(wh)
		val b = IntArray(wh)
		var rsum: Int
		var gsum: Int
		var bsum: Int
		var x: Int
		var y: Int
		var i: Int
		var p: Int
		var yp: Int
		var yi: Int
		var yw: Int
		val vmin = IntArray(max(w, h))
		
		var divsum = (div + 1) shr 1
		divsum *= divsum
		val dv = IntArray(256 * divsum)
		i = 0
		while (i < 256 * divsum) {
			dv[i] = i / divsum
			i++
		}
		
		yi = 0
		yw = 0
		
		val stack = Array(div) { IntArray(3) }
		var stackpointer: Int
		var stackstart: Int
		var sir: IntArray
		var rbs: Int
		val r1 = radius + 1
		var routsum: Int
		var goutsum: Int
		var boutsum: Int
		var rinsum: Int
		var ginsum: Int
		var binsum: Int
		
		y = 0
		while (y < h) {
			bsum = 0
			gsum = 0
			rsum = 0
			boutsum = 0
			goutsum = 0
			routsum = 0
			binsum = 0
			ginsum = 0
			rinsum = 0
			i = -radius
			while (i <= radius) {
				p = pix[yi + min(wm, max(i, 0))]
				sir = stack[i + radius]
				sir[0] = (p and 0xff0000) shr 16
				sir[1] = (p and 0x00ff00) shr 8
				sir[2] = p and 0x0000ff
				rbs = r1 - abs(i)
				rsum += sir[0] * rbs
				gsum += sir[1] * rbs
				bsum += sir[2] * rbs
				if (i > 0) {
					rinsum += sir[0]
					ginsum += sir[1]
					binsum += sir[2]
				} else {
					routsum += sir[0]
					goutsum += sir[1]
					boutsum += sir[2]
				}
				i++
			}
			stackpointer = radius
			
			x = 0
			while (x < w) {
				r[yi] = dv[rsum]
				g[yi] = dv[gsum]
				b[yi] = dv[bsum]
				
				rsum -= routsum
				gsum -= goutsum
				bsum -= boutsum
				
				stackstart = stackpointer - radius + div
				sir = stack[stackstart % div]
				
				routsum -= sir[0]
				goutsum -= sir[1]
				boutsum -= sir[2]
				
				if (y == 0) {
					vmin[x] = min(x + radius + 1, wm)
				}
				p = pix[yw + vmin[x]]
				
				sir[0] = (p and 0xff0000) shr 16
				sir[1] = (p and 0x00ff00) shr 8
				sir[2] = p and 0x0000ff
				
				rinsum += sir[0]
				ginsum += sir[1]
				binsum += sir[2]
				
				rsum += rinsum
				gsum += ginsum
				bsum += binsum
				
				stackpointer = (stackpointer + 1) % div
				sir = stack[stackpointer % div]
				
				routsum += sir[0]
				goutsum += sir[1]
				boutsum += sir[2]
				
				rinsum -= sir[0]
				ginsum -= sir[1]
				binsum -= sir[2]
				
				yi++
				x++
			}
			yw += w
			y++
		}
		
		x = 0
		while (x < w) {
			bsum = 0
			gsum = 0
			rsum = 0
			boutsum = 0
			goutsum = 0
			routsum = 0
			binsum = 0
			ginsum = 0
			rinsum = 0
			yp = -radius * w
			i = -radius
			while (i <= radius) {
				yi = max(0, yp) + x
				
				sir = stack[i + radius]
				
				sir[0] = r[yi]
				sir[1] = g[yi]
				sir[2] = b[yi]
				
				rbs = r1 - abs(i)
				
				rsum += r[yi] * rbs
				gsum += g[yi] * rbs
				bsum += b[yi] * rbs
				
				if (i > 0) {
					rinsum += sir[0]
					ginsum += sir[1]
					binsum += sir[2]
				} else {
					routsum += sir[0]
					goutsum += sir[1]
					boutsum += sir[2]
				}
				
				if (i < hm) {
					yp += w
				}
				i++
			}
			yi = x
			stackpointer = radius
			y = 0
			while (y < h) {
				val alpha = pix[yi] and 0xff000000.toInt()
				pix[yi] = alpha or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
				
				rsum -= routsum
				gsum -= goutsum
				bsum -= boutsum
				
				stackstart = stackpointer - radius + div
				sir = stack[stackstart % div]
				
				routsum -= sir[0]
				goutsum -= sir[1]
				boutsum -= sir[2]
				
				if (x == 0) {
					vmin[y] = min(y + r1, hm) * w
				}
				p = x + vmin[y]
				
				sir[0] = r[p]
				sir[1] = g[p]
				sir[2] = b[p]
				
				rinsum += sir[0]
				ginsum += sir[1]
				binsum += sir[2]
				
				rsum += rinsum
				gsum += ginsum
				bsum += binsum
				
				stackpointer = (stackpointer + 1) % div
				sir = stack[stackpointer]
				
				routsum += sir[0]
				goutsum += sir[1]
				boutsum += sir[2]
				
				rinsum -= sir[0]
				ginsum -= sir[1]
				binsum -= sir[2]
				
				yi += w
				y++
			}
			x++
		}
		
		bitmap.setPixels(pix, 0, w, 0, 0, w, h)
	}
}
