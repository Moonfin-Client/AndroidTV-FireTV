package org.jellyfin.androidtv.ui.jellyseerr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import org.jellyfin.androidtv.databinding.ItemJellyseerrContentBinding
import org.jellyfin.androidtv.data.service.jellyseerr.JellyseerrDiscoverItemDto

class MediaContentAdapter(
	private val onItemClicked: (JellyseerrDiscoverItemDto) -> Unit
) : ListAdapter<JellyseerrDiscoverItemDto, MediaContentAdapter.ViewHolder>(DiffCallback()) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemJellyseerrContentBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false
		)
		return ViewHolder(binding, onItemClicked)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	class ViewHolder(
		private val binding: ItemJellyseerrContentBinding,
		private val onItemClick: (JellyseerrDiscoverItemDto) -> Unit,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: JellyseerrDiscoverItemDto) {
			binding.apply {

				titleText.text = item.title ?: item.name ?: "Unknown"

				if (item.voteAverage != null) {
					ratingText.text = String.format("%.1f", item.voteAverage)
				}

				typeText.text = (item.mediaType ?: "unknown").uppercase()

				descriptionText.text = item.overview ?: ""

				root.setOnClickListener {
					onItemClick(item)
				}
			}
		}
	}

	private class DiffCallback : DiffUtil.ItemCallback<JellyseerrDiscoverItemDto>() {
		override fun areItemsTheSame(
			oldItem: JellyseerrDiscoverItemDto,
			newItem: JellyseerrDiscoverItemDto,
		) = oldItem.id == newItem.id

		override fun areContentsTheSame(
			oldItem: JellyseerrDiscoverItemDto,
			newItem: JellyseerrDiscoverItemDto,
		) = oldItem == newItem
	}
}
