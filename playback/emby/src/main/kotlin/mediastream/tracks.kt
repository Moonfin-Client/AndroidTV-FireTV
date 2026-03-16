package org.moonfin.playback.emby.mediastream

import org.emby.client.model.MediaStream
import org.emby.client.model.MediaStreamType
import org.jellyfin.playback.core.mediastream.MediaStreamAudioTrack
import org.jellyfin.playback.core.mediastream.MediaStreamContainer
import org.jellyfin.playback.core.mediastream.MediaStreamVideoTrack

fun MediaInfo.getMediaStreamContainer() = MediaStreamContainer(
	format = requireNotNull(mediaSource.container)
)

fun MediaInfo.getTracks() =
	mediaSource.mediaStreams
		.orEmpty()
		.mapNotNull(MediaStream::toTrack)

private fun MediaStream.toTrack() = when (type) {
	MediaStreamType.AUDIO -> MediaStreamAudioTrack(
		codec = requireNotNull(codec),
		bitrate = bitRate ?: 0,
		channels = channels ?: 1,
		sampleRate = sampleRate ?: 0,
	)
	MediaStreamType.VIDEO -> MediaStreamVideoTrack(
		codec = requireNotNull(codec),
	)
	else -> null
}
