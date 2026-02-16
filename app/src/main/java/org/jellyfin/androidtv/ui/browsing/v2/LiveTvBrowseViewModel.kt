package org.jellyfin.androidtv.ui.browsing.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.TimerInfoDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDateTime

data class LiveTvBrowseUiState(
	val isLoading: Boolean = true,
	val libraryName: String = "",
	val onNow: List<BaseItemDto> = emptyList(),
	val comingUp: List<BaseItemDto> = emptyList(),
	val favoriteChannels: List<BaseItemDto> = emptyList(),
	val otherChannels: List<BaseItemDto> = emptyList(),
	val recentRecordings: List<BaseItemDto> = emptyList(),
	val scheduledNext24h: List<BaseItemDto> = emptyList(),
	val pastDay: List<BaseItemDto> = emptyList(),
	val pastWeek: List<BaseItemDto> = emptyList(),
	val focusedItem: BaseItemDto? = null,
	val canManageRecordings: Boolean = false,
)

class LiveTvBrowseViewModel(
	val api: ApiClient,
) : ViewModel() {

	private val _uiState = MutableStateFlow(LiveTvBrowseUiState())
	val uiState: StateFlow<LiveTvBrowseUiState> = _uiState.asStateFlow()

	fun initialize(libraryName: String, canManage: Boolean) {
		_uiState.value = LiveTvBrowseUiState(
			isLoading = true,
			libraryName = libraryName,
			canManageRecordings = canManage,
		)
		loadAllRows()
	}

	fun setFocusedItem(item: BaseItemDto) {
		_uiState.update { it.copy(focusedItem = item) }
	}

	private fun loadAllRows() {
		viewModelScope.launch {
			try {
				val onNowJob = launch { loadOnNow() }
				val comingUpJob = launch { loadComingUp() }
				val favChannelsJob = launch { loadFavoriteChannels() }
				val otherChannelsJob = launch { loadOtherChannels() }
				val recordingsJob = launch { loadRecordingsAndTimers() }

				onNowJob.join()
				comingUpJob.join()
				favChannelsJob.join()
				otherChannelsJob.join()
				recordingsJob.join()

				_uiState.update { it.copy(isLoading = false) }
			} catch (err: Exception) {
				Timber.e(err, "Failed to load live TV rows")
				_uiState.update { it.copy(isLoading = false) }
			}
		}
	}

	private suspend fun loadOnNow() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecommendedPrograms(
					isAiring = true,
					fields = ItemRepository.itemFields,
					imageTypeLimit = 1,
					enableTotalRecordCount = false,
					limit = 150,
				).content
			}
			_uiState.update { it.copy(onNow = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load on now")
		}
	}

	private suspend fun loadComingUp() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecommendedPrograms(
					isAiring = false,
					hasAired = false,
					fields = ItemRepository.itemFields,
					imageTypeLimit = 1,
					enableTotalRecordCount = false,
					limit = 150,
				).content
			}
			_uiState.update { it.copy(comingUp = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load coming up")
		}
	}

	private suspend fun loadFavoriteChannels() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getLiveTvChannels(
					isFavorite = true,
				).content
			}
			_uiState.update { it.copy(favoriteChannels = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load favorite channels")
		}
	}

	private suspend fun loadOtherChannels() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getLiveTvChannels(
					isFavorite = false,
				).content
			}
			_uiState.update { it.copy(otherChannels = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load other channels")
		}
	}

	private suspend fun loadRecordingsAndTimers() {
		try {
			val recordings = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 40,
				).content
			}

			val timers = withContext(Dispatchers.IO) {
				api.liveTvApi.getTimers().content
			}

			// Scheduled in next 24 hours
			val next24 = LocalDateTime.now().plusDays(1)
			val nearTimers = timers.items
				.filter { it.startDate?.isBefore(next24) == true }
				.map { getTimerProgramInfo(it) }

			// Recent recordings split into day/week
			val past24 = LocalDateTime.now().minusDays(1)
			val pastWeekDate = LocalDateTime.now().minusWeeks(1)

			val dayItems = mutableListOf<BaseItemDto>()
			val weekItems = mutableListOf<BaseItemDto>()

			for (item in recordings.items) {
				val created = item.dateCreated ?: continue
				if (created.isAfter(past24)) {
					dayItems.add(item)
				} else if (created.isAfter(pastWeekDate)) {
					weekItems.add(item)
				}
			}

			_uiState.update { it.copy(
				recentRecordings = recordings.items,
				scheduledNext24h = nearTimers,
				pastDay = dayItems,
				pastWeek = weekItems,
			) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load recordings and timers")
		}
	}

	private fun getTimerProgramInfo(timer: TimerInfoDto): BaseItemDto {
		val programInfo = timer.programInfo ?: BaseItemDto(
			id = requireNotNull(timer.id?.toUUIDOrNull()),
			channelName = timer.channelName,
			name = timer.name.orEmpty(),
			type = BaseItemKind.PROGRAM,
			timerId = timer.id,
			seriesTimerId = timer.seriesTimerId,
			startDate = timer.startDate,
			endDate = timer.endDate,
			mediaType = MediaType.UNKNOWN,
		)
		return programInfo.copy(
			locationType = LocationType.VIRTUAL,
		)
	}
}
