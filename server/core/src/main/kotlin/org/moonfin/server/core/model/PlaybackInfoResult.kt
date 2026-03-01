package org.moonfin.server.core.model

data class PlaybackInfoResult(
    val mediaSources: List<ServerMediaSource>,
    val playSessionId: String?,
    val errorCode: PlaybackErrorCode?,
)
