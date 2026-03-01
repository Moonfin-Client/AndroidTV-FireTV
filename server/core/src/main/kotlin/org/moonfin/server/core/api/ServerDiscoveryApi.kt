package org.moonfin.server.core.api

import kotlinx.coroutines.flow.Flow
import org.moonfin.server.core.model.DiscoveredServer
import org.moonfin.server.core.model.ServerValidationResult

interface ServerDiscoveryApi {
    fun discoverLocalServers(): Flow<DiscoveredServer>
    suspend fun getAddressCandidates(input: String): List<String>
    suspend fun validateServer(address: String): ServerValidationResult
}
