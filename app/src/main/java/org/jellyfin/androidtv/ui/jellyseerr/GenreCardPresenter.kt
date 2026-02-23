package org.jellyfin.androidtv.ui.jellyseerr

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.leanback.widget.Presenter
import coil3.load
import coil3.request.crossfade
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrGenreDto
import org.jellyfin.androidtv.preference.UserPreferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

class GenreCardPresenter : Presenter(), KoinComponent {
	private val userPreferences by inject<UserPreferences>()
	private val ASPECT_RATIO = 2f // landscape 2:1
	private val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w780"

	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val cardView = LayoutInflater.from(parent.context)
			.inflate(R.layout.jellyseerr_genre_card, parent, false) as CardView

		cardView.isFocusable = true
		cardView.isFocusableInTouchMode = true

		return ViewHolder(cardView)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		val genre = item as? JellyseerrGenreDto ?: return
		val cardView = viewHolder.view as CardView
		val context = cardView.context

		val imageView = cardView.findViewById<ImageView>(R.id.genre_image)
		val titleView = cardView.findViewById<TextView>(R.id.genre_title)
		val overlay = cardView.findViewById<ViewGroup>(R.id.genre_overlay)

		titleView.text = genre.name

		// Set card size to match poster height preference with landscape aspect ratio
		val posterHeight = userPreferences[UserPreferences.posterSize].height
		val density = context.resources.displayMetrics.density
		val heightPx = (posterHeight * density).toInt()
		val widthPx = (posterHeight * ASPECT_RATIO * density).toInt()
		val layoutParams = cardView.layoutParams
		layoutParams.width = widthPx
		layoutParams.height = heightPx
		cardView.layoutParams = layoutParams

		// Load backdrop image if available
		if (genre.backdrops.isNotEmpty()) {
			// Pick a random backdrop from the list for variety
			val randomBackdrop = genre.backdrops[Random.nextInt(genre.backdrops.size)]
			val backdropUrl = "$TMDB_IMAGE_BASE_URL$randomBackdrop"
			
			imageView.load(backdropUrl) {
				crossfade(true)
			}
		} else {
			imageView.setBackgroundColor(Color.parseColor("#1a1a1a"))
		}
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		val cardView = viewHolder.view as CardView
		val imageView = cardView.findViewById<ImageView>(R.id.genre_image)
		imageView.setImageDrawable(null)
	}
}
