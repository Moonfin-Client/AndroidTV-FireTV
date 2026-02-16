package org.jellyfin.androidtv.ui.itemdetail.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.VideoRangeType
import timber.log.Timber
import java.util.UUID

data class MediaBadge(
	val type: String,
	val label: String,
)

data class ItemDetailsUiState(
	val isLoading: Boolean = true,
	val item: BaseItemDto? = null,
	val seasons: List<BaseItemDto> = emptyList(),
	val episodes: List<BaseItemDto> = emptyList(),
	val tracks: List<BaseItemDto> = emptyList(),
	val similar: List<BaseItemDto> = emptyList(),
	val cast: List<BaseItemPerson> = emptyList(),
	val nextUp: List<BaseItemDto> = emptyList(),
	val collectionItems: List<BaseItemDto> = emptyList(),
	val directors: List<BaseItemPerson> = emptyList(),
	val writers: List<BaseItemPerson> = emptyList(),
	val badges: List<MediaBadge> = emptyList(),
)

class ItemDetailsViewModel(
	private val api: ApiClient,
	private val apiClientFactory: ApiClientFactory,
) : ViewModel() {

	private val _uiState = MutableStateFlow(ItemDetailsUiState())
	val uiState: StateFlow<ItemDetailsUiState> = _uiState.asStateFlow()

	var effectiveApi: ApiClient = api
		private set

	var serverId: UUID? = null
		private set

	fun loadItem(itemId: UUID, serverId: UUID? = null) {
		viewModelScope.launch {
			_uiState.value = ItemDetailsUiState(isLoading = true)

			if (serverId != null) {
				this@ItemDetailsViewModel.serverId = serverId
				effectiveApi = apiClientFactory.getApiClientForServer(serverId) ?: api
			}

			try {
				val item = withContext(Dispatchers.IO) {
					effectiveApi.userLibraryApi.getItem(
						itemId = itemId,
					).content
				}

				// If serverId wasn't passed explicitly, try to resolve from the item itself
				if (this@ItemDetailsViewModel.serverId == null && item.serverId != null) {
					val itemServerId = Utils.uuidOrNull(item.serverId)
					if (itemServerId != null) {
						this@ItemDetailsViewModel.serverId = itemServerId
						val resolvedApi = apiClientFactory.getApiClientForServer(itemServerId)
						if (resolvedApi != null) {
							effectiveApi = resolvedApi
						}
					}
				}

				val cast = item.people
					?.filter { it.type == PersonKind.ACTOR || it.type == PersonKind.GUEST_STAR }
					?.take(20) ?: emptyList()
				val directors = item.people?.filter { it.type == PersonKind.DIRECTOR } ?: emptyList()
				val writers = item.people?.filter { it.type == PersonKind.WRITER } ?: emptyList()
				val badges = getMediaBadges(item)

				_uiState.value = ItemDetailsUiState(
					isLoading = false,
					item = item,
					cast = cast,
					directors = directors,
					writers = writers,
					badges = badges,
				)

				loadAdditionalData(item)
			} catch (err: ApiClientException) {
				Timber.e(err, "Failed to load item $itemId")
				_uiState.value = ItemDetailsUiState(isLoading = false)
			}
		}
	}

	private fun loadAdditionalData(item: BaseItemDto) {
		viewModelScope.launch {
			when (item.type) {
				BaseItemKind.SERIES -> {
					loadSeasons(item.id)
					loadNextUp(item.id)
					loadSimilar(item.id)
				}

				BaseItemKind.SEASON -> {
					loadEpisodes(item.seriesId ?: return@launch, item.id)
				}

				BaseItemKind.EPISODE -> {
					val seasonId = item.seasonId ?: item.parentId
					if (item.seriesId != null && seasonId != null) {
						loadEpisodes(item.seriesId!!, seasonId)
					}
					loadSimilar(item.id)
				}

				BaseItemKind.BOX_SET -> {
					loadCollectionItems(item.id)
				}

				BaseItemKind.PERSON -> {
					loadFilmography(item.id)
				}

				BaseItemKind.MUSIC_ALBUM -> {
					loadTracks(item.id)
				}

				BaseItemKind.PLAYLIST -> {
					loadPlaylistItems(item.id)
				}

				else -> {
					loadSimilar(item.id)
				}
			}
		}
	}

	private suspend fun loadSeasons(seriesId: UUID) {
		try {
			val seasons = withContext(Dispatchers.IO) {
				effectiveApi.tvShowsApi.getSeasons(
					seriesId = seriesId,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(seasons = seasons.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load seasons")
		}
	}

	private suspend fun loadEpisodes(seriesId: UUID, seasonId: UUID) {
		try {
			val episodes = withContext(Dispatchers.IO) {
				effectiveApi.tvShowsApi.getEpisodes(
					seriesId = seriesId,
					seasonId = seasonId,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(episodes = episodes.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load episodes")
		}
	}

	private suspend fun loadNextUp(seriesId: UUID) {
		try {
			val nextUp = withContext(Dispatchers.IO) {
				effectiveApi.tvShowsApi.getNextUp(
					seriesId = seriesId,
					fields = ItemRepository.itemFields,
					limit = 1,
				).content
			}
			_uiState.value = _uiState.value.copy(nextUp = nextUp.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load next up")
		}
	}

	private suspend fun loadSimilar(itemId: UUID) {
		try {
			val similar = withContext(Dispatchers.IO) {
				effectiveApi.libraryApi.getSimilarItems(
					itemId = itemId,
					limit = 12,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(similar = similar.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load similar items")
		}
	}

	private suspend fun loadCollectionItems(collectionId: UUID) {
		try {
			val collectionItems = withContext(Dispatchers.IO) {
				effectiveApi.libraryApi.getSimilarItems(
					itemId = collectionId,
					limit = 50,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(collectionItems = collectionItems.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load collection items")
		}
	}

	private suspend fun loadFilmography(personId: UUID) {
		try {
			val filmography = withContext(Dispatchers.IO) {
				effectiveApi.itemsApi.getItems(
					personIds = setOf(personId),
					recursive = true,
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
					sortBy = setOf(ItemSortBy.SORT_NAME),
					fields = ItemRepository.itemFields,
					limit = 100,
				).content
			}
			_uiState.value = _uiState.value.copy(similar = filmography.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load filmography")
		}
	}

	private suspend fun loadTracks(containerId: UUID) {
		try {
			val tracks = withContext(Dispatchers.IO) {
				effectiveApi.itemsApi.getItems(
					parentId = containerId,
					fields = ItemRepository.itemFields,
					sortBy = setOf(ItemSortBy.SORT_NAME),
				).content
			}
			_uiState.value = _uiState.value.copy(tracks = tracks.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load tracks")
		}
	}

	private suspend fun loadPlaylistItems(playlistId: UUID) {
		try {
			val items = withContext(Dispatchers.IO) {
				effectiveApi.playlistsApi.getPlaylistItems(
					playlistId = playlistId,
					limit = 150,
					fields = ItemRepository.itemFields,
				).content
			}
			_uiState.value = _uiState.value.copy(tracks = items.items)
		} catch (err: ApiClientException) {
			Timber.w(err, "Failed to load playlist items")
		}
	}

	fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
		val item = _uiState.value.item ?: return
		val tracks = _uiState.value.tracks.toMutableList()
		if (fromIndex !in tracks.indices || toIndex !in tracks.indices) return

		val track = tracks[fromIndex]
		val playlistItemId = track.playlistItemId ?: return

		tracks.removeAt(fromIndex)
		tracks.add(toIndex, track)
		_uiState.value = _uiState.value.copy(tracks = tracks)

		Timber.d("Moving playlist item from index %d to %d", fromIndex, toIndex)

		viewModelScope.launch {
			try {
				withContext(Dispatchers.IO) {
					effectiveApi.playlistsApi.moveItem(
						playlistId = item.id.toString(),
						itemId = playlistItemId,
						newIndex = toIndex,
					)
				}
			} catch (err: Exception) {
				Timber.w(err, "Failed to move playlist item")
				val reverted = _uiState.value.tracks.toMutableList()
				val movedItem = reverted.removeAt(toIndex)
				reverted.add(fromIndex, movedItem)
				_uiState.value = _uiState.value.copy(tracks = reverted)
			}
		}
	}

	fun removeFromPlaylist(index: Int) {
		val item = _uiState.value.item ?: return
		val tracks = _uiState.value.tracks.toMutableList()
		if (index !in tracks.indices) return

		val track = tracks[index]
		val playlistItemId = track.playlistItemId ?: return

		tracks.removeAt(index)
		_uiState.value = _uiState.value.copy(tracks = tracks)

		viewModelScope.launch {
			try {
				withContext(Dispatchers.IO) {
					effectiveApi.playlistsApi.removeItemFromPlaylist(
						playlistId = item.id.toString(),
						entryIds = listOf(playlistItemId),
					)
				}
			} catch (err: Exception) {
				Timber.w(err, "Failed to remove item from playlist")
				val reverted = _uiState.value.tracks.toMutableList()
				reverted.add(index, track)
				_uiState.value = _uiState.value.copy(tracks = reverted)
			}
		}
	}

	fun toggleFavorite() {
		val item = _uiState.value.item ?: return
		val newFavorite = !(item.userData?.isFavorite ?: false)

		_uiState.value = _uiState.value.copy(
			item = item.copy(userData = item.userData?.copy(isFavorite = newFavorite))
		)

		viewModelScope.launch {
			try {
				withContext(Dispatchers.IO) {
					if (newFavorite) effectiveApi.userLibraryApi.markFavoriteItem(itemId = item.id)
					else effectiveApi.userLibraryApi.unmarkFavoriteItem(itemId = item.id)
				}
			} catch (err: Exception) {
				Timber.e(err, "Failed to toggle favorite")
				_uiState.value = _uiState.value.copy(
					item = _uiState.value.item?.copy(userData = _uiState.value.item?.userData?.copy(isFavorite = !newFavorite))
				)
			}
		}
	}

	fun toggleWatched() {
		val item = _uiState.value.item ?: return
		val newPlayed = !(item.userData?.played ?: false)

		_uiState.value = _uiState.value.copy(
			item = item.copy(
				userData = item.userData?.copy(
					played = newPlayed,
					playedPercentage = if (newPlayed) 100.0 else 0.0,
				)
			)
		)

		viewModelScope.launch {
			try {
				withContext(Dispatchers.IO) {
					if (newPlayed) effectiveApi.playStateApi.markPlayedItem(itemId = item.id)
					else effectiveApi.playStateApi.markUnplayedItem(itemId = item.id)
				}
			} catch (err: Exception) {
				Timber.e(err, "Failed to toggle watched")
				_uiState.value = _uiState.value.copy(
					item = _uiState.value.item?.copy(
						userData = _uiState.value.item?.userData?.copy(
							played = !newPlayed,
							playedPercentage = if (!newPlayed) 100.0 else 0.0,
						)
					)
				)
			}
		}
	}

	companion object {
		fun getMediaBadges(item: BaseItemDto): List<MediaBadge> {
			val badges = mutableListOf<MediaBadge>()
			val mediaSource = item.mediaSources?.firstOrNull() ?: return badges
			val streams = mediaSource.mediaStreams ?: return badges
			val video = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
			val audio = streams.firstOrNull { it.type == MediaStreamType.AUDIO }

			if (video != null) {
				val width = video.width ?: 0
				when {
					width >= 3800 -> badges.add(MediaBadge("badge4k", "4K"))
					width >= 1900 -> badges.add(MediaBadge("badgeHd", "1080p"))
					width >= 1260 -> badges.add(MediaBadge("badgeHd", "720p"))
				}

				val rangeType = video.videoRangeType
				if (rangeType == VideoRangeType.DOVI_WITH_HDR10 || rangeType == VideoRangeType.DOVI) {
					badges.add(MediaBadge("badgeDv", "DV"))
				}
				if (rangeType != VideoRangeType.SDR) {
					when {
						rangeType == VideoRangeType.HDR10_PLUS -> badges.add(MediaBadge("badgeHdr", "HDR10+"))
						rangeType == VideoRangeType.HDR10 || rangeType == VideoRangeType.DOVI_WITH_HDR10 ->
							badges.add(MediaBadge("badgeHdr", "HDR10"))
						rangeType != VideoRangeType.DOVI -> badges.add(MediaBadge("badgeHdr", "HDR"))
					}
				}

				val codec = video.codec?.uppercase()
				if (codec != null) {
					val label = when (codec) {
						"HEVC" -> "HEVC"
						"AV1" -> "AV1"
						"H264" -> "H.264"
						"VP9" -> "VP9"
						else -> codec
					}
					badges.add(MediaBadge("badgeCodec", label))
				}
			}

			val container = mediaSource.container?.uppercase()
			if (container != null) {
				badges.add(MediaBadge("badgeContainer", container))
			}

			if (audio != null) {
				val channels = audio.channels ?: 0
				when {
					channels > 6 -> badges.add(MediaBadge("badgeSurround", "${channels - 1}.1"))
					channels == 6 -> badges.add(MediaBadge("badgeSurround", "5.1"))
					channels == 2 -> badges.add(MediaBadge("badgeSurround", "Stereo"))
				}

				val audioCodec = audio.codec?.uppercase()
				if (audioCodec != null) {
					val label = when (audioCodec) {
						"AAC" -> "AAC"
						"AC3" -> "AC3"
						"EAC3" -> "EAC3"
						"FLAC" -> "FLAC"
						"DTS" -> "DTS"
						"TRUEHD" -> "TrueHD"
						else -> audioCodec
					}
					badges.add(MediaBadge("badgeAudioCodec", label))
				}
			}

			return badges
		}
	}
}
