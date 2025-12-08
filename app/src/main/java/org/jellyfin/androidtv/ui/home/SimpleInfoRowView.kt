package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Lightweight View-based info row that displays metadata without Compose overhead.
 * Updates are simple property assignments with no recomposition.
 */
class SimpleInfoRowView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), KoinComponent {
	
	private val userPreferences by inject<UserPreferences>()
	private val items = mutableListOf<TextView>()
	
	init {
		orientation = HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		
		// Pre-create a pool of TextViews for reuse
		repeat(8) {
			val textView = TextView(context).apply {
				setTextColor(ContextCompat.getColor(context, android.R.color.white))
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
				typeface = Typeface.DEFAULT
				setShadowLayer(3f, 0f, 1f, ContextCompat.getColor(context, android.R.color.black))
				setPadding(0, 0, dpToPx(12), 0)
			}
			items.add(textView)
			addView(textView)
		}
	}
	
	fun setItem(item: BaseItemDto?) {
		// Hide all items first
		items.forEach { it.visibility = GONE; it.text = "" }
		
		if (item == null) return
		
		var index = 0
		
		// Rating (if not hidden)
		val ratingType = userPreferences[UserPreferences.defaultRatingType]
		if (ratingType != RatingType.RATING_HIDDEN) {
			item.communityRating?.let { rating ->
				setItemText(index++, "⭐ ${String.format("%.1f", rating)}")
			}
		}
		
		// Year or Date
		val dateText = when (item.type) {
			BaseItemKind.SERIES -> item.productionYear?.toString()
			BaseItemKind.EPISODE -> item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
			else -> item.productionYear?.toString() 
				?: item.premiereDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
		}
		dateText?.let { setItemText(index++, it) }
		
		// Runtime
		item.runTimeTicks?.ticks?.let { duration ->
			setItemText(index++, "⏱ ${TimeUtils.formatMillis(duration.inWholeMilliseconds)}")
		}
		
		// Official Rating (e.g., PG-13)
		item.officialRating?.let { rating ->
			if (rating.isNotBlank()) {
				setItemText(index++, rating)
			}
		}
		
		// Episode info
		if (item.type == BaseItemKind.EPISODE) {
			val seasonNum = item.parentIndexNumber
			val episodeNum = item.indexNumber
			if (seasonNum != null && episodeNum != null) {
				setItemText(index++, "S${seasonNum}E${episodeNum}")
			}
		}
		
		// Video resolution (if available)
		val width = item.width
		val height = item.height
		if (width != null && height != null) {
			val resolution = when {
				height >= 2000 -> "4K"
				height >= 1400 -> "1440p"
				height >= 1000 -> "1080p"
				height >= 700 -> "720p"
				else -> "${height}p"
			}
			setItemText(index++, resolution)
		}
		
		// HDR/Dolby Vision
		val videoStream = item.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }
		videoStream?.let { stream ->
			when {
				!stream.videoDoViTitle.isNullOrBlank() -> setItemText(index++, "DV")
				stream.videoRangeType?.let { it != org.jellyfin.sdk.model.api.VideoRangeType.SDR && it != org.jellyfin.sdk.model.api.VideoRangeType.UNKNOWN } == true -> 
					setItemText(index++, stream.videoRangeType!!.serialName.uppercase())
			}
		}
		
		// Audio codec (just the main one)
		val audioStream = item.mediaStreams?.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }
		audioStream?.let { stream ->
			when {
				stream.profile?.contains("Dolby Atmos", ignoreCase = true) == true -> setItemText(index++, "Atmos")
				stream.codec?.equals("AC3", ignoreCase = true) == true -> setItemText(index++, "AC3")
				stream.codec?.equals("EAC3", ignoreCase = true) == true -> setItemText(index++, "EAC3")
				stream.codec?.equals("DCA", ignoreCase = true) == true -> setItemText(index++, "DTS")
			}
		}
	}
	
	private fun setItemText(index: Int, text: String) {
		if (index < items.size) {
			items[index].apply {
				this.text = text
				visibility = VISIBLE
			}
		}
	}
	
	private fun dpToPx(dp: Int): Int {
		return (dp * context.resources.displayMetrics.density).toInt()
	}
}
