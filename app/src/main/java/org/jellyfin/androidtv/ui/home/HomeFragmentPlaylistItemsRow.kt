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
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber

/**
 * Home rows that display each user playlist as its own row, with the playlist's items as cards.
 * Creates one row per playlist, showing the actual movies/shows inside each playlist.
 */
class HomeFragmentPlaylistItemsRow(
	private val api: ApiClient,
) : HomeFragmentRow {

	companion object {
		private const val MAX_PLAYLISTS = 20
		private const val MAX_ITEMS_PER_PLAYLIST = 50
	}

	override fun addToRowsAdapter(
		context: Context,
		cardPresenter: CardPresenter,
		rowsAdapter: MutableObjectAdapter<Row>
	) {
		val lifecycleOwner = ProcessLifecycleOwner.get()
		lifecycleOwner.lifecycleScope.launch {
			try {
				// Fetch all user playlists
				val playlists = withContext(Dispatchers.IO) {
					api.itemsApi.getItems(
						includeItemTypes = setOf(BaseItemKind.PLAYLIST),
						recursive = true,
						sortBy = setOf(ItemSortBy.DATE_CREATED),
						sortOrder = setOf(SortOrder.DESCENDING),
						fields = ItemRepository.itemFields + ItemFields.CAN_DELETE,
						imageTypeLimit = 1,
						limit = MAX_PLAYLISTS,
					).content.items.filter { it.canDelete == true }
				}

				Timber.d("HomeFragmentPlaylistItemsRow: Found ${playlists.size} playlists")

				// For each playlist, fetch its items and create a row
				for (playlist in playlists) {
					try {
						val playlistItems = withContext(Dispatchers.IO) {
							api.itemsApi.getItems(
								parentId = playlist.id,
								fields = ItemRepository.itemFields,
								imageTypeLimit = 1,
								limit = MAX_ITEMS_PER_PLAYLIST,
							).content.items
						}

						if (playlistItems.isEmpty()) {
							Timber.d("HomeFragmentPlaylistItemsRow: Playlist '${playlist.name}' is empty, skipping")
							continue
						}

						// Create adapter and add items
						val adapter = MutableObjectAdapter<Any>(cardPresenter)
						playlistItems.forEach { item ->
							adapter.add(BaseItemDtoBaseRowItem(item))
						}

						// Create row with playlist name as header
						val header = HeaderItem(playlist.name ?: "Playlist")
						rowsAdapter.add(ListRow(header, adapter))

						Timber.d("HomeFragmentPlaylistItemsRow: Added row for '${playlist.name}' with ${playlistItems.size} items")
					} catch (e: Exception) {
						Timber.e(e, "HomeFragmentPlaylistItemsRow: Error loading items for playlist '${playlist.name}'")
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "HomeFragmentPlaylistItemsRow: Error loading playlists")
			}
		}
	}
}
