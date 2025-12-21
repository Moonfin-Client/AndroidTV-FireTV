package org.jellyfin.androidtv.ui.home

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.playback.MoonfinPlaylistManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
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

	fun loadRecentlyAdded(userViews: Collection<BaseItemDto>): HomeFragmentRow {
		return HomeFragmentLatestRow(userRepository, userViews)
	}

	fun loadResume(title: String, includeMediaTypes: Collection<MediaType>): HomeFragmentRow {
		val query = GetResumeItemsRequest(
			limit = ITEM_LIMIT_RESUME,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = includeMediaTypes,
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(title, query, 0, userPreferences[UserPreferences.seriesThumbnailsEnabled], true, arrayOf(ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback)))
	}

	fun loadResumeVideo(): HomeFragmentRow {
		return loadResume(context.getString(R.string.lbl_continue_watching), listOf(MediaType.VIDEO))
	}

	fun loadMergedContinueWatching(): HomeFragmentRow {
		val resumeQuery = GetResumeItemsRequest(
			limit = ITEM_LIMIT_RESUME,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			mediaTypes = listOf(MediaType.VIDEO),
			excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
		)

		val nextUpQuery = GetNextUpRequest(
			imageTypeLimit = 1,
			limit = ITEM_LIMIT_NEXT_UP,
			enableResumable = false,
			fields = ItemRepository.itemFields
		)

		val browseRowDef = BrowseRowDef(
			context.getString(R.string.lbl_continue_watching),
			resumeQuery,
			nextUpQuery,
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
			limit = ITEM_LIMIT_RECORDINGS
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_recordings), query))
	}

	fun loadNextUp(): HomeFragmentRow {
		val query = GetNextUpRequest(
			imageTypeLimit = 1,
			limit = ITEM_LIMIT_NEXT_UP,
			enableResumable = false,
			fields = ItemRepository.itemFields
		)

		val browseRowDef = BrowseRowDef(context.getString(R.string.lbl_next_up), query, arrayOf(ChangeTriggerType.TvPlayback))
		browseRowDef.setSectionType(HomeSectionType.NEXT_UP)
		return HomeFragmentBrowseRowDefRow(browseRowDef)
	}

	fun loadOnNow(): HomeFragmentRow {
		val query = GetRecommendedProgramsRequest(
			isAiring = true,
			fields = ItemRepository.itemFields,
			imageTypeLimit = 1,
			enableTotalRecordCount = false,
			limit = ITEM_LIMIT_ON_NOW
		)

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_on_now), query))
	}

	fun loadPlaylists(): HomeFragmentRow {
		// Get or create Moonfin playlist and return its items
		val playlistManager = MoonfinPlaylistManager(api)
		
		// Try to get the playlist ID synchronously (this will create it if needed)
		val playlistId = runBlocking {
			playlistManager.getOrCreateMoonfinPlaylist()
		}

		return if (playlistId != null) {
			HomeFragmentMoonfinPlaylistRow(context, playlistId, api)
		} else {
			// Fallback to empty row if playlist creation fails
			HomeFragmentBrowseRowDefRow(
				BrowseRowDef(
					context.getString(R.string.lbl_playlists),
					org.jellyfin.androidtv.ui.browsing.BrowsingUtils.createPlaylistsRequest(),
					60,
					false,
					true,
					arrayOf(ChangeTriggerType.LibraryUpdated),
					QueryType.AudioPlaylists
				)
			)
		}
	}

	companion object {
		// Maximum amount of items loaded for a row
		private const val ITEM_LIMIT_RESUME = 50
		private const val ITEM_LIMIT_RECORDINGS = 40
		private const val ITEM_LIMIT_NEXT_UP = 50
		private const val ITEM_LIMIT_ON_NOW = 20
	}
}
