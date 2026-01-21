package org.jellyfin.androidtv.ui.itemhandling

import android.os.Handler
import android.os.Looper
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.data.model.AggregatedItem
import org.jellyfin.androidtv.data.repository.ParentalControlsRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import timber.log.Timber

/**
 * Row adapter for aggregated items that supports pagination.
 * Items are loaded in chunks as the user scrolls through the row.
 */
class AggregatedItemRowAdapter(
	presenter: Presenter,
	private val allItems: List<AggregatedItem>,
	private val parentalControlsRepository: ParentalControlsRepository,
	private val userPreferences: UserPreferences,
	private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
	private val preferParentThumb: Boolean = false,
	private val staticHeight: Boolean = true,
	private val imageType: org.jellyfin.androidtv.constant.ImageType = org.jellyfin.androidtv.constant.ImageType.POSTER,
) : MutableObjectAdapter<BaseRowItem>(presenter) {

	private var itemsLoaded = 0
	private var fullyLoaded = false
	private var currentlyRetrieving = false
	private val handler = Handler(Looper.getMainLooper())

	private val filteredItems: List<AggregatedItem> by lazy {
		allItems.filter { aggItem ->
			!parentalControlsRepository.shouldFilterItem(aggItem.item)
		}
	}

	/**
	 * Load initial chunk of items
	 */
	fun loadInitialItems() {
		if (itemsLoaded > 0) return
		loadNextChunkInternal()
	}

	/**
	 * Check if more items need to be loaded based on current scroll position.
	 * Posts the loading to happen after the current layout pass to avoid
	 * IllegalStateException from modifying adapter during scroll.
	 */
	fun loadMoreItemsIfNeeded(pos: Int) {
		Timber.d("AggregatedItemRowAdapter: loadMoreItemsIfNeeded called pos=$pos, loaded=$itemsLoaded, total=${filteredItems.size}, fullyLoaded=$fullyLoaded")
		
		if (fullyLoaded || currentlyRetrieving) return

		val threshold = (itemsLoaded - (chunkSize / 1.7)).toInt()
		if (pos >= threshold) {
			Timber.d("AggregatedItemRowAdapter: Scheduling load more items (pos=$pos >= threshold=$threshold)")
			handler.post { loadNextChunkInternal() }
		}
	}

	private fun loadNextChunkInternal() {
		if (fullyLoaded || currentlyRetrieving) return
		currentlyRetrieving = true

		val startIndex = itemsLoaded
		val endIndex = minOf(startIndex + chunkSize, filteredItems.size)

		if (startIndex >= filteredItems.size) {
			fullyLoaded = true
			currentlyRetrieving = false
			return
		}

		val chunk = filteredItems.subList(startIndex, endIndex)
		chunk.forEach { aggItem ->
			add(AggregatedItemBaseRowItem(
				aggregatedItem = aggItem,
				preferParentThumb = preferParentThumb,
				staticHeight = staticHeight,
				preferSeriesPoster = imageType == org.jellyfin.androidtv.constant.ImageType.POSTER
			))
		}

		itemsLoaded = endIndex
		fullyLoaded = itemsLoaded >= filteredItems.size
		currentlyRetrieving = false

		Timber.d("AggregatedItemRowAdapter: Loaded chunk $startIndex-$endIndex, total loaded: $itemsLoaded/${filteredItems.size}, fullyLoaded: $fullyLoaded")
	}

	/**
	 * Get total number of items available (after filtering)
	 */
	fun getTotalItems(): Int = filteredItems.size

	/**
	 * Check if there are any items to display
	 */
	fun hasItems(): Boolean = filteredItems.isNotEmpty()

	companion object {
		const val DEFAULT_CHUNK_SIZE = 15
		const val MAX_ITEMS = 100
	}
}
