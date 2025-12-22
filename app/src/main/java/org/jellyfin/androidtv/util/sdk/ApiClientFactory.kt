package org.jellyfin.androidtv.util.sdk

import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.util.UUIDUtils
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.util.UUID

/**
 * Factory for creating ApiClient instances for specific servers.
 */
class ApiClientFactory(
	private val jellyfin: Jellyfin,
	private val authenticationStore: AuthenticationStore,
	private val defaultDeviceInfo: DeviceInfo,
) {
	fun getApiClient(serverId: UUID, userId: UUID? = null): ApiClient? {
		val server = authenticationStore.getServer(serverId)
		if (server == null) {
			Timber.w("ApiClientFactory: Server $serverId not found")
			return null
		}

		val accessToken = if (userId != null) {
			val user = authenticationStore.getUser(serverId, userId)
			if (user?.accessToken == null) {
				Timber.w("ApiClientFactory: User $userId on server $serverId has no access token")
				return null
			}
			user.accessToken
		} else {
			val users = authenticationStore.getServer(serverId)?.users
			if (users.isNullOrEmpty()) {
				Timber.w("ApiClientFactory: Server $serverId has no users")
				return null
			}
			
			val userWithToken = users.entries.firstOrNull { (_, user) ->
				!user.accessToken.isNullOrBlank()
			}
			
			if (userWithToken == null) {
				Timber.w("ApiClientFactory: Server $serverId has no users with access tokens")
				return null
			}
			
			userWithToken.value.accessToken
		}

		val deviceInfo = if (userId != null) {
			defaultDeviceInfo.forUser(userId)
		} else {
			defaultDeviceInfo
		}

		return jellyfin.createApi(
			baseUrl = server.address,
			accessToken = accessToken,
			deviceInfo = deviceInfo
		)
	}

	fun getApiClientForServer(serverId: UUID): ApiClient? = getApiClient(serverId, null)

	fun getApiClientForItem(item: BaseItemDto): ApiClient? {
		val uuid = UUIDUtils.parseUUID(item.serverId) ?: return null
		return getApiClientForServer(uuid)
	}

	fun getApiClientForItemOrFallback(item: BaseItemDto, fallback: ApiClient): ApiClient {
		return getApiClientForItem(item) ?: fallback
	}
}
