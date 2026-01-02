package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
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

class MediaCardPresenter(
	private val cardWidth: Int = 200,
	private val cardHeight: Int = 300
) : Presenter() {

	inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {
		private val cardView = view as ImageCardView

		init {
			val titleView = findTitleTextView(cardView)
			titleView?.apply {
				ellipsize = TextUtils.TruncateAt.MARQUEE
				marqueeRepeatLimit = -1
				isSingleLine = true
				isSelected = true
			}
			cardView.alpha = 1.0f
		}

		fun setItem(item: JellyseerrDiscoverItemDto) {
			cardView.titleText = item.title ?: item.name ?: "Unknown"
			
			val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
			cardView.contentText = year ?: when (item.mediaType) {
				"movie" -> "Movie"
				"tv" -> "TV Series"
				else -> ""
			}
			
			cardView.setMainImageDimensions(cardWidth, cardHeight)
			if (item.posterPath != null) {
				val posterUrl = "https://image.tmdb.org/t/p/w342${item.posterPath}"
				cardView.mainImageView?.load(posterUrl)
			} else {
				cardView.mainImage = ContextCompat.getDrawable(cardView.context, R.drawable.ic_jellyseerr_logo)
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
			cardType = BaseCardView.CARD_TYPE_INFO_UNDER
			setBackgroundColor(Color.TRANSPARENT)
		}
		cardView.setMainImageDimensions(cardWidth, cardHeight)
		
		cardView.setInfoAreaBackgroundColor(Color.TRANSPARENT)
		
		cardView.viewTreeObserver.addOnGlobalLayoutListener {
			removeBackgroundsRecursive(cardView)
		}
		
		return ViewHolder(cardView)
	}
	
	private fun removeBackgroundsRecursive(view: View) {
		view.background = null
		view.setBackgroundColor(Color.TRANSPARENT)
		
		if (view is ViewGroup) {
			for (i in 0 until view.childCount) {
				val child = view.getChildAt(i)
				if (child !is ImageView || child.id != androidx.leanback.R.id.main_image) {
					removeBackgroundsRecursive(child)
				}
			}
		}
	}

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (item is JellyseerrDiscoverItemDto && viewHolder is ViewHolder) {
			viewHolder.setItem(item)
		}
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
	}
}
