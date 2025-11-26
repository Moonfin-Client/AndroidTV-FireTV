package org.jellyfin.androidtv.ui.jellyseerr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.databinding.ItemJellyseerrRequestBinding
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrRequestDto

class RequestsAdapter :
	ListAdapter<JellyseerrRequestDto, RequestsAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemJellyseerrRequestBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false
		)
		return ViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	class ViewHolder(
		private val binding: ItemJellyseerrRequestBinding,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: JellyseerrRequestDto) {
			binding.apply {
				// Poster
				val posterPath = item.media?.posterPath
				if (posterPath != null) {
					val posterUrl = "https://image.tmdb.org/t/p/w200$posterPath"
					posterImage.load(posterUrl)
				} else {
					posterImage.setImageResource(R.drawable.app_logo)
				}

				val displayTitle = item.media?.title ?: item.media?.name ?: "Unknown"
				titleText.text = displayTitle

				typeText.text = item.type.uppercase()

				statusText.text = when (item.status) {
					1 -> "⏳ Pending"
					2 -> "✓ Approved"
					3 -> "✗ Declined"
					4 -> "✓ Available"
					else -> "Unknown"
				}

				val statusColor = when (item.status) {
					1 -> R.color.grey_light
					2 -> R.color.white
					3 -> R.color.red
					4 -> R.color.white
					else -> R.color.grey_light
				}
				statusText.setTextColor(ContextCompat.getColor(root.context, statusColor))

				val requesterName = item.requestedBy?.username ?: "Unknown"
				requestedByText.text = "Requested by: $requesterName"

				val dateStr = item.createdAt?.substringBefore("T") ?: "Unknown"
				dateText.text = "Date: $dateStr"
			}
		}
	}

	private class DiffCallback : DiffUtil.ItemCallback<JellyseerrRequestDto>() {
		override fun areItemsTheSame(
			oldItem: JellyseerrRequestDto,
			newItem: JellyseerrRequestDto,
		) = oldItem.id == newItem.id

		override fun areContentsTheSame(
			oldItem: JellyseerrRequestDto,
			newItem: JellyseerrRequestDto,
		) = oldItem == newItem
	}
}
