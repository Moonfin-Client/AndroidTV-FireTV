package org.jellyfin.androidtv.ui.browsing

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.android.ext.android.inject

/**
 * Fragment that displays all favorites from all libraries, divided by library.
 * When a library is selected, shows all favorited items within that library.
 */
class AllFavoritesFragment : EnhancedBrowseFragment() {
	private val userViewsRepository by inject<UserViewsRepository>()

	init {
		showViews = false
	}

	override fun setupQueries(rowLoader: RowLoader) {
		lifecycleScope.launch {
			// Get all user views/libraries
			userViewsRepository.views.collect { userViews ->
				val rows = mutableListOf<BrowseRowDef>()

				// For each library/user view, create a row for its favorites
				for (view in userViews) {
					if (view.id != null) {
						// Create a request for favorites in this library
						val itemsRequest = GetItemsRequest(
							parentId = view.id,
							sortBy = setOf(ItemSortBy.SORT_NAME),
							filters = setOf(ItemFilter.IS_FAVORITE),
							includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
							recursive = true,
							fields = ItemRepository.itemFields,
						)
						rows.add(BrowseRowDef(view.name ?: "Library", itemsRequest, 40))
					}
				}

				rowLoader.loadRows(rows)
			}
		}
	}
}

