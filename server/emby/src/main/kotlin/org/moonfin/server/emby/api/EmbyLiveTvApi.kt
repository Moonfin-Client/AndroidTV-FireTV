package org.moonfin.server.emby.api

import io.ktor.util.reflect.typeInfo
import org.emby.client.model.QueryResultBaseItemDto
import org.moonfin.server.core.api.ServerLiveTvApi
import org.moonfin.server.core.model.ItemsResult
import org.moonfin.server.core.model.LiveTvGuideInfo
import org.moonfin.server.core.model.LiveTvSeriesTimerInfo
import org.moonfin.server.core.model.LiveTvTimerInfo
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toCoreGuideInfo
import org.moonfin.server.emby.mapper.toEmbyTimerInfoDto
import org.moonfin.server.emby.mapper.toLiveTvSeriesTimerInfo
import org.moonfin.server.emby.mapper.toLiveTvTimerInfo
import org.moonfin.server.emby.mapper.toServerItem

class EmbyLiveTvApi(private val apiClient: EmbyApiClient) : ServerLiveTvApi {

    private val liveTv get() = apiClient.liveTvService!!

    override suspend fun getChannels(userId: String?, startIndex: Int?, limit: Int?): ItemsResult {
        val result = liveTv.getLivetvChannels(
            type = null, isLiked = null, isDisliked = null, enableFavoriteSorting = null,
            addCurrentProgram = null, artistType = null, maxOfficialRating = null,
            hasThemeSong = null, hasThemeVideo = null, hasSubtitles = null,
            hasSpecialFeature = null, hasTrailer = null, isSpecialSeason = null,
            adjacentTo = null, startItemId = null, minIndexNumber = null,
            minStartDate = null, maxStartDate = null, minEndDate = null, maxEndDate = null,
            minPlayers = null, maxPlayers = null, parentIndexNumber = null,
            hasParentalRating = null, isHD = null, isUnaired = null,
            minCommunityRating = null, minCriticRating = null, airedDuringSeason = null,
            minPremiereDate = null, minDateLastSaved = null, minDateLastSavedForUser = null,
            maxPremiereDate = null, hasOverview = null, hasImdbId = null,
            hasTmdbId = null, hasTvdbId = null, excludeItemIds = null,
            startIndex = startIndex, limit = limit, recursive = null, searchTerm = null,
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
            startIndex = startIndex ?: 0,
        )
    }

    override suspend fun getPrograms(channelIds: List<String>?, userId: String?, startIndex: Int?, limit: Int?): ItemsResult {
        // getLivetvPrograms has incorrect HttpResponse<Unit> return type in generated client (codegen bug).
        val response = liveTv.getLivetvPrograms(
            channelIds = channelIds?.joinToString(","),
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
            startIndex = startIndex, limit = limit, recursive = null, searchTerm = null,
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
        )
        val result = response.typedBody<QueryResultBaseItemDto>(typeInfo<QueryResultBaseItemDto>())
        return ItemsResult(
            items = result.items?.map { it.toServerItem() } ?: emptyList(),
            totalRecordCount = result.totalRecordCount ?: 0,
            startIndex = startIndex ?: 0,
        )
    }

    override suspend fun getRecordings(channelId: String?, seriesTimerId: String?, startIndex: Int?, limit: Int?): ItemsResult {
        // getLivetvRecordings has incorrect HttpResponse<Unit> return type in generated client (codegen bug).
        val response = liveTv.getLivetvRecordings(
            channelId = channelId, status = null, isInProgress = null,
            seriesTimerId = seriesTimerId, artistType = null, maxOfficialRating = null,
            hasThemeSong = null, hasThemeVideo = null, hasSubtitles = null,
            hasSpecialFeature = null, hasTrailer = null, isSpecialSeason = null,
            adjacentTo = null, startItemId = null, minIndexNumber = null,
            minStartDate = null, maxStartDate = null, minEndDate = null, maxEndDate = null,
            minPlayers = null, maxPlayers = null, parentIndexNumber = null,
            hasParentalRating = null, isHD = null, isUnaired = null,
            minCommunityRating = null, minCriticRating = null, airedDuringSeason = null,
            minPremiereDate = null, minDateLastSaved = null, minDateLastSavedForUser = null,
            maxPremiereDate = null, hasOverview = null, hasImdbId = null,
            hasTmdbId = null, hasTvdbId = null, excludeItemIds = null,
            startIndex = startIndex, limit = limit, recursive = null, searchTerm = null,
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
            userId = null, minOfficialRating = null, isLocked = null,
            isPlaceHolder = null, hasOfficialRating = null, groupItemsIntoCollections = null,
            is3D = null, seriesStatus = null, nameStartsWithOrGreater = null,
            artistStartsWithOrGreater = null, albumArtistStartsWithOrGreater = null,
            nameStartsWith = null, nameLessThan = null,
        )
        val result = response.typedBody<QueryResultBaseItemDto>(typeInfo<QueryResultBaseItemDto>())
        return ItemsResult(
            items = result.items?.map { it.toServerItem() } ?: emptyList(),
            totalRecordCount = result.totalRecordCount ?: 0,
            startIndex = startIndex ?: 0,
        )
    }

    override suspend fun getTimers(channelId: String?, seriesTimerId: String?): List<LiveTvTimerInfo> {
        val result = liveTv.getLivetvTimers(channelId = channelId, seriesTimerId = seriesTimerId).body()
        return result.items?.map { it.toLiveTvTimerInfo() } ?: emptyList()
    }

    override suspend fun getSeriesTimers(sortBy: String?, startIndex: Int?, limit: Int?): List<LiveTvSeriesTimerInfo> {
        val result = liveTv.getLivetvSeriestimers(
            sortBy = sortBy,
            sortOrder = null,
            startIndex = startIndex,
            limit = limit,
        ).body()
        return result.items?.map { it.toLiveTvSeriesTimerInfo() } ?: emptyList()
    }

    override suspend fun createTimer(timer: LiveTvTimerInfo) {
        liveTv.postLivetvTimers(timer.toEmbyTimerInfoDto())
    }

    override suspend fun cancelTimer(timerId: String) {
        liveTv.deleteLivetvTimersById(timerId)
    }

    override suspend fun getRecommendedPrograms(userId: String?, limit: Int?): ItemsResult {
        val result = liveTv.getLivetvProgramsRecommended(
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

    override suspend fun getGuideInfo(): LiveTvGuideInfo {
        return liveTv.getLivetvGuideinfo().body().toCoreGuideInfo()
    }
}
