package org.moonfin.server.emby

import org.moonfin.server.core.api.MediaServerClient
import org.moonfin.server.core.api.ServerAuthApi
import org.moonfin.server.core.api.ServerDisplayPreferencesApi
import org.moonfin.server.core.api.ServerImageApi
import org.moonfin.server.core.api.ServerInstantMixApi
import org.moonfin.server.core.api.ServerItemsApi
import org.moonfin.server.core.api.ServerLiveTvApi
import org.moonfin.server.core.api.ServerPlaybackApi
import org.moonfin.server.core.api.ServerSessionApi
import org.moonfin.server.core.api.ServerSystemApi
import org.moonfin.server.core.api.ServerUserLibraryApi
import org.moonfin.server.core.api.ServerUserViewsApi
import org.moonfin.server.core.model.DeviceInfo
import org.moonfin.server.core.model.ServerType
import org.moonfin.server.emby.api.EmbyAuthApi
import org.moonfin.server.emby.api.EmbyDisplayPreferencesApi
import org.moonfin.server.emby.api.EmbyImageApi
import org.moonfin.server.emby.api.EmbyInstantMixApi
import org.moonfin.server.emby.api.EmbyItemsApi
import org.moonfin.server.emby.api.EmbyLiveTvApi
import org.moonfin.server.emby.api.EmbyPlaybackApi
import org.moonfin.server.emby.api.EmbySessionApi
import org.moonfin.server.emby.api.EmbySystemApi
import org.moonfin.server.emby.api.EmbyUserLibraryApi
import org.moonfin.server.emby.api.EmbyUserViewsApi

class EmbyMediaServerClient(
    private var deviceInfo: DeviceInfo,
) : MediaServerClient {

    override val serverType: ServerType = ServerType.EMBY

    private var apiClient: EmbyApiClient = createApiClient(deviceInfo)

    override val baseUrl: String? get() = apiClient.baseUrl.ifEmpty { null }
    override val accessToken: String? get() = apiClient.accessToken

    override val authApi: ServerAuthApi get() = EmbyAuthApi(apiClient)
    override val itemsApi: ServerItemsApi get() = EmbyItemsApi(apiClient)
    override val userLibraryApi: ServerUserLibraryApi get() = EmbyUserLibraryApi(apiClient)
    override val playbackApi: ServerPlaybackApi get() = EmbyPlaybackApi(apiClient)
    override val sessionApi: ServerSessionApi get() = EmbySessionApi(apiClient)
    override val imageApi: ServerImageApi get() = EmbyImageApi(apiClient)
    override val systemApi: ServerSystemApi get() = EmbySystemApi(apiClient)
    override val userViewsApi: ServerUserViewsApi get() = EmbyUserViewsApi(apiClient)
    override val liveTvApi: ServerLiveTvApi get() = EmbyLiveTvApi(apiClient)
    override val instantMixApi: ServerInstantMixApi get() = EmbyInstantMixApi(apiClient)
    override val displayPreferencesApi: ServerDisplayPreferencesApi get() = EmbyDisplayPreferencesApi(apiClient)

    override fun configure(baseUrl: String, accessToken: String?, userId: String?, deviceInfo: DeviceInfo) {
        this.deviceInfo = deviceInfo
        apiClient = createApiClient(deviceInfo)
        apiClient.configure(baseUrl, accessToken, userId)
    }

    override fun createForServer(baseUrl: String, accessToken: String?, deviceInfo: DeviceInfo): MediaServerClient {
        val client = EmbyMediaServerClient(deviceInfo)
        client.apiClient.configure(baseUrl, accessToken, null)
        return client
    }

    private fun createApiClient(info: DeviceInfo) = EmbyApiClient(
        appVersion = info.appVersion,
        clientName = info.appName,
        deviceId = info.id,
        deviceName = info.name,
    )
}
