package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Represents the state of the currently selected item in the home screen.
 * Used to communicate selection data from HomeRowsFragment to HomeFragment
 * for displaying in the top info area.
 *
 * - title: The specific item title (e.g., episode title)
 * - name: The series/movie name (shown in logo area when no logo)
 * - logoUrl: URL to the logo image if available
 * - summary: The item's overview/description
 */
data class SelectedItemState(
	val title: String = "",
	val name: String = "",
	val summary: String = "",
	val logoUrl: String? = null,
	val baseItem: BaseItemDto? = null
) {
	companion object {
		val EMPTY = SelectedItemState()
	}
}
