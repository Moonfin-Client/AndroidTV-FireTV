package org.moonfin.server.jellyfin.mapper

import java.time.ZoneOffset
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType as JfImageType
import org.jellyfin.sdk.model.api.MediaProtocol as JfMediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo as JfMediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream as JfMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType as JfMediaStreamType
import org.jellyfin.sdk.model.api.MediaType as JfMediaType
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserItemDataDto
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
import org.moonfin.server.core.model.UserItemData

fun BaseItemKind.toItemType(): ItemType = when (this) {
    BaseItemKind.MOVIE -> ItemType.MOVIE
    BaseItemKind.SERIES -> ItemType.SERIES
    BaseItemKind.SEASON -> ItemType.SEASON
    BaseItemKind.EPISODE -> ItemType.EPISODE
    BaseItemKind.AUDIO -> ItemType.AUDIO
    BaseItemKind.MUSIC_ALBUM -> ItemType.MUSIC_ALBUM
    BaseItemKind.MUSIC_ARTIST -> ItemType.MUSIC_ARTIST
    BaseItemKind.PLAYLIST -> ItemType.PLAYLIST
    BaseItemKind.PHOTO -> ItemType.PHOTO
    BaseItemKind.PHOTO_ALBUM -> ItemType.PHOTO_ALBUM
    BaseItemKind.BOX_SET -> ItemType.BOX_SET
    BaseItemKind.CHANNEL -> ItemType.CHANNEL
    BaseItemKind.PROGRAM -> ItemType.PROGRAM
    BaseItemKind.RECORDING -> ItemType.RECORDING
    BaseItemKind.LIVE_TV_CHANNEL -> ItemType.LIVE_TV_CHANNEL
    BaseItemKind.LIVE_TV_PROGRAM -> ItemType.LIVE_TV_PROGRAM
    BaseItemKind.BOOK -> ItemType.BOOK
    BaseItemKind.TRAILER -> ItemType.TRAILER
    BaseItemKind.PERSON -> ItemType.PERSON
    BaseItemKind.STUDIO -> ItemType.STUDIO
    BaseItemKind.GENRE -> ItemType.GENRE
    BaseItemKind.MUSIC_GENRE -> ItemType.MUSIC_GENRE
    BaseItemKind.USER_VIEW -> ItemType.USER_VIEW
    BaseItemKind.COLLECTION_FOLDER -> ItemType.COLLECTION_FOLDER
    BaseItemKind.FOLDER -> ItemType.FOLDER
    BaseItemKind.BASE_PLUGIN_FOLDER -> ItemType.BASE_PLUGIN_FOLDER
    BaseItemKind.MUSIC_VIDEO -> ItemType.MUSIC_VIDEO
    BaseItemKind.VIDEO -> ItemType.VIDEO
    else -> ItemType.UNKNOWN
}

fun JfImageType.toImageType(): ImageType = when (this) {
    JfImageType.PRIMARY -> ImageType.PRIMARY
    JfImageType.ART -> ImageType.ART
    JfImageType.BACKDROP -> ImageType.BACKDROP
    JfImageType.BANNER -> ImageType.BANNER
    JfImageType.LOGO -> ImageType.LOGO
    JfImageType.THUMB -> ImageType.THUMB
    JfImageType.SCREENSHOT -> ImageType.SCREENSHOT
    else -> ImageType.PRIMARY
}

fun JfMediaType.toMediaType(): MediaType = when (this) {
    JfMediaType.VIDEO -> MediaType.VIDEO
    JfMediaType.AUDIO -> MediaType.AUDIO
    JfMediaType.PHOTO -> MediaType.PHOTO
    JfMediaType.BOOK -> MediaType.BOOK
    else -> MediaType.UNKNOWN
}

fun JfMediaProtocol.toMediaProtocol(): MediaProtocol = when (this) {
    JfMediaProtocol.FILE -> MediaProtocol.FILE
    JfMediaProtocol.HTTP -> MediaProtocol.HTTP
    JfMediaProtocol.RTMP -> MediaProtocol.RTMP
    JfMediaProtocol.RTSP -> MediaProtocol.RTSP
    JfMediaProtocol.UDP -> MediaProtocol.UDP
    JfMediaProtocol.RTP -> MediaProtocol.RTP
    JfMediaProtocol.FTP -> MediaProtocol.FTP
    else -> MediaProtocol.HTTP
}

fun JfMediaStreamType.toStreamType(): StreamType = when (this) {
    JfMediaStreamType.VIDEO -> StreamType.VIDEO
    JfMediaStreamType.AUDIO -> StreamType.AUDIO
    JfMediaStreamType.SUBTITLE -> StreamType.SUBTITLE
    JfMediaStreamType.EMBEDDED_IMAGE -> StreamType.EMBEDDED_IMAGE
    JfMediaStreamType.DATA -> StreamType.DATA
    else -> StreamType.DATA
}

fun PersonKind.toPersonType(): PersonType = when (this) {
    PersonKind.ACTOR -> PersonType.ACTOR
    PersonKind.DIRECTOR -> PersonType.DIRECTOR
    PersonKind.WRITER -> PersonType.WRITER
    PersonKind.PRODUCER -> PersonType.PRODUCER
    PersonKind.GUEST_STAR -> PersonType.GUEST_STAR
    PersonKind.COMPOSER -> PersonType.COMPOSER
    PersonKind.CONDUCTOR -> PersonType.CONDUCTOR
    PersonKind.LYRICIST -> PersonType.LYRICIST
    else -> PersonType.UNKNOWN
}

fun UserItemDataDto.toUserItemData(): UserItemData = UserItemData(
    playedPercentage = playedPercentage,
    unplayedItemCount = unplayedItemCount,
    playbackPositionTicks = playbackPositionTicks,
    playCount = playCount,
    isFavorite = isFavorite,
    lastPlayedDate = lastPlayedDate?.toInstant(ZoneOffset.UTC),
    played = played,
    key = key,
    itemId = itemId.toString(),
)

fun JfMediaStream.toServerMediaStream(): ServerMediaStream = ServerMediaStream(
    index = index,
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

fun JfMediaSourceInfo.toServerMediaSource(): ServerMediaSource = ServerMediaSource(
    id = id ?: "",
    name = name,
    container = container,
    protocol = protocol?.toMediaProtocol() ?: MediaProtocol.HTTP,
    supportsDirectPlay = supportsDirectPlay ?: false,
    supportsDirectStream = supportsDirectStream ?: false,
    supportsTranscoding = supportsTranscoding ?: false,
    transcodingUrl = transcodingUrl,
    eTag = eTag,
    liveStreamId = liveStreamId,
    isRemote = isRemote ?: false,
    bitrate = bitrate,
    mediaStreams = mediaStreams?.map { it.toServerMediaStream() } ?: emptyList(),
    defaultAudioStreamIndex = defaultAudioStreamIndex,
    defaultSubtitleStreamIndex = defaultSubtitleStreamIndex,
)

fun BaseItemPerson.toServerPerson(): ServerPerson = ServerPerson(
    id = id?.toString(),
    name = name ?: "",
    role = role,
    type = type?.toPersonType() ?: PersonType.UNKNOWN,
    primaryImageTag = primaryImageTag,
)

fun NameGuidPair.toNameIdPair(): NameIdPair = NameIdPair(
    name = name,
    id = id.toString(),
)

fun UserDto.toServerUser(): ServerUser = ServerUser(
    id = id.toString(),
    name = name ?: "",
    serverName = null,
    primaryImageTag = primaryImageTag,
    hasPassword = hasPassword,
    hasConfiguredPassword = hasConfiguredPassword,
    lastLoginDate = lastLoginDate?.toInstant(ZoneOffset.UTC),
    lastActivityDate = lastActivityDate?.toInstant(ZoneOffset.UTC),
)

fun BaseItemDto.toServerItem(): ServerItem = ServerItem(
    id = id.toString(),
    serverId = serverId,
    name = name ?: "",
    originalTitle = originalTitle,
    type = type?.toItemType() ?: ItemType.UNKNOWN,
    mediaType = mediaType?.toMediaType(),
    overview = overview,
    runTimeTicks = runTimeTicks,
    premiereDate = premiereDate?.toInstant(ZoneOffset.UTC),
    productionYear = productionYear,
    officialRating = officialRating,
    communityRating = communityRating?.toDouble(),
    criticRating = criticRating?.toDouble(),
    isFolder = isFolder ?: false,
    parentId = parentId?.toString(),
    seriesId = seriesId?.toString(),
    seriesName = seriesName,
    seasonId = seasonId?.toString(),
    indexNumber = indexNumber,
    parentIndexNumber = parentIndexNumber,
    imageTags = imageTags?.mapKeys { it.key.toImageType() } ?: emptyMap(),
    backdropImageTags = backdropImageTags ?: emptyList(),
    primaryImageAspectRatio = primaryImageAspectRatio,
    userData = userData?.toUserItemData(),
    mediaSources = mediaSources?.map { it.toServerMediaSource() },
    mediaStreams = mediaStreams?.map { it.toServerMediaStream() },
    container = container,
    channelId = channelId?.toString(),
    channelName = channelName,
    people = people?.map { it.toServerPerson() },
    genres = genres,
    tags = tags,
    studios = studios?.map { it.toNameIdPair() },
    trickplay = null,
    mediaSegments = null,
)
