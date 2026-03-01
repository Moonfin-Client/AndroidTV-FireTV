package org.jellyfin.androidtv.util.sdk

import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.util.UUIDUtils
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.moonfin.server.core.model.ServerType
import timber.log.Timber
import java.util.UUID

class ApiClientFactory(
	private val jellyfin: Jellyfin,
	private val authenticationStore: AuthenticationStore,
	private val defaultDeviceInfo: DeviceInfo,
	private val sessionRepository: SessionRepository,
) {
	fun getApiClient(serverId: UUID, userId: UUID? = null): ApiClient? {
		val server = authenticationStore.getServer(serverId)
		if (server == null) {
			Timber.w("ApiClientFactory: Server $serverId not found")
			return null
		}
		if (server.serverType == ServerType.EMBY) {
			Timber.w("ApiClientFactory: Server $serverId is an Emby server — Jellyfin ApiClient not applicable")
			return null
		}

		val resolvedUserId: UUID?
		val accessToken: String?

		if (userId != null) {
			resolvedUserId = userId
			val user = authenticationStore.getUser(serverId, userId)
			if (user?.accessToken == null) {
				Timber.w("ApiClientFactory: User $userId on server $serverId has no access token")
				return null
			}
			accessToken = user.accessToken
		} else {
			// Prefer the current session's user for the current server
			val currentSession = sessionRepository.currentSession.value
			val preferredEntry = if (currentSession != null && currentSession.serverId == serverId) {
				val currentUser = authenticationStore.getUser(serverId, currentSession.userId)
				if (currentUser?.accessToken != null) {
					currentSession.userId to currentUser.accessToken
				} else null
			} else null

			if (preferredEntry != null) {
				resolvedUserId = preferredEntry.first
				accessToken = preferredEntry.second
			} else {
				// Fallback: first user with a token (for non-current servers)
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

				resolvedUserId = userWithToken.key
				accessToken = userWithToken.value.accessToken
			}
		}

		val deviceInfo = if (resolvedUserId != null) {
			defaultDeviceInfo.forUser(resolvedUserId)
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
