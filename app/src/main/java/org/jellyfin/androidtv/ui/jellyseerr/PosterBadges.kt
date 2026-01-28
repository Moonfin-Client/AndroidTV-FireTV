package org.jellyfin.androidtv.ui.jellyseerr

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto

/**
 * Utility for creating poster badges for Jellyseerr items.
 * Handles both media type badges (MOVIE/SERIES) and availability badges (Available/Processing).
 */
object PosterBadges {
	
	private const val STATUS_PROCESSING = 3
	private const val STATUS_PARTIALLY_AVAILABLE = 4
	private const val STATUS_AVAILABLE = 5
	
	/**
	 * Adds all applicable badges to a poster container.
	 * Media type badge appears top-left, availability badge appears top-right.
	 */
	fun addToContainer(context: Context, container: FrameLayout, item: JellyseerrDiscoverItemDto) {
		createMediaTypeBadge(context, item)?.let { container.addView(it) }
		createAvailabilityBadge(context, item)?.let { container.addView(it) }
	}
	
	private fun createMediaTypeBadge(context: Context, item: JellyseerrDiscoverItemDto): View? {
		val mediaType = item.mediaType ?: return null
		val density = context.resources.displayMetrics.density
		
		val (text, backgroundRes) = when (mediaType) {
			"movie" -> "MOVIE" to R.drawable.ic_movie_badge
			"tv" -> "SERIES" to R.drawable.ic_series_badge
			else -> return null
		}
		
		return TextView(context).apply {
			this.text = text
			setBackgroundResource(backgroundRes)
			setTextColor(Color.WHITE)
			textSize = 10f
			typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
			letterSpacing = 0.1f
			isAllCaps = true
			gravity = Gravity.CENTER
			
			val paddingH = (6 * density).toInt()
			val paddingV = (2 * density).toInt()
			setPadding(paddingH, paddingV, paddingH, paddingV)
			
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				gravity = Gravity.TOP or Gravity.START
				val margin = (6 * density).toInt()
				setMargins(margin, margin, 0, 0)
			}
			
			elevation = 4 * density
		}
	}
	
	private fun createAvailabilityBadge(context: Context, item: JellyseerrDiscoverItemDto): View? {
		val status = item.mediaInfo?.status ?: return null
		val density = context.resources.displayMetrics.density
		
		val drawableRes = when (status) {
			STATUS_AVAILABLE -> R.drawable.ic_available
			STATUS_PARTIALLY_AVAILABLE -> R.drawable.ic_partially_available
			STATUS_PROCESSING -> R.drawable.ic_indigo_spinner_animated
			else -> return null
		}
		
		val size = (20 * density).toInt()
		
		return ImageView(context).apply {
			setImageResource(drawableRes)
			
			layoutParams = FrameLayout.LayoutParams(size, size).apply {
				gravity = Gravity.TOP or Gravity.END
				val margin = (6 * density).toInt()
				setMargins(0, margin, margin, 0)
			}
			
			elevation = 4 * density
			
			if (status == STATUS_PROCESSING) {
				(drawable as? Animatable)?.start()
			}
		}
	}
}
