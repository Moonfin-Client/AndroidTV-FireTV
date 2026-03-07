package org.jellyfin.androidtv.preference

import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.constant.PosterSize
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.androidtv.ui.browsing.v2.PlayedStatusFilter
import org.jellyfin.androidtv.ui.browsing.v2.SeriesStatusFilter
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class LibraryPreferences(
	displayPreferencesId: String,
	api: ApiClient,
) : DisplayPreferencesStore(
	displayPreferencesId = displayPreferencesId,
	api = api,
) {
	companion object {
		val posterSize = enumPreference("PosterSize", PosterSize.MED)
		val imageType = enumPreference("ImageType", ImageType.POSTER)
		val gridDirection = enumPreference("GridDirection", GridDirection.HORIZONTAL)

		// Filters
		val filterFavoritesOnly = booleanPreference("FilterFavoritesOnly", false)
		val filterPlayedStatus = enumPreference("filterPlayedStatus", PlayedStatusFilter.ALL)
		val filterSeriesStatus = enumPreference("filterSeriesStatus", SeriesStatusFilter.ALL)
		val filterUnwatchedOnly = booleanPreference("FilterUnwatchedOnly", false)

		// Item sorting
		val sortBy = enumPreference("SortBy", ItemSortBy.SORT_NAME)
		val sortOrder = enumPreference("SortOrder", SortOrder.ASCENDING)
	}
}
