package org.moonfin.server.core.model

data class StreamParams(
    val userId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val deviceId: String,
    val container: String,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val maxStreamingBitrate: Long? = null,
    val startTimeTicks: Long? = null,
)
