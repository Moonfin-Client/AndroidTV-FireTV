package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber

/**
 * Home rows that display each collection (BoxSet) as its own row, with the collection's items as cards.
 * Creates one row per collection, showing the actual movies/shows inside each collection.
 */
class HomeFragmentCollectionsRow(
	private val api: ApiClient,
) : HomeFragmentRow {

	companion object {
		private const val MAX_COLLECTIONS = 20
		private const val MAX_ITEMS_PER_COLLECTION = 50
	}

	override fun addToRowsAdapter(
		context: Context,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) {
		val lifecycleOwner = ProcessLifecycleOwner.get()
		lifecycleOwner.lifecycleScope.launch {
			try {
				// Fetch all collections (BoxSets)
				val collections = withContext(Dispatchers.IO) {
					api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.BOX_SET),
						recursive = true,
						sortBy = setOf(ItemSortBy.SORT_NAME),
						sortOrder = setOf(SortOrder.ASCENDING),
						fields = ItemRepository.itemFields,
						imageTypeLimit = 1,
						limit = MAX_COLLECTIONS,
					).content.items
				}

				Timber.d("HomeFragmentCollectionsRow: Found ${collections.size} collections")

				// For each collection, fetch its items and create a row
				for (collection in collections) {
					try {
						val collectionItems = withContext(Dispatchers.IO) {
							api.itemsApi.getItems(
								parentId = collection.id,
								fields = ItemRepository.itemFields,
								imageTypeLimit = 1,
								limit = MAX_ITEMS_PER_COLLECTION,
							).content.items
						}

						if (collectionItems.isEmpty()) {
							Timber.d("HomeFragmentCollectionsRow: Collection '${collection.name}' is empty, skipping")
							continue
						}

						// Create adapter and add items
						val adapter = MutableObjectAdapter<Any>(cardPresenter)
						collectionItems.forEach { item ->
							adapter.add(BaseItemDtoBaseRowItem(item))
						}

						// Create row with collection name as header
						val header = HeaderItem(collection.name ?: "Collection")
						rowsAdapter.add(ListRow(header, adapter))

						Timber.d("HomeFragmentCollectionsRow: Added row for '${collection.name}' with ${collectionItems.size} items")
					} catch (e: Exception) {
						Timber.e(e, "HomeFragmentCollectionsRow: Error loading items for collection '${collection.name}'")
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "HomeFragmentCollectionsRow: Error loading collections")
			}
		}
	}
}
