package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil3.load
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto

class MediaCardPresenter : Presenter() {
	private var cardWidth = 200
	private var cardHeight = 300

	inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {
		private val cardView = view as ImageCardView
		private var item: JellyseerrDiscoverItemDto? = null

		init {
			// Enable marquee scrolling for the title text (like Moonfin home rows)
			val titleView = findTitleTextView(cardView)
			titleView?.apply {
				ellipsize = TextUtils.TruncateAt.MARQUEE
				marqueeRepeatLimit = -1 // Repeat forever
				isSingleLine = true
				isSelected = true // Required for marquee to work
			}
		}

		fun setItem(item: JellyseerrDiscoverItemDto) {
			this.item = item
			cardView.titleText = item.title ?: item.name ?: "Unknown"
			
			// Show year, or media type if year is not available
			val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
			cardView.contentText = year ?: when (item.mediaType) {
				"movie" -> "Movie"
				"tv" -> "TV Series"
				else -> ""
			}
			
			// Load poster image from TMDB
			cardView.setMainImageDimensions(200, 300)
			if (item.posterPath != null) {
				val posterUrl = "https://image.tmdb.org/t/p/w342${item.posterPath}"
				cardView.mainImageView?.load(posterUrl)
			} else {
				cardView.mainImage = ContextCompat.getDrawable(cardView.context, R.drawable.ic_jellyseerr_logo)
			}
		}

		fun loadBackdropOnFocus(item: JellyseerrDiscoverItemDto) {
			// Load backdrop image when focused for background display
			if (item.backdropPath != null) {
				val backdropUrl = "https://image.tmdb.org/t/p/w1280${item.backdropPath}"
				// This will be loaded by the row presenter's focus handler
			}
		}

		private fun findTitleTextView(view: ViewGroup): TextView? {
			for (i in 0 until view.childCount) {
				val child = view.getChildAt(i)
				if (child is TextView && child.id == androidx.leanback.R.id.title_text) {
					return child
				} else if (child is ViewGroup) {
					val result = findTitleTextView(child)
					if (result != null) return result
				}
			}
			return null
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
		val cardView = ImageCardView(parent.context).apply {
			isFocusable = true
			isFocusableInTouchMode = true
			// Use CARD_TYPE_INFO_UNDER to show title/info text
			cardType = BaseCardView.CARD_TYPE_INFO_UNDER
			// Make the card background transparent
			setBackgroundColor(Color.TRANSPARENT)
		}
		cardView.setMainImageDimensions(cardWidth, cardHeight)
		
		// Make the info area background transparent (remove grey box)
		cardView.setInfoAreaBackgroundColor(Color.TRANSPARENT)
		
		return ViewHolder(cardView)
	}

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (item is JellyseerrDiscoverItemDto && viewHolder is ViewHolder) {
			viewHolder.setItem(item)
		}
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
		// Clean up if needed
	}
}
