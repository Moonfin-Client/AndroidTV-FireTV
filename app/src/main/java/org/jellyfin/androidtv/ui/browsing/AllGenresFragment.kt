package org.jellyfin.androidtv.ui.browsing

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

/**
 * Fragment that displays all genres from all libraries.
 * When a genre is selected, shows all items with that genre across all libraries.
 */
class AllGenresFragment : EnhancedBrowseFragment() {
	private val apiClient by inject<ApiClient>()

	init {
		showViews = false
	}

	override fun setupQueries(rowLoader: RowLoader) {
		lifecycleScope.launch {
			// Get all genres across all libraries (no parentId filter)
			val genresResponse = withContext(Dispatchers.IO) {
				apiClient.genresApi.getGenres(
					sortBy = setOf(ItemSortBy.SORT_NAME),
				).content
			}

			val rows = mutableListOf<BrowseRowDef>()

			for (genre in genresResponse.items) {
				// Create a request for items with this genre from all libraries
				// Only include movies and series, exclude episodes
				val itemsRequest = GetItemsRequest(
					sortBy = setOf(ItemSortBy.SORT_NAME),
					genres = setOf(genre.name.orEmpty()),
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
					recursive = true,
					fields = ItemRepository.itemFields,
				)
				rows.add(BrowseRowDef(genre.name, itemsRequest, 40))
			}

			rowLoader.loadRows(rows)
		}
	}
}
