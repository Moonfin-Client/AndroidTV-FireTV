package org.moonfin.server.emby.api

import org.emby.client.model.QueryResultBaseItemDto
import org.moonfin.server.core.api.ServerItemsApi
import org.moonfin.server.core.model.GetItemsRequest
import org.moonfin.server.core.model.GetLatestMediaRequest
import org.moonfin.server.core.model.GetNextUpRequest
import org.moonfin.server.core.model.GetResumeItemsRequest
import org.moonfin.server.core.model.ItemField
import org.moonfin.server.core.model.ItemFilter
import org.moonfin.server.core.model.ItemSortBy
import org.moonfin.server.core.model.ItemType
import org.moonfin.server.core.model.ItemsResult
import org.moonfin.server.core.model.MediaType
import org.moonfin.server.core.model.ServerItem
import org.moonfin.server.core.model.SortOrder
import org.moonfin.server.emby.EmbyApiClient
import org.moonfin.server.emby.mapper.toServerItem

class EmbyItemsApi(private val apiClient: EmbyApiClient) : ServerItemsApi {

    override suspend fun getItems(request: GetItemsRequest): ItemsResult {
        val userId = request.userId
        val result: QueryResultBaseItemDto = if (userId != null) {
            apiClient.itemsService!!.getUsersByUseridItems(
                userId = userId,
                startIndex = request.startIndex,
                limit = request.limit,
                recursive = request.recursive,
                searchTerm = request.searchTerm,
                sortOrder = request.sortOrder?.toEmby(),
                sortBy = request.sortBy?.joinToString(",") { it.toEmby() },
                parentId = request.parentId,
                fields = request.fields?.toEmbyFields(),
                includeItemTypes = request.includeItemTypes?.joinToString(",") { it.toEmby() },
                excludeItemTypes = null,
                filters = request.filters?.joinToString(",") { it.toEmby() },
                isFavorite = request.isFavorite,
                mediaTypes = request.mediaTypes?.joinToString(",") { it.toEmby() },
                artistIds = request.artistIds?.joinToString(","),
                personIds = request.personIds?.joinToString(","),
                studioIds = request.studioIds?.joinToString(","),
                genres = request.genres?.joinToString(","),
                tags = request.tags?.joinToString(","),
                years = request.years?.joinToString(","),
                enableImages = request.enableImages,
                enableUserData = request.enableUserData,
                imageTypeLimit = request.imageTypeLimit,
                ids = request.ids?.joinToString(","),
                groupItemsIntoCollections = request.groupItems,
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
                anyProviderIdEquals = null, isMovie = null, isSeries = null, isFolder = null,
                isNews = null, isKids = null, isSports = null, isNew = null, isPremiere = null,
                isNewOrPremiere = null, isRepeat = null, projectToMedia = null,
                imageTypes = null, isPlayed = null, officialRatings = null, excludeTags = null,
                enableImageTypes = null, person = null, personTypes = null, studios = null,
                artists = null, albums = null, videoTypes = null, containers = null,
                audioCodecs = null, audioLayouts = null, videoCodecs = null,
                extendedVideoTypes = null, subtitleCodecs = null, path = null,
                minOfficialRating = null, isLocked = null, isPlaceHolder = null,
                hasOfficialRating = null, is3D = null, seriesStatus = null,
                nameStartsWithOrGreater = null, artistStartsWithOrGreater = null,
                albumArtistStartsWithOrGreater = null, nameStartsWith = null, nameLessThan = null,
            ).body()
        } else {
            apiClient.itemsService!!.getItems(
                startIndex = request.startIndex,
                limit = request.limit,
                recursive = request.recursive,
                searchTerm = request.searchTerm,
                sortOrder = request.sortOrder?.toEmby(),
                sortBy = request.sortBy?.joinToString(",") { it.toEmby() },
                parentId = request.parentId,
                fields = request.fields?.toEmbyFields(),
                includeItemTypes = request.includeItemTypes?.joinToString(",") { it.toEmby() },
                excludeItemTypes = null,
                filters = request.filters?.joinToString(",") { it.toEmby() },
                isFavorite = request.isFavorite,
                mediaTypes = request.mediaTypes?.joinToString(",") { it.toEmby() },
                artistIds = request.artistIds?.joinToString(","),
                personIds = request.personIds?.joinToString(","),
                studioIds = request.studioIds?.joinToString(","),
                genres = request.genres?.joinToString(","),
                tags = request.tags?.joinToString(","),
                years = request.years?.joinToString(","),
                enableImages = request.enableImages,
                enableUserData = request.enableUserData,
                imageTypeLimit = request.imageTypeLimit,
                ids = request.ids?.joinToString(","),
                groupItemsIntoCollections = request.groupItems,
                userId = null,
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
                anyProviderIdEquals = null, isMovie = null, isSeries = null, isFolder = null,
                isNews = null, isKids = null, isSports = null, isNew = null, isPremiere = null,
                isNewOrPremiere = null, isRepeat = null, projectToMedia = null,
                imageTypes = null, isPlayed = null, officialRatings = null, excludeTags = null,
                enableImageTypes = null, person = null, personTypes = null, studios = null,
                artists = null, albums = null, videoTypes = null, containers = null,
                audioCodecs = null, audioLayouts = null, videoCodecs = null,
                extendedVideoTypes = null, subtitleCodecs = null, path = null,
                minOfficialRating = null, isLocked = null, isPlaceHolder = null,
                hasOfficialRating = null, is3D = null, seriesStatus = null,
                nameStartsWithOrGreater = null, artistStartsWithOrGreater = null,
                albumArtistStartsWithOrGreater = null, nameStartsWith = null, nameLessThan = null,
            ).body()
        }
        return result.toItemsResult()
    }

    override suspend fun getResumeItems(request: GetResumeItemsRequest): ItemsResult {
        val userId = request.userId ?: apiClient.userId
            ?: error("EmbyItemsApi.getResumeItems: userId not configured")
        val result: QueryResultBaseItemDto = apiClient.itemsService!!.getUsersByUseridItemsResume(
            userId = userId,
            startIndex = request.startIndex,
            limit = request.limit,
            parentId = request.parentId,
            fields = request.fields?.toEmbyFields(),
            includeItemTypes = request.includeItemTypes?.joinToString(",") { it.toEmby() },
            enableImages = request.enableImages,
            enableUserData = null,
            imageTypeLimit = request.imageTypeLimit,
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
            recursive = null, searchTerm = null, sortOrder = null, excludeItemTypes = null,
            anyProviderIdEquals = null, filters = null, isFavorite = null, isMovie = null,
            isSeries = null, isFolder = null, isNews = null, isKids = null, isSports = null,
            isNew = null, isPremiere = null, isNewOrPremiere = null, isRepeat = null,
            projectToMedia = null, mediaTypes = null, imageTypes = null, sortBy = null,
            isPlayed = null, genres = null, officialRatings = null, tags = null,
            excludeTags = null, years = null, enableImageTypes = null, person = null,
            personIds = null, personTypes = null, studios = null, studioIds = null,
            artists = null, artistIds = null, albums = null, ids = null,
            videoTypes = null, containers = null, audioCodecs = null, audioLayouts = null,
            videoCodecs = null, extendedVideoTypes = null, subtitleCodecs = null, path = null,
            minOfficialRating = null, isLocked = null, isPlaceHolder = null,
            hasOfficialRating = null, groupItemsIntoCollections = null, is3D = null,
            seriesStatus = null, nameStartsWithOrGreater = null, artistStartsWithOrGreater = null,
            albumArtistStartsWithOrGreater = null, nameStartsWith = null, nameLessThan = null,
        ).body()
        return result.toItemsResult()
    }

    override suspend fun getLatestMedia(request: GetLatestMediaRequest): List<ServerItem> {
        val userId = request.userId ?: apiClient.userId
            ?: error("EmbyItemsApi.getLatestMedia: userId not configured")
        val result: List<org.emby.client.model.BaseItemDto> =
            apiClient.userLibraryService!!.getUsersByUseridItemsLatest(
                userId = userId,
                parentId = request.parentId,
                fields = request.fields?.toEmbyFields(),
                includeItemTypes = request.includeItemTypes?.joinToString(",") { it.toEmby() },
                limit = request.limit,
                groupItems = request.groupItems,
                enableImages = null,
                imageTypeLimit = request.imageTypeLimit,
                enableImageTypes = null,
                enableUserData = null,
                mediaTypes = null,
                isFolder = null,
                isPlayed = null,
            ).body()
        return result.map { it.toServerItem() }
    }

    override suspend fun getNextUp(request: GetNextUpRequest): ItemsResult {
        val userId = request.userId ?: apiClient.userId
            ?: error("EmbyItemsApi.getNextUp: userId not configured")
        val result: QueryResultBaseItemDto = apiClient.tvShowsService!!.getShowsNextup(
            userId = userId,
            startIndex = request.startIndex,
            limit = request.limit,
            fields = request.fields?.toEmbyFields(),
            seriesId = request.seriesId,
            parentId = null,
            enableImages = request.enableImages,
            imageTypeLimit = request.imageTypeLimit,
            enableImageTypes = null,
            enableUserData = null,
        ).body()
        return result.toItemsResult()
    }

    override suspend fun getSimilarItems(itemId: String, limit: Int?): ItemsResult {
        val result: QueryResultBaseItemDto = apiClient.libraryService!!.getItemsByIdSimilar(
            id = itemId,
            limit = limit,
            userId = apiClient.userId,
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
            startIndex = null, recursive = null, searchTerm = null, sortOrder = null,
            parentId = null, fields = null, excludeItemTypes = null, includeItemTypes = null,
            anyProviderIdEquals = null, filters = null, isFavorite = null, isMovie = null,
            isSeries = null, isFolder = null, isNews = null, isKids = null, isSports = null,
            isNew = null, isPremiere = null, isNewOrPremiere = null, isRepeat = null,
            projectToMedia = null, mediaTypes = null, imageTypes = null, sortBy = null,
            isPlayed = null, genres = null, officialRatings = null, tags = null,
            excludeTags = null, years = null, enableImages = null, enableUserData = null,
            imageTypeLimit = null, enableImageTypes = null, person = null, personIds = null,
            personTypes = null, studios = null, studioIds = null, artists = null,
            artistIds = null, albums = null, ids = null, videoTypes = null, containers = null,
            audioCodecs = null, audioLayouts = null, videoCodecs = null,
            extendedVideoTypes = null, subtitleCodecs = null, path = null,
            minOfficialRating = null, isLocked = null, isPlaceHolder = null,
            hasOfficialRating = null, groupItemsIntoCollections = null, is3D = null,
            seriesStatus = null, nameStartsWithOrGreater = null, artistStartsWithOrGreater = null,
            albumArtistStartsWithOrGreater = null, nameStartsWith = null, nameLessThan = null,
        ).body()
        return result.toItemsResult()
    }

    override suspend fun getSeasons(seriesId: String, userId: String): ItemsResult {
        val result: QueryResultBaseItemDto = apiClient.tvShowsService!!.getShowsByIdSeasons(
            id = seriesId,
            userId = userId,
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
            startIndex = null, limit = null, recursive = null, searchTerm = null,
            sortOrder = null, parentId = null, fields = null, excludeItemTypes = null,
            includeItemTypes = null, anyProviderIdEquals = null, filters = null,
            isFavorite = null, isMovie = null, isSeries = null, isFolder = null,
            isNews = null, isKids = null, isSports = null, isNew = null, isPremiere = null,
            isNewOrPremiere = null, isRepeat = null, projectToMedia = null,
            mediaTypes = null, imageTypes = null, sortBy = null, isPlayed = null,
            genres = null, officialRatings = null, tags = null, excludeTags = null,
            years = null, enableImages = null, enableUserData = null,
            imageTypeLimit = null, enableImageTypes = null, person = null, personIds = null,
            personTypes = null, studios = null, studioIds = null, artists = null,
            artistIds = null, albums = null, ids = null, videoTypes = null, containers = null,
            audioCodecs = null, audioLayouts = null, videoCodecs = null,
            extendedVideoTypes = null, subtitleCodecs = null, path = null,
            minOfficialRating = null, isLocked = null, isPlaceHolder = null,
            hasOfficialRating = null, groupItemsIntoCollections = null, is3D = null,
            seriesStatus = null, nameStartsWithOrGreater = null, artistStartsWithOrGreater = null,
            albumArtistStartsWithOrGreater = null, nameStartsWith = null, nameLessThan = null,
        ).body()
        return result.toItemsResult()
    }

    override suspend fun getEpisodes(seriesId: String, seasonId: String, userId: String): ItemsResult {
        val result: QueryResultBaseItemDto = apiClient.itemsService!!.getUsersByUseridItems(
            userId = userId,
            parentId = seasonId,
            includeItemTypes = "Episode",
            recursive = false,
            startIndex = null, limit = null, searchTerm = null,
            sortOrder = null, sortBy = null, fields = null,
            excludeItemTypes = null, filters = null, isFavorite = null,
            mediaTypes = null, artistIds = null, personIds = null,
            studioIds = null, genres = null, tags = null, years = null,
            enableImages = null, enableUserData = null, imageTypeLimit = null,
            ids = null, groupItemsIntoCollections = null,
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
            anyProviderIdEquals = null, isMovie = null, isSeries = null, isFolder = null,
            isNews = null, isKids = null, isSports = null, isNew = null, isPremiere = null,
            isNewOrPremiere = null, isRepeat = null, projectToMedia = null,
            imageTypes = null, isPlayed = null, officialRatings = null, excludeTags = null,
            enableImageTypes = null, person = null, personTypes = null, studios = null,
            artists = null, albums = null, videoTypes = null, containers = null,
            audioCodecs = null, audioLayouts = null, videoCodecs = null,
            extendedVideoTypes = null, subtitleCodecs = null, path = null,
            minOfficialRating = null, isLocked = null, isPlaceHolder = null,
            hasOfficialRating = null, is3D = null, seriesStatus = null,
            nameStartsWithOrGreater = null, artistStartsWithOrGreater = null,
            albumArtistStartsWithOrGreater = null, nameStartsWith = null, nameLessThan = null,
        ).body()
        return result.toItemsResult()
    }
}

internal fun QueryResultBaseItemDto.toItemsResult(): ItemsResult = ItemsResult(
    items = items?.map { it.toServerItem() } ?: emptyList(),
    totalRecordCount = totalRecordCount ?: 0,
    startIndex = 0,
)

private fun ItemSortBy.toEmby(): String = when (this) {
    ItemSortBy.SORT_NAME -> "SortName"
    ItemSortBy.PREMIERE_DATE -> "PremiereDate"
    ItemSortBy.DATE_CREATED -> "DateCreated"
    ItemSortBy.DATE_PLAYED -> "DatePlayed"
    ItemSortBy.COMMUNITY_RATING -> "CommunityRating"
    ItemSortBy.CRITIC_RATING -> "CriticRating"
    ItemSortBy.RUNTIME -> "Runtime"
    ItemSortBy.PLAY_COUNT -> "PlayCount"
    ItemSortBy.RANDOM -> "Random"
    ItemSortBy.OFFICIAL_RATING -> "OfficialRating"
    ItemSortBy.INDEX_NUMBER -> "IndexNumber"
    ItemSortBy.TRACK_NUMBER -> "IndexNumber"
    ItemSortBy.ALBUM -> "Album"
    ItemSortBy.ALBUM_ARTIST -> "AlbumArtist"
    ItemSortBy.ARTIST -> "Artist"
}

private fun ItemFilter.toEmby(): String = when (this) {
    ItemFilter.IS_PLAYED -> "IsPlayed"
    ItemFilter.IS_UNPLAYED -> "IsUnplayed"
    ItemFilter.IS_FAVORITE -> "IsFavorite"
    ItemFilter.IS_RESUMABLE -> "IsResumable"
    ItemFilter.LIKES -> "Likes"
    ItemFilter.DISLIKES -> "Dislikes"
}

private fun SortOrder.toEmby(): String = when (this) {
    SortOrder.ASCENDING -> "Ascending"
    SortOrder.DESCENDING -> "Descending"
}

private fun ItemType.toEmby(): String = when (this) {
    ItemType.MOVIE -> "Movie"
    ItemType.SERIES -> "Series"
    ItemType.SEASON -> "Season"
    ItemType.EPISODE -> "Episode"
    ItemType.AUDIO -> "Audio"
    ItemType.MUSIC_ALBUM -> "MusicAlbum"
    ItemType.MUSIC_ARTIST -> "MusicArtist"
    ItemType.MUSIC_VIDEO -> "MusicVideo"
    ItemType.PLAYLIST -> "Playlist"
    ItemType.PHOTO -> "Photo"
    ItemType.PHOTO_ALBUM -> "PhotoAlbum"
    ItemType.BOX_SET -> "BoxSet"
    ItemType.CHANNEL -> "Channel"
    ItemType.PROGRAM -> "Program"
    ItemType.RECORDING -> "Recording"
    ItemType.LIVE_TV_CHANNEL -> "TvChannel"
    ItemType.LIVE_TV_PROGRAM -> "TvProgram"
    ItemType.BOOK -> "Book"
    ItemType.TRAILER -> "Trailer"
    ItemType.VIDEO -> "Video"
    ItemType.PERSON -> "Person"
    ItemType.STUDIO -> "Studio"
    ItemType.GENRE -> "Genre"
    ItemType.MUSIC_GENRE -> "MusicGenre"
    ItemType.USER_VIEW -> "UserView"
    ItemType.COLLECTION_FOLDER -> "CollectionFolder"
    ItemType.FOLDER -> "Folder"
    ItemType.BASE_PLUGIN_FOLDER -> "BasePluginFolder"
    ItemType.UNKNOWN -> "Video"
}

private fun MediaType.toEmby(): String = when (this) {
    MediaType.VIDEO -> "Video"
    MediaType.AUDIO -> "Audio"
    MediaType.PHOTO -> "Photo"
    MediaType.BOOK -> "Book"
    MediaType.UNKNOWN -> "Video"
}

private fun List<ItemField>.toEmbyFields(): String = mapNotNull { field ->
    when (field) {
        ItemField.OVERVIEW -> "Overview"
        ItemField.GENRES -> "Genres"
        ItemField.MEDIA_SOURCES -> "MediaSources"
        ItemField.MEDIA_STREAMS -> "MediaStreams"
        ItemField.PRIMARY_IMAGE_ASPECT_RATIO -> "PrimaryImageAspectRatio"
        ItemField.CHAPTERS -> "Chapters"
        ItemField.CHILD_COUNT -> "ChildCount"
        ItemField.DATE_CREATED -> "DateCreated"
        ItemField.CHANNEL_INFO -> "ChannelInfo"
        ItemField.CAN_DELETE -> "CanDelete"
        ItemField.TAGLINES -> "Taglines"
        ItemField.PROVIDER_IDS -> "ProviderIds"
        ItemField.DISPLAY_PREFERENCES_ID -> "DisplayPreferencesId"
        ItemField.ITEM_COUNTS -> "ItemCounts"
        ItemField.MEDIA_SOURCE_COUNT -> "MediaSourceCount"
        ItemField.CUMULATIVE_RUN_TIME_TICKS -> "CumulativeRunTimeTicks"
        ItemField.TRICKPLAY -> null
        ItemField.PATH -> "Path"
    }
}.joinToString(",")
