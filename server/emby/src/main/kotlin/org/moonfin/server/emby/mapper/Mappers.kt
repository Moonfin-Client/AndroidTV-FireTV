package org.moonfin.server.emby.mapper

import org.emby.client.model.BaseItemDto
import org.emby.client.model.BaseItemPerson
import org.emby.client.model.LiveTvSeriesTimerInfoDto
import org.emby.client.model.LiveTvTimerInfoDto
import org.emby.client.model.SortOrder as EmbySortOrder
import org.emby.client.model.MediaProtocol as EmbyMediaProtocol
import org.emby.client.model.MediaSourceInfo as EmbyMediaSourceInfo
import org.emby.client.model.MediaStream as EmbyMediaStream
import org.emby.client.model.MediaStreamType as EmbyMediaStreamType
import org.emby.client.model.NameLongIdPair
import org.emby.client.model.PersonType as EmbyPersonType
import org.emby.client.model.UserDto
import org.emby.client.model.UserItemDataDto
import org.moonfin.server.core.model.ImageType
import org.moonfin.server.core.model.ItemType
import org.moonfin.server.core.model.MediaProtocol
import org.moonfin.server.core.model.MediaType
import org.moonfin.server.core.model.NameIdPair
import org.moonfin.server.core.model.PersonType
import org.moonfin.server.core.model.ServerItem
import org.moonfin.server.core.model.ServerMediaSource
import org.moonfin.server.core.model.ServerMediaStream
import org.moonfin.server.core.model.ServerPerson
import org.moonfin.server.core.model.ServerUser
import org.moonfin.server.core.model.StreamType
import org.moonfin.server.core.model.DisplayPreferences as CoreDisplayPreferences
import org.moonfin.server.core.model.LiveTvGuideInfo as CoreLiveTvGuideInfo
import org.moonfin.server.core.model.LiveTvSeriesTimerInfo
import org.moonfin.server.core.model.LiveTvTimerInfo
import org.moonfin.server.core.model.SortOrder as CoreSortOrder
import org.moonfin.server.core.model.UserItemData
import java.time.ZoneOffset

private fun String?.toItemType(): ItemType = when (this) {
    "Movie" -> ItemType.MOVIE
    "Series" -> ItemType.SERIES
    "Season" -> ItemType.SEASON
    "Episode" -> ItemType.EPISODE
    "Audio" -> ItemType.AUDIO
    "MusicAlbum" -> ItemType.MUSIC_ALBUM
    "MusicArtist" -> ItemType.MUSIC_ARTIST
    "Playlist" -> ItemType.PLAYLIST
    "Photo" -> ItemType.PHOTO
    "PhotoAlbum" -> ItemType.PHOTO_ALBUM
    "BoxSet" -> ItemType.BOX_SET
    "Channel" -> ItemType.CHANNEL
    "Program" -> ItemType.PROGRAM
    "Recording" -> ItemType.RECORDING
    "TvChannel" -> ItemType.LIVE_TV_CHANNEL
    "TvProgram" -> ItemType.LIVE_TV_PROGRAM
    "Book" -> ItemType.BOOK
    "Trailer" -> ItemType.TRAILER
    "Person" -> ItemType.PERSON
    "Studio" -> ItemType.STUDIO
    "Genre" -> ItemType.GENRE
    "MusicGenre" -> ItemType.MUSIC_GENRE
    "UserView" -> ItemType.USER_VIEW
    "CollectionFolder" -> ItemType.COLLECTION_FOLDER
    "Folder" -> ItemType.FOLDER
    "BasePluginFolder" -> ItemType.BASE_PLUGIN_FOLDER
    "MusicVideo" -> ItemType.MUSIC_VIDEO
    "Video" -> ItemType.VIDEO
    else -> ItemType.UNKNOWN
}

private fun String?.toImageType(): ImageType = when (this) {
    "Primary" -> ImageType.PRIMARY
    "Backdrop" -> ImageType.BACKDROP
    "Banner" -> ImageType.BANNER
    "Thumb" -> ImageType.THUMB
    "Logo" -> ImageType.LOGO
    "Art" -> ImageType.ART
    "Screenshot" -> ImageType.SCREENSHOT
    else -> ImageType.PRIMARY
}

private fun String?.toMediaType(): MediaType? = when (this) {
    "Video" -> MediaType.VIDEO
    "Audio" -> MediaType.AUDIO
    "Photo" -> MediaType.PHOTO
    "Book" -> MediaType.BOOK
    null -> null
    else -> MediaType.UNKNOWN
}

private fun EmbyMediaProtocol?.toMediaProtocol(): MediaProtocol = when (this) {
    EmbyMediaProtocol.FILE -> MediaProtocol.FILE
    EmbyMediaProtocol.HTTP -> MediaProtocol.HTTP
    EmbyMediaProtocol.RTMP -> MediaProtocol.RTMP
    EmbyMediaProtocol.RTSP -> MediaProtocol.RTSP
    EmbyMediaProtocol.UDP -> MediaProtocol.UDP
    EmbyMediaProtocol.RTP -> MediaProtocol.RTP
    EmbyMediaProtocol.FTP -> MediaProtocol.FTP
    else -> MediaProtocol.HTTP
}

private fun EmbyMediaStreamType?.toStreamType(): StreamType = when (this) {
    EmbyMediaStreamType.VIDEO -> StreamType.VIDEO
    EmbyMediaStreamType.AUDIO -> StreamType.AUDIO
    EmbyMediaStreamType.SUBTITLE -> StreamType.SUBTITLE
    EmbyMediaStreamType.EMBEDDED_IMAGE -> StreamType.EMBEDDED_IMAGE
    EmbyMediaStreamType.ATTACHMENT -> StreamType.ATTACHMENT
    EmbyMediaStreamType.DATA -> StreamType.DATA
    else -> StreamType.DATA
}

private fun EmbyPersonType?.toPersonType(): PersonType = when (this) {
    EmbyPersonType.ACTOR -> PersonType.ACTOR
    EmbyPersonType.DIRECTOR -> PersonType.DIRECTOR
    EmbyPersonType.WRITER -> PersonType.WRITER
    EmbyPersonType.PRODUCER -> PersonType.PRODUCER
    EmbyPersonType.GUEST_STAR -> PersonType.GUEST_STAR
    EmbyPersonType.COMPOSER -> PersonType.COMPOSER
    EmbyPersonType.CONDUCTOR -> PersonType.CONDUCTOR
    EmbyPersonType.LYRICIST -> PersonType.LYRICIST
    else -> PersonType.UNKNOWN
}

fun UserItemDataDto.toUserItemData(): UserItemData = UserItemData(
    playedPercentage = playedPercentage,
    unplayedItemCount = unplayedItemCount,
    playbackPositionTicks = playbackPositionTicks ?: 0L,
    playCount = playCount ?: 0,
    isFavorite = isFavorite ?: false,
    lastPlayedDate = lastPlayedDate?.toInstant(),
    played = played ?: false,
    key = key ?: "",
    itemId = itemId,
)

fun EmbyMediaStream.toServerMediaStream(): ServerMediaStream = ServerMediaStream(
    index = index ?: 0,
    type = type.toStreamType(),
    codec = codec,
    language = language,
    displayTitle = displayTitle,
    isDefault = isDefault ?: false,
    isForced = isForced ?: false,
    isExternal = isExternal ?: false,
    path = path,
    width = width,
    height = height,
    channels = channels,
    sampleRate = sampleRate,
    bitRate = bitRate,
    isTextSubtitleStream = isTextSubtitleStream ?: false,
    deliveryUrl = deliveryUrl,
)

fun EmbyMediaSourceInfo.toServerMediaSource(): ServerMediaSource = ServerMediaSource(
    id = id ?: "",
    name = name,
    container = container,
    protocol = protocol.toMediaProtocol(),
    supportsDirectPlay = supportsDirectPlay ?: false,
    supportsDirectStream = supportsDirectStream ?: false,
    supportsTranscoding = supportsTranscoding ?: false,
    transcodingUrl = transcodingUrl,
    eTag = null,
    liveStreamId = liveStreamId,
    isRemote = isRemote ?: false,
    bitrate = bitrate,
    mediaStreams = mediaStreams?.map { it.toServerMediaStream() } ?: emptyList(),
    defaultAudioStreamIndex = defaultAudioStreamIndex,
    defaultSubtitleStreamIndex = defaultSubtitleStreamIndex,
)

fun BaseItemPerson.toServerPerson(): ServerPerson = ServerPerson(
    id = id,
    name = name ?: "",
    role = role,
    type = type.toPersonType(),
    primaryImageTag = primaryImageTag,
)

fun NameLongIdPair.toNameIdPair(): NameIdPair = NameIdPair(
    name = name,
    id = id?.toString(),
)

fun UserDto.toServerUser(): ServerUser = ServerUser(
    id = id ?: "",
    name = name ?: "",
    serverName = serverName,
    primaryImageTag = primaryImageTag,
    hasPassword = hasPassword ?: false,
    hasConfiguredPassword = hasConfiguredPassword ?: false,
    lastLoginDate = lastLoginDate?.toInstant(),
    lastActivityDate = lastActivityDate?.toInstant(),
)

fun BaseItemDto.toServerItem(): ServerItem = ServerItem(
    id = id ?: "",
    serverId = serverId,
    name = name ?: "",
    originalTitle = originalTitle,
    type = type.toItemType(),
    mediaType = mediaType.toMediaType(),
    overview = overview,
    runTimeTicks = runTimeTicks,
    premiereDate = premiereDate?.toInstant(),
    productionYear = productionYear,
    officialRating = officialRating,
    communityRating = communityRating?.toDouble(),
    criticRating = criticRating?.toDouble(),
    isFolder = isFolder ?: false,
    parentId = parentId,
    seriesId = seriesId,
    seriesName = seriesName,
    seasonId = seasonId,
    indexNumber = indexNumber,
    parentIndexNumber = parentIndexNumber,
    imageTags = imageTags?.mapKeys { it.key.toImageType() } ?: emptyMap(),
    backdropImageTags = backdropImageTags ?: emptyList(),
    primaryImageAspectRatio = primaryImageAspectRatio,
    userData = userData?.toUserItemData(),
    mediaSources = mediaSources?.map { it.toServerMediaSource() },
    mediaStreams = mediaStreams?.map { it.toServerMediaStream() },
    container = container,
    channelId = channelId,
    channelName = channelName,
    people = people?.map { it.toServerPerson() },
    genres = genres,
    tags = tags,
    studios = studios?.map { it.toNameIdPair() },
    trickplay = null,
    mediaSegments = null,
)

fun LiveTvTimerInfoDto.toLiveTvTimerInfo(): LiveTvTimerInfo = LiveTvTimerInfo(
    id = id ?: "",
    name = name,
    channelId = channelId,
    channelName = channelName,
    programId = programId,
    seriesTimerId = seriesTimerId,
    startDate = startDate?.toInstant(),
    endDate = endDate?.toInstant(),
    prePaddingSeconds = prePaddingSeconds,
    postPaddingSeconds = postPaddingSeconds,
    status = status?.toString(),
)

fun LiveTvSeriesTimerInfoDto.toLiveTvSeriesTimerInfo(): LiveTvSeriesTimerInfo = LiveTvSeriesTimerInfo(
    id = id ?: "",
    name = name,
    channelId = channelId,
    channelName = channelName,
    recordAnyChannel = recordAnyChannel,
    recordAnyTime = recordAnyTime,
    recordNewOnly = recordNewOnly,
    startDate = startDate?.toInstant(),
    endDate = endDate?.toInstant(),
)

fun org.emby.client.model.LiveTvGuideInfo.toCoreGuideInfo(): CoreLiveTvGuideInfo = CoreLiveTvGuideInfo(
    startDate = startDate?.toInstant(),
    endDate = endDate?.toInstant(),
)

fun LiveTvTimerInfo.toEmbyTimerInfoDto(): LiveTvTimerInfoDto = LiveTvTimerInfoDto(
    id = id.ifEmpty { null },
    name = name,
    channelId = channelId,
    programId = programId,
    seriesTimerId = seriesTimerId,
    startDate = startDate?.atOffset(ZoneOffset.UTC),
    endDate = endDate?.atOffset(ZoneOffset.UTC),
    prePaddingSeconds = prePaddingSeconds,
    postPaddingSeconds = postPaddingSeconds,
)

fun EmbySortOrder?.toCoreSortOrder(): CoreSortOrder? = when (this) {
    EmbySortOrder.ASCENDING -> CoreSortOrder.ASCENDING
    EmbySortOrder.DESCENDING -> CoreSortOrder.DESCENDING
    null -> null
}

fun CoreSortOrder?.toEmbySortOrder(): EmbySortOrder? = when (this) {
    CoreSortOrder.ASCENDING -> EmbySortOrder.ASCENDING
    CoreSortOrder.DESCENDING -> EmbySortOrder.DESCENDING
    null -> null
}

fun org.emby.client.model.DisplayPreferences.toCoreDisplayPreferences(): CoreDisplayPreferences = CoreDisplayPreferences(
    id = id,
    sortBy = sortBy,
    sortOrder = sortOrder.toCoreSortOrder(),
    customPrefs = customPrefs,
    client = client,
)

fun CoreDisplayPreferences.toEmbyDisplayPreferences(): org.emby.client.model.DisplayPreferences = org.emby.client.model.DisplayPreferences(
    id = id,
    sortBy = sortBy,
    sortOrder = sortOrder.toEmbySortOrder(),
    customPrefs = customPrefs,
    client = client,
)
