package org.moonfin.server.core.api

import org.moonfin.server.core.model.DeviceInfo
import org.moonfin.server.core.model.ServerType

interface MediaServerClient {
	val serverType: ServerType
	val baseUrl: String?
	val accessToken: String?
	val isUsable: Boolean get() = baseUrl != null && accessToken != null

	fun configure(baseUrl: String, accessToken: String? = null, userId: String? = null, deviceInfo: DeviceInfo)
	fun createForServer(baseUrl: String, accessToken: String? = null, deviceInfo: DeviceInfo): MediaServerClient

	val authApi: ServerAuthApi
	val itemsApi: ServerItemsApi
	val userLibraryApi: ServerUserLibraryApi
	val playbackApi: ServerPlaybackApi
	val sessionApi: ServerSessionApi
	val imageApi: ServerImageApi
	val systemApi: ServerSystemApi
	val userViewsApi: ServerUserViewsApi
	val liveTvApi: ServerLiveTvApi
	val instantMixApi: ServerInstantMixApi
}
