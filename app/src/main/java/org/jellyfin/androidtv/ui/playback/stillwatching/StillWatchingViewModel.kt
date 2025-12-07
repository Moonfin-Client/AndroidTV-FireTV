package org.jellyfin.androidtv.ui.playback.stillwatching

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.NextUpBehavior
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.util.apiclient.getLogoImage
import org.jellyfin.androidtv.util.apiclient.getPrimaryImage
import org.jellyfin.androidtv.util.apiclient.ioCall
import org.jellyfin.androidtv.util.sdk.getDisplayName
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.UUID

class StillWatchingViewModel(
	private val context: Context,
	private val api: ApiClient,
	private val userPreferences: UserPreferences,
	private val interactionTrackerViewModel: InteractionTrackerViewModel,
) : ViewModel() {
	private val _item = MutableStateFlow<StillWatchingItemData?>(null)
	val item: StateFlow<StillWatchingItemData?> = _item
	private val _state = MutableStateFlow(StillWatchingState.INITIALIZED)
	val state: StateFlow<StillWatchingState> = _state

	fun setItemId(id: UUID?) = viewModelScope.launch {
		if (id == null) {
			_item.value = null
			_state.value = StillWatchingState.NO_DATA
		} else {
			_item.value = loadItemData(id)
		}
	}

	fun stillWatching() {
		interactionTrackerViewModel.notifyStillWatching()
		_state.value = StillWatchingState.STILL_WATCHING
	}

	fun close() {
		_state.value = StillWatchingState.CLOSE
	}

	private suspend fun loadItemData(id: UUID) = api.ioCall {
		val item by userLibraryApi.getItem(itemId = id)

		val thumbnail = when (userPreferences[UserPreferences.nextUpBehavior]) {
			NextUpBehavior.EXTENDED -> item.getPrimaryImage()
			else -> null
		}
		val logo = item.getLogoImage()
		val title = item.getDisplayName(context)

		StillWatchingItemData(
			item,
			item.id,
			title,
			thumbnail,
			logo
		)
	}
}

