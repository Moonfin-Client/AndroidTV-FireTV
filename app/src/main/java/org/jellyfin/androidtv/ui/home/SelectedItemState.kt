package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Represents the state of the currently selected item in the home screen.
 * Used to communicate selection data from HomeRowsFragment to HomeFragment
 * for displaying in the top info area.
 * 
 * This is kept lightweight for performance - only title and summary are shown.
 */
data class SelectedItemState(
	val title: String = "",
	val summary: String = "",
	val baseItem: BaseItemDto? = null
) {
	companion object {
		val EMPTY = SelectedItemState()
	}
}
