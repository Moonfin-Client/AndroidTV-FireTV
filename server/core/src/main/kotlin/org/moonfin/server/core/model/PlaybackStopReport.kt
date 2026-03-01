package org.moonfin.server.core.model

data class PlaybackStopReport(
    val itemId: String,
    val playSessionId: String,
    val mediaSourceId: String,
    val positionTicks: Long,
    val failed: Boolean,
)
