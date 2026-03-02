package org.moonfin.server.emby.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.emby.client.api.SystemServiceApi
import org.moonfin.server.core.api.ServerDiscoveryApi
import org.moonfin.server.core.model.DiscoveredServer
import org.moonfin.server.core.model.PublicSystemInfo
import org.moonfin.server.core.model.ServerType
import org.moonfin.server.core.model.ServerValidationResult
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class EmbyServerDiscovery : ServerDiscoveryApi {

    companion object {
        private const val DISCOVERY_PORT = 7359
        private const val DISCOVERY_MESSAGE = "who is EmbyServer?"
        private const val RECEIVE_TIMEOUT_MS = 3000
        private const val DISCOVERY_ROUNDS = 3
        private const val ROUND_DELAY_MS = 1500L
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun discoverLocalServers(): Flow<DiscoveredServer> = flow {
        val seen = mutableSetOf<String>()

        repeat(DISCOVERY_ROUNDS) {
            if (!currentCoroutineContext().isActive) return@repeat

            try {
                val servers = sendDiscoveryBroadcast()
                for (server in servers) {
                    if (seen.add(server.id)) {
                        emit(server)
                    }
                }
            } catch (e: Exception) {
                Timber.d(e, "Emby discovery round failed")
            }

            if (it < DISCOVERY_ROUNDS - 1) {
                delay(ROUND_DELAY_MS)
            }
        }
    }

    override suspend fun getAddressCandidates(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()

        val candidates = mutableListOf<String>()

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            candidates.add(trimmed.trimEnd('/'))
        } else {
            candidates.add("http://$trimmed:8096")
            candidates.add("https://$trimmed:8920")
            candidates.add("http://$trimmed")
            candidates.add("https://$trimmed")
        }
        return candidates
    }

    override suspend fun validateServer(address: String): ServerValidationResult {
        return try {
            val service = SystemServiceApi(baseUrl = address)
            val info = service.getSystemInfoPublic().body()
            ServerValidationResult(
                address = address,
                isValid = true,
                serverType = ServerType.EMBY,
                systemInfo = PublicSystemInfo(
                    serverName = info.serverName ?: "",
                    version = info.version ?: "",
                    productName = "Emby Server",
                    id = info.id ?: "",
                    startupWizardCompleted = null,
                    localAddress = info.localAddress,
                    wanAddress = info.wanAddress,
                ),
                errorMessage = null,
            )
        } catch (e: Exception) {
            ServerValidationResult(
                address = address,
                isValid = false,
                serverType = null,
                systemInfo = null,
                errorMessage = e.message,
            )
        }
    }

    private suspend fun sendDiscoveryBroadcast(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiscoveredServer>()

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = RECEIVE_TIMEOUT_MS

                val data = DISCOVERY_MESSAGE.toByteArray()
                val packet = DatagramPacket(
                    data, data.size,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT,
                )
                socket.send(packet)

                val buffer = ByteArray(4096)
                while (true) {
                    try {
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)
                        val responseText = String(response.data, 0, response.length)
                        parseDiscoveryResponse(responseText)?.let { results.add(it) }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Discovery broadcast failed")
        }

        results
    }

    private fun parseDiscoveryResponse(response: String): DiscoveredServer? {
        return try {
            val obj = json.parseToJsonElement(response).jsonObject
            val address = obj["Address"]?.jsonPrimitive?.content ?: return null
            val id = obj["Id"]?.jsonPrimitive?.content ?: return null
            val name = obj["Name"]?.jsonPrimitive?.content ?: id
            DiscoveredServer(id = id, name = name, address = address)
        } catch (e: Exception) {
            Timber.d(e, "Failed to parse discovery response: $response")
            null
        }
    }
}
