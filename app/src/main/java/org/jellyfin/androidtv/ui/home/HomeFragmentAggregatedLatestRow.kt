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
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.MultiServerRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.itemhandling.AggregatedItemBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Home rows that display Recently Added items aggregated from all logged-in servers.
 * Creates one row per library across all servers, with format "Recently added in Library (ServerName)".
 */
class HomeFragmentAggregatedLatestRow : HomeFragmentRow, KoinComponent {
	private val multiServerRepository by inject<MultiServerRepository>()
	private val userPreferences by inject<UserPreferences>()

	companion object {
		private const val ITEM_LIMIT = 20
	}

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val lifecycleOwner = ProcessLifecycleOwner.get()
		lifecycleOwner.lifecycleScope.launch {
			try {
				// Get all libraries from all servers
				val libraries = withContext(Dispatchers.IO) {
					multiServerRepository.getAggregatedLibraries()
				}

				Timber.d("HomeFragmentAggregatedLatestRow: Got ${libraries.size} libraries from multiple servers")

				// For each library, create a Recently Added row
				val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]

				libraries.forEach { aggLib ->
					try {
						val items = withContext(Dispatchers.IO) {
							multiServerRepository.getAggregatedLatestItems(
								parentId = aggLib.library.id,
								limit = ITEM_LIMIT,
								serverId = aggLib.server.id // Only query this specific server
							)
						}

						if (items.isEmpty()) return@forEach

						val header = HeaderItem(context.getString(R.string.lbl_latest_in, aggLib.displayName))
						val adapter = MutableObjectAdapter<BaseRowItem>(cardPresenter)

						items.forEach { aggItem ->
							Timber.d("HomeFragmentAggregatedLatestRow: Adding item ${aggItem.item.id} with serverId=${aggItem.item.serverId} from server ${aggItem.server.name} (baseUrl=${aggItem.apiClient.baseUrl})")
							adapter.add(
								AggregatedItemBaseRowItem(
									aggregatedItem = aggItem,
									preferParentThumb = preferParentThumb,
								)
							)
						}

						rowsAdapter.add(ListRow(header, adapter))
						Timber.d("HomeFragmentAggregatedLatestRow: Added row for ${aggLib.displayName} with ${items.size} items")
					} catch (e: Exception) {
						Timber.e(e, "HomeFragmentAggregatedLatestRow: Error loading latest items for ${aggLib.displayName}")
					}
				}
			} catch (e: Exception) {
				Timber.e(e, "HomeFragmentAggregatedLatestRow: Error loading aggregated libraries")
			}
		}
	}
}
