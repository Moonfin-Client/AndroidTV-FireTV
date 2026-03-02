package org.moonfin.server.emby.api

import org.emby.client.api.SystemServiceApi
import org.moonfin.server.core.api.ServerSystemApi
import org.moonfin.server.core.model.PublicSystemInfo
import org.moonfin.server.core.model.SystemInfo
import org.moonfin.server.emby.EmbyApiClient

class EmbySystemApi(private val apiClient: EmbyApiClient) : ServerSystemApi {

    override suspend fun getPublicSystemInfo(): PublicSystemInfo {
        val service = SystemServiceApi(baseUrl = apiClient.baseUrl)
        val info = service.getSystemInfoPublic().body()
        return PublicSystemInfo(
            serverName = info.serverName ?: "",
            version = info.version ?: "",
            productName = "Emby Server",
            id = info.id ?: "",
            startupWizardCompleted = null,
            localAddress = info.localAddress,
            wanAddress = info.wanAddress,
        )
    }

    override suspend fun getSystemInfo(): SystemInfo {
        val service = SystemServiceApi(baseUrl = apiClient.baseUrl).also {
            apiClient.accessToken?.let { token -> it.setApiKey(token) }
        }
        val info = service.getSystemInfo().body()
        return SystemInfo(
            serverName = info.serverName ?: "",
            version = info.version ?: "",
            productName = "Emby Server",
            id = info.id ?: "",
            localAddress = info.localAddress,
            wanAddress = info.wanAddress,
            operatingSystem = info.operatingSystem,
            httpServerPortNumber = info.httpServerPortNumber,
            httpsPortNumber = info.httpsPortNumber,
            webSocketPortNumber = info.webSocketPortNumber,
            hasPendingRestart = info.hasPendingRestart,
            isShuttingDown = info.isShuttingDown,
            canSelfRestart = info.canSelfRestart,
            canSelfUpdate = info.canSelfUpdate,
            startupWizardCompleted = null,
        )
    }
}
