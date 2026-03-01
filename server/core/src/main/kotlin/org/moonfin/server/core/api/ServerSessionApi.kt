package org.moonfin.server.core.api

import org.moonfin.server.core.model.ClientCapabilities
import org.moonfin.server.core.model.SessionInfo

interface ServerSessionApi {
    suspend fun postCapabilities(capabilities: ClientCapabilities)
    suspend fun getSessions(): List<SessionInfo>
}
