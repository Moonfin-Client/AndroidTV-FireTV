package org.jellyfin.androidtv.ui.jellyseerr

import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto
import org.jellyfin.androidtv.util.dp

class DetailsOverviewRowPresenter : RowPresenter() {
	class ViewHolder(
		private val detailsRowView: DetailsRowView,
	) : RowPresenter.ViewHolder(detailsRowView) {
		private val binding get() = detailsRowView.binding

		fun setItem(row: DetailsOverviewRow) {
			val item = row.item
			
			binding.fdTitle.text = item.title ?: item.name ?: "Unknown"

			binding.fdMainInfoRow.removeAllViews()
			val context = binding.root.context
			
			val year = item.releaseDate?.take(4) ?: item.firstAirDate?.take(4)
			if (!year.isNullOrEmpty()) {
				addInfoText(year)
			}

			item.voteAverage?.let { rating ->
				addInfoText("★ ${String.format("%.1f", rating)}")
			}

			val mediaType = when (item.mediaType) {
				"movie" -> "Movie"
				"tv" -> "TV Series"
				else -> item.mediaType?.uppercase()
			}
			if (!mediaType.isNullOrEmpty()) {
				addInfoText(mediaType)
			}

			binding.fdTagline.isVisible = false

			binding.fdSummaryText.text = item.overview ?: ""

			populateGroupedMetadata(row)

			row.imageDrawable?.let { drawable ->
				binding.mainImage.setImageDrawable(drawable)
			}

			binding.fdButtonRow.removeAllViews()
			for (button in row.actions) {
				val parent = button.parent
				if (parent is ViewGroup) parent.removeView(button)
				binding.fdButtonRow.addView(button)
			}
		}

		private fun addInfoText(text: String) {
			val context = binding.root.context
			val textView = TextView(context).apply {
				this.text = text
				textSize = 12f
				setTextColor(context.getColor(R.color.white))
				alpha = 0.7f
			}
			
			if (binding.fdMainInfoRow.childCount > 0) {
				val divider = TextView(context).apply {
					this.text = "  •  "
					textSize = 12f
					setTextColor(context.getColor(R.color.white))
					alpha = 0.7f
				}
				binding.fdMainInfoRow.addView(divider)
			}
			
			binding.fdMainInfoRow.addView(textView)
		}

		private fun populateGroupedMetadata(row: DetailsOverviewRow) {
			val item = row.item
			val movieDetails = row.movieDetails
			val tvDetails = row.tvDetails
			
			// Genres
			val genres = when {
				movieDetails != null -> movieDetails.genres.map { it.name }
				tvDetails != null -> tvDetails.genres.map { it.name }
				else -> emptyList()
			}
			if (genres.isNotEmpty()) {
				binding.fdGenresGroup.isVisible = true
				binding.fdGenresContent.text = genres.joinToString(", ")
			} else {
				binding.fdGenresGroup.isVisible = false
			}

			// Director (only for movies)
			val director = movieDetails?.credits?.crew
				?.firstOrNull { it.job?.equals("Director", ignoreCase = true) == true }
				?.name
			if (!director.isNullOrBlank()) {
				binding.fdDirectorGroup.isVisible = true
				binding.fdDirectorContent.text = director
			} else {
				binding.fdDirectorGroup.isVisible = false
			}

			// Studio/Network
			val studio = when {
				tvDetails != null -> tvDetails.networks.firstOrNull()?.name
				else -> null
			}
			if (!studio.isNullOrBlank()) {
				binding.fdStudioGroup.isVisible = true
				binding.fdStudioContent.text = studio
			} else {
				binding.fdStudioGroup.isVisible = false
			}

			// Release Date
			val releaseDate = when {
				movieDetails != null -> movieDetails.releaseDate
				tvDetails != null -> tvDetails.firstAirDate
				else -> null
			}
			if (!releaseDate.isNullOrBlank()) {
				binding.fdReleaseDateGroup.isVisible = true
				// Format date from YYYY-MM-DD to readable format
				val formattedDate = try {
					val parts = releaseDate.split("-")
					if (parts.size == 3) {
						val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
						val month = months.getOrNull(parts[1].toInt() - 1) ?: parts[1]
						"$month ${parts[2]}, ${parts[0]}"
					} else releaseDate
				} catch (e: Exception) {
					releaseDate
				}
				binding.fdReleaseDateContent.text = formattedDate
			} else {
				binding.fdReleaseDateGroup.isVisible = false
			}

			// Runtime
			val runtime = movieDetails?.runtime
			if (runtime != null && runtime > 0) {
				binding.fdRuntimeGroup.isVisible = true
				val hours = runtime / 60
				val minutes = runtime % 60
				binding.fdRuntimeContent.text = when {
					hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
					hours > 0 -> "${hours}h"
					else -> "${minutes}m"
				}
			} else {
				binding.fdRuntimeGroup.isVisible = false
			}

			// Status (Content Status - Released, Returning Series, etc.)
			val contentStatus = when {
				movieDetails?.status != null -> movieDetails.status
				tvDetails?.status != null -> tvDetails.status
				else -> null
			}
			if (!contentStatus.isNullOrBlank()) {
				binding.fdStatusGroup.isVisible = true
				binding.fdStatusContent.text = contentStatus
			} else {
				binding.fdStatusGroup.isVisible = false
			}

			// Availability (Jellyseerr Request Status)
			val availabilityStatus = when (item.mediaInfo?.status) {
				1 -> "Unknown"
				2 -> "Pending"
				3 -> "Processing"
				4 -> "Partially Available"
				5 -> "Available"
				else -> null
			}
			if (!availabilityStatus.isNullOrBlank()) {
				binding.fdAvailabilityGroup.isVisible = true
				binding.fdAvailabilityContent.text = availabilityStatus
			} else {
				binding.fdAvailabilityGroup.isVisible = false
			}
		}
	}

	var viewHolder: ViewHolder? = null
		private set

	init {
		syncActivatePolicy = SYNC_ACTIVATED_CUSTOM
	}

	override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
		val view = DetailsRowView(parent.context)
		viewHolder = ViewHolder(view)
		return viewHolder!!
	}

	override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(viewHolder, item)
		if (item !is DetailsOverviewRow) return
		if (viewHolder !is ViewHolder) return

		viewHolder.setItem(item)
	}

	override fun onSelectLevelChanged(holder: RowPresenter.ViewHolder) = Unit
}
