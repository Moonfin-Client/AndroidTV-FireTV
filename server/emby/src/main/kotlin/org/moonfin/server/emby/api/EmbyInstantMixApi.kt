package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerInstantMixApi
import org.moonfin.server.core.model.ItemsResult
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toServerItem

class EmbyInstantMixApi(private val apiClient: EmbyApiClient) : ServerInstantMixApi {

    private val instantMix get() = apiClient.instantMixService!!

    override suspend fun getInstantMix(itemId: String, userId: String?, limit: Int?): ItemsResult {
        val result = instantMix.getItemsByIdInstantmix(
            id = itemId,
            artistType = null, maxOfficialRating = null, hasThemeSong = null,
            hasThemeVideo = null, hasSubtitles = null, hasSpecialFeature = null,
            hasTrailer = null, isSpecialSeason = null, adjacentTo = null,
            startItemId = null, minIndexNumber = null, minStartDate = null,
            maxStartDate = null, minEndDate = null, maxEndDate = null,
            minPlayers = null, maxPlayers = null, parentIndexNumber = null,
            hasParentalRating = null, isHD = null, isUnaired = null,
            minCommunityRating = null, minCriticRating = null, airedDuringSeason = null,
            minPremiereDate = null, minDateLastSaved = null, minDateLastSavedForUser = null,
            maxPremiereDate = null, hasOverview = null, hasImdbId = null,
            hasTmdbId = null, hasTvdbId = null, excludeItemIds = null,
            startIndex = null, limit = limit, recursive = null, searchTerm = null,
            sortOrder = null, parentId = null, fields = null,
            excludeItemTypes = null, includeItemTypes = null, anyProviderIdEquals = null,
            filters = null, isFavorite = null, isMovie = null, isSeries = null,
            isFolder = null, isNews = null, isKids = null, isSports = null,
            isNew = null, isPremiere = null, isNewOrPremiere = null, isRepeat = null,
            projectToMedia = null, mediaTypes = null, imageTypes = null, sortBy = null,
            isPlayed = null, genres = null, officialRatings = null, tags = null,
            excludeTags = null, years = null, enableImages = null, enableUserData = null,
            imageTypeLimit = null, enableImageTypes = null, person = null, personIds = null,
            personTypes = null, studios = null, studioIds = null, artists = null,
            artistIds = null, albums = null, ids = null, videoTypes = null,
            containers = null, audioCodecs = null, audioLayouts = null, videoCodecs = null,
            extendedVideoTypes = null, subtitleCodecs = null, path = null,
            userId = userId, minOfficialRating = null, isLocked = null,
            isPlaceHolder = null, hasOfficialRating = null, groupItemsIntoCollections = null,
            is3D = null, seriesStatus = null, nameStartsWithOrGreater = null,
            artistStartsWithOrGreater = null, albumArtistStartsWithOrGreater = null,
            nameStartsWith = null, nameLessThan = null,
        ).body()
        return ItemsResult(
            items = result.items?.map { it.toServerItem() } ?: emptyList(),
            totalRecordCount = result.totalRecordCount ?: 0,
            startIndex = 0,
        )
    }
}
