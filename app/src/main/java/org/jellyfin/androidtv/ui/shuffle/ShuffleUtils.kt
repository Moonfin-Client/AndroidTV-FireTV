package org.jellyfin.androidtv.ui.shuffle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.sdk.ApiClientFactory
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind.MOVIE
import org.jellyfin.sdk.model.api.BaseItemKind.SERIES
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.core.context.GlobalContext
import timber.log.Timber
import java.util.UUID

/**
 * Execute a quick shuffle (single click) from Java code - fetches a random item based on user preferences
 */
fun executeQuickShuffle(
	userPreferences: UserPreferences,
	navigationRepository: NavigationRepository
) {
	val api = GlobalContext.get().get<ApiClient>()
	val apiClientFactory = GlobalContext.get().get<ApiClientFactory>()
	val shuffleContentType = userPreferences[UserPreferences.shuffleContentType] ?: "both"
	
	CoroutineScope(Dispatchers.Main).launch {
		executeShuffle(
			libraryId = null,
			serverId = null,
			genreName = null,
			contentType = shuffleContentType,
			libraryCollectionType = null,
			api = api,
			apiClientFactory = apiClientFactory,
			navigationRepository = navigationRepository
		)
	}
}

/**
 * Shared shuffle execution logic - fetches a random item and navigates to it
 */
suspend fun executeShuffle(
	libraryId: UUID?,
	serverId: UUID?,
	genreName: String?,
	contentType: String,
	libraryCollectionType: CollectionType?,
	api: ApiClient,
	apiClientFactory: ApiClientFactory,
	navigationRepository: NavigationRepository
) {
	try {
		val includeTypes = when {
			libraryCollectionType == CollectionType.MOVIES -> setOf(MOVIE)
			libraryCollectionType == CollectionType.TVSHOWS -> setOf(SERIES)
			contentType == "movies" -> setOf(MOVIE)
			contentType == "tv" -> setOf(SERIES)
			else -> setOf(MOVIE, SERIES)
		}
		val targetApi = if (serverId != null) {
			apiClientFactory.getApiClientForServer(serverId) ?: api
		} else api
		val randomItem = withContext(Dispatchers.IO) {
			Timber.d("Shuffle search: genreName='$genreName', includeTypes=$includeTypes, libraryId=$libraryId")
			val response = targetApi.itemsApi.getItems(
				parentId = libraryId,
				genres = genreName?.let { setOf(it) },
				includeItemTypes = includeTypes,
				recursive = true,
				sortBy = setOf(ItemSortBy.RANDOM),
				limit = 1,
			)
			Timber.d("Shuffle search results: totalRecordCount=${response.content.totalRecordCount}, items=${response.content.items?.size ?: 0}")
			response.content.items?.firstOrNull()
		}
		if (randomItem != null) {
			Timber.d("Found random item: ${randomItem.name} (${randomItem.type})")
			navigationRepository.navigate(Destinations.itemDetails(randomItem.id))
		} else {
			Timber.w("No random item found for genre='$genreName', types=$includeTypes")
		}
	} catch (e: Exception) {
		Timber.e(e, "Failed to fetch random item")
	}
}
