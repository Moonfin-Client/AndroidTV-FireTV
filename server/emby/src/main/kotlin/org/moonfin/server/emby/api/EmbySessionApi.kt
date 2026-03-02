package org.moonfin.server.emby.api

import org.moonfin.server.core.api.ServerSessionApi
import org.moonfin.server.core.model.ClientCapabilities
import org.moonfin.server.core.model.MediaType
import org.moonfin.server.core.model.SessionInfo
import org.moonfin.server.emby.EmbyApiClient

class EmbySessionApi(private val apiClient: EmbyApiClient) : ServerSessionApi {

    override suspend fun postCapabilities(capabilities: ClientCapabilities) {
        apiClient.postCapabilities(
            playableMediaTypes = capabilities.playableMediaTypes.joinToString(",") { it.toEmbyString() },
            supportedCommands = capabilities.supportedCommands.joinToString(","),
            supportsMediaControl = capabilities.supportsMediaControl,
        )
    }

    override suspend fun getSessions(): List<SessionInfo> {
        val sessions = apiClient.sessionsService!!.getSessions(
            controllableByUserId = null,
            deviceId = null,
            id = null,
        ).body()
        return sessions.map { s ->
            SessionInfo(
                id = s.id ?: "",
                userId = s.userId,
                userName = s.userName,
                client = s.client,
                deviceId = s.deviceId,
                deviceName = s.deviceName,
                applicationVersion = s.applicationVersion,
                remoteEndPoint = s.remoteEndPoint,
                nowPlayingItemId = s.nowPlayingItem?.id,
                nowPlayingItemName = s.nowPlayingItem?.name,
                lastActivityDate = s.lastActivityDate?.toInstant(),
                supportsRemoteControl = s.supportsRemoteControl ?: false,
            )
        }
    }
}

private fun MediaType.toEmbyString(): String = when (this) {
    MediaType.VIDEO -> "Video"
    MediaType.AUDIO -> "Audio"
    MediaType.PHOTO -> "Photo"
    MediaType.BOOK -> "Book"
    MediaType.UNKNOWN -> "Unknown"
}
