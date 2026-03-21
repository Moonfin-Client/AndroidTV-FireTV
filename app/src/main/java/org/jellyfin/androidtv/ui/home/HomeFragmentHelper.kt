package org.jellyfin.androidtv.ui.home

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetRecommendedProgramsRequest
import org.jellyfin.sdk.model.api.request.GetRecordingsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeFragmentHelper(
	private val context: Context,
	private val userRepository: UserRepository,
) : KoinComponent {
	private val userPreferences by inject<UserPreferences>()
	private val api by inject<ApiClient>()
	private val serverRepository by inject<ServerRepository>()

	fun loadRecentlyAdded(userViews: Collection<BaseItemDto>): HomeFragmentRow {
		// Check if multi-server is enabled
		val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
		
		return if (enableMultiServer) {
			// Use aggregated row that shows items from all servers
			HomeFragmentAggregatedLatestRow()
		} else {
			// Use normal row for current server only
			HomeFragmentLatestRow(userRepository, userViews)
		}
	}

	fun loadRecentlyReleased(): HomeFragmentRow {
		// Query items sorted by premiere/release date (most recent first)
		val query = GetItemsRequest(
			fields = ItemRepository.itemFields,
			includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
			sortBy = setOf(ItemSortBy.PREMIERE_DATE),
			sortOrder = setOf(SortOrder.DESCENDING),
			recursive = true,
			imageTypeLimit = 1,
			enableTotalRecordCount = true,
		)

		val browseRowDef = BrowseRowDef(
			context.getString(R.string.home_section_recently_released),
			query,
			HOME_ROW_CHUNK_SIZE,
			false,
			true,
			arrayOf(ChangeTriggerType.LibraryUpdated)
		)
		browseRowDef.setSectionType(HomeSectionType.RECENTLY_RELEASED)
		return HomeFragmentBrowseRowDefRow(browseRowDef)
	}

	fun loadResume(title: String, includeMediaTypes: Collection<MediaType>): HomeFragmentRow {
		val query = GetResumeItemsRequest(
			limit = HOME_ROW_MAX_ITEMS,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = includeMediaTypes,
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)

		val browseRowDef = BrowseRowDef(title, query, HOME_ROW_CHUNK_SIZE, userPreferences[UserPreferences.seriesThumbnailsEnabled], true, arrayOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback))
		browseRowDef.setSectionType(HomeSectionType.RESUME)
		return HomeFragmentBrowseRowDefRow(browseRowDef)
	}

	fun loadResumeVideo(): HomeFragmentRow {
		// Check if multi-server is enabled
		val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
		
		return if (enableMultiServer) {
			// Use aggregated row that shows items from all servers
			HomeFragmentAggregatedResumeRow(HOME_ROW_MAX_ITEMS)
		} else {
			// Use normal row for current server only
			loadResume(context.getString(R.string.lbl_continue_watching), listOf(MediaType.VIDEO))
		}
	}

	fun loadMergedContinueWatching(): HomeFragmentRow {
		// Check if multi-server is enabled
		val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
		
		if (enableMultiServer) {
			return HomeFragmentAggregatedResumeRow(HOME_ROW_MAX_ITEMS, includeNextUp = true)
		}
		
		// Use normal merged row for current server only
		val resumeQuery = GetResumeItemsRequest(
			limit = HOME_ROW_MAX_ITEMS,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = listOf(MediaType.VIDEO),
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)

		val nextUpQuery = GetNextUpRequest(
			imageTypeLimit = 1,
			limit = HOME_ROW_MAX_ITEMS,
			enableResumable = false,
			fields = ItemRepository.itemFields
		)

		val browseRowDef = BrowseRowDef(
			context.getString(R.string.lbl_continue_watching),
			resumeQuery,
			nextUpQuery,
			HOME_ROW_CHUNK_SIZE,
			userPreferences[UserPreferences.seriesThumbnailsEnabled],
			true,
			arrayOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)
		)
		browseRowDef.setSectionType(HomeSectionType.RESUME)
		return HomeFragmentBrowseRowDefRow(browseRowDef)
	}

	fun loadResumeAudio(): HomeFragmentRow {
		return loadResume(context.getString(R.string.continue_listening), listOf(MediaType.AUDIO))
	}

	fun loadLatestLiveTvRecordings(): HomeFragmentRow {
		val query = GetRecordingsRequest(
			fields = ItemRepository.itemFields,
			enableImages = true,
			limit = HOME_ROW_MAX_ITEMS
		)

		val row = BrowseRowDef(context.getString(R.string.lbl_recordings), query, HOME_ROW_CHUNK_SIZE)
		row.setSectionType(HomeSectionType.ACTIVE_RECORDINGS)
		return HomeFragmentBrowseRowDefRow(row)
	}

	fun loadNextUp(): HomeFragmentRow {
		// Check if multi-server is enabled
		val enableMultiServer = userPreferences[UserPreferences.enableMultiServerLibraries]
		
		if (enableMultiServer) {
			// Use aggregated row that shows items from all servers
			return HomeFragmentAggregatedNextUpRow(HOME_ROW_MAX_ITEMS)
		}
		
		// Use normal row for current server only
		val query = GetNextUpRequest(
			imageTypeLimit = 1,
			limit = HOME_ROW_MAX_ITEMS,
			enableResumable = false,
			fields = ItemRepository.itemFields
		)

		val browseRowDef = BrowseRowDef(context.getString(R.string.lbl_next_up), query, HOME_ROW_CHUNK_SIZE, arrayOf(ChangeTriggerType.TvPlayback))
		browseRowDef.setSectionType(HomeSectionType.NEXT_UP)
		return HomeFragmentBrowseRowDefRow(browseRowDef)
	}

	fun loadOnNow(): HomeFragmentRow {
		val query = GetRecommendedProgramsRequest(
			isAiring = true,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			limit = HOME_ROW_MAX_ITEMS
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_on_now), query, HOME_ROW_CHUNK_SIZE))
	}

	fun loadPlaylists(): HomeFragmentRow {
		return HomeFragmentPlaylistsRow(api)
	}

	fun loadPlaylistItems(): HomeFragmentRow {
		return HomeFragmentPlaylistItemsRow(api)
	}

	fun loadCollections(): HomeFragmentRow {
		return HomeFragmentCollectionsRow(api)
	}

	companion object {
		// Initial items to load for a row (pagination chunk size)
		private const val HOME_ROW_CHUNK_SIZE = 15
		// Maximum total items that can be loaded for a row
		private const val HOME_ROW_MAX_ITEMS = 100
	}
}
