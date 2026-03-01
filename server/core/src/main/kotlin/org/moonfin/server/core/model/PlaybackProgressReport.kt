package org.moonfin.server.core.model

data class PlaybackProgressReport(
    val itemId: String,
    val playSessionId: String,
    val mediaSourceId: String,
    val positionTicks: Long,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val playMethod: PlayMethod,
    val isPaused: Boolean,
    val isMuted: Boolean,
    val volumeLevel: Int,
)
