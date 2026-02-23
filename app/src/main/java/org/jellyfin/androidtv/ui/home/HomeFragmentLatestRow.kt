package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest

class HomeFragmentLatestRow(
	private val userRepository: UserRepository,
	private val userViews: Collection<BaseItemDto>,
) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		// Get configuration (to find excluded items)
		val configuration = userRepository.currentUser.value?.configuration

		// Create a list of views to include
		val latestItemsExcludes = configuration?.latestItemsExcludes.orEmpty()
		userViews
			.filterNot { item -> item.collectionType in EXCLUDED_COLLECTION_TYPES || item.id in latestItemsExcludes }
			.map { item ->
				// Create query and add it to a new row
				val request = GetLatestMediaRequest(
					fields = ItemRepository.itemFields,
					imageTypeLimit = 1,
					parentId = item.id,
					groupItems = true,
					limit = ITEM_MAX_LIMIT,
				)

				val title = context.getString(R.string.lbl_latest_in, item.name)
				val browseRowDef = BrowseRowDef(title, request, ITEM_CHUNK_SIZE, arrayOf(ChangeTriggerType.LibraryUpdated))
				browseRowDef.setSectionType(HomeSectionType.LATEST_MEDIA)
				HomeFragmentBrowseRowDefRow(browseRowDef)
			}.forEach { row ->
				// Add row to adapter
				row.addToRowsAdapter(context, cardPresenter, rowsAdapter)
			}
	}

	companion object {
		// Collections excluded from latest row based on app support and common sense
		private val EXCLUDED_COLLECTION_TYPES = arrayOf(
			CollectionType.PLAYLISTS,
			CollectionType.LIVETV,
			CollectionType.BOXSETS,
			CollectionType.BOOKS,
		)

		// Initial items to load for a row (client-side pagination chunk size)
		private const val ITEM_CHUNK_SIZE = 15
		// Maximum total items to fetch from the server
		// (client-side pagination displays them in ITEM_CHUNK_SIZE chunks)
		private const val ITEM_MAX_LIMIT = 50
	}
}
