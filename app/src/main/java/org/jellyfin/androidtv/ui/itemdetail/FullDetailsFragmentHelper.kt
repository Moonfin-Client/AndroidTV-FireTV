package org.jellyfin.androidtv.ui.itemdetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.PrePlaybackTrackSelector
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.apiclient.getSeriesOverview
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.androidtv.util.sdk.TrailerUtils.getExternalTrailerIntent
import org.jellyfin.androidtv.util.sdk.compat.canResume
import org.jellyfin.androidtv.util.sdk.compat.copyWithServerId
import org.jellyfin.androidtv.util.sdk.compat.copyWithUserData
import org.jellyfin.androidtv.util.showIfNotEmpty
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helper function to get the correct API client for a fragment.
 * This ensures we use the current session's userId, which is critical for
 * multi-user scenarios on the same server.
 */
private fun FullDetailsFragment.getCorrectApiClient(): ApiClient {
	val api by inject<ApiClient>()
	val apiClientFactory by inject<org.jellyfin.androidtv.util.sdk.ApiClientFactory>()
	val sessionRepository by inject<org.jellyfin.androidtv.auth.repository.SessionRepository>()
	
	// Check if a specific serverId was provided (for multi-server items)
	val serverIdString: String? = arguments?.getString("ServerId")
	var serverId = Utils.uuidOrNull(serverIdString)
	
	// Get current session for fallback and to get the current userId
	val currentSession = sessionRepository.currentSession.value
	
	// If no serverId was provided, use the current session's serverId
	if (serverId == null) {
		serverId = currentSession?.serverId
	}
	
	// Get the userId - critical for multi-user scenarios on the same server
	val userId = currentSession?.userId
	
	return if (serverId != null && userId != null) {
		// Use API client for the specific server AND user
		apiClientFactory.getApiClient(serverId, userId) ?: run {
			Timber.w("Failed to create API client for server $serverId user $userId, using current session")
			api
		}
	} else if (serverId != null) {
		// Fallback to server-only (picks first user - not ideal but better than nothing)
		apiClientFactory.getApiClientForServer(serverId) ?: run {
			Timber.w("Failed to create API client for server $serverId, using current session")
			api
		}
	} else {
		// Fallback to injected API client
		api
	}
}

fun FullDetailsFragment.deleteItem(
	api: ApiClient,
	item: BaseItemDto,
	dataRefreshService: DataRefreshService,
	navigationRepository: NavigationRepository,
) = lifecycleScope.launch {
	Timber.i("Deleting item ${item.name} (id=${item.id})")

	try {
		withContext(Dispatchers.IO) {
			api.libraryApi.deleteItem(item.id)
		}
	} catch (error: ApiClientException) {
		Timber.e(error, "Failed to delete item ${item.name} (id=${item.id})")
		Toast.makeText(
			context,
			getString(R.string.item_deletion_failed, item.name),
			Toast.LENGTH_LONG
		).show()
		return@launch
	}

	dataRefreshService.lastDeletedItemId = item.id

	if (navigationRepository.canGoBack) navigationRepository.goBack()
	else navigationRepository.navigate(Destinations.home)

	Toast.makeText(context, getString(R.string.item_deleted, item.name), Toast.LENGTH_LONG).show()
}

fun FullDetailsFragment.showDetailsMenu(
	view: View,
	baseItemDto: BaseItemDto,
) = popupMenu(requireContext(), view) {
	// for each button check if it exists (not-null) and is invisible (overflow prevention)
	if (queueButton?.isVisible == false) {
		item(getString(R.string.lbl_add_to_queue)) { addItemToQueue() }
	}

	if (shuffleButton?.isVisible == false) {
		item(getString(R.string.lbl_shuffle_all)) { shufflePlay() }
	}

	if (trailerButton?.isVisible == false) {
		item(getString(R.string.lbl_play_trailers)) { playTrailers() }
	}

	if (favButton?.isVisible == false) {
		val favoriteStringRes = when (baseItemDto.userData?.isFavorite) {
			true -> R.string.lbl_remove_favorite
			else -> R.string.lbl_add_favorite
		}

		item(getString(favoriteStringRes)) { toggleFavorite() }
	}

	if (goToSeriesButton?.isVisible == false) {
		item(getString(R.string.lbl_goto_series)) { gotoSeries() }
	}
}.showIfNotEmpty()

fun FullDetailsFragment.createFakeSeriesTimerBaseItemDto(timer: SeriesTimerInfoDto) = BaseItemDto(
	id = requireNotNull(timer.id).toUUID(),
	type = BaseItemKind.FOLDER,
	mediaType = MediaType.UNKNOWN,
	seriesTimerId = timer.id,
	name = timer.name,
	overview = timer.getSeriesOverview(requireContext()),
)

fun FullDetailsFragment.toggleFavorite() {
	val itemMutationRepository by inject<ItemMutationRepository>()
	val dataRefreshService by inject<DataRefreshService>()

	lifecycleScope.launch {
		val userData = itemMutationRepository.setFavorite(
			item = mBaseItem.id,
			favorite = !(mBaseItem.userData?.isFavorite ?: false)
		)
		mBaseItem = mBaseItem.copyWithUserData(userData)
		favButton.isActivated = userData.isFavorite
		dataRefreshService.lastFavoriteUpdate = Instant.now()
	}
}

fun FullDetailsFragment.togglePlayed() {
	val itemMutationRepository by inject<ItemMutationRepository>()
	val dataRefreshService by inject<DataRefreshService>()

	lifecycleScope.launch {
		val userData = itemMutationRepository.setPlayed(
			item = mBaseItem.id,
			played = !(mBaseItem.userData?.played ?: false)
		)
		mBaseItem = mBaseItem.copyWithUserData(userData)
		mWatchedToggleButton.isActivated = userData.played

		// Adjust resume
		mResumeButton?.apply {
			isVisible = mBaseItem.canResume
		}

		// Force lists to re-fetch
		dataRefreshService.lastPlayback = Instant.now()
		when (mBaseItem.type) {
			BaseItemKind.MOVIE -> dataRefreshService.lastMoviePlayback = Instant.now()
			BaseItemKind.EPISODE -> dataRefreshService.lastTvPlayback = Instant.now()
			else -> Unit
		}

		showMoreButtonIfNeeded()
	}
}

fun FullDetailsFragment.playTrailers() {
	val localTrailerCount = mBaseItem.localTrailerCount ?: 0

	// External trailer
	if (localTrailerCount < 1) try {
		val intent = getExternalTrailerIntent(requireContext(), mBaseItem)
		if (intent != null) {
			// Show app chooser to allow user to select their preferred app (e.g., SmartTube instead of YouTube)
			val chooser = Intent.createChooser(intent, getString(R.string.lbl_play_trailers))
			startActivity(chooser)
		}
	} catch (exception: ActivityNotFoundException) {
		Timber.w(exception, "Unable to open external trailer")
		Toast.makeText(
			requireContext(),
			getString(R.string.no_player_message),
			Toast.LENGTH_LONG
		).show()
	} else lifecycleScope.launch {
		val apiToUse = getCorrectApiClient()

		try {
			val trailers = withContext(Dispatchers.IO) {
				apiToUse.userLibraryApi.getLocalTrailers(mBaseItem.id).content
			}
			play(trailers, 0, false)
		} catch (exception: ApiClientException) {
			Timber.e(exception, "Error retrieving trailers for playback")
			Toast.makeText(
				requireContext(),
				getString(R.string.msg_video_playback_error),
				Toast.LENGTH_LONG
			).show()
		}
	}
}

fun FullDetailsFragment.getItem(id: UUID, callback: (item: BaseItemDto?) -> Unit) {
	val sessionRepository by inject<org.jellyfin.androidtv.auth.repository.SessionRepository>()
	val apiToUse = getCorrectApiClient()
	
	// Get current serverId for annotating response
	val serverIdString: String? = arguments?.getString("ServerId")
	val serverId = Utils.uuidOrNull(serverIdString) ?: sessionRepository.currentSession.value?.serverId

	lifecycleScope.launch {
		var response = try {
			withContext(Dispatchers.IO) {
				apiToUse.userLibraryApi.getItem(id).content
			}
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to get item $id")
			null
		}

		// If we fetched from a specific server, annotate the item with that serverId
		// so image loaders and other components know which server to use
		if (response != null && serverId != null) {
			response = response.copyWithServerId(serverId.toString())
		}

		callback(response)
	}
}

fun FullDetailsFragment.populatePreviousButton() {
	if (mBaseItem.type != BaseItemKind.EPISODE) return

	val apiToUse = getCorrectApiClient()

	lifecycleScope.launch {
		val siblings = withContext(Dispatchers.IO) {
			apiToUse.tvShowsApi.getEpisodes(
				seriesId = requireNotNull(mBaseItem.seriesId),
				adjacentTo = mBaseItem.id,
			).content
		}

		val previousItem = siblings.items
			.filterNot { it.id == mBaseItem.id }
			.firstOrNull()
			?.id

		mPrevItemId = previousItem
		// Previous button is now in Other Options menu, so keep it hidden from main row

		showMoreButtonIfNeeded()
	}
}

fun FullDetailsFragment.getNextUpEpisode(callback: (BaseItemDto?) -> Unit) {
	lifecycleScope.launch {
		val nextUpEpisode = getNextUpEpisode()
		callback(nextUpEpisode)
	}
}

suspend fun FullDetailsFragment.getNextUpEpisode(): BaseItemDto? {
	val apiToUse = getCorrectApiClient()

	try {
		val episodes = withContext(Dispatchers.IO) {
			apiToUse.tvShowsApi.getNextUp(
				seriesId = mBaseItem.seriesId ?: mBaseItem.id,
				fields = ItemRepository.itemFields,
				limit = 1,
			).content
		}
		return episodes.items.firstOrNull()
	} catch (err: ApiClientException) {
		Timber.w(err, "Failed to get next up items")
		return null
	}
}

fun FullDetailsFragment.resumePlayback(v: View) {
	if (mBaseItem.type != BaseItemKind.SERIES) {
		val pos = (mBaseItem.userData?.playbackPositionTicks?.ticks
			?: Duration.ZERO) - resumePreroll.milliseconds
		play(mBaseItem, pos.inWholeMilliseconds.toInt(), false)
		return
	}

	lifecycleScope.launch {
		val nextUpEpisode = getNextUpEpisode()
		if (nextUpEpisode == null) {
			Toast.makeText(
				requireContext(),
				getString(R.string.msg_video_playback_error),
				Toast.LENGTH_LONG
			).show()
		} else if (nextUpEpisode.userData?.playbackPositionTicks == 0L) {
			play(nextUpEpisode, 0, false)
		} else {
			showResumeMenu(v, nextUpEpisode)
		}
	}
}

fun FullDetailsFragment.showResumeMenu(
	view: View,
	nextUpEpisode: BaseItemDto
) = popupMenu(requireContext(), view) {
	val pos = (nextUpEpisode.userData?.playbackPositionTicks?.ticks
		?: Duration.ZERO) - resumePreroll.milliseconds
	item(
		getString(
			R.string.lbl_resume_from,
			TimeUtils.formatMillis(pos.inWholeMilliseconds)
		)
	) {
		play(nextUpEpisode, pos.inWholeMilliseconds.toInt(), false)
	}
	item(getString(R.string.lbl_from_beginning)) {
		play(nextUpEpisode, 0, false)
	}
}.showIfNotEmpty()

fun FullDetailsFragment.getLiveTvSeriesTimer(
	id: String,
	callback: (timer: SeriesTimerInfoDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getSeriesTimer(id).content
			}
		}.onSuccess { timer ->
			callback(timer)
		}
	}
}

fun FullDetailsFragment.getLiveTvProgram(
	id: UUID,
	callback: (program: BaseItemDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getProgram(id.toString()).content
			}
		}.onSuccess { program ->
			callback(program)
		}
	}
}

fun FullDetailsFragment.createLiveTvSeriesTimer(
	seriesTimer: SeriesTimerInfoDto,
	callback: () -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.createSeriesTimer(seriesTimer)
			}
		}.onSuccess {
			callback()
		}
	}
}

fun FullDetailsFragment.getLiveTvDefaultTimer(
	id: UUID,
	callback: (seriesTimer: SeriesTimerInfoDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getDefaultTimer(id.toString()).content
			}
		}.onSuccess { seriesTimer ->
			callback(seriesTimer)
		}
	}
}

fun FullDetailsFragment.cancelLiveTvSeriesTimer(
	timerId: String,
	callback: () -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.cancelTimer(timerId)
			}
		}.onSuccess {
			callback()
		}
	}
}

fun FullDetailsFragment.getLiveTvChannel(
	id: UUID,
	callback: (channel: BaseItemDto) -> Unit,
) {
	val api by inject<ApiClient>()

	lifecycleScope.launch {
		runCatching {
			withContext(Dispatchers.IO) {
				api.liveTvApi.getChannel(id).content
			}
		}.onSuccess { channel ->
			callback(channel)
		}
	}
}

fun FullDetailsFragment.showAudioTrackSelector(
	view: View,
	baseItemDto: BaseItemDto,
) {
	val trackSelector by inject<PrePlaybackTrackSelector>()
	val audioTracks = trackSelector.getAudioTracks(baseItemDto)
	
	if (audioTracks.isEmpty()) {
		Toast.makeText(requireContext(), "No audio tracks available", Toast.LENGTH_SHORT).show()
		return
	}
	
	val selectedIndex = trackSelector.getSelectedAudioTrack(baseItemDto.id.toString())
	
	popupMenu(requireContext(), view) {
		audioTracks.forEach { track ->
			val displayName = trackSelector.getAudioTrackDisplayName(track)
			val isSelected = track.index == selectedIndex
			val label = if (isSelected) "✓ $displayName" else displayName
			
			item(label) {
				trackSelector.setSelectedAudioTrack(baseItemDto.id.toString(), track.index)
				Toast.makeText(requireContext(), "Audio: $displayName", Toast.LENGTH_SHORT).show()
			}
		}
		
		// Add "Default" option
		val isDefault = selectedIndex == null
		item(if (isDefault) "✓ Default" else "Default") {
			trackSelector.setSelectedAudioTrack(baseItemDto.id.toString(), null)
			Toast.makeText(requireContext(), "Audio: Default", Toast.LENGTH_SHORT).show()
		}
	}.show()
}

fun FullDetailsFragment.showSubtitleTrackSelector(
	view: View,
	baseItemDto: BaseItemDto,
) {
	val trackSelector by inject<PrePlaybackTrackSelector>()
	val subtitleTracks = trackSelector.getSubtitleTracks(baseItemDto)
	
	val selectedIndex = trackSelector.getSelectedSubtitleTrack(baseItemDto.id.toString())
	
	popupMenu(requireContext(), view) {
		// Add "None" option
		val isNone = selectedIndex == -1
		item(if (isNone) "✓ None" else "None") {
			trackSelector.setSelectedSubtitleTrack(baseItemDto.id.toString(), -1)
			Toast.makeText(requireContext(), "Subtitles: None", Toast.LENGTH_SHORT).show()
		}
		
		subtitleTracks.forEach { track ->
			val displayName = trackSelector.getSubtitleTrackDisplayName(track)
			val isSelected = track.index == selectedIndex
			val label = if (isSelected) "✓ $displayName" else displayName
			
			item(label) {
				trackSelector.setSelectedSubtitleTrack(baseItemDto.id.toString(), track.index)
				Toast.makeText(requireContext(), "Subtitles: $displayName", Toast.LENGTH_SHORT).show()
			}
		}
		
		// Add "Default" option
		val isDefault = selectedIndex == null
		item(if (isDefault) "✓ Default" else "Default") {
			trackSelector.setSelectedSubtitleTrack(baseItemDto.id.toString(), null)
			Toast.makeText(requireContext(), "Subtitles: Default", Toast.LENGTH_SHORT).show()
		}
	}.show()
}
