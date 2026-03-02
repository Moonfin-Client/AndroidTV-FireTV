package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.PrivateUser
import org.jellyfin.androidtv.auth.model.PublicUser
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.util.sdk.toPublicUser
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.UserDto
import org.moonfin.server.core.model.ServerType
import org.moonfin.server.emby.EmbyApiClient
import timber.log.Timber
import java.util.UUID

/**
 * Repository to maintain users for servers.
 * Authentication is done using the [AuthenticationRepository].
 */
interface ServerUserRepository {
	fun getStoredServerUsers(server: Server): List<PrivateUser>
	suspend fun getPublicServerUsers(server: Server): List<PublicUser>

	fun deleteStoredUser(user: PrivateUser)
}

class ServerUserRepositoryImpl(
	private val jellyfin: Jellyfin,
	private val embyApiClient: EmbyApiClient,
	private val authenticationStore: AuthenticationStore,
) : ServerUserRepository {
	override fun getStoredServerUsers(server: Server) = authenticationStore.getUsers(server.id)
		?.mapNotNull { (userId, userInfo) ->
			val authInfo = authenticationStore.getUser(server.id, userId)
			PrivateUser(
				id = userId,
				serverId = server.id,
				name = userInfo.name,
				accessToken = authInfo?.accessToken,
				imageTag = userInfo.imageTag,
				lastUsed = userInfo.lastUsed,
			)
		}
		?.sortedWith(compareByDescending<PrivateUser> { it.lastUsed }.thenBy { it.name })
		.orEmpty()

	override suspend fun getPublicServerUsers(server: Server): List<PublicUser> {
		return when (server.serverType) {
			ServerType.EMBY -> getEmbyPublicUsers(server)
			ServerType.JELLYFIN -> getJellyfinPublicUsers(server)
		}
	}

	private suspend fun getJellyfinPublicUsers(server: Server): List<PublicUser> {
		val api = jellyfin.createApi(server.address)

		return try {
			val users = withContext(Dispatchers.IO) {
				api.userApi.getPublicUsers().content
			}
			users.mapNotNull(UserDto::toPublicUser)
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to retrieve public users from Jellyfin")
			emptyList()
		}
	}

	private suspend fun getEmbyPublicUsers(server: Server): List<PublicUser> {
		return try {
			val tempClient = EmbyApiClient(
				appVersion = "1.0.0",
				clientName = "Moonfin",
				deviceId = embyApiClient.deviceId,
				deviceName = "AndroidTV",
			)
			tempClient.configure(server.address, null, null)
			val userService = tempClient.userService ?: return emptyList()

			val users = withContext(Dispatchers.IO) {
				userService.getUsersPublic().body()
			}
			users.mapNotNull { dto ->
				val id = dto.id?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@mapNotNull null
				val name = dto.name ?: return@mapNotNull null
				PublicUser(
					id = id,
					serverId = server.id,
					name = name,
					accessToken = null,
					imageTag = dto.primaryImageTag,
				)
			}
		} catch (err: Exception) {
			Timber.e(err, "Unable to retrieve public users from Emby")
			emptyList()
		}
	}

	override fun deleteStoredUser(user: PrivateUser) {
		authenticationStore.removeUser(user.serverId, user.id)
	}
}
