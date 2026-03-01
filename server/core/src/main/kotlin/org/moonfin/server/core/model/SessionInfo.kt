package org.moonfin.server.core.model

import java.time.Instant

data class SessionInfo(
    val id: String,
    val userId: String?,
    val userName: String?,
    val client: String?,
    val deviceId: String?,
    val deviceName: String?,
    val applicationVersion: String?,
    val remoteEndPoint: String?,
    val nowPlayingItemId: String?,
    val nowPlayingItemName: String?,
    val lastActivityDate: Instant?,
    val supportsRemoteControl: Boolean,
)
