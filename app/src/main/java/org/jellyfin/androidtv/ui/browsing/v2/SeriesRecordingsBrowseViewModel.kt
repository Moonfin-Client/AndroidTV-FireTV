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
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import timber.log.Timber

data class SeriesRecordingsBrowseUiState(
	val isLoading: Boolean = true,
	val seriesTimers: List<SeriesTimerInfoDto> = emptyList(),
	val focusedTimer: SeriesTimerInfoDto? = null,
)

class SeriesRecordingsBrowseViewModel(
	val api: ApiClient,
) : ViewModel() {

	private val _uiState = MutableStateFlow(SeriesRecordingsBrowseUiState())
	val uiState: StateFlow<SeriesRecordingsBrowseUiState> = _uiState.asStateFlow()

	fun initialize() {
		_uiState.value = SeriesRecordingsBrowseUiState(isLoading = true)
		loadSeriesTimers()
	}

	fun setFocusedTimer(timer: SeriesTimerInfoDto) {
		_uiState.update { it.copy(focusedTimer = timer) }
	}

	private fun loadSeriesTimers() {
		viewModelScope.launch {
			try {
				val response = withContext(Dispatchers.IO) {
					api.liveTvApi.getSeriesTimers().content
				}

				_uiState.update { it.copy(
					isLoading = false,
					seriesTimers = response.items,
				) }
			} catch (err: ApiClientException) {
				Timber.e(err, "Failed to load series timers")
				_uiState.update { it.copy(isLoading = false) }
			}
		}
	}
}
