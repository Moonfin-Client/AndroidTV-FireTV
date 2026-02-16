package org.jellyfin.androidtv.ui.browsing.v2

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDate

data class ScheduleGroup(
	val dateLabel: String,
	val items: List<BaseItemDto>,
)

data class ScheduleBrowseUiState(
	val isLoading: Boolean = true,
	val scheduleGroups: List<ScheduleGroup> = emptyList(),
	val focusedItem: BaseItemDto? = null,
)

class ScheduleBrowseViewModel(
	val api: ApiClient,
) : ViewModel() {

	private val _uiState = MutableStateFlow(ScheduleBrowseUiState())
	val uiState: StateFlow<ScheduleBrowseUiState> = _uiState.asStateFlow()

	fun initialize(context: Context) {
		_uiState.value = ScheduleBrowseUiState(isLoading = true)
		loadSchedule(context)
	}

	fun setFocusedItem(item: BaseItemDto) {
		_uiState.update { it.copy(focusedItem = item) }
	}

	private fun loadSchedule(context: Context) {
		viewModelScope.launch {
			try {
				val timers = withContext(Dispatchers.IO) {
					api.liveTvApi.getTimers().content
				}

				val grouped: Map<LocalDate, List<BaseItemDto>> = timers.items
					.filter { it.startDate != null }
					.map { timer ->
						val programInfo = timer.programInfo ?: BaseItemDto(
							id = requireNotNull(timer.id?.toUUIDOrNull()),
							channelName = timer.channelName,
							name = timer.name.orEmpty(),
							type = BaseItemKind.PROGRAM,
							mediaType = MediaType.UNKNOWN,
							timerId = timer.id,
							seriesTimerId = timer.seriesTimerId,
							startDate = timer.startDate,
							endDate = timer.endDate,
						)
						programInfo.copy(locationType = LocationType.VIRTUAL)
					}
					.groupBy { it.startDate!!.toLocalDate() }

				val scheduleGroups = grouped.entries
					.sortedBy { it.key }
					.map { (date, items) ->
						// Use the first item's startDate for the friendly date label
						val label = items.firstOrNull()?.startDate?.let { startDate ->
							TimeUtils.getFriendlyDate(context, startDate, true)
						} ?: date.toString()
						ScheduleGroup(dateLabel = label, items = items)
					}

				_uiState.update { it.copy(
					isLoading = false,
					scheduleGroups = scheduleGroups,
				) }
			} catch (err: ApiClientException) {
				Timber.e(err, "Failed to load schedule")
				_uiState.update { it.copy(isLoading = false) }
			}
		}
	}
}
