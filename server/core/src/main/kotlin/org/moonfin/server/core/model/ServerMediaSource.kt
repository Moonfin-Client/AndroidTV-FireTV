package org.moonfin.server.core.model

data class ServerMediaSource(
    val id: String,
    val name: String?,
    val container: String?,
    val protocol: MediaProtocol,
    val supportsDirectPlay: Boolean,
    val supportsDirectStream: Boolean,
    val supportsTranscoding: Boolean,
    val transcodingUrl: String?,
    val eTag: String?,
    val liveStreamId: String?,
    val isRemote: Boolean,
    val bitrate: Int?,
    val mediaStreams: List<ServerMediaStream>,
    val defaultAudioStreamIndex: Int?,
    val defaultSubtitleStreamIndex: Int?,
)
