package org.moonfin.server.core.model

data class PlaybackInfoRequest(
    val userId: String,
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val maxStreamingBitrate: Long? = null,
    val startTimeTicks: Long? = null,
    val enableDirectPlay: Boolean = true,
    val enableDirectStream: Boolean = true,
    val enableTranscoding: Boolean = true,
    val allowVideoStreamCopy: Boolean = true,
    val allowAudioStreamCopy: Boolean = true,
)
