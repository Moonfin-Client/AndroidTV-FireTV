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
import org.jellyfin.androidtv.data.repository.ParentalControlsRepository
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.itemhandling.AggregatedItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Home rows that display Recently Added items aggregated from all logged-in servers.
 * Creates one row per library across all servers, with format "Recently added in Library (ServerName)".
 * Supports pagination - loads 15 items initially, then more as the user scrolls.
 */
class HomeFragmentAggregatedLatestRow : HomeFragmentRow, KoinComponent {
	private val multiServerRepository by inject<MultiServerRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val parentalControlsRepository by inject<ParentalControlsRepository>()

	companion object {
		private const val MAX_ITEMS = 100
		private const val CHUNK_SIZE = 15
	}

	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val lifecycleOwner = ProcessLifecycleOwner.get()
		lifecycleOwner.lifecycleScope.launch {
			try {
				val libraries = withContext(Dispatchers.IO) {
					multiServerRepository.getAggregatedLibraries()
				}

				Timber.d("HomeFragmentAggregatedLatestRow: Got ${libraries.size} libraries from multiple servers")

				val preferParentThumb = userPreferences[UserPreferences.seriesThumbnailsEnabled]

				libraries.forEach { aggLib ->
					try {
						val items = withContext(Dispatchers.IO) {
							multiServerRepository.getAggregatedLatestItems(
								parentId = aggLib.library.id,
								limit = MAX_ITEMS,
								serverId = aggLib.server.id
							)
						}

						if (items.isEmpty()) return@forEach

						val imageType = userSettingPreferences.getHomeRowImageType(HomeSectionType.LATEST_MEDIA)
						val adapter = AggregatedItemRowAdapter(
							presenter = cardPresenter,
							allItems = items,
							parentalControlsRepository = parentalControlsRepository,
							userPreferences = userPreferences,
							chunkSize = CHUNK_SIZE,
							preferParentThumb = preferParentThumb,
							staticHeight = true,
							imageType = imageType
						)

						if (!adapter.hasItems()) return@forEach

						adapter.loadInitialItems()

						val header = HeaderItem(context.getString(R.string.lbl_latest_in, aggLib.displayName))
						rowsAdapter.add(ListRow(header, adapter))
						Timber.d("HomeFragmentAggregatedLatestRow: Added row for ${aggLib.displayName} with ${adapter.size()}/${adapter.getTotalItems()} items (paginated)")
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
