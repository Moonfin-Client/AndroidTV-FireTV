package org.moonfin.server.core.api

import kotlinx.coroutines.flow.Flow
import org.moonfin.server.core.model.ServerWebSocketMessage

interface ServerWebSocketApi {
    suspend fun connect()
    suspend fun disconnect()
    val messages: Flow<ServerWebSocketMessage>
}
