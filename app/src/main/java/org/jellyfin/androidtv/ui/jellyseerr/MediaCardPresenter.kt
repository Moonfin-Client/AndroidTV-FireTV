package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.Presenter
import coil3.load
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto

class MediaCardPresenter(
	private val cardWidth: Int = 200,
	private val cardHeight: Int = 300
) : Presenter() {

	inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {
		private val card = view as LinearLayout
		private val imageContainer: FrameLayout = card.getChildAt(0) as FrameLayout
		private val posterImage: ImageView = imageContainer.getChildAt(0) as ImageView
		private val titleText: TextView = card.getChildAt(1) as TextView
		private val yearText: TextView = card.getChildAt(2) as TextView

		fun setItem(item: JellyseerrDiscoverItemDto) {
			titleText.text = item.title ?: item.name ?: "Unknown"
			
			val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
			yearText.text = year ?: when (item.mediaType) {
				"movie" -> "Movie"
				"tv" -> "TV Series"
				else -> ""
			}
			
			if (item.posterPath != null) {
				val posterUrl = "https://image.tmdb.org/t/p/w342${item.posterPath}"
				posterImage.load(posterUrl)
			} else {
				posterImage.setImageResource(org.jellyfin.androidtv.R.drawable.ic_jellyseerr_logo)
			}
			
			while (imageContainer.childCount > 1) {
				imageContainer.removeViewAt(imageContainer.childCount - 1)
			}
			
			PosterBadges.addToContainer(card.context, imageContainer, item)
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
		val context = parent.context
		val density = context.resources.displayMetrics.density
		
		val card = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = ViewGroup.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
			isFocusable = true
			isFocusableInTouchMode = true
			
			setOnFocusChangeListener { view, hasFocus ->
				if (hasFocus) {
					view.scaleX = 1.05f
					view.scaleY = 1.05f
				} else {
					view.scaleX = 1.0f
					view.scaleY = 1.0f
				}
			}
		}
		
		// Image container (FrameLayout for badge overlay)
		val imageContainer = FrameLayout(context).apply {
			layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight)
		}
		
		// Poster image
		val posterImage = ImageView(context).apply {
			layoutParams = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT
			)
			scaleType = ImageView.ScaleType.CENTER_CROP
			setBackgroundColor(Color.parseColor("#1F2937"))
		}
		imageContainer.addView(posterImage)
		card.addView(imageContainer)
		
		// Title text
		val titleText = TextView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
				topMargin = (4 * density).toInt()
			}
			setTextColor(Color.WHITE)
			textSize = 14f
			maxLines = 1
			ellipsize = TextUtils.TruncateAt.END
		}
		card.addView(titleText)
		
		// Year/type text
		val yearText = TextView(context).apply {
			layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			)
			setTextColor(Color.parseColor("#9CA3AF"))
			textSize = 12f
			maxLines = 1
		}
		card.addView(yearText)
		
		return ViewHolder(card)
	}

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (item is JellyseerrDiscoverItemDto && viewHolder is ViewHolder) {
			viewHolder.setItem(item)
		}
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
	}
}
