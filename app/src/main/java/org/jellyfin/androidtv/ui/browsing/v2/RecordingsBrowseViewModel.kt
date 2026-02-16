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
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDateTime

data class RecordingsBrowseUiState(
	val isLoading: Boolean = true,
	val recentRecordings: List<BaseItemDto> = emptyList(),
	val seriesRecordings: List<BaseItemDto> = emptyList(),
	val movieRecordings: List<BaseItemDto> = emptyList(),
	val sportsRecordings: List<BaseItemDto> = emptyList(),
	val kidsRecordings: List<BaseItemDto> = emptyList(),
	val scheduledNext24h: List<BaseItemDto> = emptyList(),
	val focusedItem: BaseItemDto? = null,
)

class RecordingsBrowseViewModel(
	val api: ApiClient,
) : ViewModel() {

	private val _uiState = MutableStateFlow(RecordingsBrowseUiState())
	val uiState: StateFlow<RecordingsBrowseUiState> = _uiState.asStateFlow()

	fun initialize() {
		_uiState.value = RecordingsBrowseUiState(isLoading = true)
		loadAllRows()
	}

	fun setFocusedItem(item: BaseItemDto) {
		_uiState.update { it.copy(focusedItem = item) }
	}

	private fun loadAllRows() {
		viewModelScope.launch {
			try {
				val recentJob = launch { loadRecentRecordings() }
				val seriesJob = launch { loadSeriesRecordings() }
				val moviesJob = launch { loadMovieRecordings() }
				val sportsJob = launch { loadSportsRecordings() }
				val kidsJob = launch { loadKidsRecordings() }
				val timersJob = launch { loadScheduledTimers() }

				recentJob.join()
				seriesJob.join()
				moviesJob.join()
				sportsJob.join()
				kidsJob.join()
				timersJob.join()

				_uiState.update { it.copy(isLoading = false) }
			} catch (err: Exception) {
				Timber.e(err, "Failed to load recordings rows")
				_uiState.update { it.copy(isLoading = false) }
			}
		}
	}

	private suspend fun loadRecentRecordings() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 40,
				).content
			}
			_uiState.update { it.copy(recentRecordings = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load recent recordings")
		}
	}

	private suspend fun loadSeriesRecordings() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 60,
					isSeries = true,
				).content
			}
			_uiState.update { it.copy(seriesRecordings = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load series recordings")
		}
	}

	private suspend fun loadMovieRecordings() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 60,
					isMovie = true,
				).content
			}
			_uiState.update { it.copy(movieRecordings = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load movie recordings")
		}
	}

	private suspend fun loadSportsRecordings() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 60,
					isSports = true,
				).content
			}
			_uiState.update { it.copy(sportsRecordings = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load sports recordings")
		}
	}

	private suspend fun loadKidsRecordings() {
		try {
			val response = withContext(Dispatchers.IO) {
				api.liveTvApi.getRecordings(
					fields = ItemRepository.itemFields,
					enableImages = true,
					limit = 60,
					isKids = true,
				).content
			}
			_uiState.update { it.copy(kidsRecordings = response.items) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load kids recordings")
		}
	}

	private suspend fun loadScheduledTimers() {
		try {
			val timers = withContext(Dispatchers.IO) {
				api.liveTvApi.getTimers().content
			}

			val next24 = LocalDateTime.now().plusDays(1)
			val nearTimers = timers.items
				.filter { it.startDate?.isBefore(next24) == true }
				.map { timer ->
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
					programInfo.copy(locationType = LocationType.VIRTUAL)
				}

			_uiState.update { it.copy(scheduledNext24h = nearTimers) }
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to load scheduled timers")
		}
	}
}
