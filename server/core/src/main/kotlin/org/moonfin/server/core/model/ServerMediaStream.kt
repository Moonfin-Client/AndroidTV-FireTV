package org.moonfin.server.core.model

data class ServerMediaStream(
    val index: Int,
    val type: StreamType,
    val codec: String?,
    val language: String?,
    val displayTitle: String?,
    val isDefault: Boolean,
    val isForced: Boolean,
    val isExternal: Boolean,
    val path: String?,
    val width: Int?,
    val height: Int?,
    val channels: Int?,
    val sampleRate: Int?,
    val bitRate: Int?,
    val isTextSubtitleStream: Boolean,
    val deliveryUrl: String?,
)
