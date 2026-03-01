package org.moonfin.server.core.model

sealed class ServerWebSocketMessage {
    data class LibraryChanged(
        val itemsAdded: List<String>,
        val itemsUpdated: List<String>,
        val itemsRemoved: List<String>,
    ) : ServerWebSocketMessage()

    data class UserDataChanged(
        val userId: String,
        val itemIds: List<String>,
    ) : ServerWebSocketMessage()

    data class Play(
        val itemIds: List<String>,
        val startPositionTicks: Long?,
        val playCommand: String,
    ) : ServerWebSocketMessage()

    data class Playstate(
        val command: String,
        val seekPositionTicks: Long?,
    ) : ServerWebSocketMessage()

    data class GeneralCommand(
        val name: String,
        val arguments: Map<String, String>,
    ) : ServerWebSocketMessage()

    data object ServerRestarting : ServerWebSocketMessage()
    data object ServerShuttingDown : ServerWebSocketMessage()

    data class SessionEnded(val sessionId: String) : ServerWebSocketMessage()
}
