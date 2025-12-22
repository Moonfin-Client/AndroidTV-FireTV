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
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.AggregatedItemBaseRowItem
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Home row that displays Next Up items aggregated from all logged-in servers.
 * Items are sorted by premiere date across all servers.
 */
class HomeFragmentAggregatedNextUpRow(
	private val limit: Int = 50,
) : HomeFragmentRow, KoinComponent {
	private val multiServerRepository by inject<MultiServerRepository>()
	private val userPreferences by inject<UserPreferences>()

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val header = HeaderItem(context.getString(R.string.lbl_next_up))
		val adapter = MutableObjectAdapter<BaseRowItem>(cardPresenter)
		val row = ListRow(header, adapter)

		// Add row immediately (will be populated async)
		rowsAdapter.add(row)

		// Load items asynchronously
		val lifecycleOwner = ProcessLifecycleOwner.get()
		lifecycleOwner.lifecycleScope.launch {
			try {
				val items = withContext(Dispatchers.IO) {
					multiServerRepository.getAggregatedNextUpItems(limit)
				}

				Timber.d("HomeFragmentAggregatedNextUpRow: Loaded ${items.size} next up items from multiple servers")

				if (items.isEmpty()) {
					// Remove row if no items
					rowsAdapter.remove(row)
					return@launch
				}

				// Populate adapter with items
				val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]
				items.forEach { aggItem ->
					adapter.add(AggregatedItemBaseRowItem(
						aggregatedItem = aggItem,
						preferParentThumb = preferParentThumb,
						staticHeight = true
					))
				}
			} catch (e: Exception) {
				Timber.e(e, "HomeFragmentAggregatedNextUpRow: Error loading next up items")
				rowsAdapter.remove(row)
			}
		}
	}
}
